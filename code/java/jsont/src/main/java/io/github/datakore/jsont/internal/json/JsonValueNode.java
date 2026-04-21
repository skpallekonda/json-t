package io.github.datakore.jsont.internal.json;

import java.util.List;
import java.util.Map;

/**
 * Internal JSON value representation used by {@link JsonValueParser}.
 *
 * <p>Not part of the public API — lives in {@code internal.json} which is
 * intentionally not exported from the module.
 */
public sealed interface JsonValueNode
        permits JsonValueNode.Null,
                JsonValueNode.Bool,
                JsonValueNode.IntNum,
                JsonValueNode.FloatNum,
                JsonValueNode.Str,
                JsonValueNode.Arr,
                JsonValueNode.Obj {

    /** JSON {@code null}. */
    record Null() implements JsonValueNode {}

    /** JSON {@code true} or {@code false}. */
    record Bool(boolean value) implements JsonValueNode {}

    /**
     * A JSON integer that fits in a {@code long} without precision loss
     * (no decimal point or exponent in the source text).
     */
    record IntNum(long value) implements JsonValueNode {}

    /**
     * A JSON number that required floating-point representation
     * (has a decimal point or exponent).
     */
    record FloatNum(double value) implements JsonValueNode {}

    /** A JSON string (escape sequences already decoded). */
    record Str(String value) implements JsonValueNode {}

    /** A JSON array. */
    record Arr(List<JsonValueNode> items) implements JsonValueNode {}

    /**
     * A JSON object. Pairs are in document order; duplicate keys are
     * preserved (last one wins during field lookup).
     */
    record Obj(List<Map.Entry<String, JsonValueNode>> pairs) implements JsonValueNode {}
}
