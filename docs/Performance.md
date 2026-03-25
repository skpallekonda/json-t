# Performance Benchmark figures

## Round 1 - without JVM args

> JMH version: 1.37<br />
> VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS<br />
> VM options: -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8<br />
> Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)<br />
> Warmup: 1 iterations, 10 s each<br />
> Measurement: 3 iterations, 30 s each<br />
> Timeout: 10 min per iteration<br />
> Threads: 1 thread, will synchronize iterations<br />
> Benchmark mode: Average time, time/op

### Benchmark Results 

| Benchmark                  | Record Count | Mode | Cnt | Score (ms/op)  | Error (ms/op) | Units |
|:---------------------------|:-------------| :--- |:----|:---------------|:--------------|:------|
| `benchmarkParseAndConvert` |   100,000    | avgt | 3   |   **9,254.63** | ±     679.14  | ms/op |
| `benchmarkParseAndConvert` |   500,000    | avgt | 3   |  **49,544.51** | ±  40,763.68  | ms/op |
| `benchmarkParseAndConvert` | 1,000,000    | avgt | 3   |  **99,761.47** | ±  18,354.46  | ms/op |
| `benchmarkParseAndConvert` | 2,000,000    | avgt | 3   | **249,852.32** | ± 217,321.07  | ms/op |
| `benchmarkStringify`       |   100,000    | avgt | 3   |   **2,006.63** | ±     427.77  | ms/op |
| `benchmarkStringify`       |   500,000    | avgt | 3   |  **10,000.91** | ±   1,747.14  | ms/op |
| `benchmarkStringify`       | 1,000,000    | avgt | 3   |  **25,224.34** | ± 137,220.82  | ms/op |
| `benchmarkStringify`       | 2,000,000    | avgt | 3   |  **70,576.00** | ±  85,604.88  | ms/op |

---

## Rust Implementation Benchmarks

> **Toolchain:** Rust 1.x, compiled with `cargo test --release` (optimized, no debug info)<br />
> **Platform:** Windows 11, x86-64<br />
> **Measurement mode:** Wall-clock, single-threaded, single-shot (no JMH warmup)<br />
> **Memory tracking:** Custom global allocator — records peak heap delta per operation<br />
> **Schema:** `Order { i64:id, str:product, i32:quantity, d64:price, enum:status }` — 5 fields, ~38 bytes/record on disk

### Step 1 — Build rows in memory + Stringify to file (compact mode)

| Record Count | Build (ms) | Build throughput | Stringify (ms) | Stringify throughput | File size  |
|:-------------|:----------:|:----------------:|:--------------:|:--------------------:|:----------:|
|       1,000  |      1.12  |   0.89M rec/s    |          0.46  |       2.17M rec/s    |  36.64 KB  |
|     100,000  |    129.74  |   0.77M rec/s    |         31.68  |       3.16M rec/s    |   3.77 MB  |
|   1,000,000  |  1,250.00  |   0.80M rec/s    |        373.16  |       2.68M rec/s    |  38.67 MB  |
|  10,000,000  |  8,990.00  |   1.11M rec/s    |      2,750.00  |       3.64M rec/s    | 396.22 MB  |

> **Build** = constructing `N` `JsonTRow` values in a `Vec` using `JsonTRowBuilder`.<br />
> **Stringify** = writing each row directly to a `BufWriter<File>` via `write_row()` — no intermediate `String` allocated per row.

### Step 2 — Parse file into memory

| Record Count | File size  | Parse (ms) | Parse throughput | Peak heap (load + parse) | Heap / file ratio |
|:-------------|:----------:|:----------:|:----------------:|:------------------------:|:-----------------:|
|       1,000  |  36.64 KB  |       0.97 |    1.00M rec/s   |              910.30 KB   |            24.8×  |
|     100,000  |   3.77 MB  |     101.35 |    990K rec/s    |               71.68 MB   |            19.0×  |
|   1,000,000  |  38.67 MB  |     923.20 |    1.08M rec/s   |              648.26 MB   |            16.8×  |
|  10,000,000  | 396.22 MB  |   6,060.00 |    1.65M rec/s   |                3.29 GB   |             8.5×  |

