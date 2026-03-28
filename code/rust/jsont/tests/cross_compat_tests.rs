// =============================================================================
// tests/cross_compat_tests.rs
// =============================================================================
// Cross-language compatibility tests using the WCT20 CricketMatch schema.
//
// Design goals:
//   • Run as part of normal `cargo test` (no feature gate).
//   • 10 rows with deliberate permutations to maximise coverage:
//       - All enum variants exercised (TournamentPhase×4, WicketSurface×5,
//         MatchStatus×5, TossDecision×2).
//       - Every optional field is non-null in at least one row.
//       - Boolean and numeric boundary values included.
//   • generate_rust_fixture() writes code/cross-compat/rust-generated.jsont.
//   • parse_java_fixture()    reads  code/cross-compat/java-generated.jsont
//                             (committed) and spot-checks parsed values.
//
// How to update the committed files after a grammar change:
//   1. cargo test cross_compat_generate -- --nocapture
//   2. git add code/cross-compat/rust-generated.jsont
// =============================================================================

use std::fs;
use std::io::{BufWriter, Write};
use std::path::Path;

use rust_decimal::Decimal;

use jsont::{write_row, JsonTRow, JsonTRowBuilder, JsonTValue, Parseable};

// ── Paths (relative to the workspace root, which is the cwd during tests) ──

const RUST_OUT: &str = "../../cross-compat/rust-generated.jsont";
const JAVA_OUT: &str = "../../cross-compat/java-generated.jsont";

// =============================================================================
// Row generation — 10 rows covering all enum variants and optional patterns
// =============================================================================

//  i  | phase       | surface    | status      | toss | notable optionals
//  ---|-------------|------------|-------------|------|----------------------------
//  0  | GROUP_STAGE | GRASS      | SCHEDULED   | BAT  | groupName, winnerTeamId, tvUmpireName
//  1  | SUPER_8     | HARD_CLAY  | IN_PROGRESS | BOWL | contactEmail, attendance
//  2  | SEMI_FINAL  | RED_SOIL   | COMPLETED   | BAT  | statsApiHost, winnerTeamId, dataSourceUrl
//  3  | FINAL       | DRY_FLAT   | CANCELLED   | BOWL | tvUmpireName, avgTemp, attendance
//  4  | GROUP_STAGE | WET_FAST   | ABANDONED   | BAT  | contactEmail, winnerTeamId, attendance, ipv4/ipv6
//  5  | SUPER_8     | GRASS      | COMPLETED   | BOWL | groupName, manOfMatch, viewership, dataSourceUrl, attendance
//  6  | SEMI_FINAL  | HARD_CLAY  | SCHEDULED   | BAT  | statsApiHost, tvUmpireName, winnerTeamId, viewership
//  7  | FINAL       | RED_SOIL   | IN_PROGRESS | BOWL | contactEmail, manOfMatch, avgTemp, attendance, viewership, rain+DLS
//  8  | GROUP_STAGE | DRY_FLAT   | COMPLETED   | BAT  | winnerTeamId, manOfMatch, dataSourceUrl, viewership, hash, rain+DLS
//  9  | SUPER_8     | WET_FAST   | CANCELLED   | BOWL | tvUmpireName, attendance, viewership, hash, cert, ipv4/ipv6, rain+DLS, superOver

const PHASES:   &[&str] = &["GROUP_STAGE", "SUPER_8", "SEMI_FINAL", "FINAL"];
const SURFACES: &[&str] = &["GRASS", "HARD_CLAY", "RED_SOIL", "DRY_FLAT", "WET_FAST"];
const STATUSES: &[&str] = &["SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ABANDONED"];
const TOSS:     &[&str] = &["BAT", "BOWL"];

