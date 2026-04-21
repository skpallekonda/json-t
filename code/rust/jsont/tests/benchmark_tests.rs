// =============================================================================
// tests/benchmark_tests.rs
// =============================================================================
// Performance benchmarks using the ICC Men's T20 World Cup schema
// (code/benchmark-schema/wct20-match.jsont — 92 fields, ~1 KB/row).
//
// Excluded from normal `cargo test`. Enable with:
//   cargo test --features bench bench_wct -- --nocapture
//
// Four benchmark options (each measured independently per size):
//   1. Stringify   — build N rows in memory, stringify+write to temp file
//   2. Parse       — parse rows from the file produced by option 1
//   3. Validate    — parse + run ValidationPipeline (constraint + rule checks)
//   4. Transform   — parse + validate + apply MatchBroadcastSummary operations
//
// Five dataset sizes:
//   1_000 | 10_000 | 100_000 | 1_000_000 | 10_000_000
//
// Measurements per operation:
//   - Throughput (rows / ms)
//   - Peak heap delta (via TrackingAllocator)
//   - Total wall-clock time
//   - File size (stringify only)
// =============================================================================

#![cfg(feature = "bench")]

use std::alloc::{GlobalAlloc, Layout, System};
use std::fs::{self, File};
use std::io::{BufReader, BufWriter, Write};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant};

use rust_decimal::Decimal;

use jsont::{
    DiagnosticEvent, DiagnosticSink, JsonTNamespace, SchemaRegistry, SinkError, ValidationPipeline,
    RowTransformer,
};
use jsont::{parse_rows_streaming, RowIter};
use jsont::{write_row, JsonTRow, JsonTRowBuilder, JsonTValue, Parseable};
use jsont::json::{JsonInputMode, JsonOutputMode, JsonReader, JsonWriter};

// =============================================================================
// Tracking allocator
// Wraps the system allocator and records both live bytes and the running peak.
// Each test resets the peak before its measured section so readings are
// relative to that moment, not the whole process lifetime.
// =============================================================================

struct TrackingAllocator;

static LIVE_BYTES: AtomicUsize = AtomicUsize::new(0);
static PEAK_BYTES: AtomicUsize = AtomicUsize::new(0);

unsafe impl GlobalAlloc for TrackingAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        let ptr = unsafe { System.alloc(layout) };
        if !ptr.is_null() {
            let now = LIVE_BYTES.fetch_add(layout.size(), Ordering::Relaxed) + layout.size();
            let mut peak = PEAK_BYTES.load(Ordering::Relaxed);
            while now > peak {
                match PEAK_BYTES.compare_exchange_weak(
                    peak, now, Ordering::Relaxed, Ordering::Relaxed,
                ) {
                    Ok(_) => break,
                    Err(actual) => peak = actual,
                }
            }
        }
        ptr
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe { System.dealloc(ptr, layout) };
        LIVE_BYTES.fetch_sub(layout.size(), Ordering::Relaxed);
    }
}

#[global_allocator]
static ALLOCATOR: TrackingAllocator = TrackingAllocator;

fn reset_peak() -> usize {
    let current = LIVE_BYTES.load(Ordering::Relaxed);
    PEAK_BYTES.store(current, Ordering::Relaxed);
    current
}

fn peak_above(baseline: usize) -> usize {
    PEAK_BYTES.load(Ordering::Relaxed).saturating_sub(baseline)
}

// =============================================================================
// Schema — loaded once from wct20-match.jsont at compile time
// =============================================================================

const WCT20_SCHEMA: &str = include_str!("../../../benchmark-schema/wct20-match.jsont");

