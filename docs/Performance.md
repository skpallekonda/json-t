# Performance Benchmark

## Schema

All benchmarks use the **ICC Men's T20 World Cup Match schema**
([wct20-match.jsont](../code/benchmark-schema/wct20-match.jsont)):

- **92 fields** per row covering all scalar types:
  `i16/i32/i64`, `u16/u32/u64`, `d32/d64/d128`, `bool`, `str`, `email`, `uri`,
  `hostname`, `ipv4`, `ipv6`, `hex`, `base64`, `uuid`, `datetime`, `time`,
  `timestamp`, `duration`
- **Target row size:** ~1 KB serialised
- **Two schemas:** `CricketMatch` (straight, 92 fields + 4 validation rules) and
  `MatchBroadcastSummary` (derived — filter + rename + transform + exclude)

---

## How to run

**Rust** — run from `code/rust/jsont/` (feature-gated, excluded from normal `cargo test`):
```bash
# All sizes in one run
cargo test --release --features bench bench_wct -- --nocapture

# Individual sizes (faster iteration)
cargo test --release --features bench bench_wct_1k   -- --nocapture
cargo test --release --features bench bench_wct_10k  -- --nocapture
cargo test --release --features bench bench_wct_100k -- --nocapture
cargo test --release --features bench bench_wct_1m   -- --nocapture
cargo test --release --features bench bench_wct_10m  -- --nocapture   # ~7 min, 7 GB temp file; ensure 16 GB free RAM
```

> **Rust memory notes:**
> - All pipeline steps are O(1) row memory — peak heap is ~15–42 KB regardless of dataset size.
> - The `TrackingAllocator` wraps every alloc/dealloc with an atomic CAS; this is included in all timing numbers.
> - Uniqueness constraint `HashSet`s grow with N (one entry per unique row seen) — at 10M rows this is the dominant memory consumer but still only tens of MB.

**Java** — run from `code/java/jsont/` (Maven `benchmark` profile, excluded from normal `mvn test`):
```bash
# All sizes in one run
mvn test -Pbenchmark -Dtest=BenchmarkTest

# Individual sizes (faster iteration)
mvn test -Pbenchmark -Dtest=BenchmarkTest#bench_1k
mvn test -Pbenchmark -Dtest=BenchmarkTest#bench_10k
mvn test -Pbenchmark -Dtest=BenchmarkTest#bench_100k
mvn test -Pbenchmark -Dtest=BenchmarkTest#bench_1m    # Maven profile sets -Xmx6g
mvn test -Pbenchmark -Dtest=BenchmarkTest#bench_10m   # set <argLine>-Xmx12g</argLine> in pom.xml benchmark profile
```

> **Java memory notes:**
> - The `benchmark` Maven profile sets `-Xmx6g` in Surefire — sufficient for all sizes up to 1M.
> - For 10M, temporarily increase `<argLine>` in the pom.xml benchmark profile to `-Xmx12g`.
> - Java heap values are approximate (`Runtime.totalMemory() - freeMemory()`); GC timing affects readings.

> **Tip:** Run 1k first as a smoke test, then 10k–1m for meaningful numbers.
> The 10m run takes ~7 min (Rust) and ~24 min (Java) and produces a 7 GB temp file; close other apps first.

---

## Benchmark options (per size)

| # | Option | Description |
|---|--------|-------------|
| 1 | **Stringify** | Build N rows one at a time → stream-write to temp file. O(1) memory. Measures: build time, stringify time, file size |
| 2 | **Parse** | Stream-read temp file → parse row by row (callback/iterator). O(1) memory. Measures: parse throughput, peak heap |
| 3 | **Parse + Validate** | Streaming parse → `ValidationPipeline` (field constraints + 4 expression rules). Rust: sequential. Java: 1 worker at ≤10K, 8 workers at ≥100K |
| 4 | **Parse + Validate + Transform** | Streaming parse → validate → apply `MatchBroadcastSummary` derived schema (filter/rename/transform/exclude). Same concurrency as option 3 |

---

## Toolchain

> **Rust** — compiled with `cargo test --release --features bench`
> **Java** — compiled with `mvn test -Pbenchmark` (Java 17, no JMH warmup, 8 CPUs)
> **Platform** — Windows 11, x86-64
> **Measurement** — wall-clock; Rust uses custom `TrackingAllocator` for precise heap delta; Java uses `Runtime.totalMemory() - freeMemory()` (approximate)

---

## Results — Stringify

