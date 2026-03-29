package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.model.JsonTRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class RowWriterTest {

    // ── writeRow ──────────────────────────────────────────────────────────────

    @Test void writeRow_simple() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(i32(1), text("abc"), bool(true)), sw);
        assertEquals("{1,\"abc\",true}", sw.toString());
    }

    @Test void writeRow_nullValue() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(nullValue(), i32(42)), sw);
        assertEquals("{null,42}", sw.toString());
    }

    @Test void writeRow_allNumericTypes() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(
                i16((short) 1), i32(2), i64(3L), u16(4), u32(5L), d64(6.0)
        ), sw);
        assertEquals("{1,2,3,4,5,6.0}", sw.toString());
    }

    @Test void writeRow_textWithEscapes() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(text("say \"hi\""), text("back\\slash")), sw);
        assertEquals("{\"say \\\"hi\\\"\",\"back\\\\slash\"}", sw.toString());
    }

    @Test void writeRow_emptyRow() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(), sw);
        assertEquals("{}", sw.toString());
    }

    @Test void writeRow_withArray() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(JsonTRow.of(
                i32(1), array(List.of(i32(10), i32(20)))
        ), sw);
        assertEquals("{1,[10, 20]}", sw.toString());
    }

    @Test void writeRow_matchesStringifier() throws IOException {
        JsonTRow row = JsonTRow.of(i64(99L), text("x"), bool(false), nullValue());
        StringWriter sw = new StringWriter();
        RowWriter.writeRow(row, sw);
        assertEquals(JsonTStringifier.stringify(row), sw.toString());
    }

    // ── writeRows (Iterable) ──────────────────────────────────────────────────

    @Test void writeRows_twoRows_separatedByCommaNewline() throws IOException {
        StringWriter sw = new StringWriter();
        List<JsonTRow> rows = List.of(
                JsonTRow.of(i32(1), text("a")),
                JsonTRow.of(i32(2), text("b"))
        );
        RowWriter.writeRows(rows, sw);
        assertEquals("{1,\"a\"},\n{2,\"b\"}", sw.toString());
    }

    @Test void writeRows_singleRow_noTrailingNewline() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRows(List.of(JsonTRow.of(i32(1))), sw);
        assertEquals("{1}", sw.toString());
        assertFalse(sw.toString().endsWith("\n"));
    }

    @Test void writeRows_emptyList_writesNothing() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRows(List.of(), sw);
        assertEquals("", sw.toString());
    }

    @Test void writeRows_threeRows() throws IOException {
        StringWriter sw = new StringWriter();
        RowWriter.writeRows(List.of(
                JsonTRow.of(i32(1)),
                JsonTRow.of(i32(2)),
                JsonTRow.of(i32(3))
        ), sw);
        assertEquals("{1},\n{2},\n{3}", sw.toString());
    }

    // ── writeRows (array) ──────────────────────────────────────────────────────

    @Test void writeRows_array_twoRows() throws IOException {
        StringWriter sw = new StringWriter();
        JsonTRow[] rows = {JsonTRow.of(i32(10)), JsonTRow.of(i32(20))};
        RowWriter.writeRows(rows, sw);
        assertEquals("{10},\n{20}", sw.toString());
    }

    // ── BufferedWriter (FileWriter) ───────────────────────────────────────────

    @Test void writeRows_bufferedWriterBackedByFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("rows.jsont");
        List<JsonTRow> rows = List.of(
                JsonTRow.of(i64(1L), text("Alice"), d64(100.0)),
                JsonTRow.of(i64(2L), text("Bob"),   d64(200.0)),
                JsonTRow.of(i64(3L), text("Carol"),  d64(300.0))
        );

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.toFile()))) {
            RowWriter.writeRows(rows, bw);
        }

        String content = new String(java.nio.file.Files.readAllBytes(file));
        assertEquals("{1,\"Alice\",100.0},\n{2,\"Bob\",200.0},\n{3,\"Carol\",300.0}", content);
    }

    @Test void writeRow_bufferedWriterBackedByFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("row.jsont");
        JsonTRow row = JsonTRow.of(i32(42), text("test"));

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.toFile()))) {
            RowWriter.writeRow(row, bw);
        }

        String content = new String(java.nio.file.Files.readAllBytes(file));
        assertEquals("{42,\"test\"}", content);
    }

    // ── Large dataset throughput ───────────────────────────────────────────────

    @Test void writeRows_largeDataset_correctOutput(@TempDir Path tmp) throws IOException {
        int count = 10_000;
        List<JsonTRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(JsonTRow.of(i64((long) i), text("item" + i), d64(i * 1.5)));
        }

        Path file = tmp.resolve("large.jsont");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.toFile()))) {
            RowWriter.writeRows(rows, bw);
        }

        // verify first and last rows
        String content = new String(java.nio.file.Files.readAllBytes(file));
        assertTrue(content.startsWith("{0,\"item0\",0.0}"));
        // last row: index count-1
        assertTrue(content.endsWith("{" + (count-1) + ",\"item" + (count-1) + "\"," + ((count-1) * 1.5) + "}"));
        // correct number of separators
        long separators = content.chars().filter(c -> c == '\n').count();
        assertEquals(count - 1, separators);
    }
}