fn load_schemas() -> (jsont::JsonTSchema, jsont::JsonTSchema, SchemaRegistry) {
    let ns = JsonTNamespace::parse(WCT20_SCHEMA).expect("wct20 schema parse failed");
    // Build the registry directly without schema validation — the wct20 schema uses
    // enum-typed fields (e.g. <TournamentPhase>) which the object-ref validator would
    // flag as unknown schemas (enums are not stored in the registry).
    let mut registry = SchemaRegistry::new();
    for catalog in &ns.catalogs {
        for schema in &catalog.schemas {
            registry.register(schema.clone());
        }
    }
    let cricket = registry.get("CricketMatch")
        .expect("CricketMatch schema not found")
        .clone();
    let broadcast = registry.get("MatchBroadcastSummary")
        .expect("MatchBroadcastSummary schema not found")
        .clone();
    (cricket, broadcast, registry)
}

// =============================================================================
// Row generation — CricketMatch schema (92 fields, ~1 KB/row)
// All generated rows satisfy every declared constraint and validation rule.
// Optional fields (marked ?) are always null to keep row size predictable.
// =============================================================================

const PHASES: &[&str]   = &["GROUP_STAGE", "SUPER_8", "SEMI_FINAL", "FINAL"];
const SURFACES: &[&str] = &["GRASS", "HARD_CLAY", "RED_SOIL", "DRY_FLAT", "WET_FAST"];
const MATCH_STATUSES: &[&str] = &["SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ABANDONED"];
const TOSS_CHOICES: &[&str]   = &["BAT", "BOWL"];

