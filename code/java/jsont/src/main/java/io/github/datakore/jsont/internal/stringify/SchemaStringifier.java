package io.github.datakore.jsont.internal.stringify;

import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.stringify.StringifyOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Internal helper: serialises schema definition language types. */
public final class SchemaStringifier {

    private SchemaStringifier() {}

    // ── Public entry-points that accept StringifyOptions directly ─────────────

    public static String stringifyField(JsonTField f, StringifyOptions opts) {
        return stringifyField(f, new Ctx(opts));
    }

    public static String stringifySchema(JsonTSchema schema, StringifyOptions opts) {
        return stringifySchema(schema, new Ctx(opts));
    }

    public static String stringifyCatalog(JsonTCatalog catalog, StringifyOptions opts) {
        return stringifyCatalog(catalog, new Ctx(opts));
    }

    public static String stringifyNamespace(JsonTNamespace ns, StringifyOptions opts) {
        return stringifyNamespace(ns, new Ctx(opts));
    }

    public static String stringifyEnum(JsonTEnum e, StringifyOptions opts) {
        return stringifyEnum(e, new Ctx(opts));
    }

    // ── JsonTEnum ──────────────────────────────────────────────────────────────

    public static String stringifyEnum(JsonTEnum e, Ctx ctx) {
        String values = String.join(", ", e.values());
        if (ctx.opts.isPretty()) {
            return ctx.indent() + e.name() + ":" + ctx.sp() + "[" + values + "]";
        }
        return e.name() + ":[" + values + "]";
    }

    // ── JsonTField ─────────────────────────────────────────────────────────────

    public static String stringifyField(JsonTField f, Ctx ctx) {
        String prefix  = ctx.indent();
        String opt     = f.optional() ? "?" : "";
        String name    = f.name();
        String attrs   = buildConstraintAttrs(f.constraints());

        if (f.kind().isScalar()) {
            String kw   = f.scalarType().keyword();
            String arr  = f.kind().isArray() ? "[]" : "";
            String sens = f.sensitive() ? "~" : "";
            return prefix + kw + arr + sens + opt + ":" + ctx.sp() + name + attrs;
        } else if (f.kind().isAnyOf()) {
            String variants = f.anyOfVariants().stream()
                    .map(v -> v instanceof AnyOfVariant.Scalar s
                            ? s.type().keyword()
                            : "<" + ((AnyOfVariant.SchemaRef) v).name() + ">")
                    .collect(java.util.stream.Collectors.joining(" | "));
            String arr  = f.kind().isArray() ? "[]" : "";
            String disc = f.discriminator() != null
                    ? " on " + ValueStringifier.quoteString(f.discriminator())
                    : "";
            return prefix + "anyOf(" + variants + ")" + arr + disc + opt + ":" + ctx.sp() + name + attrs;
        } else {
            // object kind
            String ref = f.objectRef();
            String arr = f.kind().isArray() ? "[]" : "";
            return prefix + "<" + ref + ">" + arr + opt + ":" + ctx.sp() + name + attrs;
        }
    }

    private static String buildConstraintAttrs(FieldConstraints c) {
        if (!c.hasAny()) return "";
        List<String> parts = new ArrayList<>();
        if (c.minValue()        != null) parts.add("min = "              + formatNum(c.minValue()));
        if (c.maxValue()        != null) parts.add("max = "              + formatNum(c.maxValue()));
        if (c.minLength()       != null) parts.add("minLength = "        + c.minLength());
        if (c.maxLength()       != null) parts.add("maxLength = "        + c.maxLength());
        if (c.pattern()         != null) parts.add("regex = "            + ValueStringifier.quoteString(c.pattern()));
        if (c.required())                parts.add("required = true");
        if (c.maxPrecision()    != null) parts.add("maxPrecision = "     + c.maxPrecision());
        if (c.minItems()        != null) parts.add("minItems = "         + c.minItems());
        if (c.maxItems()        != null) parts.add("maxItems = "         + c.maxItems());
        if (c.allowNullElements())       parts.add("allowNullElements = true");
        if (c.maxNullElements() != null) parts.add("maxNullElements = "  + c.maxNullElements());
        if (c.constantValue()   != null) parts.add("constant = "         + ValueStringifier.stringifyValue(c.constantValue()));
        return " [" + String.join(", ", parts) + "]";
    }

