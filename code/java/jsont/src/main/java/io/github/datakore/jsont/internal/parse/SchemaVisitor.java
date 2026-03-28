package io.github.datakore.jsont.internal.parse;

import io.github.datakore.jsont.builder.*;
import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaBaseVisitor;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaLexer;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaParser;
import io.github.datakore.jsont.model.*;

import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal ANTLR4 visitor that walks the {@code JsonTSchema.g4} parse tree and
 * produces the Java model hierarchy (namespace → catalog → schema →
 * field/enum).
 */
public final class SchemaVisitor extends JsonTSchemaBaseVisitor<Object> {

    // ── Public entry ──────────────────────────────────────────────────────────

    /** Parse a full namespace DSL document, returning the namespace model. */
    public static JsonTNamespace parseNamespace(String input) {
        var lexer = new JsonTSchemaLexer(CharStreams.fromString(input));
        var tokens = new CommonTokenStream(lexer);
        var parser = new JsonTSchemaParser(tokens);

        // Replace default error listeners with one that throws
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        var errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                    int line, int charPositionInLine,
                    String msg, RecognitionException e) {
                throw new JsonTError.Parse("Syntax error at " + line + ":" + charPositionInLine + " — " + msg);
            }
        };
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        var tree = parser.namespace_decl();
        return (JsonTNamespace) new SchemaVisitor().visit(tree);
    }

    // ── Namespace ─────────────────────────────────────────────────────────────

    @Override
    public Object visitNamespace_decl(JsonTSchemaParser.Namespace_declContext ctx) {
        var nb = JsonTNamespaceBuilder.create();

        if (ctx.ns_base_url() != null) {
            String raw = ctx.ns_base_url().getText();
            nb.baseUrl(raw.substring(1, raw.length() - 1));
        }

        if (ctx.ns_version() != null) {
            String raw = ctx.ns_version().getText();
            nb.version(raw.substring(1, raw.length() - 1));
        }

        for (var catCtx : ctx.catalog()) {
            nb.catalog((JsonTCatalog) visitCatalog(catCtx));
        }

        try {
            return nb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Namespace build error: " + e.getMessage(), e);
        }
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    @Override
    public Object visitCatalog(JsonTSchemaParser.CatalogContext ctx) {
        var cb = JsonTCatalogBuilder.create();

        if (ctx.schemas_section() != null) {
            for (var entry : ctx.schemas_section().schema_entry()) {
                cb.schema((JsonTSchema) visitSchema_entry(entry));
            }
        }

        if (ctx.enums_section() != null) {
            for (var edef : ctx.enums_section().enum_def()) {
                cb.enum_((JsonTEnum) visitEnum_def(edef));
            }
        }

        try {
            return cb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Catalog build error: " + e.getMessage(), e);
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    @Override
    public Object visitSchema_entry(JsonTSchemaParser.Schema_entryContext ctx) {
        String name = ctx.ns_schema_name().getText();
        var def = ctx.schema_definition();

        if (def.straight_schema() != null) {
            return processStraightSchema(name, def.straight_schema());
        } else {
            return processDerivedSchema(name, def.derived_schema());
        }
    }

    private JsonTSchema processStraightSchema(String name, JsonTSchemaParser.Straight_schemaContext ctx) {
        var sb = JsonTSchemaBuilder.straight(name);

        if (ctx.field_block() != null) {
            for (var fieldCtx : ctx.field_block().field_decl()) {
                try {
                    sb.fieldFrom((JsonTFieldBuilder) visitField_decl(fieldCtx));
                } catch (BuildError e) {
                    throw new JsonTError.Parse("Field build error in '" + name + "': " + e.getMessage(), e);
                }
            }
        }

        if (ctx.validation_block() != null) {
            var vbb = buildValidationBlock(ctx.validation_block());
            try {
                sb.validationFrom(vbb);
            } catch (BuildError e) {
                throw new JsonTError.Parse("Validation build error in '" + name + "': " + e.getMessage(), e);
            }
        }

        try {
            return sb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Schema build error for '" + name + "': " + e.getMessage(), e);
        }
    }

    private JsonTSchema processDerivedSchema(String name, JsonTSchemaParser.Derived_schemaContext ctx) {
        String parent = ctx.ns_schema_name().getText();
        var sb = JsonTSchemaBuilder.derived(name, parent);

        if (ctx.operations_block() != null) {
            for (var opCtx : ctx.operations_block().schema_operation()) {
                SchemaOperation op = buildDerivedOperation(opCtx);
                try {
                    sb.operation(op);
                } catch (BuildError e) {
                    throw new JsonTError.Parse("Operation build error in '" + name + "': " + e.getMessage(), e);
                }
            }
        }

        if (ctx.validation_block() != null) {
            var vbb = buildValidationBlock(ctx.validation_block());
            try {
                sb.validationFrom(vbb);
            } catch (BuildError e) {
                throw new JsonTError.Parse("Validation build error in '" + name + "': " + e.getMessage(), e);
            }
        }

        try {
            return sb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Schema build error for '" + name + "': " + e.getMessage(), e);
        }
    }

    // ── Field definition ──────────────────────────────────────────────────────

    @Override
    public Object visitField_decl(JsonTSchemaParser.Field_declContext ctx) {
        if (ctx.scalar_field_decl() != null) {
            return visitScalar_field_decl(ctx.scalar_field_decl());
        }
        return visitObject_field_decl(ctx.object_field_decl());
    }

    @Override
    public Object visitScalar_field_decl(JsonTSchemaParser.Scalar_field_declContext ctx) {
        String name = ctx.ns_field_name().getText();
        var typeRef = ctx.scalar_type_ref();

        String kw = typeRef.scalar_types().getText();
        ScalarType st;
        try {
            st = ScalarType.fromKeyword(kw);
        } catch (IllegalArgumentException e) {
            throw new JsonTError.Parse("Unknown scalar type: " + kw);
        }

        var fb = JsonTFieldBuilder.scalar(name, st);

        if (typeRef.array_suffix() != null)
            fb = fb.asArray();
        if (ctx.optional_mark() != null)
            fb = fb.optional();

        if (ctx.scalar_field_attributes() != null) {
            var attrs = ctx.scalar_field_attributes();
            if (attrs.scalar_constraints_section() != null) {
                fb = applyScalarConstraints(fb, attrs.scalar_constraints_section());
            }
            if (attrs.default_value() != null) {
                // TODO: Default values are not yet supported in JsonTFieldBuilder
                // fb.defaultValue(parseLiteral(attrs.default_value().scalar_value(),
                // attrs.default_value().null_value()));
            }
            if (attrs.constant_value() != null) {
                fb.constantValue(
                        parseLiteral(attrs.constant_value().scalar_value(), attrs.constant_value().null_value()));
            }
        }

        return fb;
    }

    @Override
    public Object visitObject_field_decl(JsonTSchemaParser.Object_field_declContext ctx) {
        String name = ctx.ns_field_name().getText();
        var typeRef = ctx.object_type_ref();
        String refName = typeRef.object_type_name().getText();

        var fb = JsonTFieldBuilder.object(name, refName);

        if (typeRef.array_suffix() != null)
            fb = fb.asArray();
        if (ctx.optional_mark() != null)
            fb = fb.optional();

        if (ctx.common_constraints_section() != null) {
            for (var consCtx : ctx.common_constraints_section().common_constraint()) {
                fb = applyCommonConstraint(fb, consCtx);
            }
        }

        return fb;
    }

    private JsonTFieldBuilder applyScalarConstraints(JsonTFieldBuilder fb,
            JsonTSchemaParser.Scalar_constraints_sectionContext ctx) {
        // First alternative of scalar_constraints_section: common_constraints_section
        // (matches required=true and array item constraints such as (minItems=1))
        if (ctx.common_constraints_section() != null) {
            for (var cc : ctx.common_constraints_section().common_constraint()) {
                fb = applyCommonConstraint(fb, cc);
            }
            return fb;
        }
        // Second alternative: scalar_constraint list (value, length, regex, or common)
        for (var sc : ctx.scalar_constraint()) {
            if (sc.common_constraint() != null) {
                fb = applyCommonConstraint(fb, sc.common_constraint());
            } else if (sc.value_constraint() != null) {
                for (var opt : sc.value_constraint().value_constraint_option()) {
                    String kw = opt.value_constraint_kw().getText();
                    double val = Double.parseDouble(opt.number().getText());
                    if (kw.equals("minValue"))
                        fb = fb.minValue(val);
                    else if (kw.equals("maxValue"))
                        fb = fb.maxValue(val);
                    else if (kw.equals("maxPrecision"))
                        fb = fb.maxPrecision((int) val);
                }
            } else if (sc.length_constraint() != null) {
                for (var opt : sc.length_constraint().length_constraint_option()) {
                    String kw = opt.length_constraint_kw().getText();
                    int val = Integer.parseInt(opt.number().getText());
                    if (kw.equals("minLength"))
                        fb = fb.minLength(val);
                    else if (kw.equals("maxLength"))
                        fb = fb.maxLength(val);
                }
            } else if (sc.regex_constraint() != null) {
                String raw = sc.regex_constraint().string_val().getText();
                fb = fb.pattern(raw.substring(1, raw.length() - 1));
            }
        }
        return fb;
    }

    private JsonTFieldBuilder applyCommonConstraint(JsonTFieldBuilder fb,
            JsonTSchemaParser.Common_constraintContext ctx) {
        if (ctx.required_constraint() != null) {
            if ("true".equals(ctx.required_constraint().boolean_val().getText())) {
                fb = fb.required();
            }
        } else if (ctx.array_items_constraint() != null) {
            for (var opt : ctx.array_items_constraint().array_items_constraint_option()) {
                if (opt.array_constraint_nbr() != null) {
                    String kw = opt.array_constraint_nbr().getText();
                    int val = Integer.parseInt(opt.number().getText());
                    if (kw.equals("minItems"))
                        fb = fb.minItems(val);
                    else if (kw.equals("maxItems"))
                        fb = fb.maxItems(val);
                    else if (kw.equals("maxNullItems"))
                        fb = fb.maxNullElements(val);
                } else if (opt.array_constraint_bool() != null) {
                    if ("true".equals(opt.boolean_val().getText())) {
                        fb = fb.allowNullElements();
                    }
                }
            }
        }
        return fb;
    }

    private JsonTValue parseLiteral(JsonTSchemaParser.Scalar_valueContext sctx,
            JsonTSchemaParser.Null_valueContext nctx) {
        if (nctx != null)
            return JsonTValue.nullValue();
        if (sctx.boolean_val() != null) {
            return JsonTValue.bool("true".equals(sctx.boolean_val().getText()));
        }
        if (sctx.number() != null) {
            return JsonTValue.d64(Double.parseDouble(sctx.number().getText()));
        }
        if (sctx.string_val() != null) {
            String raw = sctx.string_val().getText();
            return JsonTValue.text(raw.substring(1, raw.length() - 1));
        }
        throw new JsonTError.Parse("Unknown literal");
    }

    // ── Validation block ──────────────────────────────────────────────────────

    private JsonTValidationBlockBuilder buildValidationBlock(JsonTSchemaParser.Validation_blockContext ctx) {
        var vbb = JsonTValidationBlockBuilder.create();

        if (ctx.unique_block() != null) {
            for (var entry : ctx.unique_block().unique_entry()) {
                vbb.unique(buildFieldPathList(entry.field_path_list()));
            }
        }

        if (ctx.rules_block() != null) {
            for (var item : ctx.rules_block().rule_item()) {
                if (item.expression() != null) {
                    vbb.rule((JsonTExpression) visit(item.expression()));
                } else if (item.conditional_requirement() != null) {
                    var expr = (JsonTExpression) visit(item.conditional_requirement().expression());
                    var paths = buildFieldPathList(item.conditional_requirement().field_path_list());
                    vbb.conditionalRule(expr, paths);
                }
            }
        }

        return vbb;
    }

    // ── Derived operations ────────────────────────────────────────────────────

    private SchemaOperation buildDerivedOperation(JsonTSchemaParser.Schema_operationContext ctx) {
        if (ctx.rename_operation() != null) {
            List<RenamePair> pairs = new ArrayList<>();
            for (var rp : ctx.rename_operation().rename_pair()) {
                FieldPath from = buildFieldPath(rp.field_path());
                String to = rp.ns_field_name().getText();
                pairs.add(new RenamePair(from, to));
            }
            return SchemaOperation.rename(pairs);
        }
        if (ctx.exclude_operation() != null) {
            return SchemaOperation.exclude(buildFieldPathList(ctx.exclude_operation().field_path_list()));
        }
        if (ctx.project_operation() != null) {
            return SchemaOperation.project(buildFieldPathList(ctx.project_operation().field_path_list()));
        }
        if (ctx.filter_operation() != null) {
            var expr = (JsonTExpression) visit(ctx.filter_operation().expression());
            return SchemaOperation.filter(expr);
        }
        if (ctx.transform_operation() != null) {
            var top = ctx.transform_operation();
            String target = top.field_path().getText();
            var expr = (JsonTExpression) visit(top.expression());
            return SchemaOperation.transform(target, expr);
        }
        throw new JsonTError.Parse("Unknown derived schema operation");
    }

    // ── Field paths ───────────────────────────────────────────────────────────

    private List<FieldPath> buildFieldPathList(JsonTSchemaParser.Field_path_listContext ctx) {
        List<FieldPath> paths = new ArrayList<>();
        for (var fpCtx : ctx.field_path()) {
            paths.add(buildFieldPath(fpCtx));
        }
        return paths;
    }

    private FieldPath buildFieldPath(JsonTSchemaParser.Field_pathContext ctx) {
        List<JsonTSchemaParser.Ns_field_nameContext> names = ctx.ns_field_name();
        if (names.size() == 1)
            return FieldPath.single(names.get(0).getText());
        String first = names.get(0).getText();
        String[] rest = new String[names.size() - 1];
        for (int i = 1; i < names.size(); i++)
            rest[i - 1] = names.get(i).getText();
        return FieldPath.of(first, rest);
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitEnum_def(JsonTSchemaParser.Enum_defContext ctx) {
        String name = ctx.ns_enum_name().getText();
        List<String> values = new ArrayList<>();
        for (var cCtx : ctx.enum_body().enum_value_constant()) {
            values.add(cCtx.getText());
        }
        return new JsonTEnum(name, values);
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    @Override
    public Object visitAndExpr(JsonTSchemaParser.AndExprContext ctx) {
        return JsonTExpression.binary(BinaryOp.AND,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitOrExpr(JsonTSchemaParser.OrExprContext ctx) {
        return JsonTExpression.binary(BinaryOp.OR,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitEqualityExpr(JsonTSchemaParser.EqualityExprContext ctx) {
        BinaryOp op = ctx.OP_EEQ() != null ? BinaryOp.EQ : BinaryOp.NE;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitRelationalExpr(JsonTSchemaParser.RelationalExprContext ctx) {
        BinaryOp op;
        if (ctx.OP_LT() != null)
            op = BinaryOp.LT;
        else if (ctx.OP_LE() != null)
            op = BinaryOp.LE;
        else if (ctx.OP_GT() != null)
            op = BinaryOp.GT;
        else
            op = BinaryOp.GE;

        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitAddSubExpr(JsonTSchemaParser.AddSubExprContext ctx) {
        BinaryOp op = ctx.OP_PLUS() != null ? BinaryOp.ADD : BinaryOp.SUB;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitMulDivExpr(JsonTSchemaParser.MulDivExprContext ctx) {
        BinaryOp op = ctx.OP_STAR() != null ? BinaryOp.MUL : BinaryOp.DIV;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expression(0)),
                (JsonTExpression) visit(ctx.expression(1)));
    }

    @Override
    public Object visitNotExpr(JsonTSchemaParser.NotExprContext ctx) {
        return JsonTExpression.not((JsonTExpression) visit(ctx.expression()));
    }

    @Override
    public Object visitNegExpr(JsonTSchemaParser.NegExprContext ctx) {
        return JsonTExpression.neg((JsonTExpression) visit(ctx.expression()));
    }

    @Override
    public Object visitPrimaryExpr(JsonTSchemaParser.PrimaryExprContext ctx) {
        return visit(ctx.primary_expression());
    }

    @Override
    public Object visitPrimary_expression(JsonTSchemaParser.Primary_expressionContext ctx) {
        if (ctx.literal() != null) {
            return JsonTExpression.literal((JsonTValue) visit(ctx.literal()));
        } else if (ctx.function_call() != null) {
            throw new JsonTError.Parse("Function calls are not yet supported in the Java expression model");
        } else if (ctx.field_path() != null) {
            FieldPath path = buildFieldPath(ctx.field_path());
            if (path.segments().size() == 1) {
                return JsonTExpression.fieldName(path.segments().get(0));
            }
            return JsonTExpression.field(path);
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        throw new JsonTError.Parse("Unknown primary expression");
    }

    @Override
    public Object visitLiteral(JsonTSchemaParser.LiteralContext ctx) {
        if (ctx.null_value() != null)
            return JsonTValue.nullValue();

        var sctx = ctx.scalar_value();
        if (sctx.boolean_val() != null) {
            return JsonTValue.bool("true".equals(sctx.boolean_val().getText()));
        }
        if (sctx.number() != null) {
            return JsonTValue.d64(Double.parseDouble(sctx.number().getText()));
        }
        if (sctx.string_val() != null) {
            String raw = sctx.string_val().getText();
            return JsonTValue.text(raw.substring(1, raw.length() - 1));
        }
        throw new JsonTError.Parse("Unknown literal");
    }
}