fn make_cricket_row(i: u64) -> JsonTRow {
    let match_uuid  = format!("550e8400-e29b-41d4-a716-{:012x}", i & 0xffff_ffff_ffff);
    let match_code  = format!("T20WC24-{:04}", i % 10_000);
    let prize_money = Decimal::new(500_000 + (i % 100_000) as i64, 2); // e.g. 5000.00

    JsonTRowBuilder::new()
        // ── Match identity (7 fields) ─────────────────────────────────────────
        .push(JsonTValue::str(&match_uuid))                             // uuid:  matchId
        .push(JsonTValue::u64(i))                                       // u64:   iccMatchSequenceId
        .push(JsonTValue::str(&match_code))                             // str:   matchCode [6..25]
        .push(JsonTValue::str("ICC Men T20 World Cup"))                 // str:   tournamentName [5..80]
        .push(JsonTValue::u16((i % 999 + 1) as u16))                   // u16:   matchNumber [1..999]
        .push(JsonTValue::null())                                       // str?:  groupName
        .push(JsonTValue::enum_val(PHASES[i as usize % PHASES.len()])) // enum:  phase
        // ── Team A (7 fields) ─────────────────────────────────────────────────
        .push(JsonTValue::str("IND"))                                   // str:   teamAId [2..10]
        .push(JsonTValue::str("India"))                                 // str:   teamAName [3..60]
        .push(JsonTValue::str("IND"))                                   // str:   teamAIccCode [3..3]
        .push(JsonTValue::u16((i % 100 + 1) as u16))                   // u16:   teamAWorldRanking [1..100]
        .push(JsonTValue::d64(850.0 + (i % 200) as f64 * 0.5))        // d64:   teamARatingPoints [0..]
        .push(JsonTValue::str("Rahul Dravid"))                          // str:   teamACoachName [3..80]
        .push(JsonTValue::null())                                       // email? teamAContactEmail
        // ── Team B (7 fields) ─────────────────────────────────────────────────
        .push(JsonTValue::str("ENG"))                                   // str:   teamBId [2..10]
        .push(JsonTValue::str("England"))                               // str:   teamBName [3..60]
        .push(JsonTValue::str("ENG"))                                   // str:   teamBIccCode [3..3]
        .push(JsonTValue::u16((i % 100 + 2) as u16))                   // u16:   teamBWorldRanking [1..100]
        .push(JsonTValue::d64(820.0 + (i % 180) as f64 * 0.5))        // d64:   teamBRatingPoints [0..]
        .push(JsonTValue::str("Matthew Mott"))                          // str:   teamBCoachName [3..80]
        .push(JsonTValue::null())                                       // email? teamBContactEmail
        // ── Venue (10 fields) ─────────────────────────────────────────────────
        .push(JsonTValue::str("MCG-01"))                                // str:   venueId [3..20]
        .push(JsonTValue::str("Melbourne Cricket Ground"))              // str:   venueName [3..100]
        .push(JsonTValue::str("Melbourne"))                             // str:   city [2..60]
        .push(JsonTValue::str("Australia"))                             // str:   country [2..60]
        .push(JsonTValue::i32(100_024))                                 // i32:   venueCapacity [1000..200000]
        .push(JsonTValue::bool(true))                                   // bool:  isFloodlit
        .push(JsonTValue::bool(false))                                  // bool:  isNeutralVenue
        .push(JsonTValue::d64(37.82))                                   // d64:   venueLatitude [..90]
        .push(JsonTValue::d64(144.98))                                  // d64:   venueLongitude [..180]
        .push(JsonTValue::enum_val(SURFACES[i as usize % SURFACES.len()])) // enum: wicketSurface
        .push(JsonTValue::null())                                       // hostname? statsApiHost
        // ── Schedule (4 fields) ───────────────────────────────────────────────
        .push(JsonTValue::str("2024-06-15T14:30:00"))                  // datetime: scheduledAt
        .push(JsonTValue::str("14:30:00"))                              // time:     matchStartLocalTime
        .push(JsonTValue::str("PT3H"))                                  // duration: expectedDuration
        .push(JsonTValue::bool(true))                                   // bool:     isDayNight
        // ── Toss (3 fields) ───────────────────────────────────────────────────
        .push(JsonTValue::str("IND"))                                   // str:  tossWinnerTeamId [2..10]
        .push(JsonTValue::enum_val(TOSS_CHOICES[i as usize % 2]))      // enum: tossDecision
        .push(JsonTValue::bool(true))                                   // bool: teamABattedFirst
        // ── Team A innings (9 fields) ─────────────────────────────────────────
        .push(JsonTValue::u32((150 + i % 100) as u32))                 // u32:  teamAInnings1Runs [0..300]
        .push(JsonTValue::u16((i % 11) as u16))                        // u16:  teamAInnings1Wickets [0..10]
        .push(JsonTValue::u16((100 + i % 20) as u16))                  // u16:  teamAInnings1Balls [0..120]
        .push(JsonTValue::d64(7.5 + (i % 50) as f64 * 0.1))           // d64:  teamAInnings1RunRate [0..]
        .push(JsonTValue::str("Virat Kohli"))                           // str:  teamATopBatsmanName [3..80]
        .push(JsonTValue::u32((50 + i % 100) as u32))                  // u32:  teamATopBatsmanRuns [0..200]
        .push(JsonTValue::str("Jasprit Bumrah"))                        // str:  teamATopBowlerName [3..80]
        .push(JsonTValue::u16((i % 5) as u16))                         // u16:  teamATopBowlerWickets [0..10]
        .push(JsonTValue::d32(6.5 + (i % 30) as f32 * 0.1))           // d32:  teamATopBowlerEconomy [0..]
        // ── Team B innings (9 fields) ─────────────────────────────────────────
        .push(JsonTValue::u32((145 + i % 100) as u32))                 // u32:  teamBInnings1Runs [0..300]
        .push(JsonTValue::u16((i % 11) as u16))                        // u16:  teamBInnings1Wickets [0..10]
        .push(JsonTValue::u16((98 + i % 20) as u16))                   // u16:  teamBInnings1Balls [0..120]
        .push(JsonTValue::d64(7.2 + (i % 50) as f64 * 0.1))           // d64:  teamBInnings1RunRate [0..]
        .push(JsonTValue::str("Joe Root"))                              // str:  teamBTopBatsmanName
        .push(JsonTValue::u32((45 + i % 100) as u32))                  // u32:  teamBTopBatsmanRuns [0..200]
        .push(JsonTValue::str("Jofra Archer"))                         // str:  teamBTopBowlerName
        .push(JsonTValue::u16((i % 5) as u16))                         // u16:  teamBTopBowlerWickets [0..10]
        .push(JsonTValue::d32(7.0 + (i % 30) as f32 * 0.1))           // d32:  teamBTopBowlerEconomy [0..]
        // ── Match outcome (12 fields) ─────────────────────────────────────────
        .push(JsonTValue::enum_val(MATCH_STATUSES[i as usize % MATCH_STATUSES.len()])) // enum: matchStatus
        .push(JsonTValue::null())                                       // str?:  winnerTeamId
        .push(JsonTValue::str("India won by 15 runs"))                  // str:   resultDescription [5..250]
        .push(JsonTValue::i32((i % 200) as i32))                       // i32:   winMarginRuns [0..200]
        .push(JsonTValue::i16((i % 11) as i16))                        // i16:   winMarginWickets [0..10]
        .push(JsonTValue::bool(false))                                  // bool:  isSuperOver
        .push(JsonTValue::d64(0.5 + (i % 100) as f64 * 0.01))         // d64:   netRunRateDiffTeamA
        .push(JsonTValue::d64(0.3 + (i % 80) as f64 * 0.01))          // d64:   netRunRateDiffTeamB
        .push(JsonTValue::i16((i % 3) as i16))                         // i16:   teamAGroupPoints [0..2]
        .push(JsonTValue::i16((i % 3) as i16))                         // i16:   teamBGroupPoints [0..2]
        .push(JsonTValue::d128(prize_money))                            // d128:  prizeMoneySplit [0..]
        .push(JsonTValue::i64((10_800 + i % 3_600) as i64))            // i64:   matchDurationSeconds [0..]
        // ── Player of the match (3 optional) ─────────────────────────────────
        .push(JsonTValue::null())                                       // uuid?: manOfTheMatchId
        .push(JsonTValue::null())                                       // str?:  manOfTheMatchName
        .push(JsonTValue::null())                                       // str?:  manOfTheMatchTeamId
        // ── Officials (4 fields) ──────────────────────────────────────────────
        .push(JsonTValue::str("Aleem Dar"))                             // str:  umpire1Name [3..80]
        .push(JsonTValue::str("Kumar Dharmasena"))                      // str:  umpire2Name [3..80]
        .push(JsonTValue::null())                                       // str?: tvUmpireName
        .push(JsonTValue::str("Ranjan Madugalle"))                      // str:  matchRefereeName [3..80]
        // ── Weather (5 fields) ────────────────────────────────────────────────
        .push(JsonTValue::bool(false))                                  // bool:  isRainAffected
        .push(JsonTValue::null())                                       // i16?:  dlsOversReduction
        .push(JsonTValue::null())                                       // d64?:  dlsTargetRevised
        .push(JsonTValue::null())                                       // d32?:  avgTemperatureCelsius
        .push(JsonTValue::u32((50_000 + i % 50_000) as u32))           // u32?:  attendanceCount
        // ── Broadcast & metadata (12 fields) ─────────────────────────────────
        .push(JsonTValue::bool(true))                                   // bool:  isHighlightsAvailable
        .push(JsonTValue::null())                                       // i64?:  totalViewership
        .push(JsonTValue::str("en,hi,ta"))                              // str:   broadcastLanguages [2..200]
        .push(JsonTValue::null())                                       // uri?:  dataSourceUrl
        .push(JsonTValue::null())                                       // hex?:  matchContentHash
        .push(JsonTValue::null())                                       // base64? matchCertificate
        .push(JsonTValue::null())                                       // ipv4?: statsServerIpv4
        .push(JsonTValue::null())                                       // ipv6?: broadcastServerIpv6
        .push(JsonTValue::str("ICC Official API"))                      // str:   dataSource [1..100]
        .push(JsonTValue::i64(1_718_460_600_000 + i as i64))           // timestamp: createdAt
        .push(JsonTValue::i64(1_718_460_600_000 + i as i64 + 3_600_000)) // timestamp: updatedAt
        .build()
}

