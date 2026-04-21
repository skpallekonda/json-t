// =============================================================================
// benchmark/BenchmarkTest.java
// =============================================================================
// Performance benchmarks using the ICC Men's T20 World Cup schema
// (code/benchmark-schema/wct20-match.jsont — 92 fields, ~1 KB/row).
//
// Excluded from the normal Maven test run. Enable with:
//   mvn test -Pbenchmark
//
// Four benchmark options (each measured independently per size):
//   1. Stringify   — build N rows in memory, stringify+write to temp file
//   2. Parse       — parse rows from the file produced by option 1
//   3. Validate    — parse + ValidationPipeline (constraint + rule checks)
//   4. Transform   — parse + validate + apply MatchBroadcastSummary operations
//
// Five dataset sizes (parallel workers for 100K+):
//   1_000 | 10_000 | 100_000 | 1_000_000 | 10_000_000
//
// Measurements per operation:
//   - Throughput (rows / ms)
//   - Approximate heap delta (via Runtime.totalMemory - freeMemory)
//   - Total wall-clock time
//   - File size (stringify only)
// =============================================================================
package io.github.datakore.jsont.benchmark;

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.diagnostic.DiagnosticSink;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.json.JsonInputMode;
import io.github.datakore.jsont.json.JsonOutputMode;
import io.github.datakore.jsont.json.JsonReader;
import io.github.datakore.jsont.json.JsonWriter;
import io.github.datakore.jsont.model.FieldKind;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.parse.JsonTParser;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.transform.RowTransformer;
import io.github.datakore.jsont.validate.ValidationPipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Performance benchmarks against the WCT20 schema.
 * Run with: {@code mvn test -Pbenchmark}
 */
@Tag("benchmark")
class BenchmarkTest {

    // ─── Schema file path (relative to Maven module dir: code/java/jsont/) ────
    private static final Path SCHEMA_PATH =
            Path.of("../../benchmark-schema/wct20-match.jsont");

    // ─── Enum constants cycling through schema enum values ───────────────────
    private static final String[] PHASES =
            {"GROUP_STAGE", "SUPER_8", "SEMI_FINAL", "FINAL"};
    private static final String[] SURFACES =
            {"GRASS", "HARD_CLAY", "RED_SOIL", "DRY_FLAT", "WET_FAST"};
    private static final String[] MATCH_STATUSES =
            {"SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ABANDONED"};
    private static final String[] TOSS_CHOICES = {"BAT", "BOWL"};

    // =========================================================================
    // Row generation — CricketMatch schema (92 fields, ~1 KB/row)
    // All rows satisfy every declared constraint and validation rule.
    // Optional fields (marked ?) are always null for predictable row size.
    // =========================================================================

