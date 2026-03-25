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

use jsont::{
    model::schema::FieldPath, model::validation::JsonTValidationBlock, DiagnosticEvent,
    DiagnosticSink, JsonTExpression, JsonTFieldBuilder, JsonTRule, JsonTSchemaBuilder, ScalarType,
    SinkError, ValidationPipeline,
};
use jsont::{parse_rows_streaming, RowIter};
use jsont::{write_row, JsonTArray, JsonTRow, JsonTRowBuilder, JsonTValue, Parseable};

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
    let product = PRODUCTS[(i as usize) % PRODUCTS.len()];
    let quantity = ((i % 99) + 1) as i32;
    let price = 9.99 + (i % 491) as f64 * 0.01; // stays positive, varies enough
    let status = STATUSES[(i as usize) % STATUSES.len()];

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
    path: std::path::PathBuf,
    build_time: Duration,
    stringify_time: Duration,
    file_bytes: usize,
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
        let file = File::create(&path).expect("cannot create temp file");
        let mut w = BufWriter::new(file);
        let last = rows.len().saturating_sub(1);
        let t1 = Instant::now();
        for (i, row) in rows.iter().enumerate() {
            write_row(row, &mut w).expect("write failed");
            if i < last {
                w.write_all(b",\n").expect("write failed");
            }
        }
        w.flush().expect("flush failed");
        let stringify_time = t1.elapsed();
        let file_bytes = fs::metadata(&path).expect("metadata failed").len() as usize;

        return BuildResult {
            path,
            build_time,
            stringify_time,
            file_bytes,
        };
    }
}

struct ParseResult {
    row_count: usize,
    parse_time: Duration,
    throughput_rps: f64, // records per millisecond
    loaded_bytes: usize,
    peak_heap_delta: usize,
}