// =============================================================================
// Formatting helpers
// =============================================================================

fn fmt_bytes(n: usize) -> String {
    if n >= 1 << 30 {
        format!("{:.2} GB", n as f64 / (1u64 << 30) as f64)
    } else if n >= 1 << 20 {
        format!("{:.2} MB", n as f64 / (1u64 << 20) as f64)
    } else if n >= 1 << 10 {
        format!("{:.2} KB", n as f64 / (1u64 << 10) as f64)
    } else {
        format!("{} B", n)
    }
}

fn fmt_duration(d: Duration) -> String {
    let ms = d.as_secs_f64() * 1_000.0;
    if ms >= 1_000.0 { format!("{:.2}s", ms / 1_000.0) } else { format!("{:.2}ms", ms) }
}

fn fmt_count(n: usize) -> String {
    if n >= 1_000_000 {
        format!("{}M", n / 1_000_000)
    } else if n >= 1_000 {
        format!("{}K", n / 1_000)
    } else {
        n.to_string()
    }
}

// =============================================================================
// NullSink — discards all events, keeps sink overhead out of timing
// =============================================================================

struct NullSink;

impl DiagnosticSink for NullSink {
    fn emit(&mut self, _: DiagnosticEvent) {}
    fn flush(&mut self) -> Result<(), SinkError> { Ok(()) }
}

