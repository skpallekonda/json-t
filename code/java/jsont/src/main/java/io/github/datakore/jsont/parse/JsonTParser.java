package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.parse.RowScanner;
import io.github.datakore.jsont.internal.parse.SchemaVisitor;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;

import java.io.IOException;
import java.io.Reader;

/**
 * Entry point for all JsonT parsing operations.
 *
 * <h2>Schema DSL parsing</h2>
 * <pre>{@code
 *   String dsl = """
 *       namespace "https://example.com" {
 *         catalog {
 *           schema Order {
 *             fields { id: i64, name: str }
 *           }
 *         }
 *       }
 *       """;
 *   JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
 * }</pre>
 *
 * <h2>Data row parsing</h2>
 * <pre>{@code
 *   List<JsonTRow> rows = new ArrayList<>();
 *   JsonTParser.parseRows("{1,\"Alice\",true},{2,\"Bob\",false}", rows::add);
 *
 *   try (RowIter iter = JsonTParser.rowIter(new FileReader("data.jsont"))) {
 *       iter.forEachRemaining(pipeline::process);
 *   }
 * }</pre>
 */
public final class JsonTParser {

    private JsonTParser() {}

    /**
     * Parses a namespace schema DSL document.
     *
     * @param input the full namespace DSL text
     * @return the parsed namespace model
     * @throws JsonTError.Parse on any grammar or semantic error
     */
    public static JsonTNamespace parseNamespace(String input) {
        return SchemaVisitor.parseNamespace(input);
    }

    /**
     * Parses all data rows from an in-memory string.
     *
     * <p>Each {@code {v1, v2, ...}} block calls {@code consumer} once.
     * Numbers are always emitted as {@code D64} (no schema context).
     *
     * @param input    rows text (e.g. {@code "{1,\"a\"},{2,\"b\"}"})
     * @param consumer called once per completed row
     * @return number of rows parsed
     * @throws JsonTError.Parse on malformed input
     */
    public static int parseRows(String input, RowConsumer consumer) {
        return RowScanner.parseRows(input, consumer);
    }

    /**
     * Parses data rows from a {@link Reader} with O(1) memory overhead.
     *
     * <p>The reader is NOT closed by this method.
     *
     * @param reader   source of row data (e.g. {@code BufferedReader(new FileReader(...))})
     * @param consumer called once per completed row
     * @return number of rows parsed
     * @throws IOException      on I/O read failure
     * @throws JsonTError.Parse on malformed row data
     */
    public static int parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException {
        return RowScanner.parseRowsStreaming(reader, consumer);
    }

    /**
     * Returns a lazy {@link RowIter} over rows from {@code reader}.
     *
     * <p>The caller is responsible for closing the iterator (which also closes the reader).
     *
     * @param reader source of row data
     * @return a lazy row iterator
     */
    public static RowIter rowIter(Reader reader) {
        return new RowIter(reader);
    }
}
