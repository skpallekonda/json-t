package io.github.datakore.jsont.model;

import io.github.datakore.jsont.error.JsonTError;
import org.junit.jupiter.api.Test;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static io.github.datakore.jsont.model.BinaryOp.*;
import static io.github.datakore.jsont.model.JsonTExpression.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTExpressionTest {

    // ─── Literals ─────────────────────────────────────────────────────────────

    @Test
    void literal_evaluatesToItselfRegardlessOfContext() {
        var expr = literal(i32(42));
        assertEquals(i32(42), expr.evaluate(EvalContext.empty()));
    }

    // ─── Field references ─────────────────────────────────────────────────────

    @Test
    void fieldName_resolvesFromContext() {
        var expr = fieldName("age");
        var ctx  = EvalContext.create().bind("age", i32(25));
        assertEquals(i32(25), expr.evaluate(ctx));
    }

    @Test
    void fieldName_throwsWhenUnbound() {
        var expr = fieldName("age");
        assertThrows(JsonTError.Eval.class, () -> expr.evaluate(EvalContext.empty()));
    }

    @Test
    void field_resolvesLeafSegment() {
        var expr = field(FieldPath.of("address", "city"));
        var ctx  = EvalContext.create().bind("city", text("Rome"));
        assertEquals(text("Rome"), expr.evaluate(ctx));
    }

    // ─── Comparison operators ─────────────────────────────────────────────────

    @Test
    void binary_EQ_numeric() {
        assertEquals(bool(true),  binary(EQ, literal(i32(5)), literal(i32(5))).evaluate(EvalContext.empty()));
        assertEquals(bool(false), binary(EQ, literal(i32(5)), literal(i64(6L))).evaluate(EvalContext.empty()));
    }

    @Test
    void binary_EQ_text() {
        assertEquals(bool(true),  binary(EQ, literal(text("a")), literal(text("a"))).evaluate(EvalContext.empty()));
        assertEquals(bool(false), binary(EQ, literal(text("a")), literal(text("b"))).evaluate(EvalContext.empty()));
    }

    @Test
    void binary_NE() {
        assertEquals(bool(false), binary(NE, literal(i32(5)), literal(i32(5))).evaluate(EvalContext.empty()));
        assertEquals(bool(true),  binary(NE, literal(i32(5)), literal(i32(6))).evaluate(EvalContext.empty()));
    }

    @Test
    void binary_LT_LE_GT_GE() {
        var ctx = EvalContext.empty();
        assertEquals(bool(true),  binary(LT, literal(i32(3)), literal(i32(5))).evaluate(ctx));
        assertEquals(bool(false), binary(LT, literal(i32(5)), literal(i32(3))).evaluate(ctx));
        assertEquals(bool(true),  binary(LE, literal(i32(5)), literal(i32(5))).evaluate(ctx));
        assertEquals(bool(true),  binary(GT, literal(i32(5)), literal(i32(3))).evaluate(ctx));
        assertEquals(bool(false), binary(GT, literal(i32(3)), literal(i32(5))).evaluate(ctx));
        assertEquals(bool(true),  binary(GE, literal(i32(5)), literal(i32(5))).evaluate(ctx));
    }

    // ─── Logical operators ────────────────────────────────────────────────────

    @Test
    void binary_AND() {
        var ctx = EvalContext.empty();
        assertEquals(bool(true),  binary(AND, literal(bool(true)),  literal(bool(true))).evaluate(ctx));
        assertEquals(bool(false), binary(AND, literal(bool(true)),  literal(bool(false))).evaluate(ctx));
        assertEquals(bool(false), binary(AND, literal(bool(false)), literal(bool(true))).evaluate(ctx));
    }

    @Test
    void binary_OR() {
        var ctx = EvalContext.empty();
        assertEquals(bool(true),  binary(OR, literal(bool(true)),  literal(bool(false))).evaluate(ctx));
        assertEquals(bool(false), binary(OR, literal(bool(false)), literal(bool(false))).evaluate(ctx));
        assertEquals(bool(true),  binary(OR, literal(bool(false)), literal(bool(true))).evaluate(ctx));
    }

    @Test
    void binary_AND_requiresBoolOperands() {
        assertThrows(JsonTError.Eval.class,
                () -> binary(AND, literal(i32(1)), literal(bool(true))).evaluate(EvalContext.empty()));
    }

    // ─── Arithmetic operators ─────────────────────────────────────────────────

    @Test
    void binary_ADD() {
        var result = binary(ADD, literal(i32(3)), literal(i32(4))).evaluate(EvalContext.empty());
        assertEquals(d64(7.0), result);
    }

    @Test
    void binary_SUB() {
        var result = binary(SUB, literal(d64(10.0)), literal(d64(4.5))).evaluate(EvalContext.empty());
        assertEquals(d64(5.5), result);
    }

    @Test
    void binary_MUL() {
        var result = binary(MUL, literal(i32(6)), literal(i32(7))).evaluate(EvalContext.empty());
        assertEquals(d64(42.0), result);
    }

    @Test
    void binary_DIV() {
        var result = binary(DIV, literal(d64(9.0)), literal(d64(3.0))).evaluate(EvalContext.empty());
        assertEquals(d64(3.0), result);
    }

    @Test
    void binary_DIV_byZero_throws() {
        assertThrows(JsonTError.Eval.class,
                () -> binary(DIV, literal(i32(5)), literal(i32(0))).evaluate(EvalContext.empty()));
    }

    // ─── Unary operators ──────────────────────────────────────────────────────

    @Test
    void unary_NOT_flipsBool() {
        assertEquals(bool(false), not(literal(bool(true))).evaluate(EvalContext.empty()));
        assertEquals(bool(true),  not(literal(bool(false))).evaluate(EvalContext.empty()));
    }

    @Test
    void unary_NOT_requiresBool() {
        assertThrows(JsonTError.Eval.class,
                () -> not(literal(i32(1))).evaluate(EvalContext.empty()));
    }

    @Test
    void unary_NEG_negatesNumber() {
        assertEquals(d64(-5.0), neg(literal(i32(5))).evaluate(EvalContext.empty()));
        assertEquals(d64(3.14), neg(literal(d64(-3.14))).evaluate(EvalContext.empty()));
    }

    @Test
    void unary_NEG_requiresNumeric() {
        assertThrows(JsonTError.Eval.class,
                () -> neg(literal(text("x"))).evaluate(EvalContext.empty()));
    }

    // ─── Composition (realistic filter expression) ────────────────────────────

    @Test
    void compositeExpression_adultAndHighIncome() {
        // age >= 18 && income > 50000
        JsonTExpression expr = binary(AND,
                binary(GE, fieldName("age"),    literal(i32(18))),
                binary(GT, fieldName("income"), literal(i32(50000))));

        var ctx1 = EvalContext.create().bind("age", i32(25)).bind("income", i32(80000));
        assertEquals(bool(true), expr.evaluate(ctx1));

        var ctx2 = EvalContext.create().bind("age", i32(16)).bind("income", i32(80000));
        assertEquals(bool(false), expr.evaluate(ctx2));

        var ctx3 = EvalContext.create().bind("age", i32(25)).bind("income", i32(30000));
        assertEquals(bool(false), expr.evaluate(ctx3));
    }

    // ─── Factory null-safety ──────────────────────────────────────────────────

    @Test
    void literal_rejectsNull() {
        assertThrows(NullPointerException.class, () -> literal(null));
    }

    @Test
    void binary_rejectsNullOp() {
        assertThrows(NullPointerException.class,
                () -> binary(null, literal(i32(1)), literal(i32(2))));
    }

    @Test
    void not_rejectsNull() {
        assertThrows(NullPointerException.class, () -> not(null));
    }
}