// =============================================================================
// Step helpers
// =============================================================================

struct StringifyResult {
    path: std::path::PathBuf,
    build_time: Duration,
    stringify_time: Duration,
    file_bytes: usize,
}

/// Build and write each row immediately — O(1) memory, no Vec accumulation.
/// Build vs stringify time is measured per-row with nanosecond accumulators
/// (~40 ns overhead/row, <1% for any count that takes over 1 s total).
fn step_stringify(count: usize) -> StringifyResult {
    let path = std::env::temp_dir()
        .join(format!("jsont_wct_bench_{}.jsont", count));
    let file = File::create(&path).expect("cannot create temp file");
    let mut w = BufWriter::new(file);
    let last = count.saturating_sub(1);

    let (mut build_ns, mut stringify_ns) = (0u64, 0u64);
    for i in 0u64..count as u64 {
        let tb = Instant::now();
        let row = make_cricket_row(i);
        build_ns += tb.elapsed().as_nanos() as u64;

        let ts = Instant::now();
        write_row(&row, &mut w).expect("write failed");
        if (i as usize) < last { w.write_all(b",\n").expect("write failed"); }
        stringify_ns += ts.elapsed().as_nanos() as u64;
    }
    w.flush().expect("flush failed");

    let file_bytes = fs::metadata(&path).expect("metadata failed").len() as usize;
    StringifyResult {
        path,
        build_time:     Duration::from_nanos(build_ns),
        stringify_time: Duration::from_nanos(stringify_ns),
        file_bytes,
    }
}

struct ParseResult {
    row_count: usize,
    parse_time: Duration,
    throughput_rms: f64,
    loaded_bytes: usize,
    peak_heap: usize,
}

/// Stream-parse rows from file — O(1) memory, one row live at a time.
fn step_parse(path: &std::path::Path) -> ParseResult {
    let baseline = reset_peak();
    let loaded_bytes = fs::metadata(path).expect("metadata failed").len() as usize;

    let file = File::open(path).expect("cannot open temp file");
    let reader = BufReader::new(file);

    let t0 = Instant::now();
    let mut count = 0usize;
    parse_rows_streaming(reader, |_row| count += 1).expect("parse failed");
    let parse_time = t0.elapsed();

    let peak_heap = peak_above(baseline);
    let ms = parse_time.as_millis().max(1) as f64;
    ParseResult {
        row_count: count,
        parse_time,
        throughput_rms: count as f64 / ms,
        loaded_bytes,
        peak_heap,
    }
}

struct ValidateResult {
    row_count: usize,
    elapsed: Duration,
    throughput_rms: f64,
    peak_heap: usize,
    clean: usize,
}