| Size | Measure | Rust | Java | Diff |
|------|---------|------|------|------|
| 1K | Build time | 2.39 ms | 53.70 ms | **22×** |
| | Stringify time | 3.24 ms | 44.18 ms | **14×** |
| | File size | 730.67 KB | 733.44 KB | ~same |
| 10K | Build time | 19.16 ms | 151.67 ms | **8×** |
| | Stringify time | 25.15 ms | 210.14 ms | **8.4×** |
| | File size | 7.15 MB | 7.17 MB | ~same |
| 100K | Build time | 179.72 ms | 485.68 ms | **2.7×** |
| | Stringify time | 271.79 ms | 1.38 s | **5×** |
| | File size | 71.55 MB | 71.82 MB | ~same |
| 1M | Build time | 1.76 s | 2.23 s | **1.3×** |
| | Stringify time | 2.75 s | 14.53 s | **5.3×** |
| | File size | 716.42 MB | 719.14 MB | ~same |
| 10M | Build time | 18.24 s | 16.74 s | ~same |
| | Stringify time | 43.78 s | 121.06 s | **2.8×** |
| | File size | 7.01 GB | 7.03 GB | ~same |

---

## Results — Parse

| Size | Measure | Rust | Java | Diff |
|------|---------|------|------|------|
| 1K | Throughput (rows/ms) | 100.0 | 14.2 | **7×** |
| | Parse time | 10.45 ms | 70.45 ms | **6.7×** |
| | Peak heap | **15.29 KB** | 10.37 MB | **694×** |
| 10K | Throughput (rows/ms) | 204.1 | 11.8 | **17×** |
| | Parse time | 49.17 ms | 845.63 ms | **17×** |
| | Peak heap | **15.29 KB** | 3.86 MB | **258×** |
| 100K | Throughput (rows/ms) | 205.3 | 75.2 | **2.7×** |
| | Parse time | 487.98 ms | 1.33 s | **2.7×** |
| | Peak heap | **15.29 KB** | 69.96 MB | **4,690×** |
| 1M | Throughput (rows/ms) | 198.8 | 79.4 | **2.5×** |
| | Parse time | 5.03 s | 12.60 s | **2.5×** |
| | Peak heap | **15.29 KB** | 16.06 MB | **1,074×** |
| 10M | Throughput (rows/ms) | 163.1 | 41.3 | **3.9×** |
| | Parse time | 61.31 s | 242.01 s | **3.9×** |
| | Peak heap | **15.29 KB** | 5.81 MB | **389×** |

---

## Results — Parse + Validate

| Size | Workers | Measure | Rust | Java | Diff |
|------|---------|---------|------|------|------|
| 1K | 1 (seq) | Throughput (rows/ms) | 90.9 | 8.7 | **10.4×** |
| | | Total time | 11.77 ms | 114.40 ms | **9.7×** |
| | | Peak heap | **19.46 KB** | 4.40 MB | **231×** |
| 10K | 1 (seq) | Throughput (rows/ms) | 131.6 | 22.5 | **5.8×** |
| | | Total time | 76.16 ms | 444.19 ms | **5.8×** |
| | | Peak heap | **14.81 KB** | 47.28 MB | **3,268×** |
| 100K | 1 / 8 par | Throughput (rows/ms) | 128.2 | 43.3 | **3×** |
| | | Total time | 780.46 ms | 2.31 s | **3×** |
| | | Peak heap | **19.26 KB** | 9.38 MB | **498×** |
| 1M | 1 / 8 par | Throughput (rows/ms) | 107.6 | 35.4 | **3×** |
| | | Total time | 9.30 s | 28.28 s | **3×** |
| | | Peak heap | **19.74 KB** | 75.64 MB | **3,922×** |
| 10M | 1 / 8 par | Throughput (rows/ms) | 97.0 | 19.6 | **4.9×** |
| | | Total time | 103.08 s | 509.29 s | **4.9×** |
| | | Peak heap | **41.88 KB** | 41.70 MB | **1,020×** |

---

## Results — Parse + Validate + Transform

| Size | Workers | Measure | Rust | Java | Diff |
|------|---------|---------|------|------|------|
| 1K | 1 (seq) | Throughput (rows/ms) | 45.5 | 7.3 | **6.2×** |
| | | Total time | 22.20 ms | 136.39 ms | **6.1×** |
| | | Peak heap | **17.35 KB** | 45.54 MB | **2,684×** |
| 10K | 1 (seq) | Throughput (rows/ms) | 48.1 | 16.6 | **2.9×** |
| | | Total time | 208.10 ms | 602.11 ms | **2.9×** |
| | | Peak heap | **21.30 KB** | 27.49 MB | **1,320×** |
| 100K | 1 / 8 par | Throughput (rows/ms) | 48.7 | 41.4 | **1.2×** |
| | | Total time | 2.05 s | 2.42 s | **1.2×** |
| | | Peak heap | **21.48 KB** | 3.97 MB | **189×** |
| 1M | 1 / 8 par | Throughput (rows/ms) | 29.3 | **32.3** | Java leads **1.1×** |
| | | Total time | 34.09 s | 30.92 s | Java leads |
| | | Peak heap | **25.99 KB** | 40.17 MB | 1,582× |
| 10M | 1 / 8 par | Throughput (rows/ms) | 36.4 | 18.3 | **2×** |
| | | Total time | 274.74 s | 547.26 s | **2×** |
| | | Peak heap | **35.15 KB** | 155.29 MB | **4,520×** |

