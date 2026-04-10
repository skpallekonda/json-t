// =============================================================================
// validate/mod.rs — Row validation pipeline with non-blocking diagnostic sinks
// =============================================================================
//
// # Responsibilities
//
//   1. Apply field-level constraints (required, value bounds, length, regex, …)
//   2. Apply row-level rules from the schema's `validations` block
//   3. Enforce uniqueness constraints across the batch
//   4. Deliver every DiagnosticEvent to registered sinks asynchronously —
//      sink I/O never stalls the validation loop
//
// # Severity contract
//
//   Fatal   — row is excluded from the returned Vec<JsonTRow>
//   Warning — row is included but flagged
//   Info    — lifecycle events (ProcessStarted, RowAccepted, ProcessCompleted)
//
// # Usage
//
//   // Build a pipeline (ConsoleSink is added by default)
//   let pipeline = ValidationPipeline::builder(schema)
//       .with_sink(Box::new(FileSink::new("errors.log").unwrap()))
//       .build();
//
//   // Streaming — process one row at a time, O(1) memory for per-row rules:
//   pipeline.validate_each(parsed_rows, |row| {
//       // called immediately for every row that has no Fatal issues
//   });
//
//   // Convenience — collect all clean rows into a Vec:
//   let clean_rows = pipeline.validate_rows(parsed_rows);
//
//   // Flush and shut down the sink thread
//   pipeline.finish()?;
//
// # Memory model
//
//   Per-row constraints and row-level rules are evaluated in O(1) space.
//   Uniqueness checking accumulates a HashSet of composite string keys —
//   O(unique-keys), not O(rows).
//   Dataset-level aggregate rules (validation.dataset) are not yet
//   implemented and require a separate pass over all rows.
//
// # Non-blocking design
//
//   An `mpsc::channel` decouples the validation loop from sink I/O.
//   The background thread owns all sinks, drains the channel, and calls
//   `emit()` per sink for every event.  Dropping the `Sender` (in `finish()`)
//   closes the channel — the thread finishes draining, flushes all sinks,
//   and terminates.
// =============================================================================

pub(crate) mod constraint;
pub(crate) mod rule;

use std::collections::HashSet;
use std::sync::mpsc;
use std::thread::JoinHandle;
use std::time::Instant;

use crate::diagnostic::{DiagnosticEvent, EventKind, Severity, SinkError};
use crate::model::data::{JsonTRow, JsonTValue};
use crate::model::field::{AnyOfVariant, JsonTField, JsonTFieldKind, ScalarType};
use crate::model::schema::{JsonTSchema, SchemaKind};
use crate::model::validation::JsonTValidationBlock;
use crate::ConsoleSink;
use crate::DiagnosticSink;

// =============================================================================
// ValidationPipeline
// =============================================================================

/// Row-validation pipeline bound to one schema.
///
/// Validates [`JsonTRow`] batches against field constraints and schema rules,
/// delivering clean rows (those with no Fatal issues) either through a
/// streaming callback ([`validate_each`]) or collected into a [`Vec`]
/// ([`validate_rows`]).
///
/// Diagnostic events are delivered asynchronously to all registered sinks
/// so I/O latency never stalls the validation loop.
///
/// # Memory model
/// - Per-row constraints and rules: O(1) — truly streaming.
/// - Uniqueness: O(unique-keys) — only composite key strings are accumulated,
///   not the row data itself.
/// - [`validate_rows`] adds O(clean-rows) for the output collection.
///
/// # Lifecycle
/// 1. Build via [`ValidationPipeline::builder`].
/// 2. Call [`validate_each`] or [`validate_rows`] one or more times.
/// 3. Call [`finish`] to flush all sinks and join the background thread.
pub struct ValidationPipeline {
    fields: Vec<JsonTField>,
    validation: Option<JsonTValidationBlock>,
    schema_name: String,
    /// Union of all field names referenced by any rule expression or
    /// `ConditionalRequirement` in the validation block, pre-computed once
    /// at build time so `check_rules` never traverses the AST per row.
    rule_field_refs: Vec<String>,
    /// Sender half of the event channel.  Dropping this closes the channel
    /// and signals the sink thread to finish.
    tx: mpsc::Sender<DiagnosticEvent>,
    sink_thread: Option<JoinHandle<Option<SinkError>>>,
}

impl ValidationPipeline {
    /// Create a [`ValidationPipelineBuilder`] for `schema`.
    ///
    /// A [`ConsoleSink`] is registered by default.
    pub fn builder(schema: JsonTSchema) -> ValidationPipelineBuilder {
        ValidationPipelineBuilder::new(schema)
    }

