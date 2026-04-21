package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.model.JsonTExpression;

import java.util.ArrayList;
import java.util.List;

/** Extracts top-level field names referenced by an expression tree. */
public final class FieldRefCollector {

    private FieldRefCollector() {}

    public static List<String> collect(JsonTExpression expr) {
        List<String> refs = new ArrayList<>();
        collectInto(expr, refs);
        return refs;
    }

    private static void collectInto(JsonTExpression expr, List<String> refs) {
        if (expr instanceof JsonTExpression.FieldRef ref) {
            refs.add(ref.path().dotJoined());
        } else if (expr instanceof JsonTExpression.Binary bin) {
            collectInto(bin.lhs(), refs);
            collectInto(bin.rhs(), refs);
        } else if (expr instanceof JsonTExpression.Unary un) {
            collectInto(un.operand(), refs);
        }
        // Literal: no refs
    }
}