/// Step 2 — load file into a String, parse into `Vec<JsonTRow>`, measure memory.
fn step2_parse(path: &std::path::Path) -> ParseResult {
    // Reset the peak counter so we capture only this operation's footprint.
    let baseline = reset_peak();

    let input = fs::read_to_string(path).expect("cannot read temp file");
    let loaded_bytes = input.len();

    let t0 = Instant::now();
    let rows = Vec::<JsonTRow>::parse(&input).expect("parse failed");
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

// =============================================================================
// Validation benchmarks
// =============================================================================
//
// Measures validation pipeline throughput and peak heap across four variants:
//
//   (a) Field constraints only, buffered   — validate_rows() → Vec<JsonTRow>
//   (b) Field constraints only, streaming  — validate_each() with null callback
//   (c) Field constraints + uniqueness on id, buffered
//   (d) Field constraints + uniqueness on id, streaming
//
// Constraints applied (all benchmark rows satisfy them — no rejections):
//   product  : minLength = 2
//   quantity : minValue = 1.0, maxValue = 99.0
//   price    : minValue = 0.01
//
// Uniqueness on id is trivially satisfied because make_row(i) uses i as the id.
//
// Run:
//   cargo test bench_validate_ -- --nocapture
// =============================================================================

// ── NullSink — discards all events, eliminates sink I/O from timing ──────────

struct NullSink;

impl DiagnosticSink for NullSink {
    fn emit(&mut self, _: DiagnosticEvent) {}
    fn flush(&mut self) -> Result<(), SinkError> {
        Ok(())
    }
}

// ── Validation schemas ────────────────────────────────────────────────────────

/// Same 5-field Order schema with per-row field constraints (80 % case).
/// No uniqueness or dataset rules — pure O(1) streaming path.
fn order_schema_constrained() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Order")
        .field(
            JsonTFieldBuilder::scalar("id", ScalarType::I64)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("product", ScalarType::Str)
                .min_length(2)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("quantity", ScalarType::I32)
                .min_value(1.0)
                .max_value(99.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("price", ScalarType::D64)
                .min_value(0.01)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("status", ScalarType::Str)
                .build()
                .unwrap(),
        )
        .unwrap()
        .build()
        .unwrap()
}

/// Same schema plus a uniqueness constraint on `id`.
/// The uniqueness HashSet accumulates O(N) key strings — illustrates the
/// memory cost of dataset-level constraints vs the O(1) streaming case.
fn order_schema_with_unique() -> jsont::JsonTSchema {
    let validation = JsonTValidationBlock {
        rules: vec![],
        unique: vec![vec![FieldPath::single("id")]],
        dataset: vec![],
    };
    JsonTSchemaBuilder::straight("Order")
        .field(
            JsonTFieldBuilder::scalar("id", ScalarType::I64)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("product", ScalarType::Str)
                .min_length(2)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("quantity", ScalarType::I32)
                .min_value(1.0)
                .max_value(99.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("price", ScalarType::D64)
                .min_value(0.01)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("status", ScalarType::Str)
                .build()
                .unwrap(),
        )
        .unwrap()
        .validation(validation)
        .build()
        .unwrap()
}

// ── Result type ───────────────────────────────────────────────────────────────

struct ValidateResult {
    total: usize,
    clean: usize,
    validate_time: Duration,
    throughput_rms: f64, // rows per millisecond
    peak_heap: usize,
}

// ── validate_rows — buffered, collects clean rows into Vec ────────────────────

fn do_validate_buffered(rows: Vec<JsonTRow>, schema: jsont::JsonTSchema) -> ValidateResult {
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(NullSink))
        .build();

    let total = rows.len();
    let baseline = reset_peak();

    let t0 = Instant::now();
    let clean = pipeline.validate_rows(rows);
    let elapsed = t0.elapsed();

    pipeline.finish().unwrap();

    let peak = peak_above(baseline);
    let ms = (elapsed.as_secs_f64() * 1_000.0).max(0.001);
    ValidateResult {
        total,
        clean: clean.len(),
        validate_time: elapsed,
        throughput_rms: total as f64 / ms,
        peak_heap: peak,
    }
}

// ── validate_each — streaming, zero output buffering ─────────────────────────

fn do_validate_streaming(rows: Vec<JsonTRow>, schema: jsont::JsonTSchema) -> ValidateResult {
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(NullSink))
        .build();

    let total = rows.len();
    let baseline = reset_peak();
    let mut clean_count = 0usize;

    let t0 = Instant::now();
    pipeline.validate_each(rows, |_| clean_count += 1);
    let elapsed = t0.elapsed();

    pipeline.finish().unwrap();

    let peak = peak_above(baseline);
    let ms = (elapsed.as_secs_f64() * 1_000.0).max(0.001);
    ValidateResult {
        total,
        clean: clean_count,
        validate_time: elapsed,
        throughput_rms: total as f64 / ms,
        peak_heap: peak,
    }
}

// ── Print helper ──────────────────────────────────────────────────────────────

fn print_vr(label: &str, vr: &ValidateResult) {
    println!(
        "  validate  {:>5} records  [{:<33}]  time {:>10}  {:>9.1} rec/ms  clean {:>5}  peak {:>10}",
        fmt_count(vr.total),
        label,
        fmt_duration(vr.validate_time),
        vr.throughput_rms,
        fmt_count(vr.clean),
        fmt_bytes(vr.peak_heap),
    );
}

// ── Core validation benchmark logic ──────────────────────────────────────────

fn run_validation_benchmark(count: usize) {
    // (a) Field constraints only — buffered output (validate_rows)
    //     Peak heap includes the output Vec<JsonTRow> for all clean rows.
    {
        let rows: Vec<JsonTRow> = (0..count as u64).map(make_row).collect();
        let vr = do_validate_buffered(rows, order_schema_constrained());
        print_vr("constraints, buffered", &vr);
    }

    // (b) Field constraints only — streaming (validate_each, null callback)
    //     Output is not buffered; peak heap reflects only per-row working memory.
    {
        let rows: Vec<JsonTRow> = (0..count as u64).map(make_row).collect();
        let vr = do_validate_streaming(rows, order_schema_constrained());
        print_vr("constraints, streaming", &vr);
    }

    // (c) Field constraints + uniqueness on id — buffered
    //     Peak heap = output Vec + uniqueness HashSet (O(N) key strings).
    {
        let rows: Vec<JsonTRow> = (0..count as u64).map(make_row).collect();
        let vr = do_validate_buffered(rows, order_schema_with_unique());
        print_vr("constraints+unique, buffered", &vr);
    }

    // (d) Field constraints + uniqueness on id — streaming
    //     No output Vec; only the uniqueness HashSet grows with N.
    //     Directly shows the O(unique-keys) cost of uniqueness checking.
    {
        let rows: Vec<JsonTRow> = (0..count as u64).map(make_row).collect();
        let vr = do_validate_streaming(rows, order_schema_with_unique());
        print_vr("constraints+unique, streaming", &vr);
    }
}