fn make_cross_row(i: u64) -> JsonTRow {
    let match_uuid = format!("cc000000-0000-0000-0000-{:012}", i);
    let match_code = format!("XCOMPAT-{:02}", i);
    let prize      = Decimal::new(100_000 + i as i64 * 10_000, 2); // 1000.00 + i*100.00

    // Optional field presence — each field non-null in a different subset of rows.
    let group_name       = opt_str(i % 5 == 0, "Group A");
    let contact_email_a  = opt_str(i % 3 == 1, "team-a@icc.cricket");
    let contact_email_b  = opt_str(i % 3 == 1, "team-b@icc.cricket");
    let stats_host       = opt_str(i % 4 == 2, "stats.icc.cricket");
    let winner_id        = opt_str(i % 2 == 0, "IND");
    let motm_id          = opt_str(i >= 5, "550e8400-e29b-41d4-a716-000000000099");
    let motm_name        = opt_str(i >= 5, "Virat Kohli");
    let motm_team        = opt_str(i >= 5, "IND");
    let tv_umpire        = opt_str(i % 3 == 0, "Simon Taufel");
    let dls_red          = if i >= 7 { JsonTValue::i16(5) } else { JsonTValue::null() };
    let dls_tgt          = if i >= 7 { JsonTValue::d64(145.0) } else { JsonTValue::null() };
    let avg_temp         = if i % 4 == 3 { JsonTValue::d32(28.5) } else { JsonTValue::null() };
    // attendance > 5000 when present so the broadcast transform filter always passes
    let attendance       = if i % 2 == 1 { JsonTValue::u32(45_000 + i as u32 * 1_000) } else { JsonTValue::null() };
    let viewership       = if i >= 5 { JsonTValue::i64(50_000_000 + i as i64 * 1_000_000) } else { JsonTValue::null() };
    let data_url         = opt_str(i % 3 == 2, "https://api.icc.cricket/data");
    let content_hash     = opt_str(i >= 8, "a3f5b8c1d2e4f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1");
    let certificate      = opt_str(i == 9, "dGVzdC1jZXJ0aWZpY2F0ZQ==");
    let ipv4             = opt_str(i % 5 == 4, "192.168.1.1");
    let ipv6             = opt_str(i % 5 == 4, "2001:db8::1");

    JsonTRowBuilder::new()
        // ── Match identity ────────────────────────────────────────────────────
        .push(JsonTValue::str(&match_uuid))                                 // uuid:    matchId
        .push(JsonTValue::u64(1_000 + i))                                   // u64:     iccMatchSequenceId
        .push(JsonTValue::str(&match_code))                                 // str:     matchCode
        .push(JsonTValue::str("ICC Men T20 World Cup"))                     // str:     tournamentName
        .push(JsonTValue::u16((i as u16 % 999) + 1))                       // u16:     matchNumber
        .push(group_name)                                                   // str?:    groupName
        .push(JsonTValue::enum_val(PHASES[i as usize % 4]))                 // enum:    phase
        // ── Team A ────────────────────────────────────────────────────────────
        .push(JsonTValue::str("IND"))                                       // str:     teamAId
        .push(JsonTValue::str("India"))                                     // str:     teamAName
        .push(JsonTValue::str("IND"))                                       // str:     teamAIccCode
        .push(JsonTValue::u16(1))                                           // u16:     teamAWorldRanking
        .push(JsonTValue::d64(900.0 + i as f64))                           // d64:     teamARatingPoints
        .push(JsonTValue::str("Rahul Dravid"))                              // str:     teamACoachName
        .push(contact_email_a)                                              // email?:  teamAContactEmail
        // ── Team B ────────────────────────────────────────────────────────────
        .push(JsonTValue::str("ENG"))                                       // str:     teamBId
        .push(JsonTValue::str("England"))                                   // str:     teamBName
        .push(JsonTValue::str("ENG"))                                       // str:     teamBIccCode
        .push(JsonTValue::u16(3))                                           // u16:     teamBWorldRanking
        .push(JsonTValue::d64(850.0 + i as f64))                           // d64:     teamBRatingPoints
        .push(JsonTValue::str("Matthew Mott"))                              // str:     teamBCoachName
        .push(contact_email_b)                                              // email?:  teamBContactEmail
        // ── Venue ─────────────────────────────────────────────────────────────
        .push(JsonTValue::str("MCG-01"))                                    // str:     venueId
        .push(JsonTValue::str("Melbourne Cricket Ground"))                  // str:     venueName
        .push(JsonTValue::str("Melbourne"))                                 // str:     city
        .push(JsonTValue::str("Australia"))                                 // str:     country
        .push(JsonTValue::i32(100_024))                                     // i32:     venueCapacity
        .push(JsonTValue::bool(true))                                       // bool:    isFloodlit
        .push(JsonTValue::bool(false))                                      // bool:    isNeutralVenue
        .push(JsonTValue::d64(37.82))                                       // d64:     venueLatitude
        .push(JsonTValue::d64(144.98))                                      // d64:     venueLongitude
        .push(JsonTValue::enum_val(SURFACES[i as usize % 5]))               // enum:    wicketSurface
        .push(stats_host)                                                   // hostname?: statsApiHost
        // ── Schedule ──────────────────────────────────────────────────────────
        .push(JsonTValue::str("2024-06-15T14:30:00Z"))                      // datetime: scheduledAt
        .push(JsonTValue::str("14:30:00"))                                  // time:     matchStartLocalTime
        .push(JsonTValue::str("PT3H"))                                      // duration: expectedDuration
        .push(JsonTValue::bool(i % 2 == 0))                                 // bool:     isDayNight
        // ── Toss ──────────────────────────────────────────────────────────────
        .push(JsonTValue::str("IND"))                                       // str:     tossWinnerTeamId
        .push(JsonTValue::enum_val(TOSS[i as usize % 2]))                   // enum:    tossDecision
        .push(JsonTValue::bool(true))                                       // bool:    teamABattedFirst
        // ── Team A innings ────────────────────────────────────────────────────
        .push(JsonTValue::u32(175))                                         // u32:     teamAInnings1Runs
        .push(JsonTValue::u16(3))                                           // u16:     teamAInnings1Wickets
        .push(JsonTValue::u16(120))                                         // u16:     teamAInnings1Balls
        .push(JsonTValue::d64(8.75))                                        // d64:     teamAInnings1RunRate
        .push(JsonTValue::str("Virat Kohli"))                               // str:     teamATopBatsmanName
        .push(JsonTValue::u32(85))                                          // u32:     teamATopBatsmanRuns
        .push(JsonTValue::str("Jasprit Bumrah"))                            // str:     teamATopBowlerName
        .push(JsonTValue::u16(3))                                           // u16:     teamATopBowlerWickets
        .push(JsonTValue::d32(6.5))                                         // d32:     teamATopBowlerEconomy
        // ── Team B innings ────────────────────────────────────────────────────
        .push(JsonTValue::u32(160))                                         // u32:     teamBInnings1Runs
        .push(JsonTValue::u16(7))                                           // u16:     teamBInnings1Wickets
        .push(JsonTValue::u16(118))                                         // u16:     teamBInnings1Balls
        .push(JsonTValue::d64(8.13))                                        // d64:     teamBInnings1RunRate
        .push(JsonTValue::str("Joe Root"))                                  // str:     teamBTopBatsmanName
        .push(JsonTValue::u32(72))                                          // u32:     teamBTopBatsmanRuns
        .push(JsonTValue::str("Jofra Archer"))                              // str:     teamBTopBowlerName
        .push(JsonTValue::u16(2))                                           // u16:     teamBTopBowlerWickets
        .push(JsonTValue::d32(7.0))                                         // d32:     teamBTopBowlerEconomy
        // ── Match outcome ─────────────────────────────────────────────────────
        .push(JsonTValue::enum_val(STATUSES[i as usize % 5]))               // enum:    matchStatus
        .push(winner_id)                                                    // str?:    winnerTeamId
        .push(JsonTValue::str("India won by 15 runs"))                      // str:     resultDescription
        .push(JsonTValue::i32(15))                                          // i32:     winMarginRuns
        .push(JsonTValue::i16(0))                                           // i16:     winMarginWickets
        .push(JsonTValue::bool(i == 9))                                     // bool:    isSuperOver (only row 9)
        .push(JsonTValue::d64(0.62))                                        // d64:     netRunRateDiffTeamA
        .push(JsonTValue::d64(0.62))                                        // d64:     netRunRateDiffTeamB
        .push(JsonTValue::i16(2))                                           // i16:     teamAGroupPoints
        .push(JsonTValue::i16(0))                                           // i16:     teamBGroupPoints
        .push(JsonTValue::d128(prize))                                      // d128:    prizeMoneySplit
        .push(JsonTValue::i64(10_800 + i as i64 * 100))                    // i64:     matchDurationSeconds
        // ── Player of the match ───────────────────────────────────────────────
        .push(motm_id)                                                      // uuid?:   manOfTheMatchId
        .push(motm_name)                                                    // str?:    manOfTheMatchName
        .push(motm_team)                                                    // str?:    manOfTheMatchTeamId
        // ── Officials ─────────────────────────────────────────────────────────
        .push(JsonTValue::str("Aleem Dar"))                                 // str:     umpire1Name
        .push(JsonTValue::str("Kumar Dharmasena"))                          // str:     umpire2Name
        .push(tv_umpire)                                                    // str?:    tvUmpireName
        .push(JsonTValue::str("Ranjan Madugalle"))                          // str:     matchRefereeName
        // ── Weather ───────────────────────────────────────────────────────────
        .push(JsonTValue::bool(i >= 7))                                     // bool:    isRainAffected
        .push(dls_red)                                                      // i16?:    dlsOversReduction
        .push(dls_tgt)                                                      // d64?:    dlsTargetRevised
        .push(avg_temp)                                                     // d32?:    avgTemperatureCelsius
        .push(attendance)                                                   // u32?:    attendanceCount
        // ── Broadcast & metadata ─────────────────────────────────────────────
        .push(JsonTValue::bool(true))                                       // bool:    isHighlightsAvailable
        .push(viewership)                                                   // i64?:    totalViewership
        .push(JsonTValue::str("en,hi"))                                     // str:     broadcastLanguages
        .push(data_url)                                                     // uri?:    dataSourceUrl
        .push(content_hash)                                                 // hex?:    matchContentHash
        .push(certificate)                                                  // base64?: matchCertificate
        .push(ipv4)                                                         // ipv4?:   statsServerIpv4
        .push(ipv6)                                                         // ipv6?:   broadcastServerIpv6
        .push(JsonTValue::str("ICC API"))                                   // str:     dataSource
        .push(JsonTValue::i64(1_718_460_600_000 + i as i64))               // timestamp: createdAt
        .push(JsonTValue::i64(1_718_460_600_000 + i as i64 + 3_600_000))  // timestamp: updatedAt
        .build()
}

