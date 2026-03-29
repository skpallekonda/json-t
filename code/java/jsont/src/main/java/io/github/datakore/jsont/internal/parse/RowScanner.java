package io.github.datakore.jsont.internal.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.parse.RowConsumer;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal streaming row scanner.
 *
 * <p>Processes input in bulk {@code char[]} chunks rather than one character at a
 * time, eliminating per-character {@code synchronized} lock acquisitions inside
 * {@link java.io.BufferedReader#read()}.  For a 92-field row (~1 KB), this removes
 * ~1000 lock acquisitions per row — the dominant overhead at large scale.
 *
 * <p>A reusable {@code char[]} row buffer replaces the {@code StringBuilder} +
 * {@code toString()} copy that the previous implementation performed per row.
 * {@link ValueParser} operates directly on that buffer, so no intermediate
 * {@code String} object is allocated to hold the raw row text.
 *
 * <p>All-caps ASCII identifiers ({@code ACTIVE}, {@code INACTIVE}) are parsed
 * as {@link JsonTValue.Enum} to match the Rust model.
 * The CDC sentinel {@code _} is parsed as {@link JsonTValue.Unspecified}.
 * Nested objects {@code {…}} are stored as {@link JsonTValue.Array} of their
 * inner values since the Java model has no {@code Object} variant.
 */
public final class RowScanner {

    private RowScanner() {}

    // ── Public entry points ───────────────────────────────────────────────────

    /** Parse all rows from an in-memory string. */
    public static int parseRows(String input, RowConsumer consumer) {
        var extractor = new InMemoryExtractor(input);
        int count = 0;
        String rowText;
        while ((rowText = extractor.nextRow()) != null) {
            consumer.accept(parseRow(rowText));
            count++;
        }
        return count;
    }

    /** Parse rows from a Reader with O(1) memory (streaming). */
    public static int parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException {
        var extractor = new RowExtractor(reader);
        int count = 0;
        JsonTRow row;
        while ((row = extractor.nextRow()) != null) {
            consumer.accept(row);
            count++;
        }
        return count;
    }

    // ── RowExtractor (streaming, bulk-buffer) ─────────────────────────────────

    /**
     * Wraps a {@link Reader} and extracts one complete {@code {…}} row at a time.
     *
     * <p>Reads in bulk chunks into an internal {@code char[]} instead of calling
     * {@link Reader#read()} per character.  A second {@code char[]} accumulates
     * the current row without building a {@code String} — {@link ValueParser}
     * parses directly from the accumulated buffer, eliminating the
     * {@code StringBuilder.toString()} copy from the previous implementation.
     *
     * Package-accessible so {@link io.github.datakore.jsont.parse.RowIter} can use it.
     */
    public static final class RowExtractor {
        // Bulk read buffer — filled once per 65536 chars rather than per char.
        private static final int CBUF_SIZE = 65536;
        private final char[] cbuf = new char[CBUF_SIZE];
        private int cbufPos = 0;
        private int cbufLen = 0;

        // Accumulates the chars of the current row without creating a String.
        // 16384 chars is generous for a ~1 KB row; grows automatically if needed.
        private char[] rowBuf = new char[16384];
        private int rowLen = 0;

        private final Reader reader;
        private int depth = 0;
        private boolean inString = false;
        private boolean escaped = false;
        private boolean eof = false;

        public RowExtractor(Reader reader) {
            this.reader = reader;
        }

        /**
         * Reads the next complete row from the reader.
         *
         * @return the next {@link JsonTRow}, or {@code null} at EOF
         * @throws IOException on I/O failure
         * @throws JsonTError.Parse on malformed row data
         */
        public JsonTRow nextRow() throws IOException {
            if (eof) return null;
            rowLen = 0;

            while (true) {
                // Refill bulk buffer when exhausted — one Reader.read() call
                // per CBUF_SIZE chars instead of one per char.
                if (cbufPos >= cbufLen) {
                    cbufLen = reader.read(cbuf);
                    cbufPos = 0;
                    if (cbufLen == -1) {
                        eof = true;
                        return null;
                    }
                }
                char c = cbuf[cbufPos++];

                if (inString) {
                    if (depth > 0) appendToRow(c);
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }

                if (c == '"') {
                    inString = true;
                    if (depth > 0) appendToRow(c);
                    continue;
                }

                if (c == '{') {
                    if (depth == 0) rowLen = 0; // discard inter-row junk
                    depth++;
                    appendToRow(c);
                    continue;
                }

                if (c == '}') {
                    if (depth > 0) {
                        appendToRow(c);
                        depth--;
                        if (depth == 0) {
                            // Complete row accumulated in rowBuf[0..rowLen].
                            return parseRowFromBuf(rowBuf, rowLen);
                        }
                    }
                    // depth == 0 closing brace without opening — ignore
                    continue;
                }

                // Inside row: keep; outside row: skip (commas, whitespace).
                if (depth > 0) appendToRow(c);
            }
        }

        /** Append {@code c} to rowBuf, growing it if necessary. */
        private void appendToRow(char c) {
            if (rowLen == rowBuf.length) {
                char[] grown = new char[rowBuf.length * 2];
                System.arraycopy(rowBuf, 0, grown, 0, rowLen);
                rowBuf = grown;
            }
            rowBuf[rowLen++] = c;
        }
    }

    // ── InMemoryExtractor (string-backed) ─────────────────────────────────────

    private static final class InMemoryExtractor {
        private final String src;
        private int pos;

        InMemoryExtractor(String src) {
            this.src = src;
            this.pos = 0;
        }

        /** Returns the raw text of the next {…} block, or null at end. */
        String nextRow() {
            while (pos < src.length() && src.charAt(pos) != '{') pos++;
            if (pos >= src.length()) return null;

            int start = pos;
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;

            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (inString) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') inString = false;
                    continue;
                }
                if (c == '"') { inString = true; continue; }
                if (c == '{') { depth++; continue; }
                if (c == '}') {
                    depth--;
                    if (depth == 0) return src.substring(start, pos);
                }
            }
            throw new JsonTError.Parse("Unterminated row starting at position " + start);
        }
    }

    // ── Row parser entry points ───────────────────────────────────────────────

    /**
     * Parses a single row string {@code {v1,v2,...}} into a {@link JsonTRow}.
     * Numbers are always emitted as {@link JsonTValue.D64}.
     * Used by the in-memory path; converts the {@code String} to a char array
     * once and delegates to the buffer-based parser.
     */
    public static JsonTRow parseRow(String text) {
        return parseRowFromBuf(text.toCharArray(), text.length());
    }

    /**
     * Parses a row directly from a {@code char[]} slice {@code buf[0..len]}.
     * Used by {@link RowExtractor} to avoid creating an intermediate {@code String}.
     */
    static JsonTRow parseRowFromBuf(char[] buf, int len) {
        return new ValueParser(buf, len).parseRow();
    }

    // ── ValueParser (private) ─────────────────────────────────────────────────

    /**
     * Parses values from a {@code char[]} buffer slice.
     *
     * <p>Operating on a {@code char[]} instead of a {@code String} eliminates
     * the {@code buf.toString()} copy per row and reduces {@code substring()}
     * calls (replaced by {@code new String(char[], offset, count)}) — one
     * allocation instead of two.
     */
    private static final class ValueParser {
        private final char[] src;
        private final int    len;
        private int pos;

        ValueParser(char[] src, int len) {
            this.src = src;
            this.len = len;
            this.pos = 0;
        }

        char peek() {
            skipWs();
            return pos < len ? src[pos] : 0;
        }


        void skipWs() {
            while (pos < len) {
                char c = src[pos];
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
                else break;
            }
        }

        void expect(char expected) {
            skipWs();
            if (pos >= len || src[pos] != expected) {
                throw new JsonTError.Parse(
                    "Expected '" + expected + "' at position " + pos
                    + " in: " + new String(src, 0, len));
            }
            pos++;
        }

        boolean tryConsume(char c) {
            skipWs();
            if (pos < len && src[pos] == c) { pos++; return true; }
            return false;
        }

        JsonTRow parseRow() {
            expect('{');
            skipWs();
            if (peek() == '}') { pos++; return JsonTRow.of(); }
            List<JsonTValue> values = new ArrayList<>();
            values.add(parseValue());
            while (true) {
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                    skipWs();
                    if (peek() == '}') { pos++; break; }
                    values.add(parseValue());
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new JsonTError.Parse(
                        "Expected ',' or '}' inside row at position " + pos
                        + " in: " + new String(src, 0, len));
                }
            }
            return JsonTRow.of(values.toArray(new JsonTValue[0]));
        }

        JsonTValue parseValue() {
            skipWs();
            if (pos >= len)
                throw new JsonTError.Parse("Unexpected end of input while reading value");

            char c = src[pos];

            if (c >= '0' && c <= '9') return parseNumber();
            if (c == '"') return parseString();
            if (c == '[') return parseArray();
            if (c == '{') return parseNestedObject();
            if (c == '_') { pos++; return JsonTValue.unspecified(); }

            if (matchKeyword("true"))  return JsonTValue.bool(true);
            if (matchKeyword("false")) return JsonTValue.bool(false);
            if (matchKeyword("null"))  return JsonTValue.nullValue();
            if (matchKeyword("nil"))   return JsonTValue.nullValue();

            if (Character.isUpperCase(c)) return parseEnumConstant();

            throw new JsonTError.Parse(
                "Unexpected character '" + c + "' (0x" + Integer.toHexString(c)
                + ") at position " + pos + " in: " + new String(src, 0, len));
        }

        boolean matchKeyword(String kw) {
            int kwLen = kw.length();
            if (pos + kwLen > len) return false;
            for (int i = 0; i < kwLen; i++) {
                if (src[pos + i] != kw.charAt(i)) return false;
            }
            int end = pos + kwLen;
            if (end < len) {
                char next = src[end];
                if (Character.isLetterOrDigit(next) || next == '_') return false;
            }
            pos = end;
            return true;
        }

        JsonTValue parseNumber() {
            int start = pos;
            while (pos < len && Character.isDigit(src[pos])) pos++;
            if (pos < len && src[pos] == '.') {
                pos++;
                while (pos < len && Character.isDigit(src[pos])) pos++;
            }
            if (pos < len && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++;
                if (pos < len && (src[pos] == '+' || src[pos] == '-')) pos++;
                while (pos < len && Character.isDigit(src[pos])) pos++;
            }
            String numStr = new String(src, start, pos - start);
            try {
                return JsonTValue.d64(Double.parseDouble(numStr));
            } catch (NumberFormatException e) {
                throw new JsonTError.Parse("Invalid number literal '" + numStr + "'");
            }
        }

        JsonTValue parseString() {
            pos++; // skip opening "
            int start = pos;
            while (pos < len) {
                char c = src[pos];
                if (c == '\\') { pos += 2; continue; }
                if (c == '"') break;
                pos++;
            }
            String raw = new String(src, start, pos - start);
            if (pos < len) pos++; // skip closing "
            return JsonTValue.text(raw);
        }

        JsonTValue parseArray() {
            pos++; // skip '['
            skipWs();
            List<JsonTValue> items = new ArrayList<>();
            if (tryConsume(']')) return JsonTValue.array(items);
            items.add(parseValue());
            while (true) {
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                    skipWs();
                    if (peek() == ']') { pos++; break; }
                    items.add(parseValue());
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new JsonTError.Parse(
                        "Expected ',' or ']' inside array at position " + pos);
                }
            }
            return JsonTValue.array(items);
        }

        JsonTValue parseNestedObject() {
            JsonTRow inner = parseRow();
            return JsonTValue.array(inner.values());
        }

        JsonTValue parseEnumConstant() {
            int start = pos;
            while (pos < len) {
                char c = src[pos];
                if (Character.isUpperCase(c) || Character.isDigit(c) || c == '_') pos++;
                else break;
            }
            return JsonTValue.enumValue(new String(src, start, pos - start));
        }
    }
}
