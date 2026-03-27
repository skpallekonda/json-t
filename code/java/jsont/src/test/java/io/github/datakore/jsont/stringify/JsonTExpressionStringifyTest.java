package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;
import static io.github.datakore.jsont.model.JsonTValue.*;
import static io.github.datakore.jsont.model.JsonTExpression.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTExpressionStringifyTest {

    @Test void literal_int() {
        assertEquals("42", JsonTStringifier.stringify(literal(i32(42))));
    }

    @Test void literal_text() {
        assertEquals("\"hello\"", JsonTStringifier.stringify(literal(text("hello"))));
    }

    @Test void literal_bool_true() {
        assertEquals("true", JsonTStringifier.stringify(literal(bool(true))));
    }

    @Test void fieldRef_simple() {
        assertEquals("price", JsonTStringifier.stringify(fieldName("price")));
    }

    @Test void fieldRef_nested() {
        assertEquals("address.city",
                JsonTStringifier.stringify(field(FieldPath.of("address", "city"))));
    }

    @Test void binary_gt() {
        assertEquals("qty > 0",
                JsonTStringifier.stringify(binary(BinaryOp.GT, fieldName("qty"), literal(i32(0)))));
    }

    @Test void binary_eq() {
        assertEquals("status == \"ACTIVE\"",
                JsonTStringifier.stringify(binary(BinaryOp.EQ, fieldName("status"), literal(text("ACTIVE")))));
    }

    @Test void binary_and() {
        assertEquals("a > 1 && b < 10",
                JsonTStringifier.stringify(
                        binary(BinaryOp.AND,
                                binary(BinaryOp.GT, fieldName("a"), literal(i32(1))),
                                binary(BinaryOp.LT, fieldName("b"), literal(i32(10))))));
    }

    @Test void binary_or_wraps_lower_precedence_and() {
        // (a && b) || c  — AND has higher precedence than OR, so no parens needed
        // But if we have OR as parent and AND as child, AND has higher prec — no parens
        // Test: OR( AND(a,b), c ) → "a && b || c"  (no parens — AND binds tighter)
        String result = JsonTStringifier.stringify(
                binary(BinaryOp.OR,
                        binary(BinaryOp.AND, fieldName("a"), fieldName("b")),
                        fieldName("c")));
        assertEquals("a && b || c", result);
    }

    @Test void binary_and_wraps_lower_prec_or() {
        // AND( OR(a,b), c ) → "(a || b) && c"  — OR is lower prec than AND, so needs parens
        String result = JsonTStringifier.stringify(
                binary(BinaryOp.AND,
                        binary(BinaryOp.OR, fieldName("a"), fieldName("b")),
                        fieldName("c")));
        assertEquals("(a || b) && c", result);
    }

    @Test void binary_arithmetic_add_sub() {
        // SUB( ADD(a,b), c ) → "a + b - c"  — same precedence, no parens needed (left assoc)
        String result = JsonTStringifier.stringify(
                binary(BinaryOp.SUB,
                        binary(BinaryOp.ADD, fieldName("a"), fieldName("b")),
                        fieldName("c")));
        assertEquals("a + b - c", result);
    }

    @Test void binary_mul_wraps_lower_prec_add() {
        // MUL( ADD(a,b), c ) → "(a + b) * c"
        String result = JsonTStringifier.stringify(
                binary(BinaryOp.MUL,
                        binary(BinaryOp.ADD, fieldName("a"), fieldName("b")),
                        fieldName("c")));
        assertEquals("(a + b) * c", result);
    }

    @Test void unary_not_simple() {
        assertEquals("!active", JsonTStringifier.stringify(not(fieldName("active"))));
    }

    @Test void unary_neg_simple() {
        assertEquals("-5", JsonTStringifier.stringify(neg(literal(i32(5)))));
    }

    @Test void unary_not_wraps_binary() {
        // NOT( EQ(a, b) ) → "!(a == b)"
        String result = JsonTStringifier.stringify(
                not(binary(BinaryOp.EQ, fieldName("a"), fieldName("b"))));
        assertEquals("!(a == b)", result);
    }

    @Test void all_binary_symbols() {
        record Case(BinaryOp op, String sym) {}
        var cases = new Case[]{
            new Case(BinaryOp.EQ, "=="), new Case(BinaryOp.NE, "!="),
            new Case(BinaryOp.LT, "<"),  new Case(BinaryOp.LE, "<="),
            new Case(BinaryOp.GT, ">"),  new Case(BinaryOp.GE, ">="),
            new Case(BinaryOp.AND, "&&"), new Case(BinaryOp.OR, "||"),
            new Case(BinaryOp.ADD, "+"), new Case(BinaryOp.SUB, "-"),
            new Case(BinaryOp.MUL, "*"), new Case(BinaryOp.DIV, "/"),
        };
        for (var c : cases) {
            String result = JsonTStringifier.stringify(
                    binary(c.op(), fieldName("x"), fieldName("y")));
            assertEquals("x " + c.sym() + " y", result, "op=" + c.op());
        }
    }
}