/// Stream-parse then validate — RowIter feeds directly into validate_each, O(1) memory.
fn step_parse_validate(path: &std::path::Path, cricket_schema: jsont::JsonTSchema) -> ValidateResult {
    let pipeline = ValidationPipeline::builder(cricket_schema)
        .without_console()
        .with_sink(Box::new(NullSink))
        .build()
        .unwrap();

    let file = File::open(path).expect("cannot open temp file");
    let iter = RowIter::new(BufReader::new(file));

    let baseline = reset_peak();
    let mut clean = 0usize;
    let t0 = Instant::now();
    pipeline.validate_each(iter, |_| clean += 1);
    let elapsed = t0.elapsed();
    pipeline.finish().unwrap();

    let peak_heap = peak_above(baseline);
    let ms = elapsed.as_millis().max(1) as f64;
    // All benchmark rows are valid → clean == total
    ValidateResult {
        row_count: clean,
        elapsed,
        throughput_rms: clean as f64 / ms,
        peak_heap,
        clean,
    }
}

struct TransformResult {
    row_count: usize,
    elapsed: Duration,
    throughput_rms: f64,
    peak_heap: usize,
    transformed: usize,
}

/// Stream-parse, validate, then transform each clean row — O(1) memory end-to-end.
fn step_parse_validate_transform(
    path: &std::path::Path,
    cricket_schema: jsont::JsonTSchema,
    broadcast_schema: jsont::JsonTSchema,
    registry: SchemaRegistry,
) -> TransformResult {
    let pipeline = ValidationPipeline::builder(cricket_schema)
        .without_console()
        .with_sink(Box::new(NullSink))
        .build()
        .unwrap();

    let file = File::open(path).expect("cannot open temp file");
    let iter = RowIter::new(BufReader::new(file));

    let baseline = reset_peak();
    let mut transformed = 0usize;
    let t0 = Instant::now();
    pipeline.validate_each(iter, |clean_row| {
        let _ = broadcast_schema.transform(clean_row, &registry)
            .expect("transform failed");
        transformed += 1;
    });
    let elapsed = t0.elapsed();
    pipeline.finish().unwrap();

    let peak_heap = peak_above(baseline);
    let ms = elapsed.as_millis().max(1) as f64;
    TransformResult {
        row_count: transformed,
        elapsed,
        throughput_rms: transformed as f64 / ms,
        peak_heap,
        transformed,
    }
}

/// Build a flat straight schema for JSON reading.
///
/// `JsonWriter` writes values by their runtime `JsonTValue` variant, not their schema type.
/// The CricketMatch benchmark rows store temporal fields (datetime, time, duration) as
/// `JsonTValue::Str(...)`, so `JsonWriter` emits them as JSON strings.  `JsonReader` then
/// tries to coerce those strings using the *schema* type — and `datetime`/`time` only accept
/// JSON integers, causing a type error.
///
/// Fix: map every field to a JSON-compatible reading type:
/// - `Bool`          → `Bool`   (JSON true/false)
/// - Pure numeric    → keep     (JSON numbers read back as the same numeric type)
/// - Everything else → `Str`    (`JsonReader::coerce_scalar(Str)` accepts JSON strings *and*
///                               JSON integers, so integer-valued timestamps also work)
/// - Object (enum)   → `Str`    (enum values are written as JSON strings)
fn flat_schema_for_json(schema: &jsont::JsonTSchema) -> jsont::JsonTSchema {
    use jsont::{JsonTFieldBuilder, JsonTSchemaBuilder, JsonTFieldKind, ScalarType, SchemaKind};

    let fields = match &schema.kind {
        SchemaKind::Straight { fields } => fields,
        _ => panic!("flat_schema_for_json requires a straight schema"),
    };

    let mut builder = JsonTSchemaBuilder::straight(&schema.name);
    for field in fields {
        let (scalar, optional) = match &field.kind {
            JsonTFieldKind::Scalar { field_type, optional, .. } => {
                let s = match field_type.scalar {
                    ScalarType::Bool => ScalarType::Bool,
                    ScalarType::I16 | ScalarType::I32 | ScalarType::I64
                    | ScalarType::U16 | ScalarType::U32 | ScalarType::U64
                    | ScalarType::D32 | ScalarType::D64 | ScalarType::D128 => field_type.scalar,
                    // All string-semantic and temporal types → Str.
                    // coerce_scalar(Str) accepts JSON strings *and* JSON integers,
                    // so integer-stored timestamps round-trip correctly too.
                    _ => ScalarType::Str,
                };
                (s, *optional)
            }
            JsonTFieldKind::Object { optional, .. } => (ScalarType::Str, *optional),
            JsonTFieldKind::AnyOf { optional, .. } => (ScalarType::Str, *optional),
        };
        let mut fb = JsonTFieldBuilder::scalar(&field.name, scalar);
        if optional { fb = fb.optional(); }
        builder = builder.field_from(fb).unwrap();
    }
    builder.build().unwrap()
}

