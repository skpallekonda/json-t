// =============================================================================
// diagnostic/mod.rs
// =============================================================================
// Core diagnostic types for the JsonT processing pipeline.
//
// Design principles:
//   - DiagnosticSink is a trait in lib.rs (like other core traits).
//   - This module owns the event types that flow through the sink.
//   - Severity has three levels: Fatal, Warning, Info.
//   - EventKind distinguishes validation issues from process notices.
//   - All types are Clone + Debug so sinks can store, format, or forward them.
// =============================================================================

pub mod sink;

use std::fmt;

// ─────────────────────────────────────────────────────────────────────────────
// SinkError
// ─────────────────────────────────────────────────────────────────────────────

/// Errors that a DiagnosticSink may encounter (primarily on flush).
#[derive(Debug, Clone)]
pub enum SinkError {
    /// An I/O error — typically from FileSink.
    Io(String),
    /// A general sink error (e.g. database write failure).
    Other(String),
}

impl fmt::Display for SinkError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SinkError::Io(msg) => write!(f, "I/O error: {}", msg),
            SinkError::Other(msg) => write!(f, "Sink error: {}", msg),
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Severity
// ─────────────────────────────────────────────────────────────────────────────

/// Severity levels for diagnostics.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Severity {
    /// Non-recoverable errors that halt processing.
    Fatal,
    /// Issues that should be fixed but don't stop the pipeline.
    Warning,
    /// Informational messages (e.g. schema inference details).
    Info,
}

impl Ord for Severity {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        let priority = |s: &Severity| match s {
            Severity::Info => 0,
            Severity::Warning => 1,
            Severity::Fatal => 2,
        };
        priority(self).cmp(&priority(other))
    }
}

impl PartialOrd for Severity {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl fmt::Display for Severity {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Severity::Fatal => write!(f, "FATAL"),
            Severity::Warning => write!(f, "WARN"),
            Severity::Info => write!(f, "INFO"),
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Diagnostic
// ─────────────────────────────────────────────────────────────────────────────

/// A single diagnostic event.
#[derive(Debug, Clone)]
pub struct Diagnostic {
    /// The severity level.
    pub severity: Severity,
    /// The category of the event.
    pub kind: EventKind,
    /// A short title or summary.
    pub title: String,
    /// A detailed message.
    pub message: String,
    /// Optional namespace name.
    pub namespace: Option<String>,
    /// Optional schema name.
    pub schema: Option<String>,
    /// Optional field name (for field-level diagnostics).
    pub field: Option<String>,
}

impl Diagnostic {
    /// Creates a new diagnostic.
    #[inline]
    pub fn new(
        severity: Severity,
        kind: EventKind,
        title: impl Into<String>,
        message: impl Into<String>,
    ) -> Self {
        Self {
            severity,
            kind,
            title: title.into(),
            message: message.into(),
            namespace: None,
            schema: None,
            field: None,
        }
    }

    /// Sets the namespace for this diagnostic.
    #[inline]
    pub fn with_namespace(mut self, name: impl Into<String>) -> Self {
        self.namespace = Some(name.into());
        self
    }

    /// Sets the schema for this diagnostic.
    #[inline]
    pub fn with_schema(mut self, name: impl Into<String>) -> Self {
        self.schema = Some(name.into());
        self
    }

    /// Sets the field for this diagnostic.
    #[inline]
    pub fn with_field(mut self, name: impl Into<String>) -> Self {
        self.field = Some(name.into());
        self
    }

    /// Returns a formatted string representation (similar to the original implementation).
    pub fn format(&self) -> String {
        let mut parts = Vec::new();

        // Severity + Kind
        parts.push(format!("[{}] [{}]", self.severity, self.kind));

        // Location (namespace / schema / field)
        if let Some(ns) = &self.namespace {
            parts.push(format!("ns={}", ns));
        }
        if let Some(sch) = &self.schema {
            parts.push(format!("schema={}", sch));
        }
        if let Some(fld) = &self.field {
            parts.push(format!("field={}", fld));
        }

        // Title
        parts.push(format!("{}", self.title));

        // Message
        parts.push(format!("{}", self.message));

        parts.join(" ")
    }
}

impl std::error::Error for SinkError {}

// ─────────────────────────────────────────────────────────────────────────────
// EventKind
// ─────────────────────────────────────────────────────────────────────────────

/// Classifies what a diagnostic event is about.
///
/// Validation variants carry the row/field context; Process variants are
/// pipeline lifecycle notices.
#[derive(Debug, Clone, PartialEq)]
pub enum EventKind {
    // ── Validation ────────────────────────────────────────────────────────
    /// A field's value did not match the declared scalar type.
    /// e.g. a string was supplied for an i32 field.
    TypeMismatch {
        field: String,
        expected: String,
        actual: String,
    },

    /// A field's shape was wrong — scalar vs array vs object.
    ShapeMismatch {
        field: String,
        expected: String, // "scalar" | "array" | "object"
        actual: String,
    },

    /// A required field was absent (null or unspecified) in the row.
    RequiredFieldMissing { field: String },

    /// A field-level constraint was violated.
    ConstraintViolation {
        field: String,
        constraint: String, // e.g. "maxLength = 100"
        reason: String,
    },

    /// A row-level rule expression evaluated to false.
    RuleViolation {
        rule: String, // stringified expression
        reason: String,
    },

    /// A conditional requirement was triggered and a required field was absent.
    ConditionalRequirementViolation {
        condition: String,
        missing_fields: Vec<String>,
    },

    /// A uniqueness constraint was violated across rows.
    UniqueViolation {
        fields: Vec<String>,
        row_index: usize,
    },

    /// A field's value failed semantic format validation (e.g. invalid UUID).
    FormatViolation {
        field: String,
        expected: String, // "uuid", "email", etc.
        actual: String,
    },

