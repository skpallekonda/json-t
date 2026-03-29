package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests expression parsing through the schema DSL — all operators, precedence,
 * associativity, literals, and field references.
 */
class ExpressionParserTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    static JsonTExpression parseExpr(String exprText) {
        String dsl = """
                {
                  namespace: {
                    baseUrl: "",
                    version: "",
                    catalogs: [
                      {
                        schemas: [
                          X: {
                            fields: {
                              i32: a,
                              i32: b,
                              d64: c,
                              bool: flag
                            },
                            validations: {
                              rules { %s }
                            }
                          }
                        ]
                      }
                    ],
                    data-schema: X
                  }
                }
                """.formatted(exprText);
        var rule = JsonTParser.parseNamespace(dsl)
                .findSchema("X").orElseThrow()
                .validation().orElseThrow()
                .rules().get(0);
        return ((JsonTRule.Expression) rule).expr();
    }

    static JsonTExpression parseFilter(String exprText) {
        String dsl = """
                {
                  namespace: {
                    baseUrl: "",
                    version: "",
                    catalogs: [
                      {
                        schemas: [
                          Base: { fields: { i32: a, i32: b, d64: c } },
                          D: FROM Base {
                            operations: ( filter %s )
                          }
                        ]
                      }
                    ],
                    data-schema: D
                  }
                }
                """.formatted(exprText);
        var op = (SchemaOperation.Filter) JsonTParser.parseNamespace(dsl)
                .findSchema("D").orElseThrow()
                .operations().get(0);
        return op.predicate();
    }

    // ── Comparison operators ──────────────────────────────────────────────────

    @Test void op_gt() {
        var e = (JsonTExpression.Binary) parseExpr("a > 0");
        assertEquals(BinaryOp.GT, e.op());
        assertInstanceOf(JsonTExpression.FieldRef.class, e.lhs());
        assertInstanceOf(JsonTExpression.Literal.class, e.rhs());
    }

    @Test void op_lt() {
        assertEquals(BinaryOp.LT, ((JsonTExpression.Binary) parseExpr("a < b")).op());
    }

    @Test void op_ge() {
        assertEquals(BinaryOp.GE, ((JsonTExpression.Binary) parseExpr("a >= 10")).op());
    }

    @Test void op_le() {
        assertEquals(BinaryOp.LE, ((JsonTExpression.Binary) parseExpr("a <= 10")).op());
    }

    @Test void op_eq() {
        assertEquals(BinaryOp.EQ, ((JsonTExpression.Binary) parseExpr("a == b")).op());
    }

    @Test void op_ne() {
        assertEquals(BinaryOp.NE, ((JsonTExpression.Binary) parseExpr("a != b")).op());
    }

    // ── Logical operators ─────────────────────────────────────────────────────

    @Test void op_and() {
        var e = (JsonTExpression.Binary) parseExpr("a > 0 && b < 10");
        assertEquals(BinaryOp.AND, e.op());
        assertInstanceOf(JsonTExpression.Binary.class, e.lhs());
        assertInstanceOf(JsonTExpression.Binary.class, e.rhs());
    }

    @Test void op_or() {
        assertEquals(BinaryOp.OR, ((JsonTExpression.Binary) parseExpr("a > 0 || b < 10")).op());
    }

    // ── Arithmetic operators ──────────────────────────────────────────────────

    @Test void op_add() {
        var gt = (JsonTExpression.Binary) parseExpr("a + b > 0");
        assertEquals(BinaryOp.GT, gt.op());
        assertEquals(BinaryOp.ADD, ((JsonTExpression.Binary) gt.lhs()).op());
    }

    @Test void op_sub() {
        var gt = (JsonTExpression.Binary) parseExpr("a - b > 0");
        assertEquals(BinaryOp.SUB, ((JsonTExpression.Binary) gt.lhs()).op());
    }

    @Test void op_mul() {
        var gt = (JsonTExpression.Binary) parseExpr("a * b > 0");
        assertEquals(BinaryOp.MUL, ((JsonTExpression.Binary) gt.lhs()).op());
    }

    @Test void op_div() {
        var gt = (JsonTExpression.Binary) parseExpr("a / b > 0");
        assertEquals(BinaryOp.DIV, ((JsonTExpression.Binary) gt.lhs()).op());
    }

    // ── Unary operators ───────────────────────────────────────────────────────

    @Test void op_not() {
        var e = (JsonTExpression.Unary) parseExpr("!flag");
        assertEquals(UnaryOp.NOT, e.op());
        assertInstanceOf(JsonTExpression.FieldRef.class, e.operand());
    }

    @Test void op_neg() {
        var gt = (JsonTExpression.Binary) parseExpr("(-a) > 0");
        assertEquals(BinaryOp.GT, gt.op());
        var neg = (JsonTExpression.Unary) gt.lhs();
        assertEquals(UnaryOp.NEG, neg.op());
        assertInstanceOf(JsonTExpression.FieldRef.class, neg.operand());
    }

    // ── Precedence ────────────────────────────────────────────────────────────

    @Test void precedence_mulBeforeAdd() {
        var gt  = (JsonTExpression.Binary) parseExpr("a + b * c > 0");
        var add = (JsonTExpression.Binary) gt.lhs();
        assertEquals(BinaryOp.ADD, add.op());
        assertEquals(BinaryOp.MUL, ((JsonTExpression.Binary) add.rhs()).op());
    }

    @Test void precedence_addBeforeComparison() {
        var gt = (JsonTExpression.Binary) parseExpr("a + b > c");
        assertEquals(BinaryOp.GT, gt.op());
        assertEquals(BinaryOp.ADD, ((JsonTExpression.Binary) gt.lhs()).op());
    }

    @Test void precedence_comparisonBeforeAnd() {
        var and = (JsonTExpression.Binary) parseExpr("a > 0 && b < 10");
        assertEquals(BinaryOp.AND, and.op());
        assertEquals(BinaryOp.GT, ((JsonTExpression.Binary) and.lhs()).op());
        assertEquals(BinaryOp.LT, ((JsonTExpression.Binary) and.rhs()).op());
    }

    @Test void precedence_andBeforeOr() {
        var or = (JsonTExpression.Binary) parseExpr("a > 0 || b < 10 && c > 0");
        assertEquals(BinaryOp.OR, or.op());
        assertEquals(BinaryOp.AND, ((JsonTExpression.Binary) or.rhs()).op());
    }

    @Test void precedence_equalityBelowComparison() {
        var eq = (JsonTExpression.Binary) parseExpr("a > b == flag");
        assertEquals(BinaryOp.EQ, eq.op());
        assertEquals(BinaryOp.GT, ((JsonTExpression.Binary) eq.lhs()).op());
    }

    // ── Associativity ─────────────────────────────────────────────────────────

    @Test void associativity_sub_leftToRight() {
        var gt  = (JsonTExpression.Binary) parseExpr("a - b - c > 0");
        var sub = (JsonTExpression.Binary) gt.lhs();      
        assertEquals(BinaryOp.SUB, sub.op());
        assertEquals(BinaryOp.SUB, ((JsonTExpression.Binary) sub.lhs()).op()); 
    }

    @Test void associativity_div_leftToRight() {
        var gt  = (JsonTExpression.Binary) parseExpr("a / b / c > 0");
        var div = (JsonTExpression.Binary) gt.lhs();
        assertEquals(BinaryOp.DIV, div.op());
        assertEquals(BinaryOp.DIV, ((JsonTExpression.Binary) div.lhs()).op());
    }

    // ── Parentheses ───────────────────────────────────────────────────────────

    @Test void parens_overrideMulAdd() {
        var gt  = (JsonTExpression.Binary) parseExpr("(a + b) * c > 0");
        var mul = (JsonTExpression.Binary) gt.lhs();
        assertEquals(BinaryOp.MUL, mul.op());
        assertEquals(BinaryOp.ADD, ((JsonTExpression.Binary) mul.lhs()).op());
    }

    @Test void parens_orInsideAnd() {
        var and = (JsonTExpression.Binary) parseExpr("a > 0 && (b < 0 || c > 0)");
        assertEquals(BinaryOp.AND, and.op());
        assertEquals(BinaryOp.OR, ((JsonTExpression.Binary) and.rhs()).op());
    }

    // ── Literal types ─────────────────────────────────────────────────────────

    @Test void literal_integer() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("a > 42")).rhs();
        assertTrue(lit.value().isNumeric());
        assertEquals(42.0, lit.value().toDouble(), 1e-9);
    }

    @Test void literal_float() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("c > 3.14")).rhs();
        assertEquals(3.14, lit.value().toDouble(), 1e-9);
    }

    @Test void literal_boolTrue() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("flag == true")).rhs();
        assertInstanceOf(JsonTValue.Bool.class, lit.value());
        assertTrue(((JsonTValue.Bool) lit.value()).value());
    }

    @Test void literal_boolFalse() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("flag == false")).rhs();
        assertFalse(((JsonTValue.Bool) lit.value()).value());
    }

    @Test void literal_null() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("a == null")).rhs();
        assertTrue(lit.value().isNull());
    }

    @Test void literal_string() {
        var lit = (JsonTExpression.Literal) ((JsonTExpression.Binary) parseExpr("a == \"hello\"")).rhs();
        assertInstanceOf(JsonTValue.Str.class, lit.value());
        assertEquals("hello", lit.value().asText());
    }

    // ── Field references ──────────────────────────────────────────────────────

    @Test void fieldRef_simple() {
        var ref = (JsonTExpression.FieldRef) ((JsonTExpression.Binary) parseExpr("a > 0")).lhs();
        assertEquals("a", ref.path().leaf());
    }

    @Test void fieldRef_dotted() {
        var ref = (JsonTExpression.FieldRef) ((JsonTExpression.Binary) parseExpr("a.b > 0")).lhs();
        assertEquals("a.b", ref.path().dotJoined());
    }

    @Test void fieldRef_threeSegments() {
        var ref = (JsonTExpression.FieldRef) ((JsonTExpression.Binary) parseExpr("a.b.c > 0")).lhs();
        assertEquals("a.b.c", ref.path().dotJoined());
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    @Test void eval_gt_true() {
        var expr = parseExpr("a > 5");
        var ctx = EvalContext.create().bind("a", JsonTValue.i32(10));
        assertEquals(JsonTValue.bool(true), expr.evaluate(ctx));
    }

    @Test void eval_gt_false() {
        var expr = parseExpr("a > 5");
        var ctx = EvalContext.create().bind("a", JsonTValue.i32(3));
        assertEquals(JsonTValue.bool(false), expr.evaluate(ctx));
    }

    @Test void eval_and() {
        var expr = parseExpr("a > 0 && b < 10");
        var ctx = EvalContext.create()
                .bind("a", JsonTValue.i32(5))
                .bind("b", JsonTValue.i32(5));
        assertEquals(JsonTValue.bool(true), expr.evaluate(ctx));
    }

    @Test void eval_or_shortCircuits() {
        var expr = parseExpr("a > 0 || b < 10");
        var ctx = EvalContext.create()
                .bind("a", JsonTValue.i32(0))
                .bind("b", JsonTValue.i32(5));
        assertEquals(JsonTValue.bool(true), expr.evaluate(ctx));
    }

    @Test void eval_arithmetic() {
        var expr = parseFilter("a * 2 > 10");
        var ctx = EvalContext.create().bind("a", JsonTValue.i32(6));
        assertEquals(JsonTValue.bool(true), expr.evaluate(ctx));
    }

    @Test void eval_not() {
        var expr = parseExpr("!flag");
        var ctx = EvalContext.create().bind("flag", JsonTValue.bool(false));
        assertEquals(JsonTValue.bool(true), expr.evaluate(ctx));
    }

    @Test void eval_eq_strings() {
        var expr = parseExpr("a == \"hi\"");
        var ctxTrue  = EvalContext.create().bind("a", JsonTValue.text("hi"));
        var ctxFalse = EvalContext.create().bind("a", JsonTValue.text("bye"));
        assertEquals(JsonTValue.bool(true),  expr.evaluate(ctxTrue));
        assertEquals(JsonTValue.bool(false), expr.evaluate(ctxFalse));
    }
}