    private static JsonTRow makeCricketRow(long i) {
        String matchUuid = String.format("550e8400-e29b-41d4-a716-%012x", i & 0xffffffffffffL);
        String matchCode = String.format("T20WC24-%04d", i % 10_000);
        BigDecimal prizeMoney = new BigDecimal(500_000 + (i % 100_000)).movePointLeft(2);
        int idx = (int) (i & Integer.MAX_VALUE);

        return JsonTRow.of(
            // ── Match identity (7 fields) ──────────────────────────────────────
            JsonTValue.text(matchUuid),                                     // uuid:  matchId
            JsonTValue.u64(i),                                              // u64:   iccMatchSequenceId
            JsonTValue.text(matchCode),                                     // str:   matchCode [6..25]
            JsonTValue.text("ICC Men T20 World Cup"),                       // str:   tournamentName [5..80]
            JsonTValue.u16((int) (i % 999 + 1)),                            // u16:   matchNumber [1..999]
            JsonTValue.nullValue(),                                         // str?:  groupName
            JsonTValue.enumValue(PHASES[idx % PHASES.length]),              // enum:  phase
            // ── Team A (7 fields) ──────────────────────────────────────────────
            JsonTValue.text("IND"),                                         // str:   teamAId [2..10]
            JsonTValue.text("India"),                                       // str:   teamAName [3..60]
            JsonTValue.text("IND"),                                         // str:   teamAIccCode [3..3]
            JsonTValue.u16((int) (i % 100 + 1)),                            // u16:   teamAWorldRanking [1..100]
            JsonTValue.d64(850.0 + (i % 200) * 0.5),                       // d64:   teamARatingPoints [0..]
            JsonTValue.text("Rahul Dravid"),                                // str:   teamACoachName [3..80]
            JsonTValue.nullValue(),                                         // email? teamAContactEmail
            // ── Team B (7 fields) ──────────────────────────────────────────────
            JsonTValue.text("ENG"),                                         // str:   teamBId [2..10]
            JsonTValue.text("England"),                                     // str:   teamBName [3..60]
            JsonTValue.text("ENG"),                                         // str:   teamBIccCode [3..3]
            JsonTValue.u16((int) (i % 100 + 2)),                            // u16:   teamBWorldRanking [1..100]
            JsonTValue.d64(820.0 + (i % 180) * 0.5),                       // d64:   teamBRatingPoints [0..]
            JsonTValue.text("Matthew Mott"),                                // str:   teamBCoachName [3..80]
            JsonTValue.nullValue(),                                         // email? teamBContactEmail
            // ── Venue (11 fields) ──────────────────────────────────────────────
            JsonTValue.text("MCG-01"),                                      // str:   venueId [3..20]
            JsonTValue.text("Melbourne Cricket Ground"),                    // str:   venueName [3..100]
            JsonTValue.text("Melbourne"),                                   // str:   city [2..60]
            JsonTValue.text("Australia"),                                   // str:   country [2..60]
            JsonTValue.i32(100_024),                                        // i32:   venueCapacity [1000..200000]
            JsonTValue.bool(true),                                          // bool:  isFloodlit
            JsonTValue.bool(false),                                         // bool:  isNeutralVenue
            JsonTValue.d64(37.82),                                          // d64:   venueLatitude [..90]
            JsonTValue.d64(144.98),                                         // d64:   venueLongitude [..180]
            JsonTValue.enumValue(SURFACES[idx % SURFACES.length]),          // enum:  wicketSurface
            JsonTValue.nullValue(),                                         // hostname? statsApiHost
            // ── Schedule (4 fields) ────────────────────────────────────────────
            JsonTValue.text("2024-06-15T14:30:00Z"),                        // datetime: scheduledAt
            JsonTValue.text("14:30:00"),                                    // time:     matchStartLocalTime
            JsonTValue.text("PT3H"),                                        // duration: expectedDuration
            JsonTValue.bool(true),                                          // bool:     isDayNight
            // ── Toss (3 fields) ────────────────────────────────────────────────
            JsonTValue.text("IND"),                                         // str:  tossWinnerTeamId [2..10]
            JsonTValue.enumValue(TOSS_CHOICES[idx % 2]),                    // enum: tossDecision
            JsonTValue.bool(true),                                          // bool: teamABattedFirst
            // ── Team A innings (9 fields) ──────────────────────────────────────
            JsonTValue.u32((long) (150 + i % 100)),                         // u32:  teamAInnings1Runs [0..300]
            JsonTValue.u16((int) (i % 11)),                                 // u16:  teamAInnings1Wickets [0..10]
            JsonTValue.u16((int) (100 + i % 20)),                           // u16:  teamAInnings1Balls [0..120]
            JsonTValue.d64(7.5 + (i % 50) * 0.1),                          // d64:  teamAInnings1RunRate [0..]
            JsonTValue.text("Virat Kohli"),                                 // str:  teamATopBatsmanName [3..80]
            JsonTValue.u32((long) (50 + i % 100)),                          // u32:  teamATopBatsmanRuns [0..200]
            JsonTValue.text("Jasprit Bumrah"),                              // str:  teamATopBowlerName [3..80]
            JsonTValue.u16((int) (i % 5)),                                  // u16:  teamATopBowlerWickets [0..10]
            JsonTValue.d32((float) (6.5 + (i % 30) * 0.1)),                // d32:  teamATopBowlerEconomy [0..]
            // ── Team B innings (9 fields) ──────────────────────────────────────
            JsonTValue.u32((long) (145 + i % 100)),                         // u32:  teamBInnings1Runs [0..300]
            JsonTValue.u16((int) (i % 11)),                                 // u16:  teamBInnings1Wickets [0..10]
            JsonTValue.u16((int) (98 + i % 20)),                            // u16:  teamBInnings1Balls [0..120]
            JsonTValue.d64(7.2 + (i % 50) * 0.1),                          // d64:  teamBInnings1RunRate [0..]
            JsonTValue.text("Joe Root"),                                     // str:  teamBTopBatsmanName
            JsonTValue.u32((long) (45 + i % 100)),                          // u32:  teamBTopBatsmanRuns [0..200]
            JsonTValue.text("Jofra Archer"),                                // str:  teamBTopBowlerName
            JsonTValue.u16((int) (i % 5)),                                  // u16:  teamBTopBowlerWickets [0..10]
            JsonTValue.d32((float) (7.0 + (i % 30) * 0.1)),                // d32:  teamBTopBowlerEconomy [0..]
            // ── Match outcome (12 fields) ──────────────────────────────────────
            JsonTValue.enumValue(MATCH_STATUSES[idx % MATCH_STATUSES.length]), // enum: matchStatus
            JsonTValue.nullValue(),                                         // str?:  winnerTeamId
            JsonTValue.text("India won by 15 runs"),                        // str:   resultDescription [5..250]
            JsonTValue.i32((int) (i % 200)),                                // i32:   winMarginRuns [0..200]
            JsonTValue.i16((short) (i % 11)),                               // i16:   winMarginWickets [0..10]
            JsonTValue.bool(false),                                         // bool:  isSuperOver
            JsonTValue.d64(0.5 + (i % 100) * 0.01),                        // d64:   netRunRateDiffTeamA
            JsonTValue.d64(0.3 + (i % 80) * 0.01),                         // d64:   netRunRateDiffTeamB
            JsonTValue.i16((short) (i % 3)),                                // i16:   teamAGroupPoints [0..2]
            JsonTValue.i16((short) (i % 3)),                                // i16:   teamBGroupPoints [0..2]
            JsonTValue.d128(prizeMoney),                                    // d128:  prizeMoneySplit [0..]
            JsonTValue.i64(10_800L + i % 3_600L),                          // i64:   matchDurationSeconds [0..]
            // ── Player of the match (3 optional) ──────────────────────────────
            JsonTValue.nullValue(),                                         // uuid?: manOfTheMatchId
            JsonTValue.nullValue(),                                         // str?:  manOfTheMatchName
            JsonTValue.nullValue(),                                         // str?:  manOfTheMatchTeamId
            // ── Officials (4 fields) ───────────────────────────────────────────
            JsonTValue.text("Aleem Dar"),                                   // str:  umpire1Name [3..80]
            JsonTValue.text("Kumar Dharmasena"),                            // str:  umpire2Name [3..80]
            JsonTValue.nullValue(),                                         // str?: tvUmpireName
            JsonTValue.text("Ranjan Madugalle"),                            // str:  matchRefereeName [3..80]
            // ── Weather (5 fields) ─────────────────────────────────────────────
            JsonTValue.bool(false),                                         // bool:  isRainAffected
            JsonTValue.nullValue(),                                         // i16?:  dlsOversReduction
            JsonTValue.nullValue(),                                         // d64?:  dlsTargetRevised
            JsonTValue.nullValue(),                                         // d32?:  avgTemperatureCelsius
            JsonTValue.u32(50_000L + (i % 50_000)),                         // u32?:  attendanceCount
            // ── Broadcast & metadata (12 fields) ──────────────────────────────
            JsonTValue.bool(true),                                          // bool:  isHighlightsAvailable
            JsonTValue.nullValue(),                                         // i64?:  totalViewership
            JsonTValue.text("en,hi,ta"),                                    // str:   broadcastLanguages [2..200]
            JsonTValue.nullValue(),                                         // uri?:  dataSourceUrl
            JsonTValue.nullValue(),                                         // hex?:  matchContentHash
            JsonTValue.nullValue(),                                         // base64? matchCertificate
            JsonTValue.nullValue(),                                         // ipv4?: statsServerIpv4
            JsonTValue.nullValue(),                                         // ipv6?: broadcastServerIpv6
            JsonTValue.text("ICC Official API"),                            // str:   dataSource [1..100]
            JsonTValue.i64(1_718_460_600_000L + i),                        // timestamp: createdAt
            JsonTValue.i64(1_718_460_600_000L + i + 3_600_000L)            // timestamp: updatedAt
        );
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    private static String fmtBytes(long n) {
        if (n >= 1L << 30) return String.format("%.2f GB", n / (double) (1L << 30));
        if (n >= 1L << 20) return String.format("%.2f MB", n / (double) (1L << 20));
        if (n >= 1L << 10) return String.format("%.2f KB", n / (double) (1L << 10));
        return n + " B";
    }

    private static String fmtMs(long ns) {
        double ms = ns / 1_000_000.0;
        return ms >= 1_000.0 ? String.format("%.2fs", ms / 1_000.0) : String.format("%.2fms", ms);
    }

    private static String fmtCount(long n) {
        if (n >= 1_000_000) return n / 1_000_000 + "M";
        if (n >= 1_000) return n / 1_000 + "K";
        return String.valueOf(n);
    }

    /** Approximate heap used = (total allocated) - (free). GC before measuring for consistency. */
    private static long heapSnapshot() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    // =========================================================================
    // Schema loading — parsed once per benchmark run
    // =========================================================================

    private record Schemas(JsonTSchema cricket, JsonTSchema broadcast, SchemaRegistry registry) {}

    private static Schemas loadSchemas() throws IOException {
        String text = Files.readString(SCHEMA_PATH);
        JsonTNamespace ns = JsonTParser.parseNamespace(text);
        SchemaRegistry registry = SchemaRegistry.empty();
        for (var catalog : ns.catalogs()) {
            for (var schema : catalog.schemas()) {
                registry = registry.register(schema);
            }
        }
        JsonTSchema cricket = registry.resolve("CricketMatch")
                .orElseThrow(() -> new IllegalStateException("CricketMatch not found"));
        JsonTSchema broadcast = registry.resolve("MatchBroadcastSummary")
                .orElseThrow(() -> new IllegalStateException("MatchBroadcastSummary not found"));
        return new Schemas(cricket, broadcast, registry);
    }

    // =========================================================================
    // Step 1 — Stringify
    // Build N rows in memory then write to a temp file, measuring separately.
    // =========================================================================

    // Streaming stringify: build and write each row immediately — O(1) memory.
    // Build and write are combined in one pass; times are measured per-row using
    // nanoTime accumulators (overhead ~30ns/row, <1% at 1M+ rows).
    private record StringifyResult(Path path, long buildNs, long stringifyNs, long fileBytes) {}

    private StringifyResult stepStringify(int count) throws IOException {
        Path path = Path.of(System.getProperty("java.io.tmpdir"),
                "jsont_wct_bench_" + count + ".jsont");
        long buildNs = 0L, stringifyNs = 0L;
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < count; i++) {
                long tb = System.nanoTime();
                JsonTRow row = makeCricketRow(i);
                buildNs += System.nanoTime() - tb;

                long ts = System.nanoTime();
                RowWriter.writeRow(row, w);
                if (i < count - 1) w.write(",\n");
                stringifyNs += System.nanoTime() - ts;
            }
        }
        return new StringifyResult(path, buildNs, stringifyNs, Files.size(path));
    }

    // =========================================================================
    // Step 2 — Parse Only
    // =========================================================================

    // Streaming parse: O(1) memory — one row live at a time.
    private record ParseResult(int count, long parseNs, double rowsPerMs, long loadedBytes, long peakHeap) {}

    private ParseResult stepParse(Path path) throws IOException {
        long loadedBytes = Files.size(path);
        Runtime.getRuntime().gc();
        long memBefore = heapSnapshot();

        int[] count = {0};
        long t0 = System.nanoTime();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonTParser.parseRowsStreaming(reader, row -> count[0]++);
        }
        long parseNs = System.nanoTime() - t0;

        long peakHeap = Math.max(0, heapSnapshot() - memBefore);
        double ms = Math.max(1, parseNs / 1_000_000.0);
        return new ParseResult(count[0], parseNs, count[0] / ms, loadedBytes, peakHeap);
    }

    // =========================================================================
    // Step 3 — Parse + Validate
    // Sequential for small sizes (< 100K); parallel workers for larger sizes.
    // =========================================================================

    // Streaming validate: file → BufferedReader → lazy row stream/iterator fed
    // directly into the pipeline — no String buffer, no row List in memory.
    private record ValidateResult(int count, long elapsedNs, double rowsPerMs, int clean, long peakHeap) {}

    private ValidateResult stepValidate(Path path, JsonTSchema cricket, boolean parallel) throws IOException {
        DiagnosticSink nullSink = event -> {};
        ValidationPipeline pipeline = ValidationPipeline.builder(cricket)
                .withoutConsole()
                .withSink(nullSink)
                .workers(parallel ? Runtime.getRuntime().availableProcessors() : 1)
                .bufferCapacity(512)
                .build();

        Runtime.getRuntime().gc();
        long memBefore = heapSnapshot();

        int[] clean = {0};
        long t0 = System.nanoTime();
        if (parallel) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                 var rowStream = JsonTParser.parseRowsStreaming(reader);
                 var validated = pipeline.validateStream(rowStream)) {
                validated.forEach(r -> clean[0]++);
            }
        } else {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                 var rowStream = JsonTParser.parseRowsStreaming(reader)) {
                pipeline.validateEach(() -> rowStream.iterator(), r -> clean[0]++);
            }
        }
        long elapsedNs = System.nanoTime() - t0;
        pipeline.finish();

        long peakHeap = Math.max(0, heapSnapshot() - memBefore);
        double ms = Math.max(1, elapsedNs / 1_000_000.0);
        // All benchmark rows are valid → clean == count
        return new ValidateResult(clean[0], elapsedNs, clean[0] / ms, clean[0], peakHeap);
    }

    // =========================================================================
    // Step 4 — Parse + Validate + Transform
    // Applies MatchBroadcastSummary operations to each clean row.
    // =========================================================================

    // Streaming transform: same streaming pattern as stepValidate — no row List.
    private record TransformResult(int count, long elapsedNs, double rowsPerMs, int transformed, long peakHeap) {}

    private TransformResult stepTransform(Path path, JsonTSchema cricket, JsonTSchema broadcast,
                                          SchemaRegistry registry, boolean parallel) throws Exception {
        DiagnosticSink nullSink = event -> {};
        ValidationPipeline pipeline = ValidationPipeline.builder(cricket)
                .withoutConsole()
                .withSink(nullSink)
                .workers(parallel ? Runtime.getRuntime().availableProcessors() : 1)
                .bufferCapacity(512)
                .build();
        RowTransformer transformer = RowTransformer.of(broadcast, registry);

        Runtime.getRuntime().gc();
        long memBefore = heapSnapshot();

        int[] transformed = {0};
        long t0 = System.nanoTime();
        if (parallel) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                 var rowStream = JsonTParser.parseRowsStreaming(reader);
                 var validated = pipeline.validateStream(rowStream)) {
                validated.forEach(row -> {
                    try { transformer.transform(row); transformed[0]++; }
                    catch (JsonTError.Transform e) { throw new RuntimeException(e); }
                });
            }
        } else {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                 var rowStream = JsonTParser.parseRowsStreaming(reader)) {
                pipeline.validateEach(() -> rowStream.iterator(), row -> {
                    try { transformer.transform(row); transformed[0]++; }
                    catch (JsonTError.Transform e) { throw new RuntimeException(e); }
                });
            }
        }
        long elapsedNs = System.nanoTime() - t0;
        pipeline.finish();

        long peakHeap = Math.max(0, heapSnapshot() - memBefore);
        double ms = Math.max(1, elapsedNs / 1_000_000.0);
        return new TransformResult(transformed[0], elapsedNs, transformed[0] / ms, transformed[0], peakHeap);
    }

    // =========================================================================
    // JSON flat schema helper
    // =========================================================================

    /**
     * Build a flat straight schema for JSON reading.
     *
     * {@code JsonWriter} serializes enum fields (FieldKind.OBJECT) as plain JSON
     * strings.  {@code JsonReader} cannot handle Object-kind fields, so we replace
     * them with optional {@code str} scalars for round-trip reading.
     * All scalar fields are preserved as-is.
     */
    /**
     * Build a flat straight schema for JSON reading.
     *
     * <p>{@code JsonWriter} writes values by their runtime {@code JsonTValue} variant, not
     * their schema type.  Benchmark rows store temporal fields (datetime, time, duration) as
     * {@code JsonTValue.text(...)}, so the writer emits them as JSON strings.  {@code JsonReader}
     * then tries to coerce those strings using the schema type — and {@code datetime}/{@code time}
     * only accept JSON integers, causing a type error.
     *
     * <p>Fix: map every field to a JSON-compatible reading type:
     * <ul>
     *   <li>{@code BOOL}              → {@code BOOL}   (JSON true/false)</li>
     *   <li>Pure numeric              → keep           (JSON numbers read back correctly)</li>
     *   <li>Everything else           → {@code STR}    ({@code coerce_scalar(STR)} accepts JSON
     *       strings <em>and</em> JSON integers, so integer-valued timestamps work too)</li>
     *   <li>Object (enum)             → {@code STR}    (enum values are written as JSON strings)</li>
     * </ul>
     */
    private static JsonTSchema flatSchemaForJson(JsonTSchema schema) throws Exception {
        JsonTSchemaBuilder builder = JsonTSchemaBuilder.straight(schema.name());
        for (JsonTField field : schema.fields()) {
            ScalarType scalar;
            if (field.kind().isScalar()) {
                scalar = switch (field.scalarType()) {
                    case BOOL -> ScalarType.BOOL;
                    case I16, I32, I64, U16, U32, U64, D32, D64, D128 -> field.scalarType();
                    // All string-semantic and temporal types → STR.
                    // coerce_scalar(STR) accepts JSON strings *and* JSON integers,
                    // so integer-stored timestamps round-trip correctly too.
                    default -> ScalarType.STR;
                };
            } else {
                // Object/enum fields are written as JSON strings
                scalar = ScalarType.STR;
            }
            JsonTFieldBuilder fb = JsonTFieldBuilder.scalar(field.name(), scalar);
            if (field.optional()) fb = fb.optional();
            builder.fieldFrom(fb);
        }
        return builder.build();
    }

    // =========================================================================
    // JSON steps — Stringify and Parse using NDJSON format
    // =========================================================================

    private record JsonStringifyResult(Path path, long writeNs, long fileBytes) {}

    /** Write each row as JSON NDJSON — row build time excluded (same as JsonT). */
    private JsonStringifyResult stepStringifyJson(int count, JsonTSchema schema) throws IOException {
        Path path = Path.of(System.getProperty("java.io.tmpdir"),
                "jsont_wct_bench_" + count + ".json");
        JsonWriter writer = JsonWriter.withSchema(schema).mode(JsonOutputMode.NDJSON).build();

        long writeNs = 0L;
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < count; i++) {
                JsonTRow row = makeCricketRow(i);
                long ts = System.nanoTime();
                writer.writeRow(row, w);
                w.write('\n');
                writeNs += System.nanoTime() - ts;
            }
        }
        return new JsonStringifyResult(path, writeNs, Files.size(path));
    }

    /** Stream-parse JSON NDJSON from file using the flat schema (enum fields → str). */
    private ParseResult stepParseJson(Path path, JsonTSchema schema) throws Exception {
        long loadedBytes = Files.size(path);
        Runtime.getRuntime().gc();
        long memBefore = heapSnapshot();

        JsonTSchema flat = flatSchemaForJson(schema);
        JsonReader reader = JsonReader.withSchema(flat).mode(JsonInputMode.NDJSON).build();

        int[] count = {0};
        long t0 = System.nanoTime();
        try (var br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.readStreaming(br, row -> count[0]++);
        }
        long parseNs = System.nanoTime() - t0;

        long peakHeap = Math.max(0, heapSnapshot() - memBefore);
        double ms = Math.max(1, parseNs / 1_000_000.0);
        return new ParseResult(count[0], parseNs, count[0] / ms, loadedBytes, peakHeap);
    }

    // =========================================================================
    // Core benchmark runner — all 4 options for a given count
    // =========================================================================

    private void runBenchmark(int count, boolean withJson) throws Exception {
        boolean parallel = count >= 100_000;
        String label = fmtCount(count);
        int cpus = Runtime.getRuntime().availableProcessors();
        System.out.printf("  Schema : CricketMatch (92 fields, ~1 KB/row) | MatchBroadcastSummary (derived)%n");
        System.out.printf("  Workers: %s (%d CPUs available)%n",
                parallel ? cpus + " parallel" : "1 (sequential)", cpus);

        Schemas s = loadSchemas();

        // ── [1] Stringify (JsonT) ─────────────────────────────────────────────
        StringifyResult sr = stepStringify(count);
        System.out.printf("  [1] Stringify (JsonT) %6s rows | build %10s | write %10s | file %12s%n",
                label, fmtMs(sr.buildNs()), fmtMs(sr.stringifyNs()), fmtBytes(sr.fileBytes()));

        // ── [2] Stringify (JSON) — optional ──────────────────────────────────
        JsonStringifyResult jsonSr = null;
        if (withJson) {
            jsonSr = stepStringifyJson(count, s.cricket());
            double sizePct = ((double) jsonSr.fileBytes() / sr.fileBytes() - 1.0) * 100.0;
            System.out.printf("  [2] Stringify (JSON ) %6s rows |              | write %10s | file %12s | %+.1f%% vs JsonT%n",
                    label, fmtMs(jsonSr.writeNs()), fmtBytes(jsonSr.fileBytes()), sizePct);
        }

        // ── [3] Parse (JsonT) ─────────────────────────────────────────────────
        int jsontParseStep = withJson ? 3 : 2;
        ParseResult pr = stepParse(sr.path());
        System.out.printf("  [%d] Parse     (JsonT) %6s rows | time  %10s | %8.1f rows/ms | loaded %12s | peak %10s%n",
                jsontParseStep, label, fmtMs(pr.parseNs()), pr.rowsPerMs(),
                fmtBytes(pr.loadedBytes()), fmtBytes(pr.peakHeap()));

        // ── [4] Parse (JSON) — optional ───────────────────────────────────────
        if (withJson && jsonSr != null) {
            ParseResult jpr = stepParseJson(jsonSr.path(), s.cricket());
            System.out.printf("  [4] Parse     (JSON ) %6s rows | time  %10s | %8.1f rows/ms | loaded %12s | peak %10s%n",
                    label, fmtMs(jpr.parseNs()), jpr.rowsPerMs(),
                    fmtBytes(jpr.loadedBytes()), fmtBytes(jpr.peakHeap()));
            Files.deleteIfExists(jsonSr.path());
        }

        // ── Parse + Validate ──────────────────────────────────────────────────
        int stepVal = withJson ? 5 : 3;
        ValidateResult vr = stepValidate(sr.path(), s.cricket(), parallel);
        System.out.printf("  [%d] Parse+Val        %6s rows | time  %10s | %8.1f rows/ms | clean %6s | peak %10s%n",
                stepVal, label, fmtMs(vr.elapsedNs()), vr.rowsPerMs(),
                fmtCount(vr.clean()), fmtBytes(vr.peakHeap()));

        // ── Parse + Validate + Transform ──────────────────────────────────────
        int stepXfm = withJson ? 6 : 4;
        TransformResult tr = stepTransform(sr.path(), s.cricket(), s.broadcast(), s.registry(), parallel);
        System.out.printf("  [%d] Parse+Val+T      %6s rows | time  %10s | %8.1f rows/ms | xfrmd %6s | peak %10s%n",
                stepXfm, label, fmtMs(tr.elapsedNs()), tr.rowsPerMs(),
                fmtCount(tr.transformed()), fmtBytes(tr.peakHeap()));

        Files.deleteIfExists(sr.path());
    }

    // =========================================================================
    // Test entry points — each size runs independently
    // =========================================================================

    @Test
    void bench_1k() throws Exception {
        System.out.println("\n=== WCT Benchmark  1K records ===");
        runBenchmark(1_000, true);
    }

    @Test
    void bench_10k() throws Exception {
        System.out.println("\n=== WCT Benchmark  10K records ===");
        runBenchmark(10_000, true);
    }

    @Test
    void bench_100k() throws Exception {
        System.out.println("\n=== WCT Benchmark  100K records ===");
        runBenchmark(100_000, true);
    }

    @Test
    void bench_1m() throws Exception {
        System.out.println("\n=== WCT Benchmark  1M records ===");
        runBenchmark(1_000_000, true);
    }

    /** Requires significant RAM and several minutes. JSON steps excluded at 10M. */
    @Test
    void bench_10m() throws Exception {
        System.out.println("\n=== WCT Benchmark  10M records ===");
        runBenchmark(10_000_000, false);
    }
}
