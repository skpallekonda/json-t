package io.github.datakore.jsont.diagnostic;

import io.github.datakore.jsont.internal.diagnostic.ConsoleSink;
import io.github.datakore.jsont.internal.diagnostic.FileSink;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticEventTest {

    // ─── DiagnosticSeverity ────────────────────────────────────────────────────

    @Test
    void severity_fatal() {
        assertTrue(DiagnosticSeverity.FATAL.isFatal());
        assertFalse(DiagnosticSeverity.FATAL.isWarning());
        assertFalse(DiagnosticSeverity.FATAL.isInfo());
    }

    @Test
    void severity_warning() {
        assertFalse(DiagnosticSeverity.WARNING.isFatal());
        assertTrue(DiagnosticSeverity.WARNING.isWarning());
        assertFalse(DiagnosticSeverity.WARNING.isInfo());
    }

    @Test
    void severity_info() {
        assertFalse(DiagnosticSeverity.INFO.isFatal());
        assertFalse(DiagnosticSeverity.INFO.isWarning());
        assertTrue(DiagnosticSeverity.INFO.isInfo());
    }

    // ─── DiagnosticEvent factories ─────────────────────────────────────────────

    @Test
    void fatal_factory() {
        DiagnosticEvent e = DiagnosticEvent.fatal(new DiagnosticEventKind.Notice("x"));
        assertEquals(DiagnosticSeverity.FATAL, e.severity());
        assertTrue(e.isFatal());
    }

    @Test
    void warning_factory() {
        DiagnosticEvent e = DiagnosticEvent.warning(new DiagnosticEventKind.Notice("x"));
        assertEquals(DiagnosticSeverity.WARNING, e.severity());
        assertFalse(e.isFatal());
    }

    @Test
    void info_factory() {
        DiagnosticEvent e = DiagnosticEvent.info(new DiagnosticEventKind.Notice("x"));
        assertEquals(DiagnosticSeverity.INFO, e.severity());
        assertFalse(e.isFatal());
    }

    // ─── Wither methods ────────────────────────────────────────────────────────

    @Test
    void atRow_setsRowIndex() {
        DiagnosticEvent e = DiagnosticEvent.fatal(new DiagnosticEventKind.Notice("x")).atRow(5);
        assertTrue(e.rowIndex().isPresent());
        assertEquals(5, e.rowIndex().get());
    }

    @Test
    void withSource_setsSource() {
        DiagnosticEvent e = DiagnosticEvent.info(new DiagnosticEventKind.Notice("x")).withSource("Order");
        assertTrue(e.source().isPresent());
        assertEquals("Order", e.source().get());
    }

    @Test
    void noRowIndex_returnsEmpty() {
        DiagnosticEvent e = DiagnosticEvent.info(new DiagnosticEventKind.Notice("x"));
        assertTrue(e.rowIndex().isEmpty());
    }

    // ─── toString format ───────────────────────────────────────────────────────

    @Test
    void toString_requiredFieldMissing() {
        DiagnosticEvent e = DiagnosticEvent.fatal(
                new DiagnosticEventKind.RequiredFieldMissing("id"))
                .atRow(5).withSource("Order");
        String s = e.toString();
        assertTrue(s.contains("[FATAL]"), s);
        assertTrue(s.contains("[row 5]"), s);
        assertTrue(s.contains("(Order)"), s);
        assertTrue(s.contains("RequiredFieldMissing: 'id'"), s);
    }

    @Test
    void toString_typeMismatch() {
        DiagnosticEvent e = DiagnosticEvent.warning(
                new DiagnosticEventKind.TypeMismatch("age", "I32", "Text"));
        String s = e.toString();
        assertTrue(s.contains("TypeMismatch on 'age': expected I32, got Text"), s);
    }

    @Test
    void toString_shapeMismatch() {
        DiagnosticEvent e = DiagnosticEvent.fatal(
                new DiagnosticEventKind.ShapeMismatch("(row)", "3 field(s)", "2 field(s)"));
        String s = e.toString();
        assertTrue(s.contains("ShapeMismatch on '(row)': expected 3 field(s), got 2 field(s)"), s);
    }

    @Test
    void toString_constraintViolation() {
        DiagnosticEvent e = DiagnosticEvent.warning(
                new DiagnosticEventKind.ConstraintViolation("qty", "minValue = 1.0", "value 0.0 violates minValue = 1.0"));
        String s = e.toString();
        assertTrue(s.contains("ConstraintViolation on 'qty' [minValue = 1.0]: value 0.0 violates minValue = 1.0"), s);
    }

    @Test
    void toString_ruleViolation() {
        DiagnosticEvent e = DiagnosticEvent.warning(
                new DiagnosticEventKind.RuleViolation("(price > 0.0)", "expression evaluated to false"));
        String s = e.toString();
        assertTrue(s.contains("RuleViolation [(price > 0.0)]: expression evaluated to false"), s);
    }

    @Test
    void toString_conditionalRequirementViolation() {
        DiagnosticEvent e = DiagnosticEvent.warning(
                new DiagnosticEventKind.ConditionalRequirementViolation("hasTax", List.of("taxRate")));
        String s = e.toString();
        assertTrue(s.contains("ConditionalRequirementViolation when 'hasTax': missing [taxRate]"), s);
    }

    @Test
    void toString_uniqueViolation() {
        DiagnosticEvent e = DiagnosticEvent.fatal(
                new DiagnosticEventKind.UniqueViolation(List.of("id"), 3));
        String s = e.toString();
        assertTrue(s.contains("UniqueViolation at row 3: fields [id]"), s);
    }

    @Test
    void toString_processCompleted() {
        DiagnosticEvent e = DiagnosticEvent.info(
                new DiagnosticEventKind.ProcessCompleted(10, 8, 1, 1, 42L));
        String s = e.toString();
        assertTrue(s.contains("ProcessCompleted: total=10 valid=8 warnings=1 invalid=1 duration=42ms"), s);
    }

    @Test
    void toString_rowRejected() {
        DiagnosticEvent e = DiagnosticEvent.fatal(
                new DiagnosticEventKind.RowRejected(2, 1));
        String s = e.toString();
        assertTrue(s.contains("RowRejected: row 2 (1 fatal issue(s))"), s);
    }

    @Test
    void toString_rowAcceptedWithWarnings() {
        DiagnosticEvent e = DiagnosticEvent.warning(
                new DiagnosticEventKind.RowAcceptedWithWarnings(3, 2));
        String s = e.toString();
        assertTrue(s.contains("RowAcceptedWithWarnings: row 3 (2 warning(s))"), s);
    }

    @Test
    void toString_notice() {
        DiagnosticEvent e = DiagnosticEvent.info(new DiagnosticEventKind.Notice("hello world"));
        assertTrue(e.toString().contains("Notice: hello world"));
    }

    // ─── MemorySink ────────────────────────────────────────────────────────────

    @Test
    void memorySink_accumulates() {
        MemorySink sink = new MemorySink();
        sink.emit(DiagnosticEvent.fatal(new DiagnosticEventKind.RequiredFieldMissing("id")));
        sink.emit(DiagnosticEvent.warning(new DiagnosticEventKind.Notice("warn")));
        sink.emit(DiagnosticEvent.info(new DiagnosticEventKind.Notice("info")));
        assertEquals(3, sink.size());
        assertEquals(3, sink.events().size());
    }

    @Test
    void memorySink_fatalEvents() {
        MemorySink sink = new MemorySink();
        sink.emit(DiagnosticEvent.fatal(new DiagnosticEventKind.RequiredFieldMissing("id")));
        sink.emit(DiagnosticEvent.warning(new DiagnosticEventKind.Notice("warn")));
        assertEquals(1, sink.fatalEvents().size());
    }

    @Test
    void memorySink_warningEvents() {
        MemorySink sink = new MemorySink();
        sink.emit(DiagnosticEvent.fatal(new DiagnosticEventKind.RequiredFieldMissing("id")));
        sink.emit(DiagnosticEvent.warning(new DiagnosticEventKind.Notice("warn")));
        assertEquals(1, sink.warningEvents().size());
    }

    @Test
    void memorySink_clear() {
        MemorySink sink = new MemorySink();
        sink.emit(DiagnosticEvent.info(new DiagnosticEventKind.Notice("x")));
        sink.clear();
        assertEquals(0, sink.size());
    }

    // ─── ConsoleSink ──────────────────────────────────────────────────────────

    @Test
    void consoleSink_emitsWithoutThrowing() {
        ConsoleSink sink = new ConsoleSink();
        assertDoesNotThrow(() -> sink.emit(DiagnosticEvent.info(new DiagnosticEventKind.Notice("test"))));
    }

    // ─── DiagnosticSink.fanOut ─────────────────────────────────────────────────

    @Test
    void fanOut_emitsToAllSinks() {
        MemorySink a = new MemorySink();
        MemorySink b = new MemorySink();
        MemorySink c = new MemorySink();
        DiagnosticSink fan = DiagnosticSink.fanOut(a, b, c);

        fan.emit(DiagnosticEvent.fatal(new DiagnosticEventKind.RequiredFieldMissing("id")));
        fan.emit(DiagnosticEvent.warning(new DiagnosticEventKind.Notice("warn")));
        fan.emit(DiagnosticEvent.info(new DiagnosticEventKind.Notice("info")));

        assertEquals(3, a.size());
        assertEquals(3, b.size());
        assertEquals(3, c.size());
    }

    @Test
    void fanOut_severityFiltering_perSink() {
        MemorySink fatals   = new MemorySink();
        MemorySink warnings = new MemorySink();
        DiagnosticSink fan = DiagnosticSink.fanOut(fatals, warnings);

        fan.emit(DiagnosticEvent.fatal(new DiagnosticEventKind.RequiredFieldMissing("x")));
        fan.emit(DiagnosticEvent.warning(new DiagnosticEventKind.Notice("w")));

        // both sinks received all events — filtering is the caller's responsibility
        assertEquals(1, fatals.fatalEvents().size());
        assertEquals(1, warnings.warningEvents().size());
        assertEquals(2, fatals.size());
        assertEquals(2, warnings.size());
    }

    @Test
    void fanOut_singleSink_behavesLikeDirect() {
        MemorySink direct = new MemorySink();
        MemorySink via    = new MemorySink();
        DiagnosticSink fan = DiagnosticSink.fanOut(via);

        DiagnosticEvent e = DiagnosticEvent.fatal(new DiagnosticEventKind.Notice("x"));
        direct.emit(e);
        fan.emit(e);

        assertEquals(direct.size(), via.size());
        assertEquals(direct.events().get(0), via.events().get(0));
    }

    @Test
    void fanOut_emptySinks_doesNotThrow() {
        DiagnosticSink fan = DiagnosticSink.fanOut();
        assertDoesNotThrow(() -> fan.emit(DiagnosticEvent.info(new DiagnosticEventKind.Notice("x"))));
    }

    // ─── FileSink ─────────────────────────────────────────────────────────────

    @Test
    void fileSink_writesToWriter() throws Exception {
        StringWriter sw = new StringWriter();
        FileSink sink = new FileSink(sw);
        DiagnosticEvent e = DiagnosticEvent.fatal(
                new DiagnosticEventKind.RequiredFieldMissing("id")).atRow(0);
        sink.emit(e);
        sink.flush();
        String output = sw.toString();
        assertTrue(output.contains("RequiredFieldMissing"), output);
        assertTrue(output.endsWith("\n"), "should end with newline");
    }
}
