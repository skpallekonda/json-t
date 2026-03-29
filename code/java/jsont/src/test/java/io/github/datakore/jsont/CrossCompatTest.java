package io.github.datakore.jsont;

import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.parse.JsonTParser;
import io.github.datakore.jsont.stringify.RowWriter;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-language compatibility tests using the WCT20 CricketMatch schema.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>{@link #crossCompatGenerate} — writes {@code code/cross-compat/java-generated.jsont},
 *       exercising every optional-field permutation and every enum variant across 10 rows.
 *   <li>{@link #crossCompatParseRust} — reads the committed
 *       {@code code/cross-compat/rust-generated.jsont} and spot-checks values, confirming
 *       grammar parity between the Rust and Java implementations.
 * </ol>
 *
 * <p>Row coverage matrix (mirrors Rust cross_compat_tests.rs exactly):
 * <pre>
 *  i | phase       | surface    | status      | toss | notable optionals
 *  --|-------------|------------|-------------|------|----------------------------
 *  0 | GROUP_STAGE | GRASS      | SCHEDULED   | BAT  | groupName, winnerTeamId, tvUmpireName
 *  1 | SUPER_8     | HARD_CLAY  | IN_PROGRESS | BOWL | contactEmail, attendance
 *  2 | SEMI_FINAL  | RED_SOIL   | COMPLETED   | BAT  | statsApiHost, winnerTeamId, dataSourceUrl
 *  3 | FINAL       | DRY_FLAT   | CANCELLED   | BOWL | tvUmpireName, avgTemp, attendance
 *  4 | GROUP_STAGE | WET_FAST   | ABANDONED   | BAT  | contactEmail, winnerTeamId, attendance, ipv4/ipv6
 *  5 | SUPER_8     | GRASS      | COMPLETED   | BOWL | groupName, manOfMatch, viewership, dataSourceUrl, attendance
 *  6 | SEMI_FINAL  | HARD_CLAY  | SCHEDULED   | BAT  | statsApiHost, tvUmpireName, winnerTeamId, viewership
 *  7 | FINAL       | RED_SOIL   | IN_PROGRESS | BOWL | contactEmail, manOfMatch, avgTemp, attendance, viewership, rain+DLS
 *  8 | GROUP_STAGE | DRY_FLAT   | COMPLETED   | BAT  | winnerTeamId, manOfMatch, dataSourceUrl, viewership, hash, rain+DLS
 *  9 | SUPER_8     | WET_FAST   | CANCELLED   | BOWL | tvUmpireName, attendance, viewership, hash, cert, ipv4/ipv6, rain+DLS, superOver
 * </pre>
 */
class CrossCompatTest {

    // Paths relative to Maven's working directory (the module root: code/java/jsont)
    private static final Path JAVA_OUT = Path.of("../../cross-compat/java-generated.jsont");
    private static final Path RUST_OUT = Path.of("../../cross-compat/rust-generated.jsont");

    private static final String[] PHASES   = {"GROUP_STAGE", "SUPER_8", "SEMI_FINAL", "FINAL"};
    private static final String[] SURFACES = {"GRASS", "HARD_CLAY", "RED_SOIL", "DRY_FLAT", "WET_FAST"};
    private static final String[] STATUSES = {"SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ABANDONED"};
    private static final String[] TOSS     = {"BAT", "BOWL"};

    // ─── Row factory ──────────────────────────────────────────────────────────

    /** Builds one CricketMatch row with deliberate optional-field variation by {@code i}. */
    private static JsonTRow makeCrossRow(long i) {
        String matchUuid = String.format("cc000000-0000-0000-0000-%012d", i);
        String matchCode = String.format("XCOMPAT-%02d", i);
        BigDecimal prize = new BigDecimal(100_000 + i * 10_000).movePointLeft(2); // 1000.00+i*100.00

        // Optional presence — each field non-null in a different row subset (mirrors Rust exactly).
        JsonTValue groupName      = i % 5 == 0 ? JsonTValue.text("Group A")              : JsonTValue.nullValue();
        JsonTValue contactEmailA  = i % 3 == 1 ? JsonTValue.text("team-a@icc.cricket")   : JsonTValue.nullValue();
        JsonTValue contactEmailB  = i % 3 == 1 ? JsonTValue.text("team-b@icc.cricket")   : JsonTValue.nullValue();
        JsonTValue statsHost      = i % 4 == 2 ? JsonTValue.text("stats.icc.cricket")    : JsonTValue.nullValue();
        JsonTValue winnerId       = i % 2 == 0 ? JsonTValue.text("IND")                  : JsonTValue.nullValue();
        JsonTValue motmId         = i >= 5      ? JsonTValue.text("550e8400-e29b-41d4-a716-000000000099") : JsonTValue.nullValue();
        JsonTValue motmName       = i >= 5      ? JsonTValue.text("Virat Kohli")          : JsonTValue.nullValue();
        JsonTValue motmTeam       = i >= 5      ? JsonTValue.text("IND")                  : JsonTValue.nullValue();
        JsonTValue tvUmpire       = i % 3 == 0  ? JsonTValue.text("Simon Taufel")         : JsonTValue.nullValue();
        JsonTValue dlsRed         = i >= 7      ? JsonTValue.i16((short) 5)               : JsonTValue.nullValue();
        JsonTValue dlsTgt         = i >= 7      ? JsonTValue.d64(145.0)                   : JsonTValue.nullValue();
        JsonTValue avgTemp        = i % 4 == 3  ? JsonTValue.d32(28.5f)                  : JsonTValue.nullValue();
        // attendance > 5000 when present so the broadcast transform filter always passes
        JsonTValue attendance     = i % 2 == 1  ? JsonTValue.u32(45_000L + i * 1_000)    : JsonTValue.nullValue();
        JsonTValue viewership     = i >= 5      ? JsonTValue.i64(50_000_000L + i * 1_000_000) : JsonTValue.nullValue();
        JsonTValue dataUrl        = i % 3 == 2  ? JsonTValue.text("https://api.icc.cricket/data") : JsonTValue.nullValue();
        JsonTValue contentHash    = i >= 8      ? JsonTValue.text("a3f5b8c1d2e4f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1") : JsonTValue.nullValue();
        JsonTValue certificate    = i == 9      ? JsonTValue.text("dGVzdC1jZXJ0aWZpY2F0ZQ==") : JsonTValue.nullValue();
        JsonTValue ipv4           = i % 5 == 4  ? JsonTValue.text("192.168.1.1")          : JsonTValue.nullValue();
        JsonTValue ipv6           = i % 5 == 4  ? JsonTValue.text("2001:db8::1")           : JsonTValue.nullValue();

        List<JsonTValue> v = new ArrayList<>(92);
        // ── Match identity ────────────────────────────────────────────────────
        v.add(JsonTValue.text(matchUuid));                                         // uuid:    matchId
        v.add(JsonTValue.u64(1_000 + i));                                          // u64:     iccMatchSequenceId
        v.add(JsonTValue.text(matchCode));                                         // str:     matchCode
        v.add(JsonTValue.text("ICC Men T20 World Cup"));                           // str:     tournamentName
        v.add(JsonTValue.u16((int) (i % 999) + 1));                               // u16:     matchNumber
        v.add(groupName);                                                          // str?:    groupName
        v.add(JsonTValue.enumValue(PHASES[(int) (i % 4)]));                         // enum:    phase
        // ── Team A ────────────────────────────────────────────────────────────
        v.add(JsonTValue.text("IND"));                                             // str:     teamAId
        v.add(JsonTValue.text("India"));                                           // str:     teamAName
        v.add(JsonTValue.text("IND"));                                             // str:     teamAIccCode
        v.add(JsonTValue.u16(1));                                                  // u16:     teamAWorldRanking
        v.add(JsonTValue.d64(900.0 + i));                                          // d64:     teamARatingPoints
        v.add(JsonTValue.text("Rahul Dravid"));                                    // str:     teamACoachName
        v.add(contactEmailA);                                                      // email?:  teamAContactEmail
        // ── Team B ────────────────────────────────────────────────────────────
        v.add(JsonTValue.text("ENG"));                                             // str:     teamBId
        v.add(JsonTValue.text("England"));                                         // str:     teamBName
        v.add(JsonTValue.text("ENG"));                                             // str:     teamBIccCode
        v.add(JsonTValue.u16(3));                                                  // u16:     teamBWorldRanking
        v.add(JsonTValue.d64(850.0 + i));                                          // d64:     teamBRatingPoints
        v.add(JsonTValue.text("Matthew Mott"));                                    // str:     teamBCoachName
        v.add(contactEmailB);                                                      // email?:  teamBContactEmail
        // ── Venue ─────────────────────────────────────────────────────────────
        v.add(JsonTValue.text("MCG-01"));                                          // str:     venueId
        v.add(JsonTValue.text("Melbourne Cricket Ground"));                        // str:     venueName
        v.add(JsonTValue.text("Melbourne"));                                       // str:     city
        v.add(JsonTValue.text("Australia"));                                       // str:     country
        v.add(JsonTValue.i32(100_024));                                            // i32:     venueCapacity
        v.add(JsonTValue.bool(true));                                              // bool:    isFloodlit
        v.add(JsonTValue.bool(false));                                             // bool:    isNeutralVenue
        v.add(JsonTValue.d64(37.82));                                              // d64:     venueLatitude
        v.add(JsonTValue.d64(144.98));                                             // d64:     venueLongitude
        v.add(JsonTValue.enumValue(SURFACES[(int) (i % 5)]));                       // enum:    wicketSurface
        v.add(statsHost);                                                          // hostname?: statsApiHost
        // ── Schedule ──────────────────────────────────────────────────────────
        v.add(JsonTValue.text("2024-06-15T14:30:00Z"));                            // datetime: scheduledAt
        v.add(JsonTValue.text("14:30:00"));                                        // time:     matchStartLocalTime
        v.add(JsonTValue.text("PT3H"));                                            // duration: expectedDuration
        v.add(JsonTValue.bool(i % 2 == 0));                                        // bool:     isDayNight
        // ── Toss ──────────────────────────────────────────────────────────────
        v.add(JsonTValue.text("IND"));                                             // str:     tossWinnerTeamId
        v.add(JsonTValue.enumValue(TOSS[(int) (i % 2)]));                           // enum:    tossDecision
        v.add(JsonTValue.bool(true));                                              // bool:    teamABattedFirst
        // ── Team A innings ────────────────────────────────────────────────────
        v.add(JsonTValue.u32(175L));                                               // u32:     teamAInnings1Runs
        v.add(JsonTValue.u16(3));                                                  // u16:     teamAInnings1Wickets
        v.add(JsonTValue.u16(120));                                                // u16:     teamAInnings1Balls
        v.add(JsonTValue.d64(8.75));                                               // d64:     teamAInnings1RunRate
        v.add(JsonTValue.text("Virat Kohli"));                                     // str:     teamATopBatsmanName
        v.add(JsonTValue.u32(85L));                                                // u32:     teamATopBatsmanRuns
        v.add(JsonTValue.text("Jasprit Bumrah"));                                  // str:     teamATopBowlerName
        v.add(JsonTValue.u16(3));                                                  // u16:     teamATopBowlerWickets
        v.add(JsonTValue.d32(6.5f));                                               // d32:     teamATopBowlerEconomy
        // ── Team B innings ────────────────────────────────────────────────────
        v.add(JsonTValue.u32(160L));                                               // u32:     teamBInnings1Runs
        v.add(JsonTValue.u16(7));                                                  // u16:     teamBInnings1Wickets
        v.add(JsonTValue.u16(118));                                                // u16:     teamBInnings1Balls
        v.add(JsonTValue.d64(8.13));                                               // d64:     teamBInnings1RunRate
        v.add(JsonTValue.text("Joe Root"));                                        // str:     teamBTopBatsmanName
        v.add(JsonTValue.u32(72L));                                                // u32:     teamBTopBatsmanRuns
        v.add(JsonTValue.text("Jofra Archer"));                                    // str:     teamBTopBowlerName
        v.add(JsonTValue.u16(2));                                                  // u16:     teamBTopBowlerWickets
        v.add(JsonTValue.d32(7.0f));                                               // d32:     teamBTopBowlerEconomy
        // ── Match outcome ─────────────────────────────────────────────────────
        v.add(JsonTValue.enumValue(STATUSES[(int) (i % 5)]));                       // enum:    matchStatus
        v.add(winnerId);                                                           // str?:    winnerTeamId
        v.add(JsonTValue.text("India won by 15 runs"));                            // str:     resultDescription
        v.add(JsonTValue.i32(15));                                                 // i32:     winMarginRuns
        v.add(JsonTValue.i16((short) 0));                                          // i16:     winMarginWickets
        v.add(JsonTValue.bool(i == 9));                                            // bool:    isSuperOver (only row 9)
        v.add(JsonTValue.d64(0.62));                                               // d64:     netRunRateDiffTeamA
        v.add(JsonTValue.d64(0.62));                                               // d64:     netRunRateDiffTeamB
        v.add(JsonTValue.i16((short) 2));                                          // i16:     teamAGroupPoints
        v.add(JsonTValue.i16((short) 0));                                          // i16:     teamBGroupPoints
        v.add(JsonTValue.d128(prize));                                             // d128:    prizeMoneySplit
        v.add(JsonTValue.i64(10_800 + i * 100));                                   // i64:     matchDurationSeconds
        // ── Player of the match ───────────────────────────────────────────────
        v.add(motmId);                                                             // uuid?:   manOfTheMatchId
        v.add(motmName);                                                           // str?:    manOfTheMatchName
        v.add(motmTeam);                                                           // str?:    manOfTheMatchTeamId
        // ── Officials ─────────────────────────────────────────────────────────
        v.add(JsonTValue.text("Aleem Dar"));                                       // str:     umpire1Name
        v.add(JsonTValue.text("Kumar Dharmasena"));                                // str:     umpire2Name
        v.add(tvUmpire);                                                           // str?:    tvUmpireName
        v.add(JsonTValue.text("Ranjan Madugalle"));                                // str:     matchRefereeName
        // ── Weather ───────────────────────────────────────────────────────────
        v.add(JsonTValue.bool(i >= 7));                                            // bool:    isRainAffected
        v.add(dlsRed);                                                             // i16?:    dlsOversReduction
        v.add(dlsTgt);                                                             // d64?:    dlsTargetRevised
        v.add(avgTemp);                                                            // d32?:    avgTemperatureCelsius
        v.add(attendance);                                                         // u32?:    attendanceCount
        // ── Broadcast & metadata ─────────────────────────────────────────────
        v.add(JsonTValue.bool(true));                                              // bool:    isHighlightsAvailable
        v.add(viewership);                                                         // i64?:    totalViewership
        v.add(JsonTValue.text("en,hi"));                                           // str:     broadcastLanguages
        v.add(dataUrl);                                                            // uri?:    dataSourceUrl
        v.add(contentHash);                                                        // hex?:    matchContentHash
        v.add(certificate);                                                        // base64?: matchCertificate
        v.add(ipv4);                                                               // ipv4?:   statsServerIpv4
        v.add(ipv6);                                                               // ipv6?:   broadcastServerIpv6
        v.add(JsonTValue.text("ICC API"));                                         // str:     dataSource
        v.add(JsonTValue.i64(1_718_460_600_000L + i));                             // timestamp: createdAt
        v.add(JsonTValue.i64(1_718_460_600_000L + i + 3_600_000L));               // timestamp: updatedAt

        return JsonTRow.at(0L, v);
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    /**
     * Generates {@code code/cross-compat/java-generated.jsont}.
     * Always overwrites so the committed file stays fresh.
     */
    @Test
    void crossCompatGenerate() throws IOException {
        Files.createDirectories(JAVA_OUT.getParent());
        var rows = new ArrayList<JsonTRow>(10);
        for (int i = 0; i < 10; i++) rows.add(makeCrossRow(i));

        try (var bw = new BufferedWriter(new FileWriter(JAVA_OUT.toFile()))) {
            RowWriter.writeRows(rows, bw);
        }

        long bytes = Files.size(JAVA_OUT);
        System.out.printf("java-generated.jsont: %d bytes, 10 rows%n", bytes);
    }

    /**
     * Parses {@code code/cross-compat/rust-generated.jsont} (committed by the Rust test)
     * and verifies structural and value invariants.
     */
    @Test
    void crossCompatParseRust() throws IOException {
        assertTrue(Files.exists(RUST_OUT),
                "rust-generated.jsont not found — run `cargo test cross_compat_generate` first");

        String content = Files.readString(RUST_OUT);
        var rows = new ArrayList<JsonTRow>();
        JsonTParser.parseRows(content, rows::add);

        assertEquals(10, rows.size(), "expected 10 rows from Rust");

        // ── Row 0: GROUP_STAGE, GRASS, SCHEDULED, BAT, groupName present ──
        var r0 = rows.get(0);
        assertText(r0, 0, "cc000000-0000-0000-0000-000000000000"); // matchId
        assertNumeric(r0, 1, 1000.0);                              // iccMatchSequenceId
        assertEnum(r0, 6, "GROUP_STAGE");                          // phase
        assertNotNull(r0, 5);                                      // groupName (i%5==0)
        assertIsNull(r0, 13);                                      // teamAContactEmail (i%3≠1)
        assertEnum(r0, 30, "GRASS");                               // wicketSurface
        assertBool(r0, 35, true);                                  // isDayNight (even → true)
        assertEnum(r0, 37, "BAT");                                 // tossDecision
        assertEnum(r0, 57, "SCHEDULED");                           // matchStatus
        assertNotNull(r0, 58);                                     // winnerTeamId (i%2==0 → "IND")
        assertBool(r0, 62, false);                                 // isSuperOver (only i==9)
        assertIsNull(r0, 69);                                      // manOfTheMatchId (i<5)
        assertBool(r0, 76, false);                                 // isRainAffected (i<7)

        // ── Row 1: SUPER_8, HARD_CLAY, IN_PROGRESS, BOWL, contactEmail present ──
        var r1 = rows.get(1);
        assertText(r1, 0, "cc000000-0000-0000-0000-000000000001");
        assertEnum(r1, 6, "SUPER_8");
        assertNotNull(r1, 13);                                     // teamAContactEmail (i%3==1)
        assertEnum(r1, 30, "HARD_CLAY");
        assertBool(r1, 35, false);                                 // isDayNight (odd → false)
        assertEnum(r1, 37, "BOWL");
        assertEnum(r1, 57, "IN_PROGRESS");
        assertIsNull(r1, 58);                                      // winnerTeamId (i%2==1)
        assertNotNull(r1, 80);                                     // attendanceCount (i%2==1)

        // ── Row 4: WET_FAST, ABANDONED, ipv4/ipv6 present ──
        var r4 = rows.get(4);
        assertEnum(r4, 6, "GROUP_STAGE");
        assertEnum(r4, 30, "WET_FAST");
        assertEnum(r4, 57, "ABANDONED");
        assertNotNull(r4, 87);                                     // statsServerIpv4 (i%5==4)
        assertNotNull(r4, 88);                                     // broadcastServerIpv6

        // ── Row 5: manOfTheMatch starts appearing ──
        var r5 = rows.get(5);
        assertNotNull(r5, 69);                                     // manOfTheMatchId (i>=5)
        assertText(r5, 70, "Virat Kohli");                        // manOfTheMatchName
        assertNotNull(r5, 82);                                     // totalViewership (i>=5)

        // ── Row 7: rain-affected with DLS ──
        var r7 = rows.get(7);
        assertBool(r7, 76, true);                                  // isRainAffected (i>=7)
        assertNotNull(r7, 77);                                     // dlsOversReduction
        assertNotNull(r7, 78);                                     // dlsTargetRevised

        // ── Row 9: superOver, certificate, ipv4/ipv6, rain ──
        var r9 = rows.get(9);
        assertEnum(r9, 6, "SUPER_8");
        assertBool(r9, 62, true);                                  // isSuperOver
        assertNotNull(r9, 85);                                     // matchContentHash (i>=8)
        assertNotNull(r9, 86);                                     // matchCertificate (i==9)
        assertNotNull(r9, 87);                                     // statsServerIpv4 (i%5==4)
        assertBool(r9, 76, true);                                  // isRainAffected

        System.out.printf("crossCompatParseRust: all %d rows validated%n", rows.size());
    }

    // ─── Assertion helpers ────────────────────────────────────────────────────

    private static JsonTValue v(JsonTRow row, int idx) { return row.get(idx); }

    private static void assertText(JsonTRow row, int idx, String expected) {
        assertTextValue(expected, idx, v(row, idx));
    }

    private static void assertTextValue(String expected, int idx, JsonTValue val) {
        assertInstanceOf(io.github.datakore.jsont.model.JsonTString.class, val, "field[" + idx + "] expected Text");
        assertEquals(expected, val.asText(), "field[" + idx + "] text mismatch");
    }

    // Row scanner is schema-less — numeric type is inferred by heuristic.
    // Use toDouble() so both D64/I64/U64 representations pass.
    private static void assertNumeric(JsonTRow row, int idx, double expected) {
        var val = v(row, idx);
        assertFalse(val instanceof JsonTValue.Null, "field[" + idx + "] expected numeric, got Null");
        assertEquals(expected, val.toDouble(), 1e-6, "field[" + idx + "] numeric mismatch");
    }

    private static void assertBool(JsonTRow row, int idx, boolean expected) {
        var val = v(row, idx);
        assertInstanceOf(JsonTValue.Bool.class, val, "field[" + idx + "] expected Bool");
        assertEquals(expected, ((JsonTValue.Bool) val).value(), "field[" + idx + "] bool mismatch");
    }

    private static void assertEnum(JsonTRow row, int idx, String expected) {
        var val = v(row, idx);
        assertInstanceOf(JsonTValue.Enum.class, val, "field[" + idx + "] expected Enum");
        assertEquals(expected, ((JsonTValue.Enum) val).value(), "field[" + idx + "] enum mismatch");
    }

    private static void assertIsNull(JsonTRow row, int idx) {
        assertInstanceOf(JsonTValue.Null.class, v(row, idx), "field[" + idx + "] expected Null");
    }

    private static void assertNotNull(JsonTRow row, int idx) {
        assertFalse(v(row, idx) instanceof JsonTValue.Null, "field[" + idx + "] expected non-null");
    }
}
