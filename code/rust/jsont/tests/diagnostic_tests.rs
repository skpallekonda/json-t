// =============================================================================
// tests/diagnostic_tests.rs — Step 1: DiagnosticEvent, Severity, Sinks
// =============================================================================

use jsont::{ConsoleSink, DiagnosticEvent, DiagnosticSink, EventKind, MemorySink, Severity};

// ─────────────────────────────────────────────────────────────────────────────
// Severity ordering
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_severity_ordering() {
    assert!(Severity::Info < Severity::Warning);
    assert!(Severity::Warning < Severity::Fatal);
    assert!(Severity::Info < Severity::Fatal);
}

#[test]
fn test_severity_display() {
    assert_eq!(Severity::Info.to_string(), "INFO");
    assert_eq!(Severity::Warning.to_string(), "WARN");
    assert_eq!(Severity::Fatal.to_string(), "FATAL");
}

// ─────────────────────────────────────────────────────────────────────────────
// DiagnosticEvent construction
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_event_fatal_constructor() {
    let ev = DiagnosticEvent::fatal(EventKind::RequiredFieldMissing {
        field: "name".into(),
    });
    assert_eq!(ev.severity, Severity::Fatal);
    assert!(ev.is_fatal());
    assert!(ev.row_index.is_none());
    assert!(ev.source.is_none());
}

#[test]
fn test_event_warning_constructor() {
    let ev = DiagnosticEvent::warning(EventKind::ConstraintViolation {
        field: "age".into(),
        constraint: "maxValue = 150".into(),
        reason: "value 200 exceeds maximum".into(),
    });
    assert_eq!(ev.severity, Severity::Warning);
    assert!(!ev.is_fatal());
}

#[test]
fn test_event_info_constructor() {
    let ev = DiagnosticEvent::info(EventKind::Notice {
        message: "optional field 'bio' absent".into(),
    });
    assert_eq!(ev.severity, Severity::Info);
    assert!(!ev.is_fatal());
}

#[test]
fn test_event_builder_chain_at_row_with_source() {
    let ev = DiagnosticEvent::fatal(EventKind::TypeMismatch {
        field: "price".into(),
        expected: "i32".into(),
        actual: "str".into(),
    })
    .at_row(7)
    .with_source("Product");

    assert_eq!(ev.row_index, Some(7));
    assert_eq!(ev.source.as_deref(), Some("Product"));
}

#[test]
fn test_event_display_contains_severity_and_kind() {
    let ev = DiagnosticEvent::fatal(EventKind::TypeMismatch {
        field: "id".into(),
        expected: "i64".into(),
        actual: "bool".into(),
    })
    .at_row(3)
    .with_source("Person");

    let s = ev.to_string();
    assert!(s.contains("FATAL"), "display should contain severity");
    assert!(s.contains("row 3"), "display should contain row index");
    assert!(s.contains("Person"), "display should contain source");
    assert!(s.contains("id"), "display should contain field name");
}

#[test]
fn test_event_display_no_row_no_source() {
    let ev = DiagnosticEvent::info(EventKind::ProcessStarted {
        source: "data.jsont".into(),
    });
    let s = ev.to_string();
    assert!(s.contains("INFO"));
    assert!(s.contains("ProcessStarted"));
    assert!(s.contains("data.jsont"));
}

// ─────────────────────────────────────────────────────────────────────────────
// EventKind Display
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_event_kind_display_type_mismatch() {
    let k = EventKind::TypeMismatch {
        field: "age".into(),
        expected: "i32".into(),
        actual: "str".into(),
    };
    let s = k.to_string();
    assert!(s.contains("TypeMismatch"));
    assert!(s.contains("age"));
}

#[test]
fn test_event_kind_display_process_completed() {
    let k = EventKind::ProcessCompleted {
        total_rows: 100,
        valid_rows: 95,
        warning_rows: 3,
        invalid_rows: 5,
        duration_ms: 42,
    };
    let s = k.to_string();
    assert!(s.contains("total=100"));
    assert!(s.contains("valid=95"));
    assert!(s.contains("invalid=5"));
    assert!(s.contains("42ms"));
}