fn opt_str(condition: bool, val: &str) -> JsonTValue {
    if condition { JsonTValue::str(val) } else { JsonTValue::null() }
}

// =============================================================================
// Tests
// =============================================================================

/// Generates code/cross-compat/rust-generated.jsont.
/// Always overwrites so the committed file stays fresh.
#[test]
fn cross_compat_generate() {
    let path = Path::new(RUST_OUT);
    fs::create_dir_all(path.parent().unwrap())
        .expect("could not create cross-compat dir");

    let rows: Vec<JsonTRow> = (0..10).map(make_cross_row).collect();

    let file = fs::File::create(path).expect("could not create rust-generated.jsont");
    let mut out = BufWriter::new(file);
    for (i, row) in rows.iter().enumerate() {
        if i > 0 { out.write_all(b",\n").unwrap(); }
        write_row(row, &mut out).expect("write_row failed");
    }
    out.flush().unwrap();

    let meta = fs::metadata(path).unwrap();
    println!("rust-generated.jsont: {} bytes, 10 rows", meta.len());
}

/// Parses code/cross-compat/java-generated.jsont (committed by the Java test)
/// and verifies structural and value invariants.
#[test]
fn cross_compat_parse_java() {
    let content = fs::read_to_string(JAVA_OUT)
        .expect("java-generated.jsont not found — run the Java CrossCompatTest first");

    let rows = Vec::<JsonTRow>::parse(&content)
        .expect("failed to parse java-generated.jsont");

    assert_eq!(rows.len(), 10, "expected 10 rows from Java");

    // Field index reference (92 fields, 0-indexed):
    //  0-6:   match identity  |  7-13:  Team A     |  14-20: Team B
    //  21-31: Venue           |  32-35: Schedule   |  36-38: Toss
    //  39-47: Team A innings  |  48-56: Team B innings
    //  57-68: Match outcome   |  69-71: Player of match
    //  72-75: Officials       |  76-80: Weather    |  81-91: Broadcast

    // ── Row 0: GROUP_STAGE, GRASS, SCHEDULED, BAT, groupName present ──
    let r0 = &rows[0];
    assert_str(r0, 0, "cc000000-0000-0000-0000-000000000000"); // matchId
    // field[1] is u64 — schema-less parser may infer I32/I64/U64 depending on language
    assert_numeric(r0, 1, 1000.0);                             // iccMatchSequenceId
    assert_enum(r0, 6, "GROUP_STAGE");                         // phase
    assert_not_null(r0, 5);                                    // groupName (i%5==0)
    assert_null(r0, 13);                                       // teamAContactEmail (i%3≠1)
    assert_enum(r0, 30, "GRASS");                              // wicketSurface
    assert_bool(r0, 35, true);                                 // isDayNight (even → true)
    assert_enum(r0, 37, "BAT");                                // tossDecision
    assert_enum(r0, 57, "SCHEDULED");                          // matchStatus
    assert_not_null(r0, 58);                                   // winnerTeamId (i%2==0 → "IND")
    assert_bool(r0, 62, false);                                // isSuperOver (only i==9)
    assert_null(r0, 69);                                       // manOfTheMatchId (i<5)
    assert_bool(r0, 76, false);                                // isRainAffected (i<7)

    // ── Row 1: SUPER_8, HARD_CLAY, IN_PROGRESS, BOWL, contactEmail present ──
    let r1 = &rows[1];
    assert_str(r1, 0, "cc000000-0000-0000-0000-000000000001");
    assert_enum(r1, 6, "SUPER_8");
    assert_not_null(r1, 13);                                   // teamAContactEmail (i%3==1)
    assert_enum(r1, 30, "HARD_CLAY");
    assert_bool(r1, 35, false);                                // isDayNight (odd → false)
    assert_enum(r1, 37, "BOWL");
    assert_enum(r1, 57, "IN_PROGRESS");
    assert_null(r1, 58);                                       // winnerTeamId (i%2==1 → null)
    assert_not_null(r1, 80);                                   // attendanceCount (i%2==1)

    // ── Row 4: GROUP_STAGE, WET_FAST, ABANDONED, BAT, ipv4/ipv6 present ──
    let r4 = &rows[4];
    assert_enum(r4, 6, "GROUP_STAGE");
    assert_enum(r4, 30, "WET_FAST");
    assert_enum(r4, 57, "ABANDONED");
    assert_not_null(r4, 87);                                   // statsServerIpv4 (i%5==4)
    assert_not_null(r4, 88);                                   // broadcastServerIpv6

    // ── Row 5: manOfTheMatch starts appearing ──
    let r5 = &rows[5];
    assert_not_null(r5, 69);                                   // manOfTheMatchId (i>=5)
    assert_str(r5, 70, "Virat Kohli");                        // manOfTheMatchName
    assert_not_null(r5, 82);                                   // totalViewership (i>=5)

    // ── Row 7: rain-affected with DLS ──
    let r7 = &rows[7];
    assert_bool(r7, 76, true);                                 // isRainAffected (i>=7)
    assert_not_null(r7, 77);                                   // dlsOversReduction
    assert_not_null(r7, 78);                                   // dlsTargetRevised

    // ── Row 9: superOver, certificate, ipv4/ipv6, rain ──
    let r9 = &rows[9];
    assert_enum(r9, 6, "SUPER_8");
    assert_bool(r9, 62, true);                                 // isSuperOver
    assert_not_null(r9, 85);                                   // matchContentHash (i>=8)
    assert_not_null(r9, 86);                                   // matchCertificate (i==9)
    assert_not_null(r9, 87);                                   // statsServerIpv4 (i%5==4)
    assert_bool(r9, 76, true);                                 // isRainAffected

    println!("cross_compat_parse_java: all {} rows validated", rows.len());
}

