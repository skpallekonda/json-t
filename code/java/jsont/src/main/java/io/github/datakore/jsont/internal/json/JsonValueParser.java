package io.github.datakore.jsont.internal.json;

import io.github.datakore.jsont.error.JsonTError;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser.
 *
 * <p>Produces a {@link JsonValueNode} tree from a standard JSON string.
 * No external dependencies — char-based cursor, hand-rolled.
 *
 * <p>Not part of the public API.
 */
public final class JsonValueParser {

    private final String src;
    private int pos;

    public JsonValueParser(String input) {
        this.src = input;
        this.pos = 0;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /** Parse the next JSON value from the current position. */
    public JsonValueNode parseValue() {
        skipWs();
        if (pos >= src.length()) {
            throw new JsonTError.Parse("unexpected end of JSON input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseStringNode();
            case 't', 'f' -> parseBool();
            case 'n' -> parseNull();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
            default -> throw new JsonTError.Parse(
                    "unexpected character '" + c + "' at position " + pos);
        };
    }

    /** Returns true when all remaining input (after whitespace) is consumed. */
    public boolean isDone() {
        skipWs();
        return pos >= src.length();
    }

    // ── Object ────────────────────────────────────────────────────────────────

    private JsonValueNode parseObject() {
        expect('{');
        skipWs();
        List<Map.Entry<String, JsonValueNode>> pairs = new ArrayList<>();

        if (peek() == '}') { advance(); return new JsonValueNode.Obj(pairs); }

        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expect(':');
            skipWs();
            JsonValueNode val = parseValue();
            pairs.add(new AbstractMap.SimpleImmutableEntry<>(key, val));
            skipWs();
            char next = peekOrThrow("',' or '}'");
            if      (next == ',') { advance(); }
            else if (next == '}') { advance(); break; }
            else throw new JsonTError.Parse(
                    "expected ',' or '}' in JSON object, got '" + next + "' at position " + pos);
        }
        return new JsonValueNode.Obj(pairs);
    }

    // ── Array ─────────────────────────────────────────────────────────────────

    private JsonValueNode parseArray() {
        expect('[');
        skipWs();
        List<JsonValueNode> items = new ArrayList<>();

        if (peek() == ']') { advance(); return new JsonValueNode.Arr(items); }

        while (true) {
            skipWs();
            items.add(parseValue());
            skipWs();
            char next = peekOrThrow("',' or ']'");
            if      (next == ',') { advance(); }
            else if (next == ']') { advance(); break; }
            else throw new JsonTError.Parse(
                    "expected ',' or ']' in JSON array, got '" + next + "' at position " + pos);
        }
        return new JsonValueNode.Arr(items);
    }

    // ── String ────────────────────────────────────────────────────────────────

    private JsonValueNode parseStringNode() {
        return new JsonValueNode.Str(parseString());
    }

    /** Parse a JSON string, handling all standard escape sequences. */
    public String parseString() {
        skipWs();
        expect('"');
        StringBuilder sb = new StringBuilder();

        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }

            // Escape sequence
            if (pos >= src.length()) throw new JsonTError.Parse("EOF inside JSON escape sequence");
            char esc = src.charAt(pos++);
            switch (esc) {
                case '"'  -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/'  -> sb.append('/');
                case 'b'  -> sb.append('\b');
                case 'f'  -> sb.append('\f');
                case 'n'  -> sb.append('\n');
                case 'r'  -> sb.append('\r');
                case 't'  -> sb.append('\t');
                case 'u'  -> {
                    if (pos + 4 > src.length()) {
                        throw new JsonTError.Parse("truncated \\u escape in JSON string");
                    }
                    String hex = src.substring(pos, pos + 4);
                    pos += 4;
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new JsonTError.Parse("invalid \\u escape: \\u" + hex);
                    }
                }
                default -> throw new JsonTError.Parse("invalid JSON escape '\\" + esc + "'");
            }
        }
        throw new JsonTError.Parse("unterminated JSON string");
    }

    // ── Boolean ───────────────────────────────────────────────────────────────

    private JsonValueNode parseBool() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return new JsonValueNode.Bool(true);
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return new JsonValueNode.Bool(false);
        }
        throw new JsonTError.Parse("expected 'true' or 'false' at position " + pos);
    }

    // ── Null ──────────────────────────────────────────────────────────────────

    private JsonValueNode parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return new JsonValueNode.Null();
        }
        throw new JsonTError.Parse("expected 'null' at position " + pos);
    }

    // ── Number ────────────────────────────────────────────────────────────────

    private JsonValueNode parseNumber() {
        int start = pos;
        boolean isFloat = false;

        if (pos < src.length() && src.charAt(pos) == '-') pos++;

        // Integer part
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;

        // Fractional part
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }

        // Exponent
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }

        String raw = src.substring(start, pos);
        try {
            if (isFloat) {
                return new JsonValueNode.FloatNum(Double.parseDouble(raw));
            } else {
                try {
                    return new JsonValueNode.IntNum(Long.parseLong(raw));
                } catch (NumberFormatException e) {
                    // Overflow: fall back to float
                    return new JsonValueNode.FloatNum(Double.parseDouble(raw));
                }
            }
        } catch (NumberFormatException e) {
            throw new JsonTError.Parse("invalid JSON number '" + raw + "' at position " + start);
        }
    }

    // ── Cursor helpers ────────────────────────────────────────────────────────

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char peekOrThrow(String expected) {
        if (pos >= src.length()) {
            throw new JsonTError.Parse("expected " + expected + " but got end of input");
        }
        return src.charAt(pos);
    }

    private void advance() { pos++; }

    private void expect(char c) {
        skipWs();
        if (pos >= src.length()) {
            throw new JsonTError.Parse("expected '" + c + "' but got end of input");
        }
        char actual = src.charAt(pos);
        if (actual != c) {
            throw new JsonTError.Parse(
                    "expected '" + c + "' but got '" + actual + "' at position " + pos);
        }
        pos++;
    }
}
