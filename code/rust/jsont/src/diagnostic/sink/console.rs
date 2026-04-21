// =============================================================================
// diagnostic/sink/console.rs — ConsoleSink
// =============================================================================
// Writes every DiagnosticEvent to stdout (Info) or stderr (Warning/Fatal).
// Optionally filtered by minimum severity.
// =============================================================================

use crate::diagnostic::{DiagnosticEvent, Severity};
use crate::DiagnosticSink;

/// A diagnostic sink that writes events to the console.
///
/// - `Fatal` and `Warning` events go to **stderr**.
/// - `Info` events go to **stdout**.
/// - A `min_severity` filter suppresses events below the threshold.
#[derive(Debug, Clone)]
pub struct ConsoleSink {
    /// Minimum severity to emit. Events below this level are silently dropped.
    pub min_severity: Severity,
}

impl ConsoleSink {
    /// Emit all events (Info and above).
    pub fn new() -> Self {
        Self {
            min_severity: Severity::Info,
        }
    }

    /// Emit only Warning and Fatal events.
    pub fn warnings_and_above() -> Self {
        Self {
            min_severity: Severity::Warning,
        }
    }

    /// Emit only Fatal events.
    pub fn fatal_only() -> Self {
        Self {
            min_severity: Severity::Fatal,
        }
    }
}

impl Default for ConsoleSink {
    fn default() -> Self {
        Self::new()
    }
}

impl DiagnosticSink for ConsoleSink {
    fn emit(&mut self, event: DiagnosticEvent) {
        if event.severity < self.min_severity {
            return;
        }
        match event.severity {
            Severity::Fatal | Severity::Warning => eprintln!("{}", event),
            Severity::Info => println!("{}", event),
        }
    }

    fn flush(&mut self) -> Result<(), crate::diagnostic::SinkError> {
        // Console is line-buffered; nothing to flush explicitly.
        Ok(())
    }
}