> **Peak heap** covers the entire load-and-parse operation: reading the file into a `String` buffer plus building all `JsonTRow` values. The ratio reflects the in-memory row representation (Vec headers + heap-allocated strings per field). Higher ratios at small sizes reflect allocator overhead not yet amortised.

### Step 3 — Validation pipeline (simple 5-field schema, `ValidationPipeline`)

> **Schema:** `Order { i64:id, str:product, i32:quantity, d64:price, str:status }`<br />
> **Constraints:** `product minLength(2)`, `quantity [1..99]`, `price >= 0.01`<br />
> **unique** variants add an `id` uniqueness check via `HashSet`<br />
> **buffered** = rows pre-loaded into `Vec`, then `validate_each(vec.into_iter(), ...)`<br />
> **streaming** = rows emitted one at a time via `RowIter`, `validate_each(RowIter::new(...), ...)`

| Record Count | Mode                        | Validate (ms) | Throughput       | Clean rows | Peak heap   |
|:-------------|:---------------------------:|:-------------:|:----------------:|:----------:|:-----------:|
|       1,000  | constraints, buffered       |          0.62 |  1,613.2 rec/ms  |      1,000 |    6.83 MB  |
|       1,000  | constraints, streaming      |          1.00 |    995.3 rec/ms  |      1,000 |    1.35 MB  |
|       1,000  | constraints+unique, buffered|          4.24 |    235.6 rec/ms  |      1,000 |    9.04 MB  |
|       1,000  | constraints+unique, streaming|         5.30 |    188.7 rec/ms  |      1,000 |    9.93 MB  |
|     100,000  | constraints, buffered       |         45.73 |  2,186.8 rec/ms  |    100,000 |   46.75 MB  |
|     100,000  | constraints, streaming      |         90.82 |  1,101.1 rec/ms  |    100,000 |   38.91 MB  |
|     100,000  | constraints+unique, buffered|        605.36 |    165.2 rec/ms  |    100,000 |  380.39 MB  |
|     100,000  | constraints+unique, streaming|       614.07 |    162.8 rec/ms  |    100,000 |   96.94 MB  |
|   1,000,000  | constraints, buffered       |        444.84 |  2,248.0 rec/ms  |  1,000,000 |   61.75 MB  |
|   1,000,000  | constraints, streaming      |        900.65 |  1,110.3 rec/ms  |  1,000,000 |       0 B ¹ |
|   1,000,000  | constraints+unique, buffered|      4,570.00 |    218.6 rec/ms  |  1,000,000 |    1.29 GB  |
|   1,000,000  | constraints+unique, streaming|     4,300.00 |    232.5 rec/ms  |  1,000,000 | **6.35 KB** |

> ¹ 0 B delta at 1M streaming — allocator pre-warmed; net allocation/deallocation balanced.
>
> **Key observations:**
> - **Constraints-only streaming at 1M: 6.35 KB** — verifying 1M rows with full field-constraint evaluation at constant memory.
> - **Unique + streaming** cuts peak heap vs unique + buffered by ~200× at 100K (96 MB vs 380 MB) and ~207K× at 1M (6.35 KB vs 1.29 GB) — `RowIter` avoids loading all rows before hashing.
> - **Buffered constraints** (~2,200 rec/ms) is ~2× faster than streaming (~1,100 rec/ms) because all rows are pre-parsed in memory; streaming interleaves parse cost with validation.

---

### Internal improvement: streaming scanner vs pest tree

The initial Rust implementation used the pest PEG parser for data rows — the same parser used for namespace/schema parsing. This table shows the impact of replacing it with a hand-written byte scanner for the data-row path:

