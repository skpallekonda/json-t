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
 * <p>Processes a stream of chars one at a time, tracking brace depth and
 * string-escape state to extract complete {@code {…}} row boundaries without
 * loading the entire input into memory.
 *
 * <p>All-caps ASCII identifiers ({@code ACTIVE}, {@code INACTIVE}) are stored
 * as {@link JsonTValue.Text} since the Java model has no {@code Enum} variant.
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

    // ── RowExtractor (streaming) ──────────────────────────────────────────────

    /**
     * Wraps a {@link Reader} and extracts one complete {@code {…}} row at a time.
     * Package-accessible so {@link io.github.datakore.jsont.parse.RowIter} can use it.
     */
    public static final class RowExtractor {
        private final Reader reader;
        private int depth = 0;
        private boolean inString = false;
        private boolean escaped = false;
        private boolean eof = false;
        private final StringBuilder buf = new StringBuilder(256);

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
            buf.setLength(0);

            while (true) {
                int ch = reader.read();
                if (ch == -1) {
                    eof = true;
                    return null;
                }
                char c = (char) ch;

                if (inString) {
                    if (depth > 0) buf.append(c);
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
                    if (depth > 0) buf.append(c);
                    continue;
                }

                if (c == '{') {
                    if (depth == 0) buf.setLength(0); // discard inter-row junk
                    depth++;
                    buf.append(c);
                    continue;
                }

                if (c == '}') {
                    if (depth > 0) {
                        buf.append(c);
                        depth--;
                        if (depth == 0) {
                            // complete row accumulated
                            return parseRow(buf.toString());
                        }
                    }
                    // depth == 0 closing brace without opening — ignore (inter-row)
                    continue;
                }

                // inside row: keep; outside row: skip (commas, whitespace)
                if (depth > 0) buf.append(c);
            }
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
            // skip inter-row content
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

    // ── Row parser ────────────────────────────────────────────────────────────

    /**
     * Parses a single row string {@code {v1,v2,...}} into a {@link JsonTRow}.
     * Numbers are always emitted as {@link JsonTValue.D64}.
     */
    public static JsonTRow parseRow(String text) {
        var p = new ValueParser(text);
        return p.parseRow();
    }

    // ── ValueParser (private) ─────────────────────────────────────────────────

    private static final class ValueParser {
        private final String src;
        private int pos;

        ValueParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        char peek() {
            skipWs();
            if (pos >= src.length()) return 0;
            return src.charAt(pos);
        }

        char advance() { return src.charAt(pos++); }

        void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
                else break;
            }
        }

        void expect(char expected) {
            skipWs();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new JsonTError.Parse(
                    "Expected '" + expected + "' at position " + pos
                    + " in: " + src);
            }
            pos++;
        }

        boolean tryConsume(char c) {
            skipWs();
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        JsonTRow parseRow() {
            expect('{');
            skipWs();
            // Grammar requires at least one value
            if (peek() == '}') {
                // allow empty rows (unlike Rust which requires >=1) for robustness
                pos++;
                return JsonTRow.of();
            }
            List<JsonTValue> values = new ArrayList<>();
            values.add(parseValue());
            while (true) {
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                    skipWs();
                    if (peek() == '}') { pos++; break; } // trailing comma
                    values.add(parseValue());
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new JsonTError.Parse(
                        "Expected ',' or '}' inside row at position " + pos + " in: " + src);
                }
            }
            return JsonTRow.of(values.toArray(new JsonTValue[0]));
        }

        JsonTValue parseValue() {
            skipWs();
            if (pos >= src.length())
                throw new JsonTError.Parse("Unexpected end of input while reading value");

            char c = src.charAt(pos);

            if (c >= '0' && c <= '9') return parseNumber();
            if (c == '"') return parseString();
            if (c == '[') return parseArray();
            if (c == '{') return parseNestedObject();
            if (c == '_') { pos++; return JsonTValue.nullValue(); }

            // keywords: true, false, null, nil
            if (matchKeyword("true"))  return JsonTValue.bool(true);
            if (matchKeyword("false")) return JsonTValue.bool(false);
            if (matchKeyword("null"))  return JsonTValue.nullValue();
            if (matchKeyword("nil"))   return JsonTValue.nullValue();

            // uppercase enum constant
            if (Character.isUpperCase(c)) return parseEnumConstant();

            throw new JsonTError.Parse(
                "Unexpected character '" + c + "' (0x" + Integer.toHexString(c)
                + ") at position " + pos + " in: " + src);
        }

        /** Match keyword at current position; keyword must not be followed by an identifier char. */
        boolean matchKeyword(String kw) {
            if (!src.startsWith(kw, pos)) return false;
            int end = pos + kw.length();
            if (end < src.length()) {
                char next = src.charAt(end);
                if (Character.isLetterOrDigit(next) || next == '_') return false;
            }
            pos = end;
            return true;
        }

        JsonTValue parseNumber() {
            int start = pos;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            // optional exponent
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String numStr = src.substring(start, pos);
            try {
                return JsonTValue.d64(Double.parseDouble(numStr));
            } catch (NumberFormatException e) {
                throw new JsonTError.Parse("Invalid number literal '" + numStr + "'");
            }
        }

        JsonTValue parseString() {
            pos++; // skip opening "
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '\\') { pos += 2; continue; } // skip escaped char
                if (c == '"') break;
                pos++;
            }
            String raw = src.substring(start, pos);
            if (pos < src.length()) pos++; // skip closing "
            return JsonTValue.text(raw); // stored raw, escape sequences not interpreted
        }

        JsonTValue parseArray() {
            pos++; // skip '['
            skipWs();
            List<JsonTValue> items = new ArrayList<>();
            if (tryConsume(']')) return JsonTValue.array(items); // empty array
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

        /** Parse nested {…} object as Array of its inner values (Java has no Object variant). */
        JsonTValue parseNestedObject() {
            JsonTRow inner = parseRow(); // reuse parseRow (it starts with '{')
            return JsonTValue.array(inner.values());
        }

        JsonTValue parseEnumConstant() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isUpperCase(c) || Character.isDigit(c) || c == '_') pos++;
                else break;
            }
            return JsonTValue.text(src.substring(start, pos));
        }
    }
}