// ─────────────────────────────────────────────────────────────────────────────
// MemorySink
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_memory_sink_captures_events() {
    let mut sink = MemorySink::new();
    assert!(sink.is_empty());

    sink.emit(DiagnosticEvent::fatal(EventKind::RequiredFieldMissing {
        field: "id".into(),
    }));
    sink.emit(DiagnosticEvent::warning(EventKind::ConstraintViolation {
        field: "age".into(),
        constraint: "maxValue = 100".into(),
        reason: "value 120 exceeds max".into(),
    }));
    sink.emit(DiagnosticEvent::info(EventKind::Notice {
        message: "processing started".into(),
    }));

    assert_eq!(sink.len(), 3);
    assert!(!sink.is_empty());
}

#[test]
fn test_memory_sink_filter_by_severity() {
    let mut sink = MemorySink::new();

    sink.emit(DiagnosticEvent::fatal(EventKind::ParseFailure {
        reason: "unexpected token".into(),
    }));
    sink.emit(DiagnosticEvent::fatal(EventKind::RequiredFieldMissing {
        field: "name".into(),
    }));
    sink.emit(DiagnosticEvent::warning(EventKind::RuleViolation {
        rule: "age > 0".into(),
        reason: "evaluated to false".into(),
    }));
    sink.emit(DiagnosticEvent::info(EventKind::RowAccepted {
        row_index: 1,
    }));

    assert_eq!(sink.fatals().len(), 2);
    assert_eq!(sink.warnings().len(), 1);
    assert_eq!(sink.infos().len(), 1);
}

#[test]
fn test_memory_sink_has_fatals() {
    let mut sink = MemorySink::new();
    assert!(!sink.has_fatals());

    sink.emit(DiagnosticEvent::warning(EventKind::Notice {
        message: "nothing fatal".into(),
    }));
    assert!(!sink.has_fatals());

    sink.emit(DiagnosticEvent::fatal(EventKind::ParseFailure {
        reason: "bad syntax".into(),
    }));
    assert!(sink.has_fatals());
}

#[test]
fn test_memory_sink_count_kind() {
    let mut sink = MemorySink::new();

    sink.emit(DiagnosticEvent::fatal(EventKind::TypeMismatch {
        field: "a".into(),
        expected: "i32".into(),
        actual: "str".into(),
    }));
    sink.emit(DiagnosticEvent::fatal(EventKind::TypeMismatch {
        field: "b".into(),
        expected: "bool".into(),
        actual: "null".into(),
    }));
    sink.emit(DiagnosticEvent::warning(EventKind::ConstraintViolation {
        field: "c".into(),
        constraint: "minValue = 0".into(),
        reason: "negative".into(),
    }));

    assert_eq!(sink.count_kind("TypeMismatch"), 2);
    assert_eq!(sink.count_kind("ConstraintViolation"), 1);
    assert_eq!(sink.count_kind("ParseFailure"), 0);
}

#[test]
fn test_memory_sink_clear() {
    let mut sink = MemorySink::new();
    sink.emit(DiagnosticEvent::info(EventKind::Notice {
        message: "x".into(),
    }));
    sink.emit(DiagnosticEvent::info(EventKind::Notice {
        message: "y".into(),
    }));
    assert_eq!(sink.len(), 2);

    sink.clear();
    assert_eq!(sink.len(), 0);
    assert!(sink.is_empty());
}

#[test]
fn test_memory_sink_into_events() {
    let mut sink = MemorySink::new();
    sink.emit(DiagnosticEvent::info(EventKind::RowAccepted {
        row_index: 0,
    }));
    sink.emit(DiagnosticEvent::info(EventKind::RowAccepted {
        row_index: 1,
    }));

    let events = sink.into_events();
    assert_eq!(events.len(), 2);
}

