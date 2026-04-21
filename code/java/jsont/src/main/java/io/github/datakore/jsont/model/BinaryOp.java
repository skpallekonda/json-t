package io.github.datakore.jsont.model;

/**
 * Binary operators available in JsonT expressions.
 *
 * <ul>
 *   <li><b>Comparison</b> ({@code EQ, NE, LT, LE, GT, GE}) — work on numerics and text.</li>
 *   <li><b>Logical</b> ({@code AND, OR}) — require {@code Bool} operands, short-circuit.</li>
 *   <li><b>Arithmetic</b> ({@code ADD, SUB, MUL, DIV}) — require numeric operands.</li>
 * </ul>
 */
public enum BinaryOp {

    /** Structural or numeric equality. */
    EQ,
    /** Structural or numeric inequality. */
    NE,
    /** Less-than (numeric). */
    LT,
    /** Less-than-or-equal (numeric). */
    LE,
    /** Greater-than (numeric). */
    GT,
    /** Greater-than-or-equal (numeric). */
    GE,
    /** Logical AND — both operands must be {@code Bool}. */
    AND,
    /** Logical OR — both operands must be {@code Bool}. */
    OR,
    /** Numeric addition. */
    ADD,
    /** Numeric subtraction. */
    SUB,
    /** Numeric multiplication. */
    MUL,
    /** Numeric division (throws {@code JsonTError.Eval} on divide-by-zero). */
    DIV;

    /** Returns {@code true} if this operator produces a {@code Bool} result. */
    public boolean isLogical() {
        return switch (this) {
            case EQ, NE, LT, LE, GT, GE, AND, OR -> true;
            default -> false;
        };
    }

    /** Returns {@code true} if both operands must be numeric and the result is numeric. */
    public boolean isArithmetic() {
        return switch (this) {
            case ADD, SUB, MUL, DIV -> true;
            default -> false;
        };
    }
}
