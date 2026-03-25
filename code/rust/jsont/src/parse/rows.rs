// =============================================================================
// parse/rows.rs — Hand-written streaming byte scanner for JsonT data rows
// =============================================================================
// Replaces the pest-based path for data-row parsing.
//
// WHY: pest builds a complete parse tree for the entire input in memory before
// walking a single node.  For 1 M rows (42 MB) that tree consumes ~3.8 GB —
// roughly 90× the input size — and parse throughput stalls around 300 rec/ms.
// This scanner processes bytes left-to-right, emits each JsonTRow to a
// callback as it is completed, and uses O(1) extra memory regardless of how
// many rows the input contains.
//
// Grammar subset handled (see jsont.pest for the full grammar):
//
//   data_rows    = data_row ("," data_row)* ","?
//   data_row     = object_value
//   object_value = "{" value ("," value)* "}"   -- at least one value
//   array_value  = "[" value ("," value)* "]"   -- empty arrays allowed
//   value        = string | number | boolean | null | unspecified
//                | enum_constant | object_value | array_value
//
// Numbers are always emitted as JsonTValue::d64(f64) — the parser has no
// schema context, so it cannot pick the precise numeric variant.  This
// matches the behaviour of the previous pest-based path.
//
// Strings are stored RAW (backslash sequences not unescaped), also matching
// the previous pest path which used pair.as_str().trim_matches(quote).
// =============================================================================

use std::io::{self, BufRead};

use crate::error::{JsonTError, ParseError};
use crate::model::data::{JsonTArray, JsonTRow, JsonTValue};

// =============================================================================
// Public API
// =============================================================================

/// Parse all data rows from `input`, calling `on_row` once per completed row.
///
/// `input` must contain data rows only — no namespace block.
/// Returns the total number of rows parsed.
///
/// # Example
/// ```text
/// { 1, "alice", ACTIVE },
/// { 2, "bob",   INACTIVE }
/// ```
pub fn parse_rows(
    input: &str,
    mut on_row: impl FnMut(JsonTRow),
) -> Result<usize, JsonTError> {
    let mut s = Scanner::new(input.as_bytes());
    s.skip_ws();

    if s.is_eof() {
        return Ok(0);
    }

    let mut count = 0usize;
    loop {
        let row = s.parse_row()?;
        on_row(row);
        count += 1;

        s.skip_ws();
        if !s.try_consume(b',') {
            break;
        }
        s.skip_ws();
        if s.is_eof() {
            break; // trailing comma after last row is allowed [D-4]
        }
    }

    s.skip_ws();
    if !s.is_eof() {
        return Err(ParseError::Unexpected(format!(
            "unexpected content after last data row at byte offset {}",
            s.pos
        ))
        .into());
    }

    Ok(count)
}

// =============================================================================
// Streaming API — BufRead-based, O(1) memory regardless of file size
// =============================================================================

/// Parse data rows from any [`BufRead`] source, calling `on_row` once per row.
///
/// Unlike [`parse_rows`], this never loads the whole input into memory.
/// The internal read buffer stays at O(BufReader capacity) — typically 8 KB —
/// and `current_row` holds at most one row at a time (~1 KB for a complex schema).
///
/// # Memory profile
/// - Read buffer:   O(buf_capacity) — usually 8 KB from `BufReader<File>`.
/// - Row buffer:    O(one row)      — cleared immediately after `on_row` returns.
/// - Accumulation: none            — rows are never collected into a `Vec`.
pub fn parse_rows_streaming(
    reader: impl BufRead,
    mut on_row: impl FnMut(JsonTRow),
) -> Result<usize, JsonTError> {
    let mut ext      = RowBytesExtractor::new(reader);
    let mut row_buf  = Vec::<u8>::with_capacity(1024);
    let mut count    = 0usize;

    loop {
        match ext.next_row_bytes(&mut row_buf) {
            Ok(true)  => {
                let row = Scanner::new(&row_buf).parse_row()?;
                on_row(row);
                count += 1;
            }
            Ok(false) => break,
            Err(e)    => {
                return Err(
                    ParseError::Unexpected(format!("I/O error during streaming parse: {}", e))
                        .into(),
                );
            }
        }
    }

    Ok(count)
}