// =============================================================================
// Field-level assertion helpers
// =============================================================================

fn v(row: &JsonTRow, idx: usize) -> &JsonTValue {
    row.get(idx)
        .unwrap_or_else(|| panic!("row has no field at index {idx}"))
}

fn assert_str(row: &JsonTRow, idx: usize, expected: &str) {
    match v(row, idx) {
        JsonTValue::Str(t) => assert_eq!(t.as_str(), expected, "field[{idx}] str mismatch"),
        other => panic!("field[{idx}] expected Str, got {other:?}"),
    }
}

// Schema-less parser infers numeric type by heuristic — compare as f64.
fn assert_numeric(row: &JsonTRow, idx: usize, expected: f64) {
    match v(row, idx) {
        JsonTValue::Number(n) => {
            let actual = n.as_f64();
            assert!((actual - expected).abs() < 1e-6,
                "field[{idx}] numeric mismatch: expected {expected}, got {actual}");
        }
        other => panic!("field[{idx}] expected Number, got {other:?}"),
    }
}

fn assert_bool(row: &JsonTRow, idx: usize, expected: bool) {
    match v(row, idx) {
        JsonTValue::Bool(b) => assert_eq!(*b, expected, "field[{idx}] bool mismatch"),
        other => panic!("field[{idx}] expected Bool, got {other:?}"),
    }
}

fn assert_enum(row: &JsonTRow, idx: usize, expected: &str) {
    match v(row, idx) {
        JsonTValue::Enum(e) => assert_eq!(e.as_str(), expected, "field[{idx}] enum mismatch"),
        other => panic!("field[{idx}] expected Enum, got {other:?}"),
    }
}

fn assert_null(row: &JsonTRow, idx: usize) {
    match v(row, idx) {
        JsonTValue::Null => {}
        other => panic!("field[{idx}] expected Null, got {other:?}"),
    }
}

fn assert_not_null(row: &JsonTRow, idx: usize) {
    match v(row, idx) {
        JsonTValue::Null => panic!("field[{idx}] expected non-null, got Null"),
        _ => {}
    }
}
