package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.internal.stringify.SchemaStringifier;
import io.github.datakore.jsont.internal.stringify.ValueStringifier;
import io.github.datakore.jsont.model.*;

/**
 * Public facade for converting JsonT model objects to their source-text representation.
 *
 * <pre>{@code
 *   StringifyOptions opts = StringifyOptions.compact();
 *   String text = JsonTStringifier.stringify(schema, opts);
 *   String text = JsonTStringifier.stringify(row);        // always compact
 *   String text = JsonTStringifier.stringify(value);      // always compact
 * }</pre>
 */
public final class JsonTStringifier {

    private JsonTStringifier() {}

    // ── Data types (compact only — no whitespace variant makes sense) ──────────

    /** Serialises a single value to compact text. */
    public static String stringify(JsonTValue value) {
        return ValueStringifier.stringifyValue(value);
    }

    /** Serialises a data row to compact wire-format text: {@code {v1,v2,...}}. */
    public static String stringify(JsonTRow row) {
        return ValueStringifier.stringifyRow(row);
    }

    /** Serialises an expression tree to text. */
    public static String stringify(JsonTExpression expr) {
        return ValueStringifier.stringifyExpr(expr);
    }

    // ── Schema definition language types ─────────────────────────────────────

    /** Serialises a field definition line. */
    public static String stringify(JsonTField field, StringifyOptions opts) {
        return SchemaStringifier.stringifyField(field, opts);
    }

    /** Serialises a schema definition. */
    public static String stringify(JsonTSchema schema, StringifyOptions opts) {
        return SchemaStringifier.stringifySchema(schema, opts);
    }

    /** Serialises a catalog. */
    public static String stringify(JsonTCatalog catalog, StringifyOptions opts) {
        return SchemaStringifier.stringifyCatalog(catalog, opts);
    }

    /** Serialises a full namespace document. */
    public static String stringify(JsonTNamespace namespace, StringifyOptions opts) {
        return SchemaStringifier.stringifyNamespace(namespace, opts);
    }

    /** Serialises an enum definition. */
    public static String stringify(JsonTEnum enumDef, StringifyOptions opts) {
        return SchemaStringifier.stringifyEnum(enumDef, opts);
    }

    // ── Compact convenience overloads ─────────────────────────────────────────

    /** Compact serialisation of a schema. */
    public static String stringify(JsonTSchema schema) {
        return stringify(schema, StringifyOptions.compact());
    }

    /** Compact serialisation of a namespace. */
    public static String stringify(JsonTNamespace namespace) {
        return stringify(namespace, StringifyOptions.compact());
    }
}