/// Iterator that lazily yields [`JsonTRow`] values from any [`BufRead`] source.
///
/// Useful when you need to pass a streaming row source to any API that accepts
/// `IntoIterator<Item = JsonTRow>` — for example [`ValidationPipeline::validate_each`].
///
/// # Memory profile
/// O(1): only one row's worth of bytes is live at a time.
///
/// # Example
/// ```no_run
/// use std::fs::File;
/// use std::io::BufReader;
/// use jsont::RowIter;
///
/// let f = File::open("data.jsont").unwrap();
/// for row in RowIter::new(BufReader::new(f)) {
///     println!("{:?}", row);
/// }
/// ```
pub struct RowIter<R: BufRead> {
    ext:      RowBytesExtractor<R>,
    row_buf:  Vec<u8>,
    done:     bool,
}

impl<R: BufRead> RowIter<R> {
    pub fn new(reader: R) -> Self {
        Self {
            ext:     RowBytesExtractor::new(reader),
            row_buf: Vec::with_capacity(1024),
            done:    false,
        }
    }
}

impl<R: BufRead> Iterator for RowIter<R> {
    type Item = JsonTRow;

    fn next(&mut self) -> Option<JsonTRow> {
        if self.done {
            return None;
        }
        match self.ext.next_row_bytes(&mut self.row_buf) {
            Ok(true)  => Scanner::new(&self.row_buf).parse_row().ok(),
            _         => { self.done = true; None }
        }
    }
}

// =============================================================================
// RowBytesExtractor — private chunk-boundary–transparent byte scanner
// =============================================================================
//
// Wraps any BufRead and uses fill_buf() + consume() to walk bytes without ever
// loading the whole source into memory.  Scanner state (depth, in_string, …)
// is kept in struct fields so it survives across fill_buf() refills transparently.
//
// Only `{` / `}` depth plus string-escape tracking are needed to reliably find
// where one row ends and the next begins.  `[` / `]` (array brackets) never
// delimit a top-level row and so need no separate depth counter.

struct RowBytesExtractor<R: BufRead> {
    reader:     R,
    depth:      usize,
    in_string:  bool,
    quote_char: u8,
    escaped:    bool,
}

impl<R: BufRead> RowBytesExtractor<R> {
    fn new(reader: R) -> Self {
        Self { reader, depth: 0, in_string: false, quote_char: 0, escaped: false }
    }

    /// Fill `out` with the raw bytes of the next complete `{…}` row.
    ///
    /// Returns `Ok(true)`  — a row was placed in `out`.
    /// Returns `Ok(false)` — EOF reached with no pending row.
    ///
    /// The caller is responsible for clearing `out` before passing it;
    /// this method calls `out.clear()` at the start of each new row search.
    fn next_row_bytes(&mut self, out: &mut Vec<u8>) -> Result<bool, io::Error> {
        out.clear();

        loop {
            // Borrow the internal buffer without copying it.
            let buf = self.reader.fill_buf()?;
            if buf.is_empty() {
                return Ok(false); // genuine EOF
            }

            let mut consumed  = 0usize;
            let mut row_done  = false;

            for i in 0..buf.len() {
                let b = buf[i];
                consumed = i + 1; // always advance past this byte

                if self.in_string {
                    if self.escaped {
                        self.escaped = false;
                    } else if b == b'\\' {
                        self.escaped = true;
                    } else if b == self.quote_char {
                        self.in_string = false;
                    }
                    if self.depth > 0 {
                        out.push(b);
                    }
                    continue;
                }

                // Not inside a string.
                match b {
                    b'"' | b'\'' => {
                        self.in_string  = true;
                        self.quote_char = b;
                        if self.depth > 0 {
                            out.push(b);
                        }
                    }
                    b'{' => {
                        if self.depth == 0 {
                            out.clear(); // discard any inter-row whitespace/commas
                        }
                        self.depth += 1;
                        out.push(b);
                    }
                    b'}' if self.depth > 0 => {
                        out.push(b);
                        self.depth -= 1;
                        if self.depth == 0 {
                            row_done = true;
                            break; // stop consuming this buffer here
                        }
                    }
                    _ => {
                        if self.depth > 0 {
                            out.push(b);
                        }
                        // depth == 0: skip commas / whitespace between rows
                    }
                }
            }

            self.reader.consume(consumed);

            if row_done {
                return Ok(true);
            }
        }
    }
}

