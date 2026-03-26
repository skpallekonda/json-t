package io.github.datakore.jsont.model;

import io.github.datakore.jsont.error.JsonTError;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

/**
 * Sealed expression tree for JsonT filter/transform rules.
 *
 * <p>Mirrors Rust's {@code JsonTExpression} enum. All variants are nested
 * records; static factories provide the Rust-like call syntax:
 *
 * <pre>{@code
 *   // age >= 18
 *   JsonTExpression adult = JsonTExpression.binary(
 *       BinaryOp.GE,
 *       JsonTExpression.fieldName("age"),
 *       JsonTExpression.literal(JsonTValue.i32(18))
 *   );
 *
 *   EvalContext ctx = EvalContext.create().bind("age", JsonTValue.i32(25));
 *   JsonTValue result = adult.evaluate(ctx);  // Bool(true)
 *
 *   // !active
 *   JsonTExpression inactive = JsonTExpression.not(JsonTExpression.fieldName("active"));
 * }</pre>
 */
public sealed interface JsonTExpression
        permits JsonTExpression.Literal,
                JsonTExpression.FieldRef,
                JsonTExpression.Binary,
                JsonTExpression.Unary {

    // ─── Nested record variants ────────────────────────────────────────────────

    /** A constant value embedded directly in the expression. */
    record Literal(JsonTValue value) implements JsonTExpression {
        public Literal {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public JsonTValue evaluate(EvalContext ctx) {
            return value;
        }
    }

    /** A reference to a named field in the evaluation context. */
    record FieldRef(FieldPath path) implements JsonTExpression {
        public FieldRef {
            Objects.requireNonNull(path, "path must not be null");
        }

        @Override
        public JsonTValue evaluate(EvalContext ctx) {
            return ctx.lookup(path.leaf())
                    .orElseThrow(() -> new JsonTError.Eval("Unbound field: " + path));
        }
    }

    /** A binary operator applied to two sub-expressions. */
    record Binary(BinaryOp op, JsonTExpression lhs, JsonTExpression rhs) implements JsonTExpression {
        public Binary {
            Objects.requireNonNull(op, "op must not be null");
            Objects.requireNonNull(lhs, "lhs must not be null");
            Objects.requireNonNull(rhs, "rhs must not be null");
        }

        @Override
        public JsonTValue evaluate(EvalContext ctx) {
            JsonTValue l = lhs.evaluate(ctx);
            JsonTValue r = rhs.evaluate(ctx);
            return switch (op) {
                case EQ  -> numericAwareEq(l, r, true);
                case NE  -> numericAwareEq(l, r, false);
                case LT  -> JsonTValue.bool(requireNumeric(l, "LT") < requireNumeric(r, "LT"));
                case LE  -> JsonTValue.bool(requireNumeric(l, "LE") <= requireNumeric(r, "LE"));
                case GT  -> JsonTValue.bool(requireNumeric(l, "GT") > requireNumeric(r, "GT"));
                case GE  -> JsonTValue.bool(requireNumeric(l, "GE") >= requireNumeric(r, "GE"));
                case AND -> JsonTValue.bool(requireBool(l, "AND") && requireBool(r, "AND"));
                case OR  -> JsonTValue.bool(requireBool(l, "OR")  || requireBool(r, "OR"));
                case ADD -> applyArithmetic(l, r, (a, b) -> a + b);
                case SUB -> applyArithmetic(l, r, (a, b) -> a - b);
                case MUL -> applyArithmetic(l, r, (a, b) -> a * b);
                case DIV -> {
                    double divisor = requireNumeric(r, "DIV");
                    if (divisor == 0.0)
                        throw new JsonTError.Eval("Division by zero");
                    yield applyArithmetic(l, r, (a, b) -> a / b);
                }
            };
        }

        private static JsonTValue numericAwareEq(JsonTValue l, JsonTValue r, boolean eq) {
            boolean same;
            if (l.isNumeric() && r.isNumeric()) {
                same = l.toDouble() == r.toDouble();
            } else if (l instanceof JsonTValue.Text lt && r instanceof JsonTValue.Text rt) {
                same = lt.value().equals(rt.value());
            } else {
                same = l.equals(r);
            }
            return JsonTValue.bool(eq == same);
        }

        private static double requireNumeric(JsonTValue v, String op) {
            if (!v.isNumeric())
                throw new JsonTError.Eval(op + " requires a numeric operand, got " + v.getClass().getSimpleName());
            return v.toDouble();
        }

        private static boolean requireBool(JsonTValue v, String op) {
            if (!(v instanceof JsonTValue.Bool b))
                throw new JsonTError.Eval(op + " requires a Bool operand, got " + v.getClass().getSimpleName());
            return b.value();
        }

        private static JsonTValue applyArithmetic(JsonTValue l, JsonTValue r, DoubleBinaryOperator fn) {
            double result = fn.applyAsDouble(requireNumeric(l, "arithmetic"), requireNumeric(r, "arithmetic"));
            return JsonTValue.d64(result);
        }
    }

    /** A unary operator applied to one sub-expression. */
    record Unary(UnaryOp op, JsonTExpression operand) implements JsonTExpression {
        public Unary {
            Objects.requireNonNull(op, "op must not be null");
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public JsonTValue evaluate(EvalContext ctx) {
            JsonTValue v = operand.evaluate(ctx);
            return switch (op) {
                case NOT -> {
                    if (!(v instanceof JsonTValue.Bool b))
                        throw new JsonTError.Eval("NOT requires a Bool operand, got " + v.getClass().getSimpleName());
                    yield JsonTValue.bool(!b.value());
                }
                case NEG -> {
                    if (!v.isNumeric())
                        throw new JsonTError.Eval("NEG requires a numeric operand, got " + v.getClass().getSimpleName());
                    yield JsonTValue.d64(-v.toDouble());
                }
            };
        }
    }

    // ─── Core method ──────────────────────────────────────────────────────────

    /**
     * Evaluates this expression against the given context.
     *
     * @param ctx named bindings for field references
     * @return the computed value
     * @throws JsonTError.Eval on unbound field, type mismatch, or div-by-zero
     */
    JsonTValue evaluate(EvalContext ctx);

    // ─── Static factories ─────────────────────────────────────────────────────

    /** A literal constant. Equivalent to Rust {@code JsonTExpression::literal(v)}. */
    static JsonTExpression literal(JsonTValue value) {
        return new Literal(value);
    }

    /** A reference to a single-segment field name. */
    static JsonTExpression fieldName(String name) {
        return new FieldRef(FieldPath.single(name));
    }

    /** A reference to a (possibly nested) field path. */
    static JsonTExpression field(FieldPath path) {
        return new FieldRef(path);
    }

    /** A binary expression. */
    static JsonTExpression binary(BinaryOp op, JsonTExpression lhs, JsonTExpression rhs) {
        return new Binary(op, lhs, rhs);
    }

    /** Logical NOT of {@code operand}. */
    static JsonTExpression not(JsonTExpression operand) {
        return new Unary(UnaryOp.NOT, operand);
    }

    /** Arithmetic negation of {@code operand}. */
    static JsonTExpression neg(JsonTExpression operand) {
        return new Unary(UnaryOp.NEG, operand);
    }
}
