package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.parse.RowScanner;
import io.github.datakore.jsont.internal.parse.SchemaVisitor;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

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

    /** @throws JsonTError.Parse on grammar or semantic error */
    public static JsonTNamespace parseNamespace(String input) {
        return SchemaVisitor.parseNamespace(input);
    }

    /**
     * Parses all data rows from a string. Numbers are emitted as {@code D64} (no schema context).
     *
     * @throws JsonTError.Parse on malformed input
     */
    public static int parseRows(String input, RowConsumer consumer) {
        return RowScanner.parseRows(input, consumer);
    }

    /**
     * Streams rows from a {@link Reader} with O(1) memory. The reader is not closed.
     *
     * @throws IOException      on read failure
     * @throws JsonTError.Parse on malformed row data
     */
    public static int parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException {
        return RowScanner.parseRowsStreaming(reader, consumer);
    }

    /**
     * Returns a lazy {@link RowIter} over rows from {@code reader}.
     * Caller must close the iterator (closes the reader with it).
     */
    public static RowIter rowIter(Reader reader) {
        return new RowIter(reader);
    }

    /**
     * Parses a full document containing a {@code namespace} block followed by data rows.
     * The namespace block is located by brace-depth scanning, then each part is parsed
     * independently.
     *
     * @throws JsonTError.Parse if either part is malformed
     */
    public static ParsedDocument parseDocument(String input) {
        int split = findNamespaceEnd(input);
        JsonTNamespace ns = parseNamespace(input.substring(0, split));
        List<JsonTRow> rows = new ArrayList<>();
        int count = parseRows(input.substring(split), rows::add);
        return new ParsedDocument(ns, rows, count);
    }

    /** Result of {@link #parseDocument(String)}. */
    public record ParsedDocument(JsonTNamespace namespace, List<JsonTRow> rows, int rowCount) {}

    // Scans for the closing `}` that ends the outermost namespace block.
    private static int findNamespaceEnd(String input) {
        int depth = 0;
        boolean inString = false;
        boolean escaped  = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped)          { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true;  continue; }
            if (c == '"')         { inString = !inString; continue; }
            if (!inString) {
                if      (c == '{') depth++;
                else if (c == '}') {
                    if (--depth == 0) return i + 1;
                }
            }
        }
        return input.length();
    }
}