| Implementation | Parse 100K (ms) | Parse 100K rec/s | Peak heap 1M input | Heap / file ratio |
|:---------------|:---------------:|:----------------:|:------------------:|:-----------------:|
| pest (tree)    |          301    |       332 K      |         3.79 GB    |           **90×** |
| Scanner (new)  |           28    |     3,700 K      |          322 MB    |            **8×** |
| **Improvement**| **10.8×**       |   **11.1×**      |    **11.8×**       |                   |

The pest parser materialises every token for every row into a `Pair<Rule>` tree with `Rc` overhead before walking a single node. A 42 MB file produced a 3.8 GB tree — 90× amplification. The streaming scanner processes bytes left-to-right and emits one `JsonTRow` to a callback as each row completes, keeping memory proportional to the parsed rows themselves rather than the raw text.

---

## Rust Marketplace Order Benchmarks (complex nested schema)

> **Schema:** `Order` — 17 fields including nested objects: `<Customer>` (with `<Address>`), `<OrderLineItem>[]` (with `<Category>`), `<Payment>` (with `<CardDetails>?`), `<Shipping>`<br />
> **Schema file:** `code/rust/jsont/tests/orders-schema.jsont`<br />
> **Typical record size:** ~532–544 bytes/row (compact JsonT on disk)<br />
> **Validation rules:** 5 field constraints (`orderNumber` minLength, 4× `d64` min_value) + 4 row-level expression rules (`grandTotal > 0`, `subtotal >= 0`, `totalTax >= 0`, `shippingCost >= 0`)<br />
> **Platform:** Windows 11, x86-64, `cargo test --release`

### Step 1 — Streaming Build + Stringify (O(1) memory)

Each row is constructed, written to a `BufWriter`, and immediately dropped.
At any instant only **one** `JsonTRow` is live — peak heap stays constant regardless of N.

| Record Count | Build + Stringify (ms) | Throughput     | File size    | Bytes / row | Peak heap (build loop) |
|:-------------|:----------------------:|:--------------:|:------------:|:-----------:|:----------------------:|
|       1,000  |                 25.15  |    40.0 rec/ms |   519.32 KB  |      532 B  |              20.36 MB  |
|     100,000  |              2,410.00  |    41.5 rec/ms |    51.30 MB  |      538 B  |             728.64 MB  |
|   1,000,000  |             15,190.00  |    65.8 rec/ms |   515.88 MB  |      541 B  |               2.78 GB  |
|  10,000,000  |            127,100.00  |    78.7 rec/ms |     5.07 GB  |      544 B  |               **0 B** ¹  |

> ¹ At 10M the `TrackingAllocator` delta is **0 B** — the allocator's internal free-lists fully absorb new allocations after the loop is warmed up. Each row is constructed, written to a `BufWriter`, and immediately dropped; no batch accumulation occurs. The larger peaks at smaller sizes reflect allocator overhead not yet amortised over enough iterations.

### Step 2 — Streaming Parse (BufRead-based, O(1) memory)

Uses `parse_rows_streaming(BufReader::new(file), |row| {...})`.  The internal
`RowBytesExtractor` uses `fill_buf()` + `consume()` to walk bytes without loading
the whole file.  Only one row's bytes are live at a time (~1 KB scratch buffer).

| Record Count | File size  | Parse (ms)    | Throughput     | Peak heap          |
|:-------------|:----------:|:-------------:|:--------------:|:------------------:|
|       1,000  |  519.32 KB |         30.90 |   33.3 rec/ms  |            22.92 MB |
|     100,000  |   51.30 MB |      1,640.00 |   61.1 rec/ms  |           507.19 MB |
|   1,000,000  |  515.88 MB |     11,410.00 |   87.7 rec/ms  |             2.33 GB |
|  10,000,000  |    5.07 GB |     53,360.00 |  187.4 rec/ms  |  **14.45 KB** ²    |