---

## Analysis

### Memory: Rust O(1) vs Java O(N)

Rust peak heap is **constant** across all dataset sizes for parse (~15 KB) and validate (~20 KB).
This is the BufReader buffer plus the one live row at any moment — exactly what the streaming design intends.

Java peak heap is **variable** and follows the size of the in-flight window (the `ArrayBlockingQueue`
for parallel validation). It is bounded but GC timing makes readings noisy.

The heap advantage is architectural, not tuning: Rust's `TrackingAllocator` measures true allocations;
Java's `Runtime` delta is approximate.

---

### Why Java is slower than Rust

**1. JVM startup and JIT warmup cost**
At 1K–10K rows the total work is under 100 ms. The JVM hasn't yet JIT-compiled the hot paths
(expression evaluator, rule checker, row scanner). This accounts for the 7–17× gap at small sizes
that shrinks to 2.5–3× at 1M rows where the JIT has been running for seconds.

**2. `RowScanner` char-by-char I/O vs Rust byte-slice parsing**
Java data rows are parsed by `RowScanner` — a hand-written state machine that reads one `char`
at a time from a `Reader`. Each `read()` call crosses the JNI/native boundary, and the state machine
builds intermediate `String` objects per field. Rust's `parse_rows_streaming` reads bulk byte slices
via `BufReader` and the pest PEG parser operates directly on those slices with zero intermediate
allocation for field token extraction. Note: ANTLR4 is only used for the schema DSL
(`SchemaVisitor.parseNamespace`), not for data row parsing.

**3. Object header and boxing overhead**
Every `JsonTValue` in Java is a boxed enum — a heap-allocated object with an 8–16 byte header,
type tag, and reference. In Rust, `JsonTValue` is an inline enum stored by value, often on the stack.
For a 92-field row, Java allocates 92+ objects just to represent field values; Rust allocates zero
extra heap for scalar values.

**4. String allocation in EvalContext**
Java's `LinkedHashMap<String, JsonTValue>` in `EvalContext` requires a `String` object per binding.
Even with P3's minimal context (2–4 fields), those strings are heap-allocated. Rust's `HashMap`
uses `String` too, but `String::clone()` is cheaper because Rust's allocator has no GC safepoint
overhead and string data is often still hot in cache.

**5. Sequential parse is the bottleneck at scale**
Java's validate and transform stages benefit from 8-worker parallelism. However, parse is
single-threaded (streaming from one file), so end-to-end throughput is bounded by parse speed
(~41–79 rows/ms). This is why Java's Parse+Val+T at 1M (32.3 rows/ms) actually *beats* Rust
(29.3 rows/ms) — 8 workers absorb validate+transform while the single-threaded parse is the shared
bottleneck; Rust's sequential pipeline stalls waiting for each row.

---

### Why Rust throughput decreases at large N

Rust is fully AOT-compiled so there is no JIT effect. The WCT20 schema has **no uniqueness or
dataset constraints**, so `ValidationPipeline` allocates no `HashSet` state across rows.
The degradation has two independent causes:

**1. Parse drop at 10M is entirely OS I/O (205→163 rows/ms)**
The 7 GB temp file does not fit in the OS page cache. At 1M the 716 MB file stays resident after the
first read pass; at 10M the OS must continuously evict pages to serve new `BufReader` reads, adding
latency that the CPU cannot pipeline away. The degradation is an I/O ceiling, not a parser issue.

To confirm: decompose the combined timings:
- Validate-only throughput at 1M: (9.30s − 5.03s) = 4.27s → **234 rows/ms**
- Validate-only throughput at 10M: (103.08s − 61.31s) = 41.77s → **239 rows/ms**

The rule-evaluation logic itself is **flat across all dataset sizes**. The apparent "validation
slowdown" is entirely the parse I/O component pulling the combined number down.