    /// Validate rows one at a time, calling `on_clean` immediately for each
    /// row that has no Fatal issues.
    ///
    /// This is the core streaming API:
    /// - Per-row constraints and row-level rules are evaluated in O(1) space.
    /// - Uniqueness accumulates O(unique-keys) — key strings only, not rows.
    /// - Clean rows are handed to `on_clean` without buffering.
    ///
    /// All diagnostic events are sent to sinks asynchronously.
    ///
    /// Uniqueness state is local to each `validate_each` call; successive
    /// calls start with fresh uniqueness state.
    pub fn validate_each<I, F>(&self, rows: I, mut on_clean: F)
    where
        I: IntoIterator<Item = JsonTRow>,
        F: FnMut(JsonTRow),
    {
        let start = Instant::now();
        let mut total = 0usize;
        let mut valid_count = 0usize;
        let mut warn_count = 0usize;
        let mut invalid_count = 0usize;

        // One HashSet per uniqueness rule; lives for the duration of this call.
        // Accumulates composite key strings — not row data.
        // String keys (null-byte separated) are ~60 bytes vs Vec<String> ~84+ bytes.
        let mut unique_sets: Vec<HashSet<String>> = self
            .validation
            .as_ref()
            .map(|v| vec![HashSet::new(); v.unique.len()])
            .unwrap_or_default();

        self.emit(DiagnosticEvent::info(EventKind::ProcessStarted {
            source: self.schema_name.clone(),
        }));

        for (idx, row) in rows.into_iter().enumerate() {
            total += 1;
            let mut row_events: Vec<DiagnosticEvent> = Vec::new();

            // Shape-mismatch rows stay as None; promoted rows carry through to
            // on_clean so the moved value is available after the else block.
            let promoted: Option<JsonTRow>;

            // ── Shape check ───────────────────────────────────────────────
            if row.fields.len() != self.fields.len() {
                row_events.push(
                    DiagnosticEvent::fatal(EventKind::ShapeMismatch {
                        field: "(row)".into(),
                        expected: format!("{} field(s)", self.fields.len()),
                        actual: format!("{} field(s)", row.fields.len()),
                    })
                    .at_row(idx)
                    .with_source(&self.schema_name),
                );
                promoted = None;
            } else {
                // Promote Str values to their semantic variants now that the
                // field type is known. Interpret failures as fatal FormatViolations.
                let (p_row, p_events) = try_promote_row(row, &self.fields, idx);
                row_events.extend(p_events);
                let row = p_row;

                // ── Field constraints ─────────────────────────────────────
                for (field, value) in self.fields.iter().zip(row.fields.iter()) {
                    row_events.extend(constraint::check_field(field, value, idx));
                }

                // ── Row rules (skipped if any field check was fatal) ───────
                if !has_fatal(&row_events) {
                    if let Some(validation) = &self.validation {
                        row_events.extend(rule::check_rules(
                            &self.fields,
                            validation,
                            &row.fields,
                            idx,
                            &self.rule_field_refs,
                        ));
                    }
                }

                // ── Uniqueness (skipped if any check was fatal) ────────────
                if !has_fatal(&row_events) {
                    if let Some(validation) = &self.validation {
                        for (ui, paths) in validation.unique.iter().enumerate() {
                            let key = build_unique_key(&self.fields, &row.fields, paths);
                            if !unique_sets[ui].insert(key) {
                                let field_names: Vec<String> =
                                    paths.iter().map(|fp| fp.join()).collect();
                                row_events.push(
                                    DiagnosticEvent::fatal(EventKind::UniqueViolation {
                                        fields: field_names,
                                        row_index: idx,
                                    })
                                    .at_row(idx)
                                    .with_source(&self.schema_name),
                                );
                            }
                        }
                    }
                }

                promoted = Some(row);
            }

            // ── Tally and emit ─────────────────────────────────────────────
            let fatal_n = row_events.iter().filter(|e| e.is_fatal()).count();
            let warn_n = row_events
                .iter()
                .filter(|e| e.severity == Severity::Warning)
                .count();

            for event in row_events {
                self.emit(event);
            }

            if fatal_n > 0 {
                invalid_count += 1;
                self.emit(
                    DiagnosticEvent::fatal(EventKind::RowRejected {
                        row_index: idx,
                        fatal_count: fatal_n,
                    })
                    .with_source(&self.schema_name),
                );
            } else if let Some(row) = promoted {
                on_clean(row);
                if warn_n > 0 {
                    warn_count += 1;
                    self.emit(
                        DiagnosticEvent::warning(EventKind::RowAcceptedWithWarnings {
                            row_index: idx,
                            warning_count: warn_n,
                        })
                        .with_source(&self.schema_name),
                    );
                } else {
                    valid_count += 1;
                    self.emit(
                        DiagnosticEvent::info(EventKind::RowAccepted { row_index: idx })
                            .with_source(&self.schema_name),
                    );
                }
            }
        }

        let duration_ms = start.elapsed().as_millis() as u64;
        self.emit(DiagnosticEvent::info(EventKind::ProcessCompleted {
            total_rows: total,
            valid_rows: valid_count,
            warning_rows: warn_count,
            invalid_rows: invalid_count,
            duration_ms,
        }));
    }

