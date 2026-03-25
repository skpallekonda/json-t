// =============================================================================
// tests/benchmark_tests.rs
// =============================================================================
// Performance benchmarks across four dataset sizes:
//   1_000 / 100_000 / 1_000_000 / 10_000_000 records
//
// Schema (Order — 5 fields: i64 id, str product, i32 quantity, d64 price,
//                             enum status):
//   Each row is ~37 bytes compact on disk.
//
// Step 1 measures:
//   (a) Build time   — constructing N JsonTRow values in memory
//   (b) Stringify time — serialising each row to compact text and writing
//                        to a temp file on disk
//
// Step 2 measures (on the files produced by Step 1):
//   - Parse time (ms)
//   - Throughput (records / ms)
//   - Total memory loaded (file bytes read into String)
//   - Peak heap allocation during load + parse (via TrackingAllocator)
//
// Run all sizes:
//   cargo test bench_ -- --nocapture
//
// Run individual sizes:
//   cargo test bench_1k   -- --nocapture
//   cargo test bench_100k -- --nocapture
//   cargo test bench_1m   -- --nocapture
//   cargo test bench_10m  -- --nocapture   ← needs ~4 GB RAM, several minutes
// =============================================================================

use std::alloc::{GlobalAlloc, Layout, System};
use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{Duration, Instant};