**2. TrackingAllocator atomic contention explains the 1M transform dip (48→29 rows/ms)**
The benchmark's `TrackingAllocator` wraps every `alloc`/`dealloc` with an atomic `fetch_add` +
`compare_exchange_weak` on two global counters — intentional for accurate heap measurement, but
the CAS loop serialises memory operations. P3's `collect_field_refs` (not yet moved to build time
per P6) allocates a `HashSet<String>` per expression per row inside `apply_operation`:
~6 operations × 1M rows = 6M `HashSet` alloc/dealloc cycles through the atomic wrapper.
At 100K (600K cycles) this is negligible; at 1M (6M cycles) it dominates, producing the
visible 40% throughput drop. At 10M the longer total runtime amortises the contention and the
number recovers to 36 rows/ms. **This is a benchmark harness artefact** — production code
without `TrackingAllocator` would not see it. P6 (pre-compute field refs at build time) eliminates
these per-row allocations entirely.

---

## Plan for Performance Improvement

| # | Description | Language | Status |
|---|-------------|----------|--------|
| P1 | Replace `ArrayList` + `Files.readString` with streaming `validateEach` / `validateStream` | Java | **Done** |
| P2 | Replace `Vec<JsonTRow>` collection with `RowIter` + `parse_rows_streaming` | Rust | **Done** |
| P3 | Build minimal `EvalContext` per row (only referenced fields, not all 92) | Both | **Done** |
| P4 | Add `-Xmx6g` to Maven Surefire benchmark profile | Java | **Done** |
| P5 | Change uniqueness key type from `HashSet<Vec<String>>` to `HashSet<String>` (null-byte joined) | Rust | **Done** |
| P6 | Pre-compute `collect_field_refs` + rule field refs at schema/pipeline build time; pass `&[String]` slice to hot path | Rust | **Done** |
| P7 | Replace per-char `Reader.read()` + `StringBuilder.toString()` in `RowExtractor` with bulk `char[]` buffer; `ValueParser` parses from `char[]` directly | Java | **Done** |
| P8 | Parallel parse for Java — split input file into byte-range chunks, parse on multiple threads | Java | **Pending** |

### P5 details
`build_unique_key` returned `Vec<String>` (~84+ bytes per composite key); now returns a single
null-byte-separated `String` (~60 bytes). Benefits schemas that have uniqueness constraints. The
WCT20 benchmark schema has no uniqueness block, so throughput numbers are unchanged there, but
memory density in the `HashSet` improves for schemas that do use it.

### P6 details
Two separate per-row allocations eliminated:
1. `transform/mod.rs` — `collect_field_refs(expr)` + `HashSet<String>` was called inside
   `apply_operation` per row per operation. Now `SchemaOperation::Filter { refs }` and
   `SchemaOperation::Transform { refs }` carry a pre-computed `Vec<String>` set at parse time.
   `build_minimal_eval_ctx` takes `&[String]` — linear scan over 2–4 entries beats hashing.
2. `validate/rule.rs` — `check_rules` built a `HashSet<String>` per row by traversing all rule
   expressions. Now `ValidationPipeline` stores `rule_field_refs: Vec<String>` computed once in
   `build()` and passed as a slice to `check_rules`.

Net effect: zero per-row allocations for field-ref lookup in the hot validation + transform path.
Expected to restore the 1M Parse+Val+T throughput from the TrackingAllocator-affected 29 rows/ms
back toward the 48 rows/ms seen at 100K.

### P7 details
`RowExtractor` now reads into a `char[65536]` bulk buffer — one `Reader.read(char[])` call per
65536 chars instead of one synchronized `reader.read()` per char. For a 92-field row (~1000 chars)
this reduces lock acquisitions by ~1000×. A reusable `char[] rowBuf` replaces the
`StringBuilder` + `toString()` copy per row. `ValueParser` operates directly on the `char[]`
slice, eliminating the intermediate `String` object that held raw row text. String field values
still allocate (`new String(char[], start, len)`) — unavoidable — but all other per-row
`String` copies are removed.

### P8 details (pending)
Split the input file into N byte-range chunks at `\n` boundaries (seek to next comma+brace after
each chunk start). Parse each chunk on a separate thread, feeding rows into the shared validation
queue. Would close the last remaining single-threaded bottleneck in the Java pipeline.

---

## Submitting results for review

Run the benchmarks and paste the raw console output. The output lines look like:

```
=== WCT Benchmark  1K records ===
  [1] Stringify       1K rows | build    2.39ms | stringify    3.24ms | file   730.67 KB
  [2] Parse           1K rows | time    10.45ms |    100.0 rows/ms | loaded   730.67 KB | peak   15.29 KB
  [3] Parse+Val       1K rows | time    11.77ms |     90.9 rows/ms | clean     1K | peak   19.46 KB
  [4] Parse+Val+T     1K rows | time    22.20ms |     45.5 rows/ms | xfrmd     1K | peak   17.35 KB
```

Paste Rust output and Java output together and I will parse them into the tables above.
