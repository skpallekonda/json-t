package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTValue;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class RowScannerTest {

    private static List<JsonTRow> collectRows(String input) {
        List<JsonTRow> rows = new ArrayList<>();
        JsonTParser.parseRows(input, rows::add);
        return rows;
    }

    // ── parseRows (in-memory) ─────────────────────────────────────────────────

    @Test void parseRows_empty_returnsZero() {
        assertEquals(0, JsonTParser.parseRows("", row -> {}));
    }

    @Test void parseRows_whitespaceOnly_returnsZero() {
        assertEquals(0, JsonTParser.parseRows("   \n  ", row -> {}));
    }

    @Test void parseRows_singleRow_integers() {
        List<JsonTRow> rows = collectRows("{1,2,3}");
        assertEquals(1, rows.size());
        JsonTRow r = rows.get(0);
        assertEquals(3, r.size());
        assertEquals(JsonTValue.d64(1.0), r.get(0));
        assertEquals(JsonTValue.d64(2.0), r.get(1));
        assertEquals(JsonTValue.d64(3.0), r.get(2));
    }

    @Test void parseRows_singleRow_string() {
        List<JsonTRow> rows = collectRows("{\"hello\"}");
        assertEquals(1, rows.size());
        assertEquals(JsonTValue.text("hello"), rows.get(0).get(0));
    }

    @Test void parseRows_singleRow_boolean() {
        List<JsonTRow> rows = collectRows("{true,false}");
        assertEquals(bool(true),  rows.get(0).get(0));
        assertEquals(bool(false), rows.get(0).get(1));
    }

    @Test void parseRows_singleRow_null() {
        List<JsonTRow> rows = collectRows("{null}");
        assertEquals(nullValue(), rows.get(0).get(0));
    }

    @Test void parseRows_nil_mapsToNull() {
        List<JsonTRow> rows = collectRows("{nil}");
        assertEquals(nullValue(), rows.get(0).get(0));
    }

    @Test void parseRows_unspecified_mapsToNull() {
        List<JsonTRow> rows = collectRows("{_}");
        assertEquals(nullValue(), rows.get(0).get(0));
    }

    @Test void parseRows_enumConstant_storedAsText() {
        List<JsonTRow> rows = collectRows("{ACTIVE}");
        assertEquals(text("ACTIVE"), rows.get(0).get(0));
    }

    @Test void parseRows_floatValue() {
        List<JsonTRow> rows = collectRows("{3.14}");
        assertEquals(d64(3.14), rows.get(0).get(0));
    }

    @Test void parseRows_twoRows_commaSeparated() {
        List<JsonTRow> rows = collectRows("{1,\"a\"},{2,\"b\"}");
        assertEquals(2, rows.size());
        assertEquals(d64(1.0), rows.get(0).get(0));
        assertEquals(d64(2.0), rows.get(1).get(0));
    }

    @Test void parseRows_trailingCommaAccepted() {
        List<JsonTRow> rows = collectRows("{1},{2},");
        assertEquals(2, rows.size());
    }

    @Test void parseRows_whitespaceInsideRow() {
        List<JsonTRow> rows = collectRows("{ 1 , \"hello\" , true }");
        assertEquals(3, rows.get(0).size());
    }

    @Test void parseRows_stringWithEscapes_storedRaw() {
        List<JsonTRow> rows = collectRows("{\"say \\\"hi\\\"\"}");
        // Raw — escape sequences not interpreted
        assertEquals(text("say \\\"hi\\\""), rows.get(0).get(0));
    }

    @Test void parseRows_arrayValue() {
        List<JsonTRow> rows = collectRows("{1,[10,20,30]}");
        JsonTValue v = rows.get(0).get(1);
        assertInstanceOf(JsonTValue.Array.class, v);
        assertEquals(3, ((JsonTValue.Array) v).elements().size());
    }

    @Test void parseRows_nestedObject_asArray() {
        List<JsonTRow> rows = collectRows("{1,{2,\"inner\"}}");
        JsonTValue v = rows.get(0).get(1);
        assertInstanceOf(JsonTValue.Array.class, v); // nested {} becomes Array
        List<JsonTValue> inner = ((JsonTValue.Array) v).elements();
        assertEquals(2, inner.size());
        assertEquals(d64(2.0), inner.get(0));
        assertEquals(text("inner"), inner.get(1));
    }

    @Test void parseRows_trailingCommaInsideRow() {
        List<JsonTRow> rows = collectRows("{1,2,3,}");
        assertEquals(3, rows.get(0).size());
    }

    @Test void parseRows_returns_count() {
        int count = JsonTParser.parseRows("{1},{2},{3}", row -> {});
        assertEquals(3, count);
    }

    @Test void parseRows_mixedTypes() {
        List<JsonTRow> rows = collectRows("{1,\"Alice\",true,null,ACTIVE}");
        JsonTRow r = rows.get(0);
        assertEquals(5, r.size());
        assertEquals(d64(1.0),    r.get(0));
        assertEquals(text("Alice"), r.get(1));
        assertEquals(bool(true),  r.get(2));
        assertEquals(nullValue(), r.get(3));
        assertEquals(text("ACTIVE"), r.get(4));
    }

    // ── parseRowsStreaming ────────────────────────────────────────────────────

    @Test void parseRowsStreaming_fromStringReader() throws IOException {
        List<JsonTRow> rows = new ArrayList<>();
        int count = JsonTParser.parseRowsStreaming(new StringReader("{1,\"a\"},{2,\"b\"}"), rows::add);
        assertEquals(2, count);
        assertEquals(2, rows.size());
        assertEquals(d64(1.0), rows.get(0).get(0));
        assertEquals(d64(2.0), rows.get(1).get(0));
    }

    @Test void parseRowsStreaming_fromBufferedReader() throws IOException {
        String input = "{1,\"Alice\",100.0},{2,\"Bob\",200.0},{3,\"Carol\",300.0}";
        List<JsonTRow> rows = new ArrayList<>();
        int count = JsonTParser.parseRowsStreaming(
                new BufferedReader(new StringReader(input)), rows::add);
        assertEquals(3, count);
        assertEquals(text("Alice"), rows.get(0).get(1));
        assertEquals(text("Carol"), rows.get(2).get(1));
    }

    @Test void parseRowsStreaming_fileBackedReader(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("rows.jsont");
        // write 500 rows
        try (var bw = new BufferedWriter(new FileWriter(file.toFile()))) {
            for (int i = 0; i < 500; i++) {
                if (i > 0) bw.write(",\n");
                bw.write("{" + i + ",\"item" + i + "\"," + (i * 1.5) + "}");
            }
        }

        List<JsonTRow> rows = new ArrayList<>();
        int count;
        try (var reader = new BufferedReader(new FileReader(file.toFile()))) {
            count = JsonTParser.parseRowsStreaming(reader, rows::add);
        }
        assertEquals(500, count);
        assertEquals(d64(0.0), rows.get(0).get(0));
        assertEquals(d64(499.0), rows.get(499).get(0));
    }

    // ── RowIter ───────────────────────────────────────────────────────────────

    @Test void rowIter_iteratesRows() throws IOException {
        List<JsonTRow> rows = new ArrayList<>();
        try (var iter = JsonTParser.rowIter(new StringReader("{1},{2},{3}"))) {
            iter.forEachRemaining(rows::add);
        }
        assertEquals(3, rows.size());
        assertEquals(d64(1.0), rows.get(0).get(0));
        assertEquals(d64(3.0), rows.get(2).get(0));
    }

    @Test void rowIter_emptySource_hasNoNext() throws IOException {
        try (var iter = JsonTParser.rowIter(new StringReader(""))) {
            assertFalse(iter.hasNext());
        }
    }

    @Test void rowIter_hasNext_idempotent() {
        var iter = JsonTParser.rowIter(new StringReader("{1}"));
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext()); // calling twice must be safe
        iter.next();
        assertFalse(iter.hasNext());
    }
}
