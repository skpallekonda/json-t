package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Infers a straight {@link JsonTSchema} from a sample of data rows.
 *
 * <p>Numeric types widen to accommodate the column (I16 → I32 → I64, D32 → D64 → D128).
 * Mixed int+float columns become {@code D64}. Any string in a column wins over numerics.
 * A column is marked optional when its null fraction exceeds {@link #nullableThreshold}
 * (default {@code 0.0} — any null triggers optional).
 *
 * <pre>{@code
 *   JsonTSchema schema = SchemaInferrer.create()
 *       .schemaName("Person")
 *       .nullableThreshold(0.5)
 *       .inferWithNames(rows, List.of("id", "name", "active"));
 * }</pre>
 */
public final class SchemaInferrer {

    private final int sampleSize;          // 0 = all rows
    private final double nullableThreshold;
    private final String schemaName;

    private SchemaInferrer(int sampleSize, double nullableThreshold, String schemaName) {
        this.sampleSize = sampleSize;
        this.nullableThreshold = nullableThreshold;
        this.schemaName = schemaName;
    }

    /** Creates an inferrer with defaults: all rows sampled, threshold=0.0, name="Inferred". */
    public static SchemaInferrer create() { return new SchemaInferrer(0, 0.0, "Inferred"); }

    /** Limits inference to the first {@code n} rows (0 = use all rows). */
    public SchemaInferrer sampleSize(int n) {
        if (n < 0) throw new IllegalArgumentException("sampleSize must be >= 0");
        return new SchemaInferrer(n, nullableThreshold, schemaName);
    }

    /**
     * Sets the null-fraction threshold above which a column is marked optional.
     * Clamped to [0.0, 1.0]. {@code 0.0} means any null makes the field optional.
     */
    public SchemaInferrer nullableThreshold(double t) {
        return new SchemaInferrer(sampleSize, Math.max(0.0, Math.min(1.0, t)), schemaName);
    }

    /** Sets the name for the inferred schema. */
    public SchemaInferrer schemaName(String name) {
        return new SchemaInferrer(sampleSize, nullableThreshold, name);
    }

    /**
     * Infers a schema using auto-generated field names ({@code field_0}, {@code field_1}, …).
     *
     * @throws BuildError if rows have inconsistent widths
     */
    public JsonTSchema infer(List<JsonTRow> rows) throws BuildError {
        return inferInner(rows, null);
    }

    /**
     * Infers a schema using the supplied field name hints.
     *
     * @throws BuildError if hint count differs from row width, or rows have inconsistent widths
     */
    public JsonTSchema inferWithNames(List<JsonTRow> rows, List<String> names) throws BuildError {
        return inferInner(rows, names);
    }

    // ── Core inference ────────────────────────────────────────────────────────

    private JsonTSchema inferInner(List<JsonTRow> rows, List<String> nameHints) throws BuildError {
        List<JsonTRow> sample = sample(rows);

        // Empty dataset → zero-field schema (bypasses builder's non-empty guard)
        if (sample.isEmpty()) {
            return new JsonTSchema(schemaName, SchemaKind.STRAIGHT,
                    List.of(), null, List.of(), null);
        }

        int width = rowWidth(sample);
        if (nameHints != null && nameHints.size() != width) {
            throw new BuildError("Name hint count (" + nameHints.size()
                    + ") does not match row width (" + width + ")");
        }

        var sb = JsonTSchemaBuilder.straight(schemaName);
        for (int col = 0; col < width; col++) {
            String fieldName = nameHints != null ? nameHints.get(col) : "field_" + col;

            List<JsonTValue> colValues = new ArrayList<>(sample.size());
            for (JsonTRow row : sample) colValues.add(row.values().get(col));

            long nullCount = colValues.stream().filter(v -> v instanceof JsonTValue.Null).count();
            boolean optional = (double) nullCount / colValues.size() > nullableThreshold;

            List<JsonTValue> nonNull = colValues.stream()
                    .filter(v -> !(v instanceof JsonTValue.Null) && !(v instanceof JsonTValue.Unspecified))
                    .toList();

            ScalarType type = nonNull.isEmpty() ? ScalarType.STR : inferType(nonNull);
            var fb = JsonTFieldBuilder.scalar(fieldName, type);
            if (optional) fb = fb.optional();
            sb.fieldFrom(fb);
        }

        return sb.build();
    }

    private List<JsonTRow> sample(List<JsonTRow> rows) {
        if (sampleSize == 0 || sampleSize >= rows.size()) return rows;
        return rows.subList(0, sampleSize);
    }

    private static int rowWidth(List<JsonTRow> rows) throws BuildError {
        int width = rows.get(0).values().size();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).values().size() != width) {
                throw new BuildError("Inconsistent row width: row 0 has " + width
                        + " values, row " + i + " has " + rows.get(i).values().size());
            }
        }
        return width;
    }

    private static ScalarType inferType(List<JsonTValue> values) {
        boolean hasInt = false, hasFloat = false, hasString = false, hasBool = false, hasOther = false;
        int intWidth = 0;   // max of 16, 32, 64
        int floatWidth = 0; // max of 32, 64, 128

        // Track whether all non-null values share a single semantic string variant.
        // This mirrors the Rust inferrer: when a column is uniformly one semantic type
        // (e.g. all Email), infer that type rather than falling back to STR.
        ScalarType uniformSemantic = null;
        boolean semanticMixed = false;

        for (JsonTValue v : values) {
            if      (v instanceof JsonTValue.Bool)      { hasBool   = true; }
            else if (v instanceof JsonTValue.I16)       { hasInt    = true; intWidth   = Math.max(intWidth, 16); }
            else if (v instanceof JsonTValue.I32)       { hasInt    = true; intWidth   = Math.max(intWidth, 32); }
            else if (v instanceof JsonTValue.I64)       { hasInt    = true; intWidth   = Math.max(intWidth, 64); }
            else if (v instanceof JsonTValue.U16)       { hasInt    = true; intWidth   = Math.max(intWidth, 32); } // promoted to I32
            else if (v instanceof JsonTValue.U32)       { hasInt    = true; intWidth   = Math.max(intWidth, 64); } // promoted to I64
            else if (v instanceof JsonTValue.U64)       { hasInt    = true; intWidth   = Math.max(intWidth, 64); }
            else if (v instanceof JsonTValue.D32)       { hasFloat  = true; floatWidth = Math.max(floatWidth, 32); }
            else if (v instanceof JsonTValue.D64)       { hasFloat  = true; floatWidth = Math.max(floatWidth, 64); }
            else if (v instanceof JsonTValue.D128)      { hasFloat  = true; floatWidth = Math.max(floatWidth, 128); }
            else if (v instanceof JsonTValue.Text)      { hasString = true; }
            else {
                // Semantic string variants — check for uniform type across the column.
                ScalarType sv = semanticTypeOf(v);
                if (sv != null) {
                    if (uniformSemantic == null)      uniformSemantic = sv;
                    else if (uniformSemantic != sv)   semanticMixed = true;
                } else {
                    hasOther = true;
                }
            }
        }

        if (hasBool && !hasInt && !hasFloat && !hasString && uniformSemantic == null && !hasOther) return ScalarType.BOOL;
        // Plain Text wins over semantic variants — column contains heterogeneous strings.
        if (hasString) return ScalarType.STR;
        // All values are the same semantic variant → infer that type precisely.
        if (uniformSemantic != null && !semanticMixed && !hasInt && !hasFloat && !hasBool && !hasOther) return uniformSemantic;
        if (hasInt && hasFloat) return floatWidth >= 128 ? ScalarType.D128 : ScalarType.D64;
        if (hasFloat) return switch (floatWidth) {
            case 128 -> ScalarType.D128;
            case 64  -> ScalarType.D64;
            default  -> ScalarType.D32;
        };
        if (hasInt) return switch (intWidth) {
            case 64 -> ScalarType.I64;
            case 32 -> ScalarType.I32;
            default -> ScalarType.I16;
        };
        return ScalarType.STR;
    }

    /**
     * Maps a semantic {@link JsonTValue} variant to its corresponding {@link ScalarType},
     * or returns {@code null} if the value is not a semantic string variant.
     *
     * Used by {@link #inferType} to detect uniformly-typed semantic columns.
     */
    private static ScalarType semanticTypeOf(JsonTValue v) {
        if (v instanceof JsonTValue.Nstr)      return ScalarType.NSTR;
        if (v instanceof JsonTValue.Uuid)      return ScalarType.UUID;
        if (v instanceof JsonTValue.Uri)       return ScalarType.URI;
        if (v instanceof JsonTValue.Email)     return ScalarType.EMAIL;
        if (v instanceof JsonTValue.Hostname)  return ScalarType.HOSTNAME;
        if (v instanceof JsonTValue.Ipv4)      return ScalarType.IPV4;
        if (v instanceof JsonTValue.Ipv6)      return ScalarType.IPV6;
        if (v instanceof JsonTValue.Date)      return ScalarType.DATE;
        if (v instanceof JsonTValue.Time)      return ScalarType.TIME;
        if (v instanceof JsonTValue.DateTime)  return ScalarType.DATETIME;
        if (v instanceof JsonTValue.Timestamp) return ScalarType.TIMESTAMP;
        if (v instanceof JsonTValue.Tsz)       return ScalarType.TSZ;
        if (v instanceof JsonTValue.Inst)      return ScalarType.INST;
        if (v instanceof JsonTValue.Duration)  return ScalarType.DURATION;
        if (v instanceof JsonTValue.Base64)    return ScalarType.BASE64;
        if (v instanceof JsonTValue.Hex)       return ScalarType.HEX;
        if (v instanceof JsonTValue.Oid)       return ScalarType.OID;
        return null;
    }
}