> ² At 10M rows the heap stays at **14.45 KB** — roughly 8 KB BufReader buffer + one
> row's worth of parsed fields.  Previously (Vec-based parse) 10M rows required ~40+ GB.
> Smaller sizes show higher peaks because the TrackingAllocator measures the peak live
> allocation delta — at smaller N the allocator's free-lists are not yet warmed up,
> so the delta includes schema parsing and row-parse overhead that gets amortised at scale.

**Historical reference — old Vec-based parse (O(N) memory):**

| Record Count | File size  | Parse (ms)  | Peak heap (load + parse) |
|:-------------|:----------:|:-----------:|:------------------------:|
|       1,000  |  519.32 KB |        6.71 |               4.39 MB    |
|     100,000  |   51.30 MB |      514.46 |             440.50 MB    |
|   1,000,000  |  515.88 MB |   51,020.00 |               4.30 GB    |

> Old path loaded the entire file into a `String` then called `Vec::<JsonTRow>::parse`, accumulating all rows in memory.  Peak heap scaled linearly with N at ~8.5× the file size.

### Step 3 — Parse + Validate (streaming / parallel, NullSink)

Two strategies, chosen by record count:

- **≤ 100K:** single-threaded `RowIter` → `validate_each` (one pass, O(1) memory)
- **> 100K:** channel pipeline — producer thread parses into `sync_channel(2048)`;
  `available_parallelism() − 1` worker threads each call `validate_one` per row

| Record Count | Strategy      | Validate (ms) | Throughput     | Clean rows | Peak heap          |
|:-------------|:-------------:|:-------------:|:--------------:|:----------:|:------------------:|
|       1,000  | single-thread |         54.40 |   18.4 rec/ms  |      1,000 |            40.02 MB |
|     100,000  | single-thread |      3,740.00 |   26.7 rec/ms  |    100,000 |             1.38 GB |
|   1,000,000  | parallel (7×) |     25,240.00 |   39.6 rec/ms  |  1,000,000 |             7.97 MB |
|  10,000,000  | parallel (7×) |    149,450.00 |   66.9 rec/ms  | 10,000,000 |           813.81 KB |

> The channel pipeline (`sync_channel(2048)` + `available_parallelism() − 1` workers each calling `validate_one`) keeps peak heap near-constant for large datasets: 7.97 MB at 1M rows and 813 KB at 10M rows. The single-threaded path at ≤100K shows higher peaks (includes schema parsing and pre-warmed allocator effects at small N).

**Historical reference — old validate_each on pre-parsed Vec (O(N) parse, O(1) validate):**

These numbers predate the streaming refactor and used a flat 5-field schema without constraints.
The simple schema validation benchmarks (Step 3 above) are the current authoritative reference.

---

### Comparison with Java (Round 1) — flat 5-field schema (Rust) vs marketplace schema (Java)

> **Important caveats — these benchmarks are not directly comparable:**
> - Java `benchmarkParseAndConvert` parses AND converts rows into typed Java POJOs via schema adapters; Rust produces raw positional `JsonTRow` values with no type conversion.
> - Java `benchmarkStringify` serialises complex object graphs (marketplace schema with nested objects); Rust serialises pre-built flat `JsonTRow` values.
> - Java measurements use JMH (3 × 30 s measurement iterations with warmup); Rust uses wall-clock single-shot timing.
> - The Java marketplace schema is substantially larger per record (~1 KB/rec JsonT) vs the Rust Order schema (~38 bytes/rec).

| Operation    | Record Count | Java (ms)     | Java rec/s   | Rust (ms)  | Rust rec/s     |
|:-------------|:------------:|:-------------:|:------------:|:----------:|:--------------:|
| Parse        |    100,000   |   9,254.63    |   ~10.8 K    |    101.35  |     ~990 K     |
| Parse        |  1,000,000   |  99,761.47    |   ~10.0 K    |    923.20  |   ~1,083 K     |
| Stringify    |    100,000   |   2,006.63    |   ~49.8 K    |     31.68  |   ~3,157 K     |
| Stringify    |  1,000,000   |  25,224.34    |   ~39.6 K    |    373.16  |   ~2,681 K     |