// =============================================================================
// Scanner — private implementation
// =============================================================================

struct Scanner<'a> {
    src: &'a [u8],
    pub pos: usize,
}

impl<'a> Scanner<'a> {
    fn new(src: &'a [u8]) -> Self {
        Self { src, pos: 0 }
    }

    #[inline]
    fn peek(&self) -> Option<u8> {
        self.src.get(self.pos).copied()
    }

    #[inline]
    fn peek_at(&self, offset: usize) -> Option<u8> {
        self.src.get(self.pos + offset).copied()
    }

    #[inline]
    fn advance(&mut self) {
        self.pos += 1;
    }

    #[inline]
    fn is_eof(&self) -> bool {
        self.pos >= self.src.len()
    }

    #[inline]
    fn try_consume(&mut self, b: u8) -> bool {
        if self.peek() == Some(b) {
            self.advance();
            true
        } else {
            false
        }
    }

    fn expect(&mut self, b: u8) -> Result<(), JsonTError> {
        match self.peek() {
            Some(c) if c == b => {
                self.advance();
                Ok(())
            }
            Some(c) => Err(ParseError::Unexpected(format!(
                "at byte {}: expected '{}', got '{}'",
                self.pos, b as char, c as char
            ))
            .into()),
            None => Err(ParseError::Unexpected(format!(
                "at byte {}: expected '{}', got EOF",
                self.pos, b as char
            ))
            .into()),
        }
    }

    // ── Whitespace + comment skipping ────────────────────────────────────────

    /// Skip ASCII whitespace and both comment forms (`//…` and `/* … */`).
    fn skip_ws(&mut self) {
        loop {
            match self.peek() {
                Some(b' ' | b'\t' | b'\r' | b'\n') => self.advance(),
                Some(b'/') => match self.peek_at(1) {
                    Some(b'/') => {
                        self.pos += 2;
                        while let Some(c) = self.peek() {
                            self.advance();
                            if c == b'\n' {
                                break;
                            }
                        }
                    }
                    Some(b'*') => {
                        self.pos += 2;
                        loop {
                            match self.peek() {
                                None => break,
                                Some(b'*') if self.peek_at(1) == Some(b'/') => {
                                    self.pos += 2;
                                    break;
                                }
                                _ => self.advance(),
                            }
                        }
                    }
                    _ => break,
                },
                _ => break,
            }
        }
    }

    // ── Row / object / array ─────────────────────────────────────────────────

