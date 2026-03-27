package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.internal.parse.RowScanner;
import io.github.datakore.jsont.model.JsonTRow;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Lazy iterator over {@link JsonTRow}s from a {@link Reader} source.
 *
 * <p>Only one row's worth of data is live at a time — memory is O(1) relative
 * to the total input size.
 *
 * <pre>{@code
 *   try (RowIter iter = JsonTParser.rowIter(new FileReader("data.jsont"))) {
 *       while (iter.hasNext()) {
 *           process(iter.next());
 *       }
 *   }
 * }</pre>
 */
public final class RowIter implements Iterator<JsonTRow>, AutoCloseable {

    private final RowScanner.RowExtractor extractor;
    private final Reader reader;
    private JsonTRow pending;
    private boolean done;

    RowIter(Reader reader) {
        this.reader = reader;
        this.extractor = new RowScanner.RowExtractor(reader);
        this.pending = null;
        this.done = false;
    }

    @Override
    public boolean hasNext() {
        if (done) return false;
        if (pending != null) return true;
        try {
            pending = extractor.nextRow();
            if (pending == null) {
                done = true;
                return false;
            }
            return true;
        } catch (IOException e) {
            done = true;
            return false;
        }
    }

    @Override
    public JsonTRow next() {
        if (!hasNext()) throw new NoSuchElementException();
        JsonTRow row = pending;
        pending = null;
        return row;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
