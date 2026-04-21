package io.github.datakore.jsont.model;

/**
 * Unary operators available in JsonT expressions.
 */
public enum UnaryOp {

    /** Logical NOT — operand must be {@code Bool}, result is {@code Bool}. */
    NOT,

    /** Arithmetic negation — operand must be numeric, result is {@code D64}. */
    NEG;
}
