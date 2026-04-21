// =============================================================================
// diagnostic/sink/file.rs — FileSink
// =============================================================================
// Appends DiagnosticEvents to a file, one event per line.
// Uses BufWriter for performance; flush() drains the buffer.
// =============================================================================

use std::fs::{File, OpenOptions};
use std::io::{BufWriter, Write};
use std::path::Path;

use crate::diagnostic::{DiagnosticEvent, Severity, SinkError};
use crate::DiagnosticSink;

/// A diagnostic sink that writes events to a file.
///
/// Each event is written as a single line using its `Display` impl.
/// The file is opened in append mode, so existing content is preserved.
pub struct FileSink {
    writer: BufWriter<File>,
    min_severity: Severity,
}

impl FileSink {
    /// Open (or create) a file at `path` for append, emitting all severities.
    pub fn new(path: impl AsRef<Path>) -> Result<Self, SinkError> {
        Self::with_severity(path, Severity::Info)
    }

    /// Open (or create) a file, emitting only events at or above `min_severity`.
    pub fn with_severity(
        path: impl AsRef<Path>,
        min_severity: Severity,
    ) -> Result<Self, SinkError> {
        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .map_err(|e| SinkError::Io(e.to_string()))?;

        Ok(Self {
            writer: BufWriter::new(file),
            min_severity,
        })
    }
}

impl DiagnosticSink for FileSink {
    fn emit(&mut self, event: DiagnosticEvent) {
        if event.severity < self.min_severity {
            return;
        }
        // Ignore individual write errors — they will surface on flush().
        let _ = writeln!(self.writer, "{}", event);
    }

    fn flush(&mut self) -> Result<(), SinkError> {
        self.writer
            .flush()
            .map_err(|e| SinkError::Io(e.to_string()))
    }
}