    /** Emit integer representation when the double is whole (e.g. 1.0 → "1"), otherwise decimal. */
    private static String formatNum(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    // ── SchemaOperation ────────────────────────────────────────────────────────

    public static String stringifyOperation(SchemaOperation op, Ctx ctx) {
        if (op instanceof SchemaOperation.Rename rename) {
            String inner = rename.pairs().stream()
                    .map(rp -> rp.from().dotJoined() + " as " + rp.to())
                    .collect(Collectors.joining(", "));
            return "rename(" + inner + ")";
        }
        if (op instanceof SchemaOperation.Exclude exclude) {
            String inner = exclude.paths().stream()
                    .map(FieldPath::dotJoined)
                    .collect(Collectors.joining(", "));
            return "exclude(" + inner + ")";
        }
        if (op instanceof SchemaOperation.Project project) {
            String inner = project.paths().stream()
                    .map(FieldPath::dotJoined)
                    .collect(Collectors.joining(", "));
            return "project(" + inner + ")";
        }
        if (op instanceof SchemaOperation.Filter filter) {
            return "filter " + ValueStringifier.stringifyExpr(filter.predicate());
        }
        if (op instanceof SchemaOperation.Transform transform) {
            return "transform " + transform.target().dotJoined()
                    + " = " + ValueStringifier.stringifyExpr(transform.expr());
        }
        if (op instanceof SchemaOperation.Decrypt decrypt) {
            return "decrypt(" + String.join(", ", decrypt.fields()) + ")";
        }
        throw new IllegalArgumentException("Unknown SchemaOperation: " + op);
    }

    // ── JsonTValidationBlock ───────────────────────────────────────────────────

    public static String stringifyValidationBlock(JsonTValidationBlock vb, Ctx ctx) {
        Ctx c = ctx.deeper();
        List<String> parts = new ArrayList<>();

        if (!vb.rules().isEmpty()) {
            String rulesStr = vb.rules().stream()
                    .map(r -> {
                        String rStr;
                        if (r instanceof JsonTRule.Expression e) {
                            rStr = ValueStringifier.stringifyExpr(e.expr());
                        } else if (r instanceof JsonTRule.ConditionalRequirement cr) {
                            String cond = ValueStringifier.stringifyExpr(cr.condition());
                            String flds = cr.requiredFields().stream()
                                    .map(FieldPath::dotJoined)
                                    .collect(Collectors.joining(", "));
                            rStr = "if (" + cond + ") require (" + flds + ")";
                        } else {
                            rStr = r.toString();
                        }
                        return (ctx.opts.isPretty() ? c.deeper().indent() : "") + rStr;
                    })
                    .collect(Collectors.joining(c.sep()));
            if (ctx.opts.isPretty()) {
                parts.add(c.indent() + "rules:" + ctx.sp() + "{" + ctx.nl()
                        + rulesStr + ctx.nl() + c.indent() + "}");
            } else {
                parts.add("rules:{" + rulesStr + "}");
            }
        }

        if (!vb.uniqueKeys().isEmpty()) {
            String uStr = vb.uniqueKeys().stream()
                    .map(paths -> {
                        String inner = paths.stream()
                                .map(FieldPath::dotJoined)
                                .collect(Collectors.joining(", "));
                        return (ctx.opts.isPretty() ? c.deeper().indent() : "") + "(" + inner + ")";
                    })
                    .collect(Collectors.joining(c.sep()));
            if (ctx.opts.isPretty()) {
                parts.add(c.indent() + "unique:" + ctx.sp() + "{" + ctx.nl()
                        + uStr + ctx.nl() + c.indent() + "}");
            } else {
                parts.add("unique:{" + uStr + "}");
            }
        }

        String body = String.join(ctx.sep(), parts);
        if (ctx.opts.isPretty()) {
            return ctx.indent() + "validations:" + ctx.sp() + "{" + ctx.nl()
                    + body + ctx.nl() + ctx.indent() + "}";
        }
        return "validations:{" + body + "}";
    }

    // ── JsonTSchema ────────────────────────────────────────────────────────────

    public static String stringifySchema(JsonTSchema schema, Ctx ctx) {
        Ctx c  = ctx.deeper();
        Ctx c2 = c.deeper();

        if (schema.isStraight()) {
            String fieldsStr = schema.fields().stream()
                    .map(f -> stringifyField(f, c2))
                    .collect(Collectors.joining(c.sep()));
            String valStr = schema.validation()
                    .map(vb -> ctx.sep() + stringifyValidationBlock(vb, c))
                    .orElse("");

            if (ctx.opts.isPretty()) {
                return ctx.indent() + schema.name() + ":" + ctx.sp() + "{" + ctx.nl()
                        + c.indent() + "fields:" + ctx.sp() + "{" + ctx.nl()
                        + fieldsStr + ctx.nl()
                        + c.indent() + "}"
                        + valStr + ctx.nl()
                        + ctx.indent() + "}";
            } else {
                return schema.name() + ":{fields:{" + fieldsStr + "}" + valStr + "}";
            }
        } else {
            // Derived
            String parent = schema.derivedFrom().orElse("");
            String opsStr = schema.operations().stream()
                    .map(op -> stringifyOperation(op, c))
                    .collect(Collectors.joining(ctx.sep()));
            String valStr = schema.validation()
                    .map(vb -> ctx.sep() + stringifyValidationBlock(vb, c))
                    .orElse("");

            if (ctx.opts.isPretty()) {
                return ctx.indent() + schema.name() + ":" + ctx.sp() + "FROM " + parent + ctx.sp() + "{" + ctx.nl()
                        + c.indent() + "operations:" + ctx.sp() + "(" + opsStr + ")"
                        + valStr + ctx.nl()
                        + ctx.indent() + "}";
            } else {
                return schema.name() + ":FROM " + parent + "{operations:(" + opsStr + ")" + valStr + "}";
            }
        }
    }

    // ── JsonTCatalog ───────────────────────────────────────────────────────────

    public static String stringifyCatalog(JsonTCatalog catalog, Ctx ctx) {
        Ctx c = ctx.deeper();
        String schemasStr = catalog.schemas().stream()
                .map(s -> stringifySchema(s, c))
                .collect(Collectors.joining(ctx.sep()));

        if (catalog.enums().isEmpty()) {
            if (ctx.opts.isPretty()) {
                return ctx.indent() + "{" + ctx.nl()
                        + c.indent() + "schemas:" + ctx.sp() + "[" + ctx.nl()
                        + schemasStr + ctx.nl()
                        + c.indent() + "]" + ctx.nl()
                        + ctx.indent() + "}";
            }
            return "{schemas:[" + schemasStr + "]}";
        }

        String enumsStr = catalog.enums().stream()
                .map(e -> stringifyEnum(e, c))
                .collect(Collectors.joining(ctx.sep()));
        if (ctx.opts.isPretty()) {
            return ctx.indent() + "{" + ctx.nl()
                    + c.indent() + "schemas:" + ctx.sp() + "[" + ctx.nl()
                    + schemasStr + ctx.nl()
                    + c.indent() + "]," + ctx.nl()
                    + c.indent() + "enums:" + ctx.sp() + "[" + ctx.nl()
                    + enumsStr + ctx.nl()
                    + c.indent() + "]" + ctx.nl()
                    + ctx.indent() + "}";
        }
        return "{schemas:[" + schemasStr + "],enums:[" + enumsStr + "]}";
    }

    // ── JsonTNamespace ─────────────────────────────────────────────────────────

    public static String stringifyNamespace(JsonTNamespace ns, Ctx ctx) {
        Ctx c1 = ctx.deeper();
        Ctx c2 = c1.deeper();

        String catalogsStr = ns.catalogs().stream()
                .map(cat -> stringifyCatalog(cat, c2))
                .collect(Collectors.joining(ctx.sep()));

        if (ctx.opts.isPretty()) {
            return "{" + ctx.nl()
                    + c1.indent() + "namespace:" + ctx.sp() + "{" + ctx.nl()
                    + c2.indent() + "baseUrl:" + ctx.sp() + ValueStringifier.quoteString(ns.baseUrl()) + "," + ctx.nl()
                    + c2.indent() + "catalogs:" + ctx.sp() + "[" + ctx.nl()
                    + catalogsStr + ctx.nl()
                    + c2.indent() + "]" + ctx.nl()
                    + c1.indent() + "}" + ctx.nl()
                    + "}";
        }
        return "{namespace:{baseUrl:" + ValueStringifier.quoteString(ns.baseUrl())
                + ",catalogs:[" + catalogsStr + "]}}";
    }
}