// ── Test entry points ─────────────────────────────────────────────────────────

#[test]
fn bench_validate_1k() {
    println!("\n=== JsonT Validation Benchmark  1K records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, str:status }}");
    println!("  Constraints: product minLength(2), quantity [1..99], price >= 0.01");
    println!("  All benchmark rows satisfy all constraints — no rejections.");
    run_validation_benchmark(1_000);
}

#[test]
fn bench_validate_100k() {
    println!("\n=== JsonT Validation Benchmark  100K records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, str:status }}");
    println!("  Constraints: product minLength(2), quantity [1..99], price >= 0.01");
    println!("  All benchmark rows satisfy all constraints — no rejections.");
    run_validation_benchmark(100_000);
}

#[test]
fn bench_validate_1m() {
    println!("\n=== JsonT Validation Benchmark  1M records ===");
    println!("  Schema: Order {{ i64:id, str:product, i32:quantity, d64:price, str:status }}");
    println!("  Constraints: product minLength(2), quantity [1..99], price >= 0.01");
    println!("  All benchmark rows satisfy all constraints — no rejections.");
    run_validation_benchmark(1_000_000);
}

// =============================================================================
// Marketplace Order benchmarks (full nested schema)
// =============================================================================
//
// Schema: Order (17 fields, nested objects and arrays):
//   u64:orderId, str:orderNumber, date:orderDate, <OrderStatus>:orderStatus,
//   <Customer>:customer (u64,str,str,str,str,<Address>,<Address>?),
//   <OrderLineItem>[]:lineItems (u64,str,str,u32,d64,d64?,d64,d64,<Category>),
//   <Payment>:payment (enum,str,date,d64,str,<CardDetails>?),
//   <Shipping>:shipping (str,str,date,date?,d64,enum),
//   d64×5 (subtotal,totalTax,totalDiscount,shippingCost,grandTotal),
//   date×2 (createdAt,updatedAt), str?×2 (createdBy,lastModifiedBy)
//
// Each row has 1–3 line items; typical compact size: ~450–650 bytes/row.
// This mirrors the marketplace schema used by the Java benchmark (~1 KB/row JSON).
//
// Validation:
//   Field constraints: orderNumber minLength(1), subtotal/tax/discount/
//     shippingCost/grandTotal >= 0 (min_value)
//   Row rules: grandTotal > 0, subtotal >= 0, totalTax >= 0, shippingCost >= 0
//   All generated rows satisfy every constraint — no rejections expected.
//
// Three timed steps per run:
//   Step 1 — Build N rows in memory + stringify+write to temp file
//   Step 2 — Load file → parse into Vec<JsonTRow> (peak heap measured)
//   Step 3 — Validate against Order schema (streaming, NullSink)
//
// Run selected sizes:
//   cargo test bench_orders_1k   -- --nocapture
//   cargo test bench_orders_100k -- --nocapture
//   cargo test bench_orders_1m   -- --nocapture   ← ~2-4 GB peak heap
//   cargo test bench_orders_10m  -- --nocapture   ← ~20-40 GB, large systems only
// =============================================================================

// ── Data generation constants ─────────────────────────────────────────────────

