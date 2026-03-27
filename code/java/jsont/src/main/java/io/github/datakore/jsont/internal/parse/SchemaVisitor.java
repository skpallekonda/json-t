package io.github.datakore.jsont.internal.parse;

import io.github.datakore.jsont.builder.*;
import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaBaseVisitor;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaLexer;
import io.github.datakore.jsont.internal.grammar.JsonTSchemaParser;
import io.github.datakore.jsont.model.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal ANTLR4 visitor that walks the {@code JsonTSchema.g4} parse tree and
 * produces the Java model hierarchy (namespace → catalog → schema → field/enum).
 */
public final class SchemaVisitor extends JsonTSchemaBaseVisitor<Object> {

    // ── Public entry ──────────────────────────────────────────────────────────

    /** Parse a full namespace DSL document, returning the namespace model. */
    public static JsonTNamespace parseNamespace(String input) {
        var lexer  = new JsonTSchemaLexer(CharStreams.fromString(input));
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

        var tree = parser.namespace_def();
        return (JsonTNamespace) new SchemaVisitor().visit(tree);
    }

    // ── Namespace ─────────────────────────────────────────────────────────────

    @Override
    public Object visitNamespace_def(JsonTSchemaParser.Namespace_defContext ctx) {
        var nb = JsonTNamespaceBuilder.create();

        if (ctx.STRING_LITERAL() != null) {
            String raw = ctx.STRING_LITERAL().getText();
            // strip surrounding quotes
            nb.baseUrl(raw.substring(1, raw.length() - 1));
        }

        for (var catCtx : ctx.catalog_def()) {
            nb.catalog((JsonTCatalog) visitCatalog_def(catCtx));
        }

        try {
            return nb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Namespace build error: " + e.getMessage(), e);
        }
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    @Override
    public Object visitCatalog_def(JsonTSchemaParser.Catalog_defContext ctx) {
        var cb = JsonTCatalogBuilder.create();

        for (var itemCtx : ctx.catalog_item()) {
            var result = visitCatalog_item(itemCtx);
            if (result instanceof JsonTSchema schema) {
                cb.schema(schema);
            } else if (result instanceof JsonTEnum enumDef) {
                cb.enum_(enumDef);
            }
        }

        try {
            return cb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Catalog build error: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitCatalog_item(JsonTSchemaParser.Catalog_itemContext ctx) {
        if (ctx.schema_def() != null) return visitSchema_def(ctx.schema_def());
        return visitEnum_def(ctx.enum_def());
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    @Override
    public Object visitSchema_def(JsonTSchemaParser.Schema_defContext ctx) {
        if (ctx.straight_schema_def() != null) return visitStraight_schema_def(ctx.straight_schema_def());
        return visitDerived_schema_def(ctx.derived_schema_def());
    }

    @Override
    public Object visitStraight_schema_def(JsonTSchemaParser.Straight_schema_defContext ctx) {
        String name = ctx.IDENT().getText();
        var sb = JsonTSchemaBuilder.straight(name);

        for (var bodyCtx : ctx.straight_schema_body()) {
            if (bodyCtx.fields_block() != null) {
                for (var fieldCtx : bodyCtx.fields_block().field_def()) {
                    try {
                        sb.fieldFrom((JsonTFieldBuilder) visitField_def(fieldCtx));
                    } catch (BuildError e) {
                        throw new JsonTError.Parse("Field build error in '" + name + "': " + e.getMessage(), e);
                    }
                }
            } else if (bodyCtx.validations_block() != null) {
                var vbb = buildValidationBlock(bodyCtx.validations_block());
                try {
                    sb.validationFrom(vbb);
                } catch (BuildError e) {
                    throw new JsonTError.Parse("Validation build error in '" + name + "': " + e.getMessage(), e);
                }
            }
        }

        try {
            return sb.build();
        } catch (BuildError e) {
            throw new JsonTError.Parse("Schema build error for '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitDerived_schema_def(JsonTSchemaParser.Derived_schema_defContext ctx) {
        List<TerminalNode> idents = ctx.IDENT();
        String name   = idents.get(0).getText();
        String parent = idents.get(1).getText();
        var sb = JsonTSchemaBuilder.derived(name, parent);

        for (var bodyCtx : ctx.derived_schema_body()) {
            SchemaOperation op = buildDerivedOperation(bodyCtx);
            try {
                sb.operation(op);
            } catch (BuildError e) {
                throw new JsonTError.Parse("Operation build error in '" + name + "': " + e.getMessage(), e);
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
    public Object visitField_def(JsonTSchemaParser.Field_defContext ctx) {
        String name = ctx.field_name().getStart().getText();
        var spec = ctx.field_type_spec();
        var base = spec.base_type();

        JsonTFieldBuilder fb;
        if (base.scalar_type() != null) {
            String kw = base.scalar_type().getStart().getText();
            ScalarType st;
            try {
                st = ScalarType.fromKeyword(kw);
            } catch (IllegalArgumentException e) {
                throw new JsonTError.Parse("Unknown scalar type: " + kw);
            }
            fb = JsonTFieldBuilder.scalar(name, st);
        } else {
            // object_type: LT IDENT GT
            String ref = base.object_type().IDENT().getText();
            fb = JsonTFieldBuilder.object(name, ref);
        }

        if (spec.ARRAY_SUFFIX() != null)    fb = fb.asArray();
        if (spec.OPTIONAL_SUFFIX() != null) fb = fb.optional();

        if (spec.constraints() != null) {
            fb = applyConstraints(fb, spec.constraints());
        }

        return fb;
    }

    private JsonTFieldBuilder applyConstraints(JsonTFieldBuilder fb,
                                               JsonTSchemaParser.ConstraintsContext ctx) {
        for (var item : ctx.constraint_item()) {
            fb = applyConstraintItem(fb, item);
        }
        return fb;
    }

    private JsonTFieldBuilder applyConstraintItem(JsonTFieldBuilder fb,
                                                   JsonTSchemaParser.Constraint_itemContext ctx) {
        if (ctx instanceof JsonTSchemaParser.MinValueItemContext c) {
            return fb.minValue(parseSignedNumber(c.signed_number()));
        }
        if (ctx instanceof JsonTSchemaParser.MaxValueItemContext c) {
            return fb.maxValue(parseSignedNumber(c.signed_number()));
        }
        if (ctx instanceof JsonTSchemaParser.MinLengthItemContext c) {
            return fb.minLength(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.MaxLengthItemContext c) {
            return fb.maxLength(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.PatternItemContext c) {
            String raw = c.STRING_LITERAL().getText();
            return fb.pattern(raw.substring(1, raw.length() - 1));
        }
        if (ctx instanceof JsonTSchemaParser.RequiredItemContext) {
            return fb.required();
        }
        if (ctx instanceof JsonTSchemaParser.MaxPrecisionItemContext c) {
            return fb.maxPrecision(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.MinItemsItemContext c) {
            return fb.minItems(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.MaxItemsItemContext c) {
            return fb.maxItems(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.AllowNullsItemContext) {
            return fb.allowNullElements();
        }
        if (ctx instanceof JsonTSchemaParser.MaxNullItemsItemContext c) {
            return fb.maxNullElements(Integer.parseInt(c.INT_LITERAL().getText()));
        }
        if (ctx instanceof JsonTSchemaParser.ConstantValueItemContext c) {
            return fb.constantValue(parseConstraintLiteral(c.constraint_literal()));
        }
        throw new JsonTError.Parse("Unknown constraint type: " + ctx.getText());
    }

    private double parseSignedNumber(JsonTSchemaParser.Signed_numberContext ctx) {
        String text = ctx.INT_LITERAL() != null
                ? ctx.INT_LITERAL().getText()
                : ctx.FLOAT_LITERAL().getText();
        double value = Double.parseDouble(text);
        return ctx.MINUS() != null ? -value : value;
    }

    private JsonTValue parseConstraintLiteral(JsonTSchemaParser.Constraint_literalContext ctx) {
        if (ctx.BOOL_LITERAL() != null) return JsonTValue.bool("true".equals(ctx.BOOL_LITERAL().getText()));
        if (ctx.NULL_LITERAL() != null) return JsonTValue.nullValue();
        if (ctx.STRING_LITERAL() != null) {
            String raw = ctx.STRING_LITERAL().getText();
            return JsonTValue.text(raw.substring(1, raw.length() - 1));
        }
        // numeric (INT or FLOAT, optionally negated)
        String text = ctx.INT_LITERAL() != null ? ctx.INT_LITERAL().getText() : ctx.FLOAT_LITERAL().getText();
        double val = Double.parseDouble(text);
        if (ctx.MINUS() != null) val = -val;
        return JsonTValue.d64(val);
    }

    // ── Validation block ──────────────────────────────────────────────────────

    private JsonTValidationBlockBuilder buildValidationBlock(
            JsonTSchemaParser.Validations_blockContext ctx) {
        var vbb = JsonTValidationBlockBuilder.create();

        for (var itemCtx : ctx.validation_item()) {
            if (itemCtx.unique_validation() != null) {
                var paths = buildFieldPathList(itemCtx.unique_validation().field_path_list());
                vbb.unique(paths);
            } else if (itemCtx.rule_validation() != null) {
                var expr = (JsonTExpression) visit(itemCtx.rule_validation().expr());
                vbb.rule(expr);
            } else if (itemCtx.conditional_validation() != null) {
                var cv = itemCtx.conditional_validation();
                var cond = (JsonTExpression) visit(cv.expr());
                var fields = buildFieldPathList(cv.field_path_list());
                vbb.conditionalRule(cond, fields);
            }
        }

        return vbb;
    }

    // ── Derived operations ────────────────────────────────────────────────────

    private SchemaOperation buildDerivedOperation(JsonTSchemaParser.Derived_schema_bodyContext ctx) {
        if (ctx.rename_op() != null) {
            var op = ctx.rename_op();
            List<RenamePair> pairs = new ArrayList<>();
            for (var rp : op.rename_pair()) {
                List<TerminalNode> ids = rp.IDENT();
                FieldPath from = FieldPath.single(ids.get(0).getText());
                String to = ids.get(1).getText();
                pairs.add(new RenamePair(from, to));
            }
            return SchemaOperation.rename(pairs);
        }
        if (ctx.exclude_op() != null) {
            return SchemaOperation.exclude(buildFieldPathList(ctx.exclude_op().field_path_list()));
        }
        if (ctx.project_op() != null) {
            return SchemaOperation.project(buildFieldPathList(ctx.project_op().field_path_list()));
        }
        if (ctx.filter_op() != null) {
            var expr = (JsonTExpression) visit(ctx.filter_op().expr());
            return SchemaOperation.filter(expr);
        }
        if (ctx.transform_op() != null) {
            var top = ctx.transform_op();
            String target = top.IDENT().getText();
            var expr = (JsonTExpression) visit(top.expr());
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
        List<JsonTSchemaParser.Field_nameContext> names = ctx.field_name();
        if (names.size() == 1) return FieldPath.single(names.get(0).getStart().getText());
        String first = names.get(0).getStart().getText();
        String[] rest = new String[names.size() - 1];
        for (int i = 1; i < names.size(); i++) rest[i - 1] = names.get(i).getStart().getText();
        return FieldPath.of(first, rest);
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitEnum_def(JsonTSchemaParser.Enum_defContext ctx) {
        String name = ctx.IDENT().getText();
        List<String> values = new ArrayList<>();
        for (var idCtx : ctx.enum_values().IDENT()) {
            values.add(idCtx.getText());
        }
        return new JsonTEnum(name, values);
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    @Override
    public Object visitBinaryAndExpr(JsonTSchemaParser.BinaryAndExprContext ctx) {
        return JsonTExpression.binary(BinaryOp.AND,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitBinaryOrExpr(JsonTSchemaParser.BinaryOrExprContext ctx) {
        return JsonTExpression.binary(BinaryOp.OR,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitEqualityExpr(JsonTSchemaParser.EqualityExprContext ctx) {
        BinaryOp op = ctx.EQ_OP() != null ? BinaryOp.EQ : BinaryOp.NE;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitComparisonExpr(JsonTSchemaParser.ComparisonExprContext ctx) {
        BinaryOp op;
        if      (ctx.LT()    != null) op = BinaryOp.LT;
        else if (ctx.LE_OP() != null) op = BinaryOp.LE;
        else if (ctx.GT()    != null) op = BinaryOp.GT;
        else                          op = BinaryOp.GE;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitAddSubExpr(JsonTSchemaParser.AddSubExprContext ctx) {
        BinaryOp op = ctx.PLUS() != null ? BinaryOp.ADD : BinaryOp.SUB;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitMulDivExpr(JsonTSchemaParser.MulDivExprContext ctx) {
        BinaryOp op = ctx.STAR() != null ? BinaryOp.MUL : BinaryOp.DIV;
        return JsonTExpression.binary(op,
                (JsonTExpression) visit(ctx.expr(0)),
                (JsonTExpression) visit(ctx.expr(1)));
    }

    @Override
    public Object visitNotExpr(JsonTSchemaParser.NotExprContext ctx) {
        return JsonTExpression.not((JsonTExpression) visit(ctx.expr()));
    }

    @Override
    public Object visitNegExpr(JsonTSchemaParser.NegExprContext ctx) {
        return JsonTExpression.neg((JsonTExpression) visit(ctx.expr()));
    }

    @Override
    public Object visitParenExpr(JsonTSchemaParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Object visitNullLiteralExpr(JsonTSchemaParser.NullLiteralExprContext ctx) {
        return JsonTExpression.literal(JsonTValue.nullValue());
    }

    @Override
    public Object visitBoolLiteralExpr(JsonTSchemaParser.BoolLiteralExprContext ctx) {
        return JsonTExpression.literal(JsonTValue.bool("true".equals(ctx.getText())));
    }

    @Override
    public Object visitIntLiteralExpr(JsonTSchemaParser.IntLiteralExprContext ctx) {
        return JsonTExpression.literal(JsonTValue.d64(Double.parseDouble(ctx.getText())));
    }

    @Override
    public Object visitFloatLiteralExpr(JsonTSchemaParser.FloatLiteralExprContext ctx) {
        return JsonTExpression.literal(JsonTValue.d64(Double.parseDouble(ctx.getText())));
    }

    @Override
    public Object visitStringLiteralExpr(JsonTSchemaParser.StringLiteralExprContext ctx) {
        String raw = ctx.getText();
        return JsonTExpression.literal(JsonTValue.text(raw.substring(1, raw.length() - 1)));
    }

    @Override
    public Object visitFieldRefExpr(JsonTSchemaParser.FieldRefExprContext ctx) {
        List<TerminalNode> idents = ctx.IDENT();
        if (idents.size() == 1) return JsonTExpression.fieldName(idents.get(0).getText());
        String first = idents.get(0).getText();
        String[] rest = new String[idents.size() - 1];
        for (int i = 1; i < idents.size(); i++) rest[i - 1] = idents.get(i).getText();
        return JsonTExpression.field(FieldPath.of(first, rest));
    }
}
