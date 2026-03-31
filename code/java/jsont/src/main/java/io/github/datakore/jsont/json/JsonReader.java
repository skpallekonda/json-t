package io.github.datakore.jsont.json;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.json.JsonValueNode;
import io.github.datakore.jsont.internal.json.JsonValueParser;
import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.parse.RowConsumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.*;

/**
 * Converts JSON input into {@link JsonTRow} values guided by a {@link JsonTSchema}.
 *
 * <p>Obtain a configured reader via the fluent builder:
 * <pre>{@code
 *   JsonReader reader = JsonReader.withSchema(schema)
 *       .mode(JsonInputMode.NDJSON)
 *       .unknownFields(UnknownFieldPolicy.SKIP)
 *       .build();
 *
 *   reader.read(jsonString, row -> pipeline.process(row));
 * }</pre>
 *
 * <h2>Schema requirement</h2>
 * <p>Only <em>straight</em> schemas are supported directly. For derived schemas,
 * resolve the full field list with a registry before constructing the reader.
 */
public final class JsonReader {

    private final JsonTSchema        schema;
    private final JsonInputMode      mode;
    private final UnknownFieldPolicy unknownFields;
    private final MissingFieldPolicy missingFields;

    JsonReader(JsonTSchema schema,
               JsonInputMode mode,
               UnknownFieldPolicy unknownFields,
               MissingFieldPolicy missingFields) {
        this.schema        = schema;
        this.mode          = mode;
        this.unknownFields = unknownFields;
        this.missingFields = missingFields;
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Create a builder for a reader bound to the given schema.
     *
     * @param schema the schema to use for field-name ↔ position mapping
     * @return a new {@link JsonReaderBuilder}
     */
    public static JsonReaderBuilder withSchema(JsonTSchema schema) {
        return new JsonReaderBuilder(schema);
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    /**
     * Parse all rows from a JSON string. Returns the number of rows produced.
     *
     * @param input    the JSON source (NDJSON, array, or single object)
     * @param consumer callback invoked for each parsed row
     * @return number of rows produced
     * @throws JsonTError.Parse on malformed JSON or policy violations
     */
    public int read(String input, RowConsumer consumer) {
        return switch (mode) {
            case NDJSON -> readNdjson(input, consumer);
            case ARRAY  -> readArray(input, consumer);
            case OBJECT -> readSingle(input.strip(), consumer);
        };
    }

    /**
     * Parse rows from a {@link Reader} (streaming, O(1) for NDJSON mode).
     *
     * <p>The reader is not closed by this method.
     *
     * @param reader   the source reader
     * @param consumer callback invoked for each parsed row
     * @return number of rows produced
     * @throws IOException      on read failure
     * @throws JsonTError.Parse on malformed JSON or policy violations
     */
    public int readStreaming(Reader reader, RowConsumer consumer) throws IOException {
        if (mode == JsonInputMode.NDJSON) {
            // True streaming: process one line at a time.
            // We do NOT close the reader — the caller owns it.
            BufferedReader br = (reader instanceof BufferedReader b) ? b : new BufferedReader(reader);
            int count = 0;
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) continue;
                consumer.accept(objectStrToRow(trimmed));
                count++;
            }
            return count;
        } else {
            // Array / Object modes: buffer then delegate.
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            return read(sb.toString(), consumer);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private int readNdjson(String input, RowConsumer consumer) {
        int count = 0;
        for (String line : input.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            consumer.accept(objectStrToRow(trimmed));
            count++;
        }
        return count;
    }

    private int readArray(String input, RowConsumer consumer) {
        JsonValueNode node = new JsonValueParser(input).parseValue();
        if (!(node instanceof JsonValueNode.Arr arr)) {
            throw new JsonTError.Parse("expected a JSON array at top level for ARRAY mode");
        }
        int count = 0;
        for (JsonValueNode item : arr.items()) {
            consumer.accept(nodeToRow(item));
            count++;
        }
        return count;
    }

    private int readSingle(String input, RowConsumer consumer) {
        consumer.accept(objectStrToRow(input));
        return 1;
    }

    private JsonTRow objectStrToRow(String input) {
        return nodeToRow(new JsonValueParser(input).parseValue());
    }

    private JsonTRow nodeToRow(JsonValueNode node) {
        if (!(node instanceof JsonValueNode.Obj obj)) {
            throw new JsonTError.Parse(
                    "expected a JSON object for row conversion, got " + nodeKind(node));
        }
        if (!schema.isStraight()) {
            throw new JsonTError.Parse(
                    "JsonReader requires a straight schema; resolve derived schemas first");
        }

        List<JsonTField> fields = schema.fields();

        // Build key → node lookup (last value wins on duplicate keys).
        Map<String, JsonValueNode> map = new LinkedHashMap<>();
        for (var entry : obj.pairs()) map.put(entry.getKey(), entry.getValue());

        // Check for unknown fields when policy is REJECT.
        if (unknownFields == UnknownFieldPolicy.REJECT) {
            Set<String> fieldNames = new HashSet<>();
            for (JsonTField f : fields) fieldNames.add(f.name());
            for (String key : map.keySet()) {
                if (!fieldNames.contains(key)) {
                    throw new JsonTError.Parse(
                            "unknown JSON field '" + key + "' (policy: REJECT)");
                }
            }
        }

        // Build positional row.
        List<JsonTValue> values = new ArrayList<>(fields.size());
        for (JsonTField field : fields) {
            JsonValueNode valueNode = map.get(field.name());
            if (valueNode == null) {
                if (missingFields == MissingFieldPolicy.REJECT) {
                    throw new JsonTError.Parse(
                            "missing JSON field '" + field.name() + "' (policy: REJECT)");
                }
                values.add(JsonTValue.nullValue());
            } else {
                values.add(nodeToValue(valueNode, field));
            }
        }
        return JsonTRow.of(values.toArray(new JsonTValue[0]));
    }

    // ── Node → JsonTValue conversion ──────────────────────────────────────────

    private JsonTValue nodeToValue(JsonValueNode node, JsonTField field) {
        if (node instanceof JsonValueNode.Null) return JsonTValue.nullValue();

        FieldKind kind = field.kind();
        if (kind.isObject()) {
            if (kind.isArray()) {
                if (!(node instanceof JsonValueNode.Arr arr)) throw typeErr(field.name(), "JSON array", node);
                List<JsonTValue> items = new ArrayList<>();
                for (JsonValueNode item : arr.items()) {
                    items.add(JsonTValue.nullValue()); // placeholder — nested schema resolution needed
                }
                return JsonTValue.array(items);
            }
            throw new JsonTError.Parse("field '" + field.name() + "': nested object fields require "
                    + "schema resolution; use a flat straight schema");
        }

        return coerceScalarOrArray(node, field.scalarType(), kind.isArray(), field.name());
    }

    private JsonTValue coerceScalarOrArray(JsonValueNode node, ScalarType scalar,
                                           boolean isArray, String fname) {
        if (!isArray) return coerceScalar(node, scalar, fname);

        if (!(node instanceof JsonValueNode.Arr arr)) throw typeErr(fname, "JSON array", node);
        List<JsonTValue> items = new ArrayList<>();
        for (JsonValueNode item : arr.items()) {
            items.add(item instanceof JsonValueNode.Null
                    ? JsonTValue.nullValue()
                    : coerceScalar(item, scalar, fname));
        }
        return JsonTValue.array(items);
    }

    /**
     * Coerce a single (non-array) JSON node to the declared scalar type.
     * Uses Java 17 {@code instanceof} pattern matching — no switch on sealed types.
     */
    private JsonTValue coerceScalar(JsonValueNode node, ScalarType scalar, String fname) {
        return switch (scalar) {

            // ── Boolean ──────────────────────────────────────────────────────
            case BOOL -> {
                if (node instanceof JsonValueNode.Bool b) yield JsonTValue.bool(b.value());
                throw typeErr(fname, "bool", node);
            }

            // ── Signed integers ───────────────────────────────────────────────
            case I16 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.i16((short) n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.i16((short) n.value());
                throw typeErr(fname, "i16", node);
            }
            case I32 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.i32((int) n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.i32((int) n.value());
                throw typeErr(fname, "i32", node);
            }
            case I64 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.i64(n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.i64((long) n.value());
                throw typeErr(fname, "i64", node);
            }

            // ── Unsigned integers ─────────────────────────────────────────────
            case U16 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.u16((int) n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.u16((int) n.value());
                throw typeErr(fname, "u16", node);
            }
            case U32 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.u32(n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.u32((long) n.value());
                throw typeErr(fname, "u32", node);
            }
            case U64 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.u64(n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.u64((long) n.value());
                throw typeErr(fname, "u64", node);
            }

            // ── Decimal ───────────────────────────────────────────────────────
            case D32 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.d32((float) n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.d32((float) n.value());
                throw typeErr(fname, "d32", node);
            }
            case D64 -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.d64((double) n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.d64(n.value());
                throw typeErr(fname, "d64", node);
            }
            case D128 -> {
                if (node instanceof JsonValueNode.IntNum  n)
                    yield JsonTValue.d128(BigDecimal.valueOf(n.value()));
                if (node instanceof JsonValueNode.FloatNum n)
                    yield JsonTValue.d128(BigDecimal.valueOf(n.value()));
                if (node instanceof JsonValueNode.Str s) {
                    try { yield JsonTValue.d128(new BigDecimal(s.value())); }
                    catch (NumberFormatException e) {
                        throw new JsonTError.Parse(
                                "field '" + fname + "': cannot parse '" + s.value() + "' as d128");
                    }
                }
                throw typeErr(fname, "d128", node);
            }

            // ── Temporal integers (integer on wire) ───────────────────────────
            case DATE -> {
                if (node instanceof JsonValueNode.IntNum n) yield JsonTValue.dateInt((int) n.value());
                throw typeErr(fname, "date (YYYYMMDD integer)", node);
            }
            case TIME -> {
                if (node instanceof JsonValueNode.IntNum n) yield JsonTValue.timeInt((int) n.value());
                throw typeErr(fname, "time (HHmmss integer)", node);
            }
            case DATETIME -> {
                if (node instanceof JsonValueNode.IntNum n) yield JsonTValue.dateTimeInt(n.value());
                throw typeErr(fname, "datetime (YYYYMMDDHHmmss integer)", node);
            }
            case TIMESTAMP -> {
                if (node instanceof JsonValueNode.IntNum  n) yield JsonTValue.timestampInt(n.value());
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.timestampInt((long) n.value());
                throw typeErr(fname, "timestamp (epoch integer)", node);
            }

            // ── Plain / semantic strings ──────────────────────────────────────
            case STR -> {
                if (node instanceof JsonValueNode.Str      s) yield JsonTValue.text(s.value());
                if (node instanceof JsonValueNode.IntNum   n) yield JsonTValue.text(String.valueOf(n.value()));
                if (node instanceof JsonValueNode.FloatNum n) yield JsonTValue.text(String.valueOf(n.value()));
                throw typeErr(fname, "str", node);
            }
            case NSTR -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.nstr(s.value());
                throw typeErr(fname, "nstr", node);
            }
            case URI -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.uri(s.value());
                throw typeErr(fname, "uri", node);
            }
            case UUID -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.uuid(s.value());
                throw typeErr(fname, "uuid", node);
            }
            case EMAIL -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.email(s.value());
                throw typeErr(fname, "email", node);
            }
            case HOSTNAME -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.hostname(s.value());
                throw typeErr(fname, "hostname", node);
            }
            case IPV4 -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.ipv4(s.value());
                throw typeErr(fname, "ipv4", node);
            }
            case IPV6 -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.ipv6(s.value());
                throw typeErr(fname, "ipv6", node);
            }

            // ── Temporal strings ──────────────────────────────────────────────
            case TSZ -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.tsz(s.value());
                throw typeErr(fname, "tsz", node);
            }
            case INST -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.inst(s.value());
                throw typeErr(fname, "inst", node);
            }
            case DURATION -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.duration(s.value());
                throw typeErr(fname, "duration", node);
            }

            // ── Binary / encoded strings ──────────────────────────────────────
            case BASE64 -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.base64(s.value());
                throw typeErr(fname, "base64", node);
            }
            case HEX -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.hex(s.value());
                throw typeErr(fname, "hex", node);
            }
            case OID -> {
                if (node instanceof JsonValueNode.Str s) yield JsonTValue.oid(s.value());
                throw typeErr(fname, "oid", node);
            }
        };
    }

    // ── Error helpers ─────────────────────────────────────────────────────────

    private static String nodeKind(JsonValueNode node) {
        if (node instanceof JsonValueNode.Null)     return "null";
        if (node instanceof JsonValueNode.Bool)     return "bool";
        if (node instanceof JsonValueNode.IntNum)   return "integer";
        if (node instanceof JsonValueNode.FloatNum) return "float";
        if (node instanceof JsonValueNode.Str)      return "string";
        if (node instanceof JsonValueNode.Arr)      return "array";
        if (node instanceof JsonValueNode.Obj)      return "object";
        return "unknown";
    }

    private static JsonTError.Parse typeErr(String fname, String expected, JsonValueNode node) {
        return new JsonTError.Parse(
                "field '" + fname + "': expected " + expected + ", got " + nodeKind(node));
    }
}