const ORDER_STATUSES: &[&str] = &["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"];
const FIRST_NAMES: &[&str] = &[
    "John", "Jane", "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank",
];
const LAST_NAMES: &[&str] = &[
    "Smith", "Doe", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
];
const CITIES: &[&str] = &[
    "Springfield",
    "Shelbyville",
    "Portland",
    "Denver",
    "Austin",
    "Chicago",
    "Boston",
    "Miami",
];
const US_STATES: &[&str] = &["IL", "OR", "CO", "TX", "MA", "FL", "CA", "NY"];
const CARRIERS: &[&str] = &["FedEx", "UPS", "USPS", "DHL"];
const SHIP_METHODS: &[&str] = &["EXPRESS", "OVERNIGHT", "STANDARD"];
const PAY_METHODS: &[&str] = &["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "CRYPTO"];
const CARD_TYPES: &[&str] = &["VISA", "MASTERCARD", "AMEX", "DISCOVER"];
const CATEGORIES: &[(&str, &str, &str)] = &[
    ("CAT001", "Electronics", "Tech"),
    ("CAT002", "Clothing", "Fashion"),
    ("CAT003", "Books", "Media"),
    ("CAT004", "Furniture", "Home"),
    ("CAT005", "Sports", "Outdoors"),
];

// ── Nested value builders ─────────────────────────────────────────────────────

/// Address: { street, city, state, zipCode, country }
fn make_address_val(i: u64) -> JsonTValue {
    let city = CITIES[(i as usize) % CITIES.len()];
    let state = US_STATES[(i as usize) % US_STATES.len()];
    JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::str(format!("{} Main St", (i % 999) + 1)),
        JsonTValue::str(city),
        JsonTValue::str(state),
        JsonTValue::str(format!("{:05}", 60000u64 + (i % 40000))),
        JsonTValue::str("US"),
    ]))
}

/// Category: { categoryId, categoryName, department }
fn make_category_val(i: u64) -> JsonTValue {
    let (id, name, dept) = CATEGORIES[(i as usize) % CATEGORIES.len()];
    JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::str(id),
        JsonTValue::str(name),
        JsonTValue::str(dept),
    ]))
}

/// OrderLineItem: { lineItemId, sku, productName, quantity, unitPrice,
///                  discount?, tax, totalPrice, <Category> }
fn make_line_item_val(order_i: u64, item_idx: u64) -> JsonTValue {
    let quantity = ((item_idx % 5) + 1) as i32;
    let unit_price = 9.99 + (order_i % 100) as f64 + item_idx as f64 * 5.0;
    let tax = (unit_price * quantity as f64 * 0.08 * 100.0).round() / 100.0;
    let total_price = (unit_price * quantity as f64 * 100.0).round() / 100.0 + tax;
    JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::u64(order_i * 10 + item_idx + 1),
        JsonTValue::str(format!("SKU-{:06}", (order_i * 3 + item_idx) % 1_000_000)),
        JsonTValue::str(PRODUCTS[((order_i + item_idx) as usize) % PRODUCTS.len()]),
        JsonTValue::i32(quantity),
        JsonTValue::d64(unit_price),
        JsonTValue::null(), // discount is optional
        JsonTValue::d64(tax),
        JsonTValue::d64(total_price),
        make_category_val(order_i + item_idx),
    ]))
}

/// Customer: { customerId, firstName, lastName, email, phoneNumber,
///             <Address>:billingAddress, <Address>?:shippingAddress }
fn make_customer_val(i: u64) -> JsonTValue {
    let first = FIRST_NAMES[(i as usize) % FIRST_NAMES.len()];
    let last = LAST_NAMES[(i as usize) % LAST_NAMES.len()];
    JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::u64(1000 + i % 10000),
        JsonTValue::str(first),
        JsonTValue::str(last),
        JsonTValue::str(format!(
            "{}.{}{}@example.com",
            first.to_lowercase(),
            last.to_lowercase(),
            i % 1000
        )),
        JsonTValue::str(format!("555-{:04}", i % 10000)),
        make_address_val(i),
        JsonTValue::null(), // shippingAddress is optional
    ]))
}

