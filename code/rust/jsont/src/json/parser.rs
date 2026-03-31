// =============================================================================
// json/parser.rs — minimal recursive-descent JSON parser (internal)
// =============================================================================
// Parses standard JSON into a JsonNode tree for use by JsonReader.
// No external dependencies — byte-based cursor, hand-rolled.
// =============================================================================

/// Internal JSON value representation.
#[derive(Debug, Clone)]
pub(crate) enum JsonNode {
    Null,
    Bool(bool),
    /// A JSON integer that fits in i64 (no decimal point, no exponent beyond range).
    Integer(i64),
    /// A JSON number that required float parsing.
    Float(f64),
    Str(String),
    Array(Vec<JsonNode>),
    /// Key-value pairs in document order.
    Object(Vec<(String, JsonNode)>),
}

// ─────────────────────────────────────────────────────────────────────────────

pub(crate) struct JsonParser<'a> {
    src: &'a [u8],
    pos: usize,
}

impl<'a> JsonParser<'a> {
    pub(crate) fn new(input: &'a str) -> Self {
        Self { src: input.as_bytes(), pos: 0 }
    }

    // ── Cursor helpers ────────────────────────────────────────────────────────

    fn peek(&self) -> Option<u8> {
        self.src.get(self.pos).copied()
    }

    fn advance(&mut self) -> Option<u8> {
        let b = self.src.get(self.pos).copied();
        if b.is_some() { self.pos += 1; }
        b
    }

    fn skip_ws(&mut self) {
        while let Some(b) = self.peek() {
            if b == b' ' || b == b'\t' || b == b'\n' || b == b'\r' {
                self.pos += 1;
            } else {
                break;
            }
        }
    }

    fn expect_byte(&mut self, byte: u8) -> Result<(), String> {
        self.skip_ws();
        match self.advance() {
            Some(b) if b == byte => Ok(()),
            Some(b) => Err(format!(
                "expected '{}' but got '{}' at pos {}",
                byte as char, b as char, self.pos - 1
            )),
            None => Err(format!("expected '{}' but got EOF", byte as char)),
        }
    }

    // ── Public entry point ────────────────────────────────────────────────────

    pub(crate) fn parse_value(&mut self) -> Result<JsonNode, String> {
        self.skip_ws();
        match self.peek() {
            Some(b'{') => self.parse_object(),
            Some(b'[') => self.parse_array(),
            Some(b'"') => self.parse_string_node(),
            Some(b't') | Some(b'f') => self.parse_bool(),
            Some(b'n') => self.parse_null(),
            Some(b'-') | Some(b'0'..=b'9') => self.parse_number(),
            Some(b) => Err(format!("unexpected byte '{}' at pos {}", b as char, self.pos)),
            None => Err("unexpected end of input".to_string()),
        }
    }

    /// Returns true when the remaining input (after whitespace) is exhausted.
    pub(crate) fn is_done(&mut self) -> bool {
        self.skip_ws();
        self.pos >= self.src.len()
    }

    // ── Object ────────────────────────────────────────────────────────────────

    fn parse_object(&mut self) -> Result<JsonNode, String> {
        self.expect_byte(b'{')?;
        self.skip_ws();
        let mut pairs: Vec<(String, JsonNode)> = Vec::new();

        if self.peek() == Some(b'}') {
            self.advance();
            return Ok(JsonNode::Object(pairs));
        }

        loop {
            self.skip_ws();
            let key = self.parse_string()?;
            self.skip_ws();
            self.expect_byte(b':')?;
            self.skip_ws();
            let val = self.parse_value()?;
            pairs.push((key, val));
            self.skip_ws();
            match self.peek() {
                Some(b',') => { self.advance(); }
                Some(b'}') => { self.advance(); break; }
                other => return Err(format!(
                    "expected ',' or '}}' in object, got {:?}",
                    other.map(|b| b as char)
                )),
            }
        }
        Ok(JsonNode::Object(pairs))
    }

    // ── Array ─────────────────────────────────────────────────────────────────

    fn parse_array(&mut self) -> Result<JsonNode, String> {
        self.expect_byte(b'[')?;
        self.skip_ws();
        let mut items: Vec<JsonNode> = Vec::new();

        if self.peek() == Some(b']') {
            self.advance();
            return Ok(JsonNode::Array(items));
        }

        loop {
            self.skip_ws();
            let val = self.parse_value()?;
            items.push(val);
            self.skip_ws();
            match self.peek() {
                Some(b',') => { self.advance(); }
                Some(b']') => { self.advance(); break; }
                other => return Err(format!(
                    "expected ',' or ']' in array, got {:?}",
                    other.map(|b| b as char)
                )),
            }
        }
        Ok(JsonNode::Array(items))
    }

    // ── String ────────────────────────────────────────────────────────────────

