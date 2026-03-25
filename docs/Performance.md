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
|       1,000  |      0.25  |    4.0M rec/s    |          0.29  |       3.4M rec/s     |   36.6 KB  |
|     100,000  |     25.86  |    3.9M rec/s    |         20.62  |       4.9M rec/s     |    3.8 MB  |
|   1,000,000  |    264.25  |    3.8M rec/s    |        198.43  |       5.0M rec/s     |   38.7 MB  |
|  10,000,000  |  2,480.00  |    4.0M rec/s    |      2,180.00  |       4.6M rec/s     |  396.2 MB  |

> **Build** = constructing `N` `JsonTRow` values in a `Vec` using `JsonTRowBuilder`.<br />
> **Stringify** = writing each row directly to a `BufWriter<File>` via `write_row()` — no intermediate `String` allocated per row.

### Step 2 — Parse file into memory

| Record Count | File size  | Parse (ms) | Parse throughput | Peak heap (load + parse) | Heap / file ratio |
|:-------------|:----------:|:----------:|:----------------:|:------------------------:|:-----------------:|
|       1,000  |   36.6 KB  |       0.23 |   ~4.3M rec/s    |                 326 KB   |             8.9×  |
|     100,000  |    3.8 MB  |      27.88 |    3.7M rec/s    |               32.7 MB    |             8.7×  |
|   1,000,000  |   38.7 MB  |     299.72 |    3.3M rec/s    |              321.9 MB    |             8.3×  |
|  10,000,000  |  396.2 MB  |   3,030.00 |    3.3M rec/s    |                3.3 GB    |             8.5×  |

> **Peak heap** covers the entire load-and-parse operation: reading the file into a `String` buffer plus building all `JsonTRow` values. The ~8.5× ratio reflects the in-memory row representation (Vec headers + heap-allocated strings per field). The input `String` buffer is included.

### Internal improvement: streaming scanner vs pest tree

The initial Rust implementation used the pest PEG parser for data rows — the same parser used for namespace/schema parsing. This table shows the impact of replacing it with a hand-written byte scanner for the data-row path:

| Implementation | Parse 100K (ms) | Parse 100K rec/s | Peak heap 1M input | Heap / file ratio |
|:---------------|:---------------:|:----------------:|:------------------:|:-----------------:|
| pest (tree)    |          301    |       332 K      |         3.79 GB    |           **90×** |
| Scanner (new)  |           28    |     3,700 K      |          322 MB    |            **8×** |
| **Improvement**| **10.8×**       |   **11.1×**      |    **11.8×**       |                   |

The pest parser materialises every token for every row into a `Pair<Rule>` tree with `Rc` overhead before walking a single node. A 42 MB file produced a 3.8 GB tree — 90× amplification. The streaming scanner processes bytes left-to-right and emits one `JsonTRow` to a callback as each row completes, keeping memory proportional to the parsed rows themselves rather than the raw text.

### Comparison with Java (Round 1)

> **Important caveats — these benchmarks are not directly comparable:**
> - Java `benchmarkParseAndConvert` parses AND converts rows into typed Java POJOs via schema adapters; Rust produces raw positional `JsonTRow` values with no type conversion.
> - Java `benchmarkStringify` serialises complex object graphs (marketplace schema with nested objects); Rust serialises pre-built flat `JsonTRow` values.
> - Java measurements use JMH (3 × 30 s measurement iterations with warmup); Rust uses wall-clock single-shot timing.
> - The Java marketplace schema is substantially larger per record (~1 KB/rec JsonT) vs the Rust Order schema (~38 bytes/rec).

| Operation    | Record Count | Java (ms)     | Java rec/s   | Rust (ms)  | Rust rec/s     |
|:-------------|:------------:|:-------------:|:------------:|:----------:|:--------------:|
| Parse        |    100,000   |   9,254.63    |   ~10.8 K    |     27.88  |   ~3,700 K     |
| Parse        |  1,000,000   |  99,761.47    |   ~10.0 K    |    299.72  |   ~3,340 K     |
| Stringify    |    100,000   |   2,006.63    |   ~49.8 K    |     20.62  |   ~4,850 K     |
| Stringify    |  1,000,000   |  25,224.34    |   ~39.6 K    |    198.43  |   ~5,040 K     |

Taking data throughput into account (bytes processed per second) narrows the gap considerably, since Java records are ~25× larger on disk. The record-count ratio overstates the difference; the byte-throughput ratio is a more honest comparison once schema complexity is equalised.

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
