// =============================================================================
// diagnostic/sink/memory.rs — MemorySink
// =============================================================================
// Captures all DiagnosticEvents into an in-memory Vec.
// Primary use: unit tests — inspect exactly which events were emitted.
// =============================================================================

use crate::diagnostic::{DiagnosticEvent, Severity, SinkError};
use crate::DiagnosticSink;

/// A diagnostic sink that stores events in memory.
///
/// Primarily used in tests to assert on emitted events without I/O.
#[derive(Debug, Clone, Default)]
pub struct MemorySink {
    events: Vec<DiagnosticEvent>,
}

impl MemorySink {
    pub fn new() -> Self {
        Self::default()
    }

    /// All events captured so far.
    pub fn events(&self) -> &[DiagnosticEvent] {
        &self.events
    }

    /// Events filtered to a specific severity.
    pub fn by_severity(&self, severity: Severity) -> Vec<&DiagnosticEvent> {
        self.events
            .iter()
            .filter(|e| e.severity == severity)
            .collect()
    }

    /// All Fatal events.
    pub fn fatals(&self) -> Vec<&DiagnosticEvent> {
        self.by_severity(Severity::Fatal)
    }

    /// All Warning events.
    pub fn warnings(&self) -> Vec<&DiagnosticEvent> {
        self.by_severity(Severity::Warning)
    }

    /// All Info events.
    pub fn infos(&self) -> Vec<&DiagnosticEvent> {
        self.by_severity(Severity::Info)
    }

    /// Total event count.
    pub fn len(&self) -> usize {
        self.events.len()
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }

    /// True if any Fatal event was captured.
    pub fn has_fatals(&self) -> bool {
        self.events.iter().any(|e| e.severity == Severity::Fatal)
    }

    /// True if any Warning event was captured.
    pub fn has_warnings(&self) -> bool {
        self.events.iter().any(|e| e.severity == Severity::Warning)
    }

    /// Clear all captured events.
    pub fn clear(&mut self) {
        self.events.clear();
    }

    /// Consume the sink, returning all captured events.
    pub fn into_events(self) -> Vec<DiagnosticEvent> {
        self.events
    }

    /// Count events matching a specific EventKind discriminant by name.
    /// Used in tests: `sink.count_kind("TypeMismatch")`.
    pub fn count_kind(&self, kind_name: &str) -> usize {
        self.events
            .iter()
            .filter(|e| format!("{:?}", e.kind).starts_with(kind_name))
            .count()
    }
}

impl DiagnosticSink for MemorySink {
    fn emit(&mut self, event: DiagnosticEvent) {
        self.events.push(event);
    }

    fn flush(&mut self) -> Result<(), SinkError> {
        // Nothing to flush for an in-memory sink.
        Ok(())
    }
}
