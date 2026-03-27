package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress-tests the streaming row parser with tiny read buffers.
 * Chunk-boundary bugs only surface when a row brace, separator, or escape
 * sequence falls across a read boundary — these tests force that.
 */
class StreamingParserTest {

    // ── Helper: throttled reader ──────────────────────────────────────────────

    /** Wraps a Reader and returns at most {@code chunkSize} chars per read call. */
    private static Reader chunked(String input, int chunkSize) {
        return new Reader() {
            private final StringReader inner = new StringReader(input);
            @Override public int read(char[] buf, int off, int len) throws IOException {
                return inner.read(buf, off, Math.min(len, chunkSize));
            }
            @Override public void close() throws IOException { inner.close(); }
        };
    }

    private static List<JsonTRow> collect(String input, int chunkSize) throws IOException {
        List<JsonTRow> rows = new ArrayList<>();
        JsonTParser.parseRowsStreaming(chunked(input, chunkSize), rows::add);
        return rows;
    }

    // ── Single-byte chunk boundary ────────────────────────────────────────────

    @Test void chunkSize1_singleRow() throws IOException {
        List<JsonTRow> rows = collect("{1,\"hello\",true}", 1);
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).size(), 3);
        assertEquals(text("hello"), rows.get(0).get(1));
    }

    @Test void chunkSize1_multipleRows() throws IOException {
        List<JsonTRow> rows = collect("{1,\"a\"},{2,\"b\"},{3,\"c\"}", 1);
        assertEquals(3, rows.size());
        assertEquals(d64(1.0), rows.get(0).get(0));
        assertEquals(d64(3.0), rows.get(2).get(0));
    }

    @Test void chunkSize1_nullAndBool() throws IOException {
        List<JsonTRow> rows = collect("{null,true,false}", 1);
        assertEquals(1, rows.size());
        assertEquals(nullValue(), rows.get(0).get(0));
        assertEquals(bool(true),  rows.get(0).get(1));
        assertEquals(bool(false), rows.get(0).get(2));
    }

    @Test void chunkSize1_enumValue() throws IOException {
        List<JsonTRow> rows = collect("{ACTIVE,INACTIVE}", 1);
        assertEquals(enumValue("ACTIVE"),   rows.get(0).get(0));
        assertEquals(enumValue("INACTIVE"), rows.get(0).get(1));
    }

    @Test void chunkSize1_unspecified() throws IOException {
        List<JsonTRow> rows = collect("{_,1}", 1);
        assertEquals(unspecified(), rows.get(0).get(0));
    }

    // ── Two-byte chunk boundary ────────────────────────────────────────────────

    @Test void chunkSize2_multipleRows() throws IOException {
        List<JsonTRow> rows = collect("{10},{20},{30}", 2);
        assertEquals(3, rows.size());
        assertEquals(d64(10.0), rows.get(0).get(0));
        assertEquals(d64(30.0), rows.get(2).get(0));
    }

    @Test void chunkSize2_stringWithEscape() throws IOException {
        // Escape sequence \" split across a 2-byte chunk boundary
        List<JsonTRow> rows = collect("{\"say \\\"hi\\\"\"}", 2);
        assertEquals(1, rows.size());
        assertEquals(text("say \\\"hi\\\""), rows.get(0).get(0));
    }

    @Test void chunkSize2_nestedBraces() throws IOException {
        // Nested {inner} split across chunks
        List<JsonTRow> rows = collect("{1,{2,3}}", 2);
        assertEquals(1, rows.size());
        assertInstanceOf(JsonTValue.Array.class, rows.get(0).get(1));
    }

    // ── Three-byte chunk boundary ─────────────────────────────────────────────

    @Test void chunkSize3_multipleRows_countMatches() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i > 0) sb.append(',');
            sb.append("{").append(i).append(",\"item").append(i).append("\"}");
        }
        List<JsonTRow> rows = collect(sb.toString(), 3);
        assertEquals(20, rows.size());
        assertEquals(d64(0.0),  rows.get(0).get(0));
        assertEquals(d64(19.0), rows.get(19).get(0));
    }

    // ── Enum identifier at a chunk boundary ───────────────────────────────────

    @Test void chunkSize3_enumAtBoundary() throws IOException {
        // "ACTIVE" is 6 chars — with chunkSize=3 the identifier is split "ACT"/"IVE"
        List<JsonTRow> rows = collect("{ACTIVE}", 3);
        assertEquals(enumValue("ACTIVE"), rows.get(0).get(0));
    }

    // ── Empty and whitespace inputs ───────────────────────────────────────────

    @Test void chunkSize1_emptyInput_returnsZero() throws IOException {
        assertEquals(0, collect("", 1).size());
    }

    @Test void chunkSize1_whitespaceOnly_returnsZero() throws IOException {
        assertEquals(0, collect("   \n  ", 1).size());
    }

    // ── Large payload ─────────────────────────────────────────────────────────

    @Test void chunkSize7_largePayload_allRowsReceived() throws IOException {
        int n = 100;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append("{").append(i).append(",\"name").append(i).append("\",").append(i * 1.5).append("}");
        }
        List<JsonTRow> rows = collect(sb.toString(), 7);
        assertEquals(n, rows.size());
        assertEquals(d64(0.0),           rows.get(0).get(0));
        assertEquals(d64((double)(n-1)), rows.get(n-1).get(0));
    }
}
