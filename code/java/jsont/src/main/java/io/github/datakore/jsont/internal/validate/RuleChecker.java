package io.github.datakore.jsont.internal.validate;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticEventKind;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.EvalContext;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTRule;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.UnaryOp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleChecker {

    public static List<DiagnosticEvent> checkRules(
            List<JsonTField> fields,
            JsonTValidationBlock validation,
            List<JsonTValue> rowValues,
            int rowIndex) {

        List<DiagnosticEvent> events = new ArrayList<>();

        // Pre-compute the union of field names referenced by all rules.
        // Building a minimal context (typically 2-4 fields) avoids binding all
        // schema fields (e.g. 92) into the map on every row.
        Set<String> needed = new HashSet<>();
        for (JsonTRule rule : validation.rules()) {
            if (rule instanceof JsonTRule.Expression e) {
                needed.addAll(collectFieldRefs(e.expr()));
            } else if (rule instanceof JsonTRule.ConditionalRequirement cr) {
                needed.addAll(collectFieldRefs(cr.condition()));
                // required_fields are looked up via ctx so they must be bound too.
                for (FieldPath fp : cr.requiredFields()) needed.add(fp.leaf());
            }
        }
        EvalContext ctx = EvalContext.create();
        for (int i = 0; i < fields.size() && i < rowValues.size(); i++) {
            String name = fields.get(i).name();
            if (needed.contains(name)) ctx.bind(name, rowValues.get(i));
        }

        // Evaluate each rule — dispatch on JsonTRule variant
        for (JsonTRule rule : validation.rules()) {
            if (rule instanceof JsonTRule.Expression e) {
                try {
                    JsonTValue result = e.expr().evaluate(ctx);
                    if (result instanceof JsonTValue.Bool b) {
                        if (!b.value()) {
                            events.add(DiagnosticEvent.warning(
                                    new DiagnosticEventKind.RuleViolation(
                                            stringifyExpr(e.expr()),
                                            "expression evaluated to false"))
                                    .atRow(rowIndex));
                        }
                        // true = OK, no event
                    } else {
                        events.add(DiagnosticEvent.warning(
                                new DiagnosticEventKind.RuleViolation(
                                        stringifyExpr(e.expr()),
                                        "non-boolean result: " + typeName(result)))
                                .atRow(rowIndex));
                    }
                } catch (JsonTError.Eval err) {
                    events.add(DiagnosticEvent.warning(
                            new DiagnosticEventKind.RuleViolation(
                                    stringifyExpr(e.expr()),
                                    "evaluation error: " + err.getMessage()))
                            .atRow(rowIndex));
                }
            } else if (rule instanceof JsonTRule.ConditionalRequirement cr) {
                // Evaluate the condition; if true, all requiredFields must be non-null/non-unspecified
                try {
                    JsonTValue condResult = cr.condition().evaluate(ctx);
                    if (condResult instanceof JsonTValue.Bool b && b.value()) {
                        List<String> missing = new ArrayList<>();
                        for (FieldPath fp : cr.requiredFields()) {
                            String name = fp.leaf();
                            JsonTValue val = ctx.lookup(name).orElse(null);
                            if (val == null || val instanceof JsonTValue.Null
                                    || val instanceof JsonTValue.Unspecified) {
                                missing.add(name);
                            }
                        }
                        if (!missing.isEmpty()) {
                            events.add(DiagnosticEvent.fatal(
                                    new DiagnosticEventKind.ConditionalRequirementViolation(
                                            stringifyExpr(cr.condition()), missing))
                                    .atRow(rowIndex));
                        }
                    }
                    // condition false or non-bool → no violation
                } catch (JsonTError.Eval err) {
                    events.add(DiagnosticEvent.warning(
                            new DiagnosticEventKind.RuleViolation(
                                    stringifyExpr(cr.condition()),
                                    "condition evaluation error: " + err.getMessage()))
                            .atRow(rowIndex));
                }
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
