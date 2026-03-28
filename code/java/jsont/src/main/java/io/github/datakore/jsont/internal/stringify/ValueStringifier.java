package io.github.datakore.jsont.internal.stringify;

import io.github.datakore.jsont.model.*;

import java.util.List;

/** Internal helper: serialises {@link JsonTValue}, {@link JsonTRow} and {@link JsonTExpression}. */
public final class ValueStringifier {

    private ValueStringifier() {}

    // ── JsonTValue ─────────────────────────────────────────────────────────────

    public static String stringifyValue(JsonTValue v) {
        if (v instanceof JsonTValue.Null)         return "null";
        if (v instanceof JsonTValue.Bool  b)      return Boolean.toString(b.value());
        if (v instanceof JsonTValue.I16   n)      return Short.toString(n.value());
        if (v instanceof JsonTValue.I32   n)      return Integer.toString(n.value());
        if (v instanceof JsonTValue.I64   n)      return Long.toString(n.value());
        if (v instanceof JsonTValue.U16   n)      return Integer.toString(n.value());
        if (v instanceof JsonTValue.U32   n)      return Long.toString(n.value());
        if (v instanceof JsonTValue.U64   n)      return Long.toUnsignedString(n.value());
        if (v instanceof JsonTValue.D32   n)      return Float.toString(n.value());
        if (v instanceof JsonTValue.D64   n)      return Double.toString(n.value());
        if (v instanceof JsonTValue.D128  n)      return n.value().toPlainString();
        if (v instanceof JsonTValue.Text      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Nstr      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Uuid      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Uri       t) return quoteString(t.value());
        if (v instanceof JsonTValue.Email     t) return quoteString(t.value());
        if (v instanceof JsonTValue.Hostname  t) return quoteString(t.value());
        if (v instanceof JsonTValue.Ipv4      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Ipv6      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Date      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Time      t) return quoteString(t.value());
        if (v instanceof JsonTValue.DateTime  t) return quoteString(t.value());
        if (v instanceof JsonTValue.Timestamp t) return quoteString(t.value());
        if (v instanceof JsonTValue.Tsz       t) return quoteString(t.value());
        if (v instanceof JsonTValue.Inst      t) return quoteString(t.value());
        if (v instanceof JsonTValue.Duration  t) return quoteString(t.value());
        if (v instanceof JsonTValue.Base64    t) return quoteString(t.value());
        if (v instanceof JsonTValue.Hex       t) return quoteString(t.value());
        if (v instanceof JsonTValue.Oid       t) return quoteString(t.value());
        if (v instanceof JsonTValue.Array     a) return stringifyArray(a.elements());
        if (v instanceof JsonTValue.Unspecified) return "_";
        if (v instanceof JsonTValue.Enum      e) return e.value();
        throw new IllegalArgumentException("Unknown JsonTValue: " + v);
    }

    public static String stringifyArray(List<JsonTValue> elements) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(stringifyValue(elements.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // ── JsonTRow (wire format — no extra spaces) ───────────────────────────────

    public static String stringifyRow(JsonTRow row) {
        var sb = new StringBuilder("{");
        List<JsonTValue> values = row.values();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(stringifyValue(values.get(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    // ── JsonTExpression ────────────────────────────────────────────────────────

    public static String stringifyExpr(JsonTExpression expr) {
        if (expr instanceof JsonTExpression.Literal lit) {
            return stringifyValue(lit.value());
        }
        if (expr instanceof JsonTExpression.FieldRef ref) {
            return ref.path().dotJoined();
        }
        if (expr instanceof JsonTExpression.Unary unary) {
            String sym   = unarySymbol(unary.op());
            String inner = stringifyExpr(unary.operand());
            // wrap if operand is a binary op (same logic as Rust)
            if (unary.operand() instanceof JsonTExpression.Binary) {
                return sym + "(" + inner + ")";
            }
            return sym + inner;
        }
        if (expr instanceof JsonTExpression.Binary bin) {
            String l = exprParen(bin.lhs(), bin.op());
            String r = exprParen(bin.rhs(), bin.op());
            return l + " " + binarySymbol(bin.op()) + " " + r;
        }
        throw new IllegalArgumentException("Unknown JsonTExpression: " + expr);
    }

    /** Wrap child in parens when it has lower precedence than the parent operator. */
    private static String exprParen(JsonTExpression child, BinaryOp parentOp) {
        String childStr = stringifyExpr(child);
        if (child instanceof JsonTExpression.Binary childBin) {
            if (precedence(childBin.op()) < precedence(parentOp)) {
                return "(" + childStr + ")";
            }
        }
        return childStr;
    }

    private static int precedence(BinaryOp op) {
        return switch (op) {
            case OR          -> 1;
            case AND         -> 2;
            case EQ, NE      -> 3;
            case LT, LE, GT, GE -> 4;
            case ADD, SUB    -> 5;
            case MUL, DIV    -> 6;
        };
    }

    private static String binarySymbol(BinaryOp op) {
        return switch (op) {
            case EQ  -> "==";
            case NE  -> "!=";
            case LT  -> "<";
            case LE  -> "<=";
            case GT  -> ">";
            case GE  -> ">=";
            case AND -> "&&";
            case OR  -> "||";
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
        };
    }

    private static String unarySymbol(UnaryOp op) {
        return switch (op) {
            case NOT -> "!";
            case NEG -> "-";
        };
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Wraps {@code s} in double-quotes, escaping {@code \} and {@code "}. */
    static String quoteString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