    /// Parse one `object_value` = `{ value (, value)* }`.
    /// Returns an error for empty `{}` — the grammar requires ≥1 value.
    fn parse_row(&mut self) -> Result<JsonTRow, JsonTError> {
        self.expect(b'{')?;
        self.skip_ws();

        // Grammar: value ~ ("," ~ value)* — at least one value required.
        if self.peek() == Some(b'}') {
            return Err(ParseError::Unexpected(
                "empty object value: at least one field value is required".into(),
            )
            .into());
        }

        let mut fields = Vec::with_capacity(8);
        fields.push(self.parse_value()?);

        loop {
            self.skip_ws();
            match self.peek() {
                Some(b',') => {
                    self.advance();
                    self.skip_ws();
                    if self.try_consume(b'}') {
                        break; // trailing comma inside row is accepted
                    }
                    fields.push(self.parse_value()?);
                }
                Some(b'}') => {
                    self.advance();
                    break;
                }
                Some(c) => {
                    return Err(ParseError::Unexpected(format!(
                        "at byte {}: expected ',' or '}}' inside row, got '{}'",
                        self.pos, c as char
                    ))
                    .into())
                }
                None => {
                    return Err(ParseError::Unexpected(
                        "unexpected EOF inside row".into(),
                    )
                    .into())
                }
            }
        }

        Ok(JsonTRow::new(fields))
    }

    fn parse_array(&mut self) -> Result<JsonTValue, JsonTError> {
        self.expect(b'[')?;
        self.skip_ws();

        let mut items = Vec::new();

        // Empty arrays are accepted here (grammar is strict but we are lenient
        // for array values embedded inside rows).
        if self.try_consume(b']') {
            return Ok(JsonTValue::Array(JsonTArray::new(items)));
        }

        items.push(self.parse_value()?);
        loop {
            self.skip_ws();
            match self.peek() {
                Some(b',') => {
                    self.advance();
                    self.skip_ws();
                    if self.try_consume(b']') {
                        break;
                    }
                    items.push(self.parse_value()?);
                }
                Some(b']') => {
                    self.advance();
                    break;
                }
                Some(c) => {
                    return Err(ParseError::Unexpected(format!(
                        "at byte {}: expected ',' or ']' inside array, got '{}'",
                        self.pos, c as char
                    ))
                    .into())
                }
                None => {
                    return Err(ParseError::Unexpected(
                        "unexpected EOF inside array".into(),
                    )
                    .into())
                }
            }
        }

        Ok(JsonTValue::Array(JsonTArray::new(items)))
    }

    // ── Value dispatch ────────────────────────────────────────────────────────

    fn parse_value(&mut self) -> Result<JsonTValue, JsonTError> {
        self.skip_ws();
        match self.peek() {
            Some(b'"') | Some(b'\'') => self.parse_string(),
            Some(b'0'..=b'9') => self.parse_number(),
            Some(b't') => {
                self.expect_keyword(b"true")?;
                Ok(JsonTValue::Bool(true))
            }
            Some(b'f') => {
                self.expect_keyword(b"false")?;
                Ok(JsonTValue::Bool(false))
            }
            Some(b'n') => {
                // "nil" (3 bytes) or "null" (4 bytes) — both map to Null [D-5]
                if self.src.get(self.pos..self.pos + 3) == Some(b"nil")
                    && !self
                        .src
                        .get(self.pos + 3)
                        .map_or(false, |c| c.is_ascii_alphanumeric() || *c == b'_')
                {
                    self.pos += 3;
                } else {
                    self.expect_keyword(b"null")?;
                }
                Ok(JsonTValue::Null)
            }
            Some(b'_') => {
                self.advance();
                Ok(JsonTValue::Unspecified)
            }
            Some(b'{') => {
                let row = self.parse_row()?;
                Ok(JsonTValue::Object(row))
            }
            Some(b'[') => self.parse_array(),
            Some(c) if c.is_ascii_uppercase() => self.parse_enum(),
            Some(c) => Err(ParseError::Unexpected(format!(
                "at byte {}: unexpected '{}' (0x{:02x}) at start of value",
                self.pos, c as char, c
            ))
            .into()),
            None => Err(
                ParseError::Unexpected("unexpected EOF while reading value".into()).into(),
            ),
        }
    }

    // ── Scalar parsers ────────────────────────────────────────────────────────