#[test]
fn test_memory_sink_flush_succeeds() {
    let mut sink = MemorySink::new();
    assert!(sink.flush().is_ok());
}

#[test]
fn test_memory_sink_row_index_preserved() {
    let mut sink = MemorySink::new();

    sink.emit(
        DiagnosticEvent::fatal(EventKind::TypeMismatch {
            field: "x".into(),
            expected: "i32".into(),
            actual: "str".into(),
        })
        .at_row(42),
    );

    let ev = &sink.events()[0];
    assert_eq!(ev.row_index, Some(42));
}

// ─────────────────────────────────────────────────────────────────────────────
// ConsoleSink — smoke tests (output goes to stderr/stdout; we just check no panic)
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_console_sink_emit_does_not_panic() {
    let mut sink = ConsoleSink::new();
    sink.emit(DiagnosticEvent::info(EventKind::Notice {
        message: "console sink smoke test".into(),
    }));
    assert!(sink.flush().is_ok());
}

#[test]
fn test_console_sink_min_severity_filters_events() {
    // warnings_and_above suppresses Info — nothing visible but also no panic.
    let mut sink = ConsoleSink::warnings_and_above();
    sink.emit(DiagnosticEvent::info(EventKind::Notice {
        message: "this should be filtered".into(),
    }));
    sink.emit(DiagnosticEvent::warning(EventKind::Notice {
        message: "this should appear on stderr".into(),
    }));
    assert!(sink.flush().is_ok());
}

// ─────────────────────────────────────────────────────────────────────────────
// FileSink — write to a temp file
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_file_sink_writes_and_flushes() {
    use jsont::FileSink;
    use std::io::Read;

    let path = std::env::temp_dir().join("jsont_diagnostic_test.log");

    // Remove if leftover from a previous run.
    let _ = std::fs::remove_file(&path);

    {
        let mut sink = FileSink::new(&path).expect("FileSink::new should succeed");
        sink.emit(
            DiagnosticEvent::fatal(EventKind::ParseFailure {
                reason: "unexpected EOF".into(),
            })
            .at_row(5),
        );
        sink.emit(
            DiagnosticEvent::warning(EventKind::ConstraintViolation {
                field: "score".into(),
                constraint: "maxValue = 100".into(),
                reason: "value 105".into(),
            })
            .at_row(5),
        );
        sink.flush().expect("flush should succeed");
    }

    let mut content = String::new();
    std::fs::File::open(&path)
        .unwrap()
        .read_to_string(&mut content)
        .unwrap();

    assert!(content.contains("FATAL"), "file should contain FATAL");
    assert!(
        content.contains("ParseFailure"),
        "file should contain ParseFailure"
    );
    assert!(content.contains("WARN"), "file should contain WARN");
    assert!(
        content.contains("ConstraintViolation"),
        "file should contain ConstraintViolation"
    );
    assert!(
        content.lines().count() >= 2,
        "file should have at least 2 lines"
    );

    let _ = std::fs::remove_file(&path);
}

// ─────────────────────────────────────────────────────────────────────────────
// Multiple sinks — same events fan-out manually
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_fan_out_to_multiple_sinks() {
    let mut console = ConsoleSink::fatal_only();
    let mut memory = MemorySink::new();

    let events = vec![
        DiagnosticEvent::fatal(EventKind::RequiredFieldMissing { field: "id".into() }).at_row(0),
        DiagnosticEvent::info(EventKind::RowAccepted { row_index: 1 }),
    ];

    for ev in events {
        console.emit(ev.clone());
        memory.emit(ev);
    }

    // ConsoleSink only emits Fatal to stderr — no assertions needed beyond no-panic.
    assert!(console.flush().is_ok());

    // MemorySink captured both.
    assert_eq!(memory.len(), 2);
    assert_eq!(memory.fatals().len(), 1);
    assert_eq!(memory.infos().len(), 1);
}