/// Payment: { <PaymentMethod>, transactionId, date, amount, currency,
///            <CardDetails>? }
fn make_payment_val(i: u64, amount: f64) -> JsonTValue {
    let method = PAY_METHODS[(i as usize) % PAY_METHODS.len()];
    let card_details = if method == "CREDIT_CARD" {
        let card_type = CARD_TYPES[(i as usize) % CARD_TYPES.len()];
        // CardDetails: { last4Digits, cardType, expiryDate }
        JsonTValue::Object(JsonTRow::new(vec![
            JsonTValue::str(format!("{:04}", (i * 7 + 1234) % 10000)),
            JsonTValue::str(card_type),
            JsonTValue::str("2027-12-31"),
        ]))
    } else {
        JsonTValue::null()
    };
    JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::enum_val(method),
        JsonTValue::str(format!("TXN-{:010}", i)),
        JsonTValue::str("2024-01-15"),
        JsonTValue::d64(amount),
        JsonTValue::str("USD"),
        card_details,
    ]))
}

/// Shipping: { carrier, trackingNumber, estimatedDelivery, actualDelivery?,
///             shippingCost, <ShippingMethod> }
/// Returns (value, shippingCost) so the caller can include it in grandTotal.
fn make_shipping_val(i: u64) -> (JsonTValue, f64) {
    let method = SHIP_METHODS[(i as usize) % SHIP_METHODS.len()];
    let carrier = CARRIERS[(i as usize) % CARRIERS.len()];
    let cost = match method {
        "EXPRESS" => 15.99,
        "OVERNIGHT" => 29.99,
        _ => 5.99,
    };
    let val = JsonTValue::Object(JsonTRow::new(vec![
        JsonTValue::str(carrier),
        JsonTValue::str(format!("TRK-{:012}", i)),
        JsonTValue::str("2024-01-20"),
        JsonTValue::null(), // actualDelivery is optional
        JsonTValue::d64(cost),
        JsonTValue::enum_val(method),
    ]));
    (val, cost)
}

/// Build one full Order row (17 fields, 1–3 nested line items).
fn make_marketplace_order_row(i: u64) -> JsonTRow {
    let status = ORDER_STATUSES[(i as usize) % ORDER_STATUSES.len()];
    let num_items = (i % 3) + 1;

    let line_items = JsonTValue::Array(JsonTArray::new(
        (0..num_items).map(|j| make_line_item_val(i, j)).collect(),
    ));

    let subtotal = 50.0 + (i % 500) as f64;
    let tax = (subtotal * 0.08 * 100.0).round() / 100.0;
    let discount = 0.0_f64;
    let (shipping_val, shipping_cost) = make_shipping_val(i);
    let grand_total = subtotal + tax + shipping_cost;

    JsonTRowBuilder::new()
        .push(JsonTValue::u64(i + 1))
        .push(JsonTValue::str(format!("ORD-{:08}", i + 1)))
        .push(JsonTValue::str("2024-01-15"))
        .push(JsonTValue::enum_val(status))
        .push(make_customer_val(i))
        .push(line_items)
        .push(make_payment_val(i, grand_total))
        .push(shipping_val)
        .push(JsonTValue::d64(subtotal))
        .push(JsonTValue::d64(tax))
        .push(JsonTValue::d64(discount))
        .push(JsonTValue::d64(shipping_cost))
        .push(JsonTValue::d64(grand_total))
        .push(JsonTValue::str("2024-01-15"))
        .push(JsonTValue::str("2024-01-15"))
        .push(JsonTValue::null()) // createdBy optional
        .push(JsonTValue::null()) // lastModifiedBy optional
        .build()
}

// ── Order schema for validation ───────────────────────────────────────────────
//
// Validates the top-level scalar fields and three row-level rules.
// Nested schema fields (customer, lineItems, payment, shipping) are declared as
// Object fields so the pipeline checks presence; their own sub-constraints are
// enforced by their respective schemas (not included here — no registry needed
// for the top-level streaming benchmark).