    /// A dataset-level expression evaluated to false.
    DatasetRuleViolation { rule: String, reason: String },

    /// A row could not be parsed from the source text.
    ParseFailure { reason: String },

    // ── Process notices ───────────────────────────────────────────────────
    /// The pipeline started processing a file / input.
    ProcessStarted {
        source: String, // file path or description
    },

    /// A single row was successfully parsed and validated.
    RowAccepted { row_index: usize },

    /// A row was rejected (one or more Fatal events).
    RowRejected {
        row_index: usize,
        fatal_count: usize,
    },

    /// A row was accepted with warnings.
    RowAcceptedWithWarnings {
        row_index: usize,
        warning_count: usize,
    },

    /// The pipeline finished processing all rows.
    ProcessCompleted {
        total_rows: usize,
        valid_rows: usize,
        warning_rows: usize,
        invalid_rows: usize,
        duration_ms: u64,
    },

    /// A general informational notice (default applied, optional field absent, etc.)
    Notice { message: String },
}

impl fmt::Display for EventKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            EventKind::TypeMismatch {
                field,
                expected,
                actual,
            } => write!(
                f,
                "TypeMismatch on '{}': expected {}, got {}",
                field, expected, actual
            ),

            EventKind::ShapeMismatch {
                field,
                expected,
                actual,
            } => write!(
                f,
                "ShapeMismatch on '{}': expected {}, got {}",
                field, expected, actual
            ),

            EventKind::RequiredFieldMissing { field } => {
                write!(f, "RequiredFieldMissing: '{}'", field)
            }

            EventKind::ConstraintViolation {
                field,
                constraint,
                reason,
            } => write!(
                f,
                "ConstraintViolation on '{}' [{}]: {}",
                field, constraint, reason
            ),

            EventKind::RuleViolation { rule, reason } => {
                write!(f, "RuleViolation [{}]: {}", rule, reason)
            }

            EventKind::ConditionalRequirementViolation {
                condition,
                missing_fields,
            } => write!(
                f,
                "ConditionalRequirementViolation when '{}': missing {:?}",
                condition, missing_fields
            ),

            EventKind::UniqueViolation { fields, row_index } => write!(
                f,
                "UniqueViolation at row {}: fields {:?}",
                row_index, fields
            ),

            EventKind::DatasetRuleViolation { rule, reason } => {
                write!(f, "DatasetRuleViolation [{}]: {}", rule, reason)
            }

            EventKind::FormatViolation { field, expected, actual } => {
                write!(f, "FormatViolation on '{}': expected {}, got {}", field, expected, actual)
            }

            EventKind::ParseFailure { reason } => write!(f, "ParseFailure: {}", reason),

            EventKind::ProcessStarted { source } => write!(f, "ProcessStarted: {}", source),

            EventKind::RowAccepted { row_index } => write!(f, "RowAccepted: row {}", row_index),

            EventKind::RowRejected {
                row_index,
                fatal_count,
            } => write!(
                f,
                "RowRejected: row {} ({} fatal issue(s))",
                row_index, fatal_count
            ),

            EventKind::RowAcceptedWithWarnings {
                row_index,
                warning_count,
            } => write!(
                f,
                "RowAcceptedWithWarnings: row {} ({} warning(s))",
                row_index, warning_count
            ),

            EventKind::ProcessCompleted {
                total_rows,
                valid_rows,
                warning_rows,
                invalid_rows,
                duration_ms,
            } => write!(
                f,
                "ProcessCompleted: total={} valid={} warnings={} invalid={} duration={}ms",
                total_rows, valid_rows, warning_rows, invalid_rows, duration_ms
            ),

            EventKind::Notice { message } => write!(f, "Notice: {}", message),
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DiagnosticEvent
// ─────────────────────────────────────────────────────────────────────────────

/// A single event emitted during pipeline processing.
///
/// Every event carries:
///   - `severity`  — Fatal / Warning / Info
///   - `kind`      — what happened (validation issue or process notice)
///   - `row_index` — which row triggered this (None for pipeline-level events)
///   - `source`    — optional origin label (file name, schema name, etc.)
#[derive(Debug, Clone)]
pub struct DiagnosticEvent {
    pub severity: Severity,
    pub kind: EventKind,
    pub row_index: Option<usize>,
    pub source: Option<String>,
}

impl DiagnosticEvent {
    // ── Constructors ──────────────────────────────────────────────────────

    pub fn fatal(kind: EventKind) -> Self {
        Self {
            severity: Severity::Fatal,
            kind,
            row_index: None,
            source: None,
        }
    }

    pub fn warning(kind: EventKind) -> Self {
        Self {
            severity: Severity::Warning,
            kind,
            row_index: None,
            source: None,
        }
    }

    pub fn info(kind: EventKind) -> Self {
        Self {
            severity: Severity::Info,
            kind,
            row_index: None,
            source: None,
        }
    }

    /// Attach a row index to this event.
    pub fn at_row(mut self, index: usize) -> Self {
        self.row_index = Some(index);
        self
    }

    /// Attach a source label (schema name, file path, etc.) to this event.
    pub fn with_source(mut self, source: impl Into<String>) -> Self {
        self.source = Some(source.into());
        self
    }

    /// True if this event will cause a row to be rejected.
    pub fn is_fatal(&self) -> bool {
        self.severity == Severity::Fatal
    }
}

impl fmt::Display for DiagnosticEvent {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let row = self
            .row_index
            .map(|i| format!(" [row {}]", i))
            .unwrap_or_default();
        let src = self
            .source
            .as_deref()
            .map(|s| format!(" ({})", s))
            .unwrap_or_default();
        write!(f, "[{}]{}{} {}", self.severity, row, src, self.kind)
    }
}