struct JsonStringifyResult {
    path: std::path::PathBuf,
    write_time: Duration,
    file_bytes: usize,
}

/// Write each row as JSON NDJSON to a temp file.
/// Row build time is not measured (same cost as JsonT — comparison is write-only).
fn step_stringify_json(count: usize, schema: &jsont::JsonTSchema) -> JsonStringifyResult {
    let path = std::env::temp_dir()
        .join(format!("jsont_wct_bench_{}.json", count));
    let file = File::create(&path).expect("cannot create temp file");
    let mut w = BufWriter::new(file);

    let writer = JsonWriter::with_schema(schema.clone())
        .mode(JsonOutputMode::Ndjson)
        .build();

    let mut write_ns = 0u64;
    for i in 0u64..count as u64 {
        let row = make_cricket_row(i);
        let ts = Instant::now();
        writer.write_row(&row, &mut w).expect("json write failed");
        w.write_all(b"\n").expect("write failed");
        write_ns += ts.elapsed().as_nanos() as u64;
    }
    w.flush().expect("flush failed");

    let file_bytes = fs::metadata(&path).expect("metadata failed").len() as usize;
    JsonStringifyResult {
        path,
        write_time: Duration::from_nanos(write_ns),
        file_bytes,
    }
}

/// Stream-parse JSON NDJSON rows from file — O(1) memory, one row live at a time.
/// Uses a flat schema (enum Object fields mapped to Str) so `JsonReader` can
/// handle all 92 fields without requiring schema resolution.
fn step_parse_json(path: &std::path::Path, schema: &jsont::JsonTSchema) -> ParseResult {
    let baseline = reset_peak();
    let loaded_bytes = fs::metadata(path).expect("metadata failed").len() as usize;

    let file = File::open(path).expect("cannot open temp file");
    let reader_buf = BufReader::new(file);

    let flat = flat_schema_for_json(schema);
    let json_reader = JsonReader::with_schema(flat)
        .mode(JsonInputMode::Ndjson)
        .build();

    let t0 = Instant::now();
    let mut count = 0usize;
    json_reader.read_streaming(reader_buf, |_row| count += 1)
        .expect("json parse failed");
    let parse_time = t0.elapsed();

    let peak_heap = peak_above(baseline);
    let ms = parse_time.as_millis().max(1) as f64;
    ParseResult {
        row_count: count,
        parse_time,
        throughput_rms: count as f64 / ms,
        loaded_bytes,
        peak_heap,
    }
}

// =============================================================================
// Core benchmark runner — executes all 4 options for a given count
// =============================================================================