Taking data throughput into account (bytes processed per second) narrows the gap considerably, since Java records are ~25× larger on disk. The record-count ratio overstates the difference; the byte-throughput ratio is a more honest comparison once schema complexity is equalised.

---

### Comparison with Java (Round 1) — equivalent marketplace schema

Now that the Rust implementation uses the same marketplace Order schema (~541 B/rec JsonT), a byte-throughput comparison is meaningful.

> **Caveats remain:** Java uses JMH warmup + 3×30 s averaging; Rust uses single-shot wall-clock. Java does full POJO conversion; Rust produces `JsonTRow`. Rust build+stringify includes row construction time.

| Operation          | Record Count | Java (ms)   | Java MB/s  | Rust (ms)    | Rust MB/s  | Ratio (Rust/Java) |
|:-------------------|:------------:|:-----------:|:----------:|:------------:|:----------:|:-----------------:|
| Parse (streaming)  |   10,000,000 |     —       |   —        |  53,360.00   |  ~95.0 ³   |  —                |
| Build + Stringify  |    100,000   |   2,006.63  |  ~50.8     |   2,410.00   |  ~21.3     |  **~0.4×** ¹      |
| Build + Stringify  |  1,000,000   |  25,224.34  |  ~40.4     |  15,190.00   |  ~34.0     |  **~0.8×**        |
| Build + Stringify  | 10,000,000   |     —       |   —        | 127,100.00   |  ~39.9 ²   |  —                |

> ¹ Build+stringify at 100K includes constructing all nested `String`/`JsonTRow`/`JsonTArray` allocations from scratch. Java `benchmarkStringify` serialises already-materialised Java objects; the construction cost is not included in Java's measurement, making direct comparison difficult.
>
> ² At 10M the streaming build loop achieves ~39.9 MB/s write throughput. Each row is written immediately and dropped; the dataset size is irrelevant to memory.
>
> ³ Streaming parse at 10M (5.07 GB file) uses only **14.45 KB peak heap** — O(1) memory via `BufRead::fill_buf()` + `consume()`. At ~95 MB/s the 5 GB file parses in ~53 seconds on a single thread.

---

## File size comparison

| No of records | Json File Size                            | JsonT File Size                             | Schema Size | % Reduction<br/>JsonT/Json | % Schema space     | 
|:--------------|:------------------------------------------|:--------------------------------------------|:------------|:---------------------------|:-------------------|
| 1             | 3,650 [json](./marketplace_data-1.json)   | 2,288  [jsont](./marketplace_data-1.jsont)  | 1200        | 37.3%                      | 52.44%             |
| 10            | 42,528 [json](./marketplace_data-10.json) | 12,047 [jsont](./marketplace_data-10.jsont) | == Same ==  | 28.33%                     | 9.96%              |
| 100           | 391,296                                   | 98,141                                      | == Same ==  | 25.08%                     | 1.22%              |
| 1_000         | 4,096,064                                 | 1,024,568                                   | == Same ==  | 25.01%                     | 0.11% (negligible) |
| 10_000        | 40,839,489                                | 10,220,048                                  | == Same ==  | 25%                        | negligible         |
| 100_000       | 407,598,275                               | 101,976,245                                 | == same ==  | == same ==                 | negligible         |
| 200_000       | 815,465,540                               | 204,013,581                                 | == same ==  | == same ==                 | negligible         |
| 500_000       | 2,037,135,474                             | 509,657,941                                 | == same ==  | == same ==                 | negligible         |
| 1_000_000     | 4,072,728,905                             | 1,018,924,832                               | == same ==  | == same ==                 | negligible         |