    /// Parse a single- or double-quoted string.
    /// The raw bytes between the quotes are returned verbatim — escape
    /// sequences are NOT interpreted, matching the behaviour of the pest path
    /// (`walk_string` called `pair.as_str().trim_matches(quote)`).
    fn parse_string(&mut self) -> Result<JsonTValue, JsonTError> {
        let quote = self.peek().unwrap();
        self.advance(); // consume opening quote

        let start = self.pos;
        loop {
            match self.peek() {
                None => {
                    return Err(ParseError::Unexpected(
                        "unterminated string literal".into(),
                    )
                    .into())
                }
                Some(b'\\') => {
                    self.advance(); // skip '\'
                    self.advance(); // skip escaped character
                }
                Some(c) if c == quote => break,
                _ => self.advance(),
            }
        }
        let raw = &self.src[start..self.pos];
        self.advance(); // consume closing quote

        let s = std::str::from_utf8(raw)
            .map_err(|_| ParseError::Unexpected("invalid UTF-8 in string literal".into()))?
            .to_owned();
        Ok(JsonTValue::Str(s))
    }

    /// Parse a non-negative decimal number literal.
    /// Emitted as `JsonTValue::d64(f64)` — no schema context is available.
    fn parse_number(&mut self) -> Result<JsonTValue, JsonTError> {
        let start = self.pos;
        while matches!(self.peek(), Some(b'0'..=b'9')) {
            self.advance();
        }
        if self.peek() == Some(b'.') {
            self.advance();
            if !matches!(self.peek(), Some(b'0'..=b'9')) {
                return Err(ParseError::Unexpected(format!(
                    "at byte {}: expected digit after decimal point in number",
                    self.pos
                ))
                .into());
            }
            while matches!(self.peek(), Some(b'0'..=b'9')) {
                self.advance();
            }
        }
        // SAFETY: we only consumed ASCII digits and '.'.
        let s = unsafe { std::str::from_utf8_unchecked(&self.src[start..self.pos]) };
        let n: f64 = s
            .parse()
            .map_err(|_| ParseError::Unexpected(format!("invalid number literal '{}'", s)))?;
        Ok(JsonTValue::d64(n))
    }

    /// Match `kw` at the current position, checking that the next character
    /// is not an identifier-continue character `[a-zA-Z0-9_]` [D-3].
    fn expect_keyword(&mut self, kw: &[u8]) -> Result<(), JsonTError> {
        let end = self.pos + kw.len();
        if self.src.get(self.pos..end) == Some(kw)
            && !self
                .src
                .get(end)
                .map_or(false, |c| c.is_ascii_alphanumeric() || *c == b'_')
        {
            self.pos = end;
            Ok(())
        } else {
            let got_slice = self
                .src
                .get(self.pos..self.pos + kw.len().max(8).min(self.src.len() - self.pos))
                .unwrap_or(&[]);
            let got = std::str::from_utf8(got_slice).unwrap_or("?");
            Err(ParseError::Unexpected(format!(
                "at byte {}: expected keyword '{}', got '{}'",
                self.pos,
                std::str::from_utf8(kw).unwrap_or("?"),
                got,
            ))
            .into())
        }
    }

    /// Parse a CONSTID enum constant: `[A-Z][A-Z0-9_]+` (≥2 chars total).
    fn parse_enum(&mut self) -> Result<JsonTValue, JsonTError> {
        let start = self.pos;
        while matches!(
            self.peek(),
            Some(c) if c.is_ascii_uppercase() || c.is_ascii_digit() || c == b'_'
        ) {
            self.advance();
        }
        if self.pos - start < 2 {
            return Err(ParseError::Unexpected(format!(
                "at byte {}: enum constant (CONSTID) must be at least 2 characters",
                start
            ))
            .into());
        }
        // SAFETY: we only consumed ASCII bytes.
        let s = unsafe { std::str::from_utf8_unchecked(&self.src[start..self.pos]) }.to_owned();
        Ok(JsonTValue::Enum(s))
    }
}
