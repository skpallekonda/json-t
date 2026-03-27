package io.github.datakore.jsont.internal.validate;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticEventKind;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.EvalContext;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.UnaryOp;

import java.util.ArrayList;
import java.util.List;

public class RuleChecker {

    public static List<DiagnosticEvent> checkRules(
            List<JsonTField> fields,
            JsonTValidationBlock validation,
            List<JsonTValue> rowValues,
            int rowIndex) {

        List<DiagnosticEvent> events = new ArrayList<>();

        // Build eval context
        EvalContext ctx = EvalContext.create();
        for (int i = 0; i < fields.size() && i < rowValues.size(); i++) {
            ctx.bind(fields.get(i).name(), rowValues.get(i));
        }

        // Evaluate each rule
        for (JsonTExpression rule : validation.rules()) {
            try {
                JsonTValue result = rule.evaluate(ctx);
                if (result instanceof JsonTValue.Bool b) {
                    if (!b.value()) {
                        events.add(DiagnosticEvent.warning(
                                new DiagnosticEventKind.RuleViolation(
                                        stringifyExpr(rule),
                                        "expression evaluated to false"))
                                .atRow(rowIndex));
                    }
                    // true = OK, no event
                } else {
                    events.add(DiagnosticEvent.warning(
                            new DiagnosticEventKind.RuleViolation(
                                    stringifyExpr(rule),
                                    "non-boolean result: " + typeName(result)))
                            .atRow(rowIndex));
                }
            } catch (JsonTError.Eval e) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.RuleViolation(
                                stringifyExpr(rule),
                                "evaluation error: " + e.getMessage()))
                        .atRow(rowIndex));
            }
        }

        return events;
    }

    static String stringifyExpr(JsonTExpression expr) {
        if (expr instanceof JsonTExpression.Literal lit) {
            return lit.value().toString();
        }
        if (expr instanceof JsonTExpression.FieldRef ref) {
            return ref.path().leaf();
        }
        if (expr instanceof JsonTExpression.Binary bin) {
            return "(" + stringifyExpr(bin.lhs()) + " " + symbolOf(bin.op()) + " " + stringifyExpr(bin.rhs()) + ")";
        }
        if (expr instanceof JsonTExpression.Unary un) {
            return symbolOf(un.op()) + stringifyExpr(un.operand());
        }
        return expr.toString();
    }

    private static String symbolOf(BinaryOp op) {
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

    private static String symbolOf(UnaryOp op) {
        return switch (op) {
            case NOT -> "!";
            case NEG -> "-";
        };
    }

    static String typeName(JsonTValue v) {
        return v.getClass().getSimpleName();
    }

    public static List<String> collectFieldRefs(JsonTExpression expr) {
        List<String> refs = new ArrayList<>();
        collectFieldRefsInto(expr, refs);
        return refs;
    }

    private static void collectFieldRefsInto(JsonTExpression expr, List<String> refs) {
        if (expr instanceof JsonTExpression.Literal) {
            // no refs
        } else if (expr instanceof JsonTExpression.FieldRef ref) {
            refs.add(ref.path().leaf());
        } else if (expr instanceof JsonTExpression.Binary bin) {
            collectFieldRefsInto(bin.lhs(), refs);
            collectFieldRefsInto(bin.rhs(), refs);
        } else if (expr instanceof JsonTExpression.Unary un) {
            collectFieldRefsInto(un.operand(), refs);
        }
    }
}