fn run_wct_benchmark(count: usize, with_json: bool) {
    let label = fmt_count(count);
    println!("  Schema : CricketMatch (92 fields, ~1 KB/row) | MatchBroadcastSummary (derived)");
    println!("  Workers: single-threaded (Rust sequential validation)");

    // ── Load schemas once per run ─────────────────────────────────────────────
    let (cricket, broadcast, registry) = load_schemas();

    // ── [1] Stringify (JsonT) ─────────────────────────────────────────────────
    let sr = step_stringify(count);
    println!(
        "  [1] Stringify (JsonT) {:>6} rows | build {:>10} | write {:>10} | file {:>12}",
        label,
        fmt_duration(sr.build_time),
        fmt_duration(sr.stringify_time),
        fmt_bytes(sr.file_bytes),
    );

    // ── [2] Stringify (JSON) — optional ──────────────────────────────────────
    let json_sr: Option<JsonStringifyResult> = if with_json {
        let r = step_stringify_json(count, &cricket);
        let size_pct = ((r.file_bytes as f64 / sr.file_bytes as f64) - 1.0) * 100.0;
        println!(
            "  [2] Stringify (JSON ) {:>6} rows |              | write {:>10} | file {:>12} | {:+.1}% vs JsonT",
            label,
            fmt_duration(r.write_time),
            fmt_bytes(r.file_bytes),
            size_pct,
        );
        Some(r)
    } else {
        None
    };

    // ── [3] Parse (JsonT) — step number shifts if JSON included ──────────────
    let jsont_parse_step = if with_json { 3 } else { 2 };
    let pr = step_parse(&sr.path);
    println!(
        "  [{}] Parse     (JsonT) {:>6} rows | time  {:>10} | {:>8.1} rows/ms | loaded {:>12} | peak {:>10}",
        jsont_parse_step,
        label,
        fmt_duration(pr.parse_time),
        pr.throughput_rms,
        fmt_bytes(pr.loaded_bytes),
        fmt_bytes(pr.peak_heap),
    );

    // ── [4] Parse (JSON) — optional ──────────────────────────────────────────
    if let Some(ref jsr) = json_sr {
        let jpr = step_parse_json(&jsr.path, &cricket);
        println!(
            "  [4] Parse     (JSON ) {:>6} rows | time  {:>10} | {:>8.1} rows/ms | loaded {:>12} | peak {:>10}",
            label,
            fmt_duration(jpr.parse_time),
            jpr.throughput_rms,
            fmt_bytes(jpr.loaded_bytes),
            fmt_bytes(jpr.peak_heap),
        );
        let _ = fs::remove_file(&jsr.path);
    }

    // ── Parse + Validate ──────────────────────────────────────────────────────
    let step_val = if with_json { 5 } else { 3 };
    let vr = step_parse_validate(&sr.path, cricket.clone());
    println!(
        "  [{}] Parse+Val        {:>6} rows | time  {:>10} | {:>8.1} rows/ms | clean {:>6} | peak {:>10}",
        step_val,
        label,
        fmt_duration(vr.elapsed),
        vr.throughput_rms,
        fmt_count(vr.clean),
        fmt_bytes(vr.peak_heap),
    );

    // ── Parse + Validate + Transform ──────────────────────────────────────────
    let step_xfm = if with_json { 6 } else { 4 };
    let tr = step_parse_validate_transform(&sr.path, cricket, broadcast, registry);
    println!(
        "  [{}] Parse+Val+T      {:>6} rows | time  {:>10} | {:>8.1} rows/ms | xfrmd {:>6} | peak {:>10}",
        step_xfm,
        label,
        fmt_duration(tr.elapsed),
        tr.throughput_rms,
        fmt_count(tr.transformed),
        fmt_bytes(tr.peak_heap),
    );

    let _ = fs::remove_file(&sr.path);
}

// =============================================================================
// Individual test entry points — each size runs independently
// =============================================================================

#[test]
fn bench_wct_1k() {
    println!("\n=== WCT Benchmark  1K records ===");
    run_wct_benchmark(1_000, true);
}

#[test]
fn bench_wct_10k() {
    println!("\n=== WCT Benchmark  10K records ===");
    run_wct_benchmark(10_000, true);
}

#[test]
fn bench_wct_100k() {
    println!("\n=== WCT Benchmark  100K records ===");
    run_wct_benchmark(100_000, true);
}

#[test]
fn bench_wct_1m() {
    println!("\n=== WCT Benchmark  1M records ===");
    run_wct_benchmark(1_000_000, true);
}

/// Pipeline is fully streaming — row memory is O(1).
/// Main cost is uniqueness-constraint HashSets (~640 MB per unique field at 10M rows)
/// and the ~90s stringify time. Omit from regular runs.
/// JSON steps are excluded at 10M — use 1M for format comparison.
#[test]
fn bench_wct_10m() {
    println!("\n=== WCT Benchmark  10M records ===");
    run_wct_benchmark(10_000_000, false);
}