use jsont::{JsonTRow, JsonTRowBuilder, JsonTValue, Parseable, write_row};

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
            // CAS loop: update peak if now exceeds it
            let mut peak = PEAK_BYTES.load(Ordering::Relaxed);
            while now > peak {
                match PEAK_BYTES.compare_exchange_weak(
                    peak,
                    now,
                    Ordering::Relaxed,
                    Ordering::Relaxed,
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

/// Anchor the peak counter at the current live-byte level and return it as a
/// baseline. Call `peak_above(baseline)` after the measured section to get the
/// maximum incremental heap used during that section.
fn reset_peak() -> usize {
    let current = LIVE_BYTES.load(Ordering::Relaxed);
    PEAK_BYTES.store(current, Ordering::Relaxed);
    current
}

/// Returns the highest LIVE_BYTES value observed since the last `reset_peak`,
/// expressed relative to the baseline returned by that call.
fn peak_above(baseline: usize) -> usize {
    PEAK_BYTES.load(Ordering::Relaxed).saturating_sub(baseline)
}

// =============================================================================
// Data generation
// Order schema: { i64: id, str: product, i32: quantity, d64: price, status }
// status is an enum constant (CONSTID — all-uppercase, 2+ chars).
// =============================================================================

const PRODUCTS: &[&str] = &["Widget", "Gadget", "Doohickey", "Thingamajig", "Whatsit"];
// CONSTID rule: [A-Z][A-Z0-9_]+  (all uppercase, 2+ total chars)
const STATUSES: &[&str] = &["PENDING", "SHIPPED", "DELIVERED", "CANCELLED"];

fn make_row(i: u64) -> JsonTRow {
    let product  = PRODUCTS[(i as usize) % PRODUCTS.len()];
    let quantity = ((i % 99) + 1) as i32;
    let price    = 9.99 + (i % 491) as f64 * 0.01; // stays positive, varies enough
    let status   = STATUSES[(i as usize) % STATUSES.len()];

    JsonTRowBuilder::new()
        .push(JsonTValue::i64(i as i64))
        .push(JsonTValue::str(product))
        .push(JsonTValue::i32(quantity))
        .push(JsonTValue::d64(price))
        .push(JsonTValue::enum_val(status))
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
    if ms >= 1_000.0 {
        format!("{:.2}s", ms / 1_000.0)
    } else {
        format!("{:.2}ms", ms)
    }
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
// Core benchmark logic
// =============================================================================

struct BuildResult {
    path:           std::path::PathBuf,
    build_time:     Duration,
    stringify_time: Duration,
    file_bytes:     usize,
}

/// Step 1 — build `count` rows in memory, then stringify+write to a temp file.
fn step1_build_and_stringify(count: usize) -> BuildResult {
    // ── (a) Build ─────────────────────────────────────────────────────────────
    let t0 = Instant::now();
    let rows: Vec<JsonTRow> = (0..count as u64).map(make_row).collect();
    let build_time = t0.elapsed();

    // ── (b) Stringify + write ─────────────────────────────────────────────────
    let path = std::env::temp_dir().join(format!("jsont_bench_{}.jsont", count));
    {
        let file   = File::create(&path).expect("cannot create temp file");
        let mut w  = BufWriter::new(file);
        let last   = rows.len().saturating_sub(1);
        let t1 = Instant::now();
        for (i, row) in rows.iter().enumerate() {
            write_row(row, &mut w).expect("write failed");
            if i < last {
                w.write_all(b",\n").expect("write failed");
            }
        }
        w.flush().expect("flush failed");
        let stringify_time = t1.elapsed();
        let file_bytes = fs::metadata(&path)
            .expect("metadata failed")
            .len() as usize;

        return BuildResult { path, build_time, stringify_time, file_bytes };
    }
}

struct ParseResult {
    row_count:       usize,
    parse_time:      Duration,
    throughput_rps:  f64, // records per millisecond
    loaded_bytes:    usize,
    peak_heap_delta: usize,
}

/// Step 2 — load file into a String, parse into `Vec<JsonTRow>`, measure memory.
fn step2_parse(path: &std::path::Path) -> ParseResult {
    // Reset the peak counter so we capture only this operation's footprint.
    let baseline = reset_peak();

    let input        = fs::read_to_string(path).expect("cannot read temp file");
    let loaded_bytes = input.len();

    let t0     = Instant::now();
    let rows   = Vec::<JsonTRow>::parse(&input).expect("parse failed");
    let parse_time = t0.elapsed();

    let peak_heap_delta = peak_above(baseline);

    let ms = parse_time.as_millis().max(1) as f64;
    let throughput_rps = rows.len() as f64 / ms;

    ParseResult {
        row_count: rows.len(),
        parse_time,
        throughput_rps,
        loaded_bytes,
        peak_heap_delta,
    }
}

/// Run both steps for `count` records, print results, clean up the temp file.
fn run_benchmark(count: usize) {
    let br = step1_build_and_stringify(count);

    println!(
        "  build     {:>5} records  build {:>10}  stringify {:>10}  file {:>10}",
        fmt_count(count),
        fmt_duration(br.build_time),
        fmt_duration(br.stringify_time),
        fmt_bytes(br.file_bytes),
    );

    let pr = step2_parse(&br.path);
    assert_eq!(pr.row_count, count, "parsed row count mismatch");

    println!(
        "  parse     {:>5} records  time  {:>10}  throughput {:>9.1} rec/ms  loaded {:>10}  peak {:>10}",
        fmt_count(pr.row_count),
        fmt_duration(pr.parse_time),
        pr.throughput_rps,
        fmt_bytes(pr.loaded_bytes),
        fmt_bytes(pr.peak_heap_delta),
    );

    let _ = fs::remove_file(&br.path);
}

// =============================================================================
// Individual test entry points (so each size can be run independently)
// =============================================================================

#[test]
fn bench_1k() {
    println!("\n=== JsonT Benchmark  1K records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, enum:status }}");
    run_benchmark(1_000);
}

#[test]
fn bench_100k() {
    println!("\n=== JsonT Benchmark  100K records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, enum:status }}");
    run_benchmark(100_000);
}

#[test]
fn bench_1m() {
    println!("\n=== JsonT Benchmark  1M records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, enum:status }}");
    run_benchmark(1_000_000);
}

/// 10 M records requires ~4 GB RAM and several minutes to run.
#[test]
fn bench_10m() {
    println!("\n=== JsonT Benchmark  10M records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, enum:status }}");
    run_benchmark(10_000_000);
}
