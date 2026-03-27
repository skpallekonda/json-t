package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.model.JsonTRow;

/**
 * Callback receiving each parsed data row.
 *
 * <pre>{@code
 *   List<JsonTRow> rows = new ArrayList<>();
 *   JsonTParser.parseRows("{1,\"a\"},{2,\"b\"}", rows::add);
 * }</pre>
 */
@FunctionalInterface
public interface RowConsumer {
    /** Called once per successfully parsed row. */
    void accept(JsonTRow row);
}