    /// Convenience wrapper: validate rows and collect clean ones into a `Vec`.
    ///
    /// Accepts any value that implements `IntoIterator<Item = JsonTRow>`,
    /// including `Vec<JsonTRow>`, arrays, or custom iterators.
    ///
    /// Internally delegates to [`validate_each`]; only the output collection
    /// allocates beyond O(unique-keys) working memory.
    pub fn validate_rows(&self, rows: impl IntoIterator<Item = JsonTRow>) -> Vec<JsonTRow> {
        let mut clean = Vec::new();
        self.validate_each(rows, |row| clean.push(row));
        clean
    }

    /// Validate a single row and immediately call `on_clean` if it passes.
    ///
    /// Designed for per-row streaming pipelines where the caller already drives
    /// the row source (e.g. a `RowIter` inside a channel worker).  Unlike
    /// [`validate_each`], this method emits no `ProcessStarted` / `ProcessCompleted`
    /// lifecycle events and maintains no uniqueness state — call it in a tight loop
    /// without the per-batch overhead.
    ///
    /// Field constraints and row-level rules are applied exactly as in
    /// [`validate_each`].  Any diagnostic events produced are forwarded to the
    /// registered sinks asynchronously.
    pub fn validate_one(&self, row: JsonTRow, on_clean: impl FnOnce(JsonTRow)) {
        let idx = 0usize; // no meaningful batch index in single-shot mode
        let mut events: Vec<DiagnosticEvent> = Vec::new();

        if row.fields.len() != self.fields.len() {
            self.emit(
                DiagnosticEvent::fatal(EventKind::ShapeMismatch {
                    field: "(row)".into(),
                    expected: format!("{} field(s)", self.fields.len()),
                    actual: format!("{} field(s)", row.fields.len()),
                })
                .at_row(idx)
                .with_source(&self.schema_name),
            );
            return;
        }
        // Promote Str values to their semantic variants now that the
        // field type is known. Interpret failures as fatal FormatViolations.
        let (row, p_events) = try_promote_row(row, &self.fields, idx);
        events.extend(p_events);

        // Field constraints
        for (field, value) in self.fields.iter().zip(row.fields.iter()) {
            events.extend(constraint::check_field(field, value, idx));
        }

        // Row-level rules (skipped when any field check was fatal)
        if !has_fatal(&events) {
            if let Some(validation) = &self.validation {
                events.extend(rule::check_rules(
                    &self.fields,
                    validation,
                    &row.fields,
                    idx,
                    &self.rule_field_refs,
                ));
            }
        }

        let is_clean = !has_fatal(&events);
        for event in events {
            self.emit(event);
        }

        if is_clean {
            on_clean(row);
        }
    }

    /// Flush all sinks and join the background sink thread.
    ///
    /// **Must be called** after all `validate_each`/`validate_rows` invocations
    /// to ensure every event is delivered and every sink is flushed.
    pub fn finish(mut self) -> Result<(), SinkError> {
        // Dropping the sender closes the channel → sink thread will drain
        // remaining events, flush all sinks, and exit.
        drop(self.tx);

        if let Some(handle) = self.sink_thread.take() {
            match handle.join() {
                Ok(Some(err)) => return Err(err),
                Ok(None) => {}
                Err(_) => {
                    return Err(SinkError::Other("sink thread panicked".into()));
                }
            }
        }
        Ok(())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /// Send an event to the background sink thread.
    /// A send failure (thread crashed) is silently ignored.
    #[inline]
    fn emit(&self, event: DiagnosticEvent) {
        let _ = self.tx.send(event);
    }
}

// =============================================================================
// ValidationPipelineBuilder
// =============================================================================

/// Builder for [`ValidationPipeline`].
///
/// A [`ConsoleSink`] is registered by default.  Call [`without_console`] to
/// suppress it, then add only the sinks you want via [`with_sink`].
pub struct ValidationPipelineBuilder {
    schema: JsonTSchema,
    /// Whether to prepend a `ConsoleSink` when building.
    has_console: bool,
    /// User-supplied additional sinks.
    extra_sinks: Vec<Box<dyn DiagnosticSink + Send>>,
}

impl ValidationPipelineBuilder {
    /// Create a builder.  A `ConsoleSink` is included by default.
    pub fn new(schema: JsonTSchema) -> Self {
        Self {
            schema,
            has_console: true,
            extra_sinks: Vec::new(),
        }
    }