    fn parse_string_node(&mut self) -> Result<JsonNode, String> {
        self.parse_string().map(JsonNode::Str)
    }

    pub(crate) fn parse_string(&mut self) -> Result<String, String> {
        self.skip_ws();
        self.expect_byte(b'"')?;
        let mut s = String::new();

        loop {
            match self.advance() {
                None => return Err("unterminated JSON string".to_string()),
                Some(b'"') => break,
                Some(b'\\') => match self.advance() {
                    Some(b'"')  => s.push('"'),
                    Some(b'\\') => s.push('\\'),
                    Some(b'/')  => s.push('/'),
                    Some(b'b')  => s.push('\x08'),
                    Some(b'f')  => s.push('\x0C'),
                    Some(b'n')  => s.push('\n'),
                    Some(b'r')  => s.push('\r'),
                    Some(b't')  => s.push('\t'),
                    Some(b'u')  => {
                        let mut code: u32 = 0;
                        for _ in 0..4 {
                            let h = self.advance().ok_or("EOF in \\u escape")?;
                            let digit = match h {
                                b'0'..=b'9' => (h - b'0') as u32,
                                b'a'..=b'f' => (h - b'a' + 10) as u32,
                                b'A'..=b'F' => (h - b'A' + 10) as u32,
                                _ => return Err(format!("invalid hex digit '{}' in \\u escape", h as char)),
                            };
                            code = code * 16 + digit;
                        }
                        let ch = char::from_u32(code)
                            .ok_or_else(|| format!("invalid unicode codepoint U+{code:04X}"))?;
                        s.push(ch);
                    }
                    Some(c) => return Err(format!("invalid escape '\\{}'", c as char)),
                    None    => return Err("EOF inside escape sequence".to_string()),
                },
                Some(b) if b < 0x80 => s.push(b as char),
                Some(b) => {
                    // Multi-byte UTF-8 sequence: collect continuation bytes.
                    let extra = if b >= 0xF0 { 3 } else if b >= 0xE0 { 2 } else { 1 };
                    let mut bytes = vec![b];
                    for _ in 0..extra {
                        match self.advance() {
                            Some(cont) => bytes.push(cont),
                            None => return Err("truncated UTF-8 sequence in JSON string".to_string()),
                        }
                    }
                    let ch = std::str::from_utf8(&bytes)
                        .map_err(|e| format!("invalid UTF-8 in JSON string: {e}"))?
                        .chars()
                        .next()
                        .ok_or("empty UTF-8 sequence")?;
                    s.push(ch);
                }
            }
        }
        Ok(s)
    }

    // ── Boolean ───────────────────────────────────────────────────────────────

    fn parse_bool(&mut self) -> Result<JsonNode, String> {
        if self.src[self.pos..].starts_with(b"true") {
            self.pos += 4;
            Ok(JsonNode::Bool(true))
        } else if self.src[self.pos..].starts_with(b"false") {
            self.pos += 5;
            Ok(JsonNode::Bool(false))
        } else {
            Err(format!("expected 'true' or 'false' at pos {}", self.pos))
        }
    }

    // ── Null ──────────────────────────────────────────────────────────────────

    fn parse_null(&mut self) -> Result<JsonNode, String> {
        if self.src[self.pos..].starts_with(b"null") {
            self.pos += 4;
            Ok(JsonNode::Null)
        } else {
            Err(format!("expected 'null' at pos {}", self.pos))
        }
    }

    // ── Number ────────────────────────────────────────────────────────────────

    fn parse_number(&mut self) -> Result<JsonNode, String> {
        let start = self.pos;
        let mut is_float = false;

        if self.peek() == Some(b'-') { self.pos += 1; }

        // Integer part
        while matches!(self.peek(), Some(b'0'..=b'9')) { self.pos += 1; }

        // Fractional part
        if self.peek() == Some(b'.') {
            is_float = true;
            self.pos += 1;
            while matches!(self.peek(), Some(b'0'..=b'9')) { self.pos += 1; }
        }

        // Exponent
        if matches!(self.peek(), Some(b'e') | Some(b'E')) {
            is_float = true;
            self.pos += 1;
            if matches!(self.peek(), Some(b'+') | Some(b'-')) { self.pos += 1; }
            while matches!(self.peek(), Some(b'0'..=b'9')) { self.pos += 1; }
        }

        let s = std::str::from_utf8(&self.src[start..self.pos])
            .map_err(|e| e.to_string())?;

        if is_float {
            s.parse::<f64>().map(JsonNode::Float).map_err(|e| e.to_string())
        } else {
            match s.parse::<i64>() {
                Ok(n)  => Ok(JsonNode::Integer(n)),
                Err(_) => s.parse::<f64>().map(JsonNode::Float).map_err(|e| e.to_string()),
            }
        }
    }
}