fn marketplace_order_schema() -> jsont::JsonTSchema {
    let validation = JsonTValidationBlock {
        rules: vec![
            JsonTRule::Expression(JsonTExpression::gt(
                JsonTExpression::field_name("grandTotal"),
                JsonTExpression::literal(JsonTValue::d64(0.0)),
            )),
            JsonTRule::Expression(JsonTExpression::ge(
                JsonTExpression::field_name("subtotal"),
                JsonTExpression::literal(JsonTValue::d64(0.0)),
            )),
            JsonTRule::Expression(JsonTExpression::ge(
                JsonTExpression::field_name("totalTax"),
                JsonTExpression::literal(JsonTValue::d64(0.0)),
            )),
            JsonTRule::Expression(JsonTExpression::ge(
                JsonTExpression::field_name("shippingCost"),
                JsonTExpression::literal(JsonTValue::d64(0.0)),
            )),
        ],
        unique: vec![],
        dataset: vec![],
    };

    JsonTSchemaBuilder::straight("Order")
        .field(
            JsonTFieldBuilder::scalar("orderId", ScalarType::U64)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("orderNumber", ScalarType::Str)
                .min_length(1)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("orderDate", ScalarType::Str)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::object("orderStatus", "OrderStatus")
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::object("customer", "Customer")
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::object("lineItems", "OrderLineItem")
                .as_array()
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::object("payment", "Payment")
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::object("shipping", "Shipping")
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("subtotal", ScalarType::D64)
                .min_value(0.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("totalTax", ScalarType::D64)
                .min_value(0.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("totalDiscount", ScalarType::D64)
                .min_value(0.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("shippingCost", ScalarType::D64)
                .min_value(0.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("grandTotal", ScalarType::D64)
                .min_value(0.0)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("createdAt", ScalarType::Str)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("updatedAt", ScalarType::Str)
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("createdBy", ScalarType::Str)
                .optional()
                .build()
                .unwrap(),
        )
        .unwrap()
        .field(
            JsonTFieldBuilder::scalar("lastModifiedBy", ScalarType::Str)
                .optional()
                .build()
                .unwrap(),
        )
        .unwrap()
        .validation(validation)
        .build()
        .unwrap()
}

// ── Benchmark steps ───────────────────────────────────────────────────────────

struct OrdersBuildResult {
    path: std::path::PathBuf,
    /// Combined build + write time.  Both phases are interleaved in the
    /// streaming loop so a single elapsed covers both.
    total_time: Duration,
    file_bytes: usize,
    /// Peak heap above the baseline during the loop — should be near-constant
    /// (one row at a time) regardless of N.
    peak_heap_delta: usize,
}

/// Step 1 — streaming build+write.
///
/// Each row is built, written to the BufWriter, and immediately dropped.
/// At any given moment only ONE `JsonTRow` is live on the heap, so peak
/// memory stays O(1) no matter how many records are requested.
fn orders_step1(count: usize) -> OrdersBuildResult {
    let path = std::env::temp_dir().join(format!("jsont_orders_{}.jsont", count));
    let file = File::create(&path).expect("cannot create temp file");
    let mut w = BufWriter::new(file);
    let last = count.saturating_sub(1);

    let baseline = reset_peak();
    let t0 = Instant::now();

    for i in 0..count as u64 {
        let row = make_marketplace_order_row(i);
        write_row(&row, &mut w).expect("write failed");
        if (i as usize) < last {
            w.write_all(b",\n").expect("write failed");
        }
        // `row` is dropped here — no accumulation
    }

    w.flush().expect("flush failed");
    let total_time = t0.elapsed();
    let peak_heap_delta = peak_above(baseline);
    let file_bytes = fs::metadata(&path).expect("metadata").len() as usize;

    OrdersBuildResult {
        path,
        total_time,
        file_bytes,
        peak_heap_delta,
    }
}

struct OrdersParseResult {
    row_count: usize,
    parse_time: Duration,
    throughput_rps: f64,
    file_bytes: usize,
    peak_heap_delta: usize,
}

/// Step 2 — stream-parse the file produced by Step 1.
///
/// Uses `parse_rows_streaming` (BufRead + fill_buf/consume) so the only heap
/// beyond a fixed ~8 KB read buffer is the current row being parsed.
/// Peak heap is constant — O(1) — regardless of record count.
fn orders_step2(path: &std::path::Path) -> OrdersParseResult {
    let file_bytes = fs::metadata(path).expect("metadata").len() as usize;
    let baseline = reset_peak();

    let t0 = Instant::now();
    let mut count = 0usize;
    let file = File::open(path).expect("cannot open file");
    let reader = std::io::BufReader::new(file);
    parse_rows_streaming(reader, |_row| {
        count += 1;
    })
    .expect("parse failed");
    let parse_time = t0.elapsed();

    let peak_heap_delta = peak_above(baseline);
    let ms = parse_time.as_millis().max(1) as f64;

    OrdersParseResult {
        row_count: count,
        parse_time,
        throughput_rps: count as f64 / ms,
        file_bytes,
        peak_heap_delta,
    }
}

/// Step 3 (single-threaded) — stream-parse + validate, O(1) memory.
///
/// `RowIter` lazily yields rows from `BufReader`; `validate_each` processes
/// each row immediately — nothing is buffered.  Used for 1K and 100K records.
///
/// `total` is passed in from the caller (already known after step 1) so no
/// extra file scan is needed.
fn orders_step3_streaming(path: &std::path::Path, total: usize) -> ValidateResult {
    let pipeline = ValidationPipeline::builder(marketplace_order_schema())
        .without_console()
        .with_sink(Box::new(NullSink))
        .build();

    let file = File::open(path).expect("cannot open file for streaming validate");
    let row_iter = RowIter::new(std::io::BufReader::new(file));

    let baseline = reset_peak();
    let mut clean_count = 0usize;

    let t0 = Instant::now();
    pipeline.validate_each(row_iter, |_| {
        clean_count += 1;
    });
    let elapsed = t0.elapsed();

    pipeline.finish().unwrap();

    let peak = peak_above(baseline);
    let ms = (elapsed.as_secs_f64() * 1_000.0).max(0.001);
    ValidateResult {
        total,
        clean: clean_count,
        validate_time: elapsed,
        throughput_rms: total as f64 / ms,
        peak_heap: peak,
    }
}

/// Step 3 (parallel) — channel pipeline: producer parses rows into a bounded
/// channel; `n_workers` threads each hold their own `ValidationPipeline` and
/// call `validate_one` per received row.
///
/// Used for 1M and 10M records where single-threaded validation is the
/// bottleneck.  `total` is passed in from the caller — no extra file scan.
fn orders_step3_parallel(path: &std::path::Path, total: usize) -> ValidateResult {
    use std::sync::mpsc::sync_channel;
    use std::sync::{Arc, Mutex};

    let n_workers = std::thread::available_parallelism()
        .map(|n| n.get().saturating_sub(1).max(1))
        .unwrap_or(1);

    // Bounded channel: back-pressures the producer when workers are busy.
    let (tx, rx) = sync_channel::<JsonTRow>(2048);
    let rx = Arc::new(Mutex::new(rx));
    let clean_total = Arc::new(AtomicUsize::new(0));

    let baseline = reset_peak();
    let t0 = Instant::now();

    std::thread::scope(|s| {
        // ── Producer ──────────────────────────────────────────────────────
        let path_ref = path;
        s.spawn(move || {
            let file = File::open(path_ref).expect("open for produce");
            let row_iter = RowIter::new(std::io::BufReader::new(file));
            for row in row_iter {
                if tx.send(row).is_err() {
                    break; // all workers exited early
                }
            }
            // tx dropped here → channel closes → workers drain and exit
        });

        // ── Workers ───────────────────────────────────────────────────────
        for _ in 0..n_workers {
            let rx_arc = Arc::clone(&rx);
            let counter = Arc::clone(&clean_total);
            s.spawn(move || {
                let pipeline = ValidationPipeline::builder(marketplace_order_schema())
                    .without_console()
                    .with_sink(Box::new(NullSink))
                    .build();

                let mut local_clean = 0usize;
                loop {
                    let row = rx_arc.lock().unwrap().recv().ok();
                    match row {
                        Some(r) => pipeline.validate_one(r, |_| {
                            local_clean += 1;
                        }),
                        None => break, // channel closed
                    }
                }

                counter.fetch_add(local_clean, Ordering::Relaxed);
                pipeline.finish().unwrap();
            });
        }
    });

    let elapsed = t0.elapsed();
    let peak = peak_above(baseline);
    let clean_count = clean_total.load(Ordering::Relaxed);
    let ms = (elapsed.as_secs_f64() * 1_000.0).max(0.001);

    ValidateResult {
        total,
        clean: clean_count,
        validate_time: elapsed,
        throughput_rms: total as f64 / ms,
        peak_heap: peak,
    }
}

// ── Core benchmark runner ─────────────────────────────────────────────────────

fn run_orders_benchmark(count: usize) {
    // ── Step 1: streaming build + stringify ───────────────────────────────
    let br = orders_step1(count);
    let throughput_s1 = count as f64 / br.total_time.as_millis().max(1) as f64;

    println!(
        "  stream   {:>7} orders  build+stringify {:>10}  {:>9.1} rec/ms  \
         file {:>10}  ({:.0} B/row)  peak {:>8}",
        fmt_count(count),
        fmt_duration(br.total_time),
        throughput_s1,
        fmt_bytes(br.file_bytes),
        br.file_bytes as f64 / count as f64,
        fmt_bytes(br.peak_heap_delta),
    );

    // ── Step 2: streaming parse (O(1) memory) ─────────────────────────────
    let pr = orders_step2(&br.path);
    assert_eq!(pr.row_count, count, "parsed row count mismatch");

    println!(
        "  parse    {:>7} orders  time  {:>10}  \
         throughput {:>9.1} rec/ms  file {:>10}  peak {:>10}",
        fmt_count(pr.row_count),
        fmt_duration(pr.parse_time),
        pr.throughput_rps,
        fmt_bytes(pr.file_bytes),
        fmt_bytes(pr.peak_heap_delta),
    );

    // ── Step 3: parse + validate (single-threaded ≤100K, parallel >100K) ─
    let vr = if count <= 100_000 {
        println!("  [step 3: single-threaded streaming parse+validate]");
        orders_step3_streaming(&br.path, count)
    } else {
        let n = std::thread::available_parallelism()
            .map(|n| n.get().saturating_sub(1).max(1))
            .unwrap_or(1);
        println!(
            "  [step 3: parallel channel pipeline, {} worker threads]",
            n
        );
        orders_step3_parallel(&br.path, count)
    };

    println!(
        "  validate {:>7} orders  time  {:>10}  \
         throughput {:>9.1} rec/ms  clean {:>7}  peak {:>10}",
        fmt_count(vr.total),
        fmt_duration(vr.validate_time),
        vr.throughput_rms,
        fmt_count(vr.clean),
        fmt_bytes(vr.peak_heap),
    );

    let _ = fs::remove_file(&br.path);
}

// ── Test entry points ─────────────────────────────────────────────────────────

#[test]
fn bench_orders_1k() {
    println!("\n=== Orders Benchmark  1K records (Marketplace schema — nested objects) ===");
    println!("  Schema ref: tests/orders-schema.jsont");
    println!("  17 fields: 5 scalars, 4 nested objects (<Customer>, <Payment>, <Shipping>, <OrderLineItem>[])");
    println!("  Validation: 5 field constraints + 4 row rules (grandTotal>0, subtotal>=0, totalTax>=0, shippingCost>=0)");
    run_orders_benchmark(1_000);
}

#[test]
fn bench_orders_100k() {
    println!("\n=== Orders Benchmark  100K records (Marketplace schema — nested objects) ===");
    println!("  Schema ref: tests/orders-schema.jsont");
    run_orders_benchmark(100_000);
}

#[test]
fn bench_orders_1m() {
    println!("\n=== Orders Benchmark  1M records (Marketplace schema — nested objects) ===");
    println!("  Schema ref: tests/orders-schema.jsont");
    println!("  Warning: ~2–4 GB peak heap expected. Ensure sufficient RAM.");
    run_orders_benchmark(1_000_000);
}

/// 10 M complex orders — requires ~20-40 GB RAM. Only run on large-memory systems.
#[test]
fn bench_orders_10m() {
    println!("\n=== Orders Benchmark  10M records (Marketplace schema — nested objects) ===");
    println!("  Schema ref: tests/orders-schema.jsont");
    println!("  Warning: ~20–40 GB peak heap expected. Large-memory systems only.");
    run_orders_benchmark(10_000_000);
}