    /// Remove the default console sink.
    ///
    /// Useful when you only want file output, or in tests that must not produce
    /// console noise.
    pub fn without_console(mut self) -> Self {
        self.has_console = false;
        self
    }

    /// Register an additional sink.
    ///
    /// Multiple sinks can be registered; all receive every event.
    pub fn with_sink(mut self, sink: Box<dyn DiagnosticSink + Send>) -> Self {
        self.extra_sinks.push(sink);
        self
    }

    /// Spawn the background sink thread and return a ready `ValidationPipeline`.
    pub fn build(self) -> ValidationPipeline {
        let fields: Vec<JsonTField> = match &self.schema.kind {
            SchemaKind::Straight { fields } => fields.clone(),
            // Derived-schema validation is not yet implemented.
            SchemaKind::Derived { .. } => Vec::new(),
        };
        let validation = self.schema.validation.clone();
        let schema_name = self.schema.name.clone();

        // Pre-compute the union of all field names referenced by any rule
        // expression or ConditionalRequirement.  Storing this once avoids
        // traversing the expression AST on every row in check_rules.
        use crate::model::validation::JsonTRule;
        use crate::transform::collect_field_refs;
        let rule_field_refs: Vec<String> = {
            let mut seen = std::collections::HashSet::new();
            let mut refs = Vec::new();
            if let Some(v) = &validation {
                for rule in &v.rules {
                    let candidates = match rule {
                        JsonTRule::Expression(expr) => collect_field_refs(expr),
                        JsonTRule::ConditionalRequirement {
                            condition,
                            required_fields,
                        } => {
                            let mut r = collect_field_refs(condition);
                            r.extend(required_fields.iter().map(|fp| fp.join()));
                            r
                        }
                    };
                    for name in candidates {
                        if seen.insert(name.clone()) {
                            refs.push(name);
                        }
                    }
                }
            }
            refs
        };

        // Assemble the final sink list in declared order.
        let mut sinks: Vec<Box<dyn DiagnosticSink + Send>> = Vec::new();
        if self.has_console {
            sinks.push(Box::new(ConsoleSink::new()));
        }
        sinks.extend(self.extra_sinks);

        let (tx, rx) = mpsc::channel::<DiagnosticEvent>();

        let sink_thread = std::thread::spawn(move || {
            // Drain events until the sender is dropped (channel closed).
            for event in rx {
                for sink in &mut sinks {
                    sink.emit(event.clone());
                }
            }
            // Channel closed: flush all sinks, propagate the first error.
            let mut last_err: Option<SinkError> = None;
            for sink in &mut sinks {
                if let Err(e) = sink.flush() {
                    last_err = Some(e);
                }
            }
            last_err
        });

        ValidationPipeline {
            fields,
            validation,
            schema_name,
            rule_field_refs,
            tx,
            sink_thread: Some(sink_thread),
        }
    }
}

// =============================================================================
// Private helpers
// =============================================================================

#[inline]
fn has_fatal(events: &[DiagnosticEvent]) -> bool {
    events.iter().any(|e| e.is_fatal())
}

/// Promotes each `Str` value in a row to its semantic variant based on the
/// declared `ScalarType` of the corresponding schema field.
///
/// Called once per clean row, after all constraint checks pass.  Non-Str
/// values (including already-promoted variants) are passed through unchanged.
/// Promotes each `Str` value in a row to its semantic variant based on the
/// declared `ScalarType` of the corresponding schema field.
///
/// Failures (e.g. invalid UUID format) are returned as fatal `FormatViolation` events.
fn try_promote_row(
    row: JsonTRow,
    fields: &[JsonTField],
    row_idx: usize,
) -> (JsonTRow, Vec<DiagnosticEvent>) {
    let mut events = Vec::new();
    let promoted: Vec<JsonTValue> = row
        .fields
        .into_iter()
        .zip(fields.iter())
        .map(|(val, field)| {
            match &field.kind {
                JsonTFieldKind::Scalar { field_type, sensitive, .. } => {
                    // If the field is sensitive and the wire value carries a
                    // "base64:" envelope, decode it into Encrypted now that we
                    // have schema context.  The row scanner emits it as a plain
                    // Str — promotion is the first place the schema is known.
                    if *sensitive {
                        if let JsonTValue::Str(ref js) = val {
                            if let crate::model::data::JsonTString::Plain(ref s) = *js {
                                if let Some(b64) = s.strip_prefix("base64:") {
                                    use base64::Engine as _;
                                    match base64::engine::general_purpose::STANDARD.decode(b64) {
                                        Ok(bytes) => return JsonTValue::encrypted(bytes),
                                        Err(_) => {
                                            events.push(
                                                DiagnosticEvent::fatal(EventKind::FormatViolation {
                                                    field:    field.name.clone(),
                                                    expected: "base64-encoded envelope".to_string(),
                                                    actual:   format!("invalid base64 in \"base64:{}\"", b64),
                                                })
                                                .at_row(row_idx),
                                            );
                                            return val;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    let original = val.clone();
                    match val.promote(&field_type.scalar) {
                        Ok(v) => v,
                        Err(_) => {
                            events.push(
                                DiagnosticEvent::fatal(EventKind::FormatViolation {
                                    field:    field.name.clone(),
                                    expected: field_type.scalar.keyword().to_string(),
                                    actual:   constraint::describe_value(&original),
                                })
                                .at_row(row_idx),
                            );
                            original // Keep original value but flagged as fatal
                        }
                    }
                }

                JsonTFieldKind::AnyOf { variants, .. } => {
                    promote_any_of(val, variants)
                }

                // Object and unknown kinds: pass through unchanged.
                _ => val,
            }
        })
        .collect();
    (JsonTRow::new(promoted), events)
}

/// Promote a raw scanned value against a list of anyOf variants (first-match-wins).
///
/// Mirrors Java's `promoteAnyOf` behaviour exactly:
/// - Bool   → first BOOL variant wins immediately.
/// - Number → first numeric variant wins; promotion is applied (may return the
///            original if precision/range doesn't fit), but no fallthrough to the
///            next numeric variant — same as Java's unconditional `return promote(val, type)`.
/// - String → first string-like variant wins; format variants that reject coercion
///            (e.g. "hello" is not a valid UUID) are skipped; plain `str` acts as
///            catch-all and always succeeds.
/// - Everything else (SchemaRef variants, Object, Null) passes through unchanged.
fn promote_any_of(val: JsonTValue, variants: &[AnyOfVariant]) -> JsonTValue {
    for v in variants {
        let AnyOfVariant::Scalar(t) = v else { continue };

        match &val {
            JsonTValue::Bool(_) if *t == ScalarType::Bool => return val,

            // First numeric variant always wins — no fallthrough on failure.
            // Java's promote() for numeric→numeric never throws; it returns the
            // original if the conversion is not applicable. We mirror that: if
            // our promote() returns Err (range/precision violation), we still
            // return the original rather than trying the next numeric variant.
            JsonTValue::Number(_) if t.is_numeric() => {
                return val.clone().promote(t).unwrap_or(val);
            }

            JsonTValue::Str(_) if t.is_string_like() => {
                if *t == ScalarType::Str {
                    // Plain str always succeeds — stop here.
                    return val.clone().promote(t).unwrap_or(val);
                }
                // Format-specific type: try coercion, fall through on failure.
                match val.clone().promote(t) {
                    Ok(promoted) => return promoted,
                    Err(_)       => continue,
                }
            }

            _ => continue,
        }
    }
    val // no scalar variant matched — return as-is
}

/// Build a composite uniqueness key as a single null-byte-separated `String`.
///
/// Using a single `String` instead of `Vec<String>` reduces per-key allocation
/// from ~84+ bytes (Vec header + String headers + heap data) to ~60 bytes
/// (single String header + heap data), improving cache density in the HashSet.
///
/// The `\x00` separator cannot appear in any serialised JsonTValue, so two
/// distinct value combinations can never produce the same composite key.
///
/// Only top-level (single-segment) field paths are currently supported.
/// Nested paths produce a `"<nested>"` placeholder — conservative and safe.
fn build_unique_key(
    fields: &[JsonTField],
    values: &[JsonTValue],
    paths: &[crate::model::schema::FieldPath],
) -> String {
    paths
        .iter()
        .map(|fp| {
            let name = fp.join();
            if name.contains('.') {
                return "<nested>".into();
            }
            if let Some(idx) = fields.iter().position(|f| f.name == name) {
                if let Some(v) = values.get(idx) {
                    return constraint::describe_value(v);
                }
            }
            "<missing>".into()
        })
        .collect::<Vec<String>>()
        .join("\x00")
}
