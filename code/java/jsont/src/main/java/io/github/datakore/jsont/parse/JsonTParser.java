package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.parse.RowScanner;
import io.github.datakore.jsont.internal.parse.SchemaVisitor;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Entry point for parsing JsonT. You can parse namespaces or 
 * scan data rows from strings and readers easily.
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
     * Returns a sequential {@link Stream} of rows parsed lazily from {@code reader}.
     * Callers should close the stream (try-with-resources) to release the underlying reader.
     */
    public static Stream<JsonTRow> parseRowsStreaming(Reader reader) {
        RowIter iter = new RowIter(reader);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED | Spliterator.NONNULL),
                false
        ).onClose(() -> {
            try { iter.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
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
