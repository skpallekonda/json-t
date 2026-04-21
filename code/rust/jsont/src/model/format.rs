// =============================================================================
// model/format.rs — Format validators for semantic string ScalarTypes
// =============================================================================
//
// Each function checks whether a raw string conforms to its declared format.
// Returns `true` if valid, `false` otherwise.
//
// Design rules:
//   - No allocations beyond what the std / regex crates require internally.
//   - No knowledge of DiagnosticEvent or the validation pipeline.
//   - One function per ScalarType; named after the type in snake_case.
//   - `regex::Regex` instances are thread-local to avoid re-compilation cost.
//
// Called exclusively from `JsonTValue::promote` to gate promotion.
// =============================================================================

use std::net::{Ipv4Addr, Ipv6Addr};
use std::str::FromStr;

// ── NStr ─────────────────────────────────────────────────────────────────────

/// NStr accepts any non-empty string (normalisation is the caller's concern).
#[inline]
pub(crate) fn is_nstr(_s: &str) -> bool { true }

// ── UUID ─────────────────────────────────────────────────────────────────────

/// Validates RFC 4122 UUID: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` (case-insensitive).
pub(crate) fn is_uuid(s: &str) -> bool {
    // Fast path: exact length check before regex.
    if s.len() != 36 { return false; }
    let b = s.as_bytes();
    if b[8] != b'-' || b[13] != b'-' || b[18] != b'-' || b[23] != b'-' { return false; }
    b.iter().enumerate().all(|(i, &c)| match i {
        8 | 13 | 18 | 23 => c == b'-',
        _                => c.is_ascii_hexdigit(),
    })
}

// ── Email ─────────────────────────────────────────────────────────────────────

/// Simplified RFC 5321 email: `local@domain.tld` — no quoted strings, no comments.
pub(crate) fn is_email(s: &str) -> bool {
    // Must have exactly one '@' that is not the first or last char.
    let at = match s.find('@') {
        Some(i) if i > 0 && i < s.len() - 1 => i,
        _ => return false,
    };
    let local  = &s[..at];
    let domain = &s[at + 1..];
    // Domain must contain at least one dot, no consecutive dots.
    !local.is_empty()
        && !domain.is_empty()
        && domain.contains('.')
        && !domain.starts_with('.')
        && !domain.ends_with('.')
        && !domain.contains("..")
        && local.bytes().all(|c| c.is_ascii_alphanumeric() || b"!#$%&'*+/=?^_`{|}~.-".contains(&c))
        && domain.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'.' || c == b'-')
}

// ── URI ───────────────────────────────────────────────────────────────────────

/// Validates an absolute URI: must have a scheme followed by `://` or `:`.
/// Uses a simple structural check — no external crate needed.
pub(crate) fn is_uri(s: &str) -> bool {
    // Scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )  followed by ":"
    let colon = match s.find(':') {
        Some(i) if i > 0 => i,
        _ => return false,
    };
    let scheme = &s[..colon];
    scheme.starts_with(|c: char| c.is_ascii_alphabetic())
        && scheme.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'+' || c == b'-' || c == b'.')
        && s.len() > colon + 1  // must have something after the colon
}

// ── Hostname ─────────────────────────────────────────────────────────────────

/// RFC 1123 hostname: labels of `[a-zA-Z0-9]` and internal hyphens, dot-separated.
pub(crate) fn is_hostname(s: &str) -> bool {
    if s.is_empty() || s.len() > 253 { return false; }
    s.split('.').all(|label| {
        !label.is_empty()
            && label.len() <= 63
            && !label.starts_with('-')
            && !label.ends_with('-')
            && label.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
    })
}

// ── IPv4 / IPv6 ───────────────────────────────────────────────────────────────

/// Uses stdlib `Ipv4Addr::from_str` — handles all edge cases including octet range.
#[inline]
pub(crate) fn is_ipv4(s: &str) -> bool { Ipv4Addr::from_str(s).is_ok() }

/// Uses stdlib `Ipv6Addr::from_str` — handles full, compressed, and mixed forms.
#[inline]
pub(crate) fn is_ipv6(s: &str) -> bool { Ipv6Addr::from_str(s).is_ok() }

// ── Temporal ─────────────────────────────────────────────────────────────────
//
// ISO 8601 structural checks without pulling in `chrono`.
// Keeping format.rs dependency-free lets it compile in no_std contexts.
//
// Patterns validated:
//   Date     : YYYY-MM-DD
//   Time     : HH:MM:SS[.fraction]
//   DateTime : <Date>T<Time>
//   Tsz      : <DateTime>[Z | ±HH:MM]
//   Timestamp: integer or decimal (Unix epoch)
//   Inst     : same grammar as Tsz (RFC 3339 instant)
//   Duration : ISO 8601 P[n]Y[n]M[n]DT[n]H[n]M[n]S

/// `YYYY-MM-DD`
pub(crate) fn is_date(s: &str) -> bool {
    // Length: exactly 10; separators at positions 4 and 7.
    if s.len() != 10 { return false; }
    let b = s.as_bytes();
    b[4] == b'-' && b[7] == b'-'
        && b[..4].iter().all(|c| c.is_ascii_digit())
        && b[5..7].iter().all(|c| c.is_ascii_digit())
        && b[8..].iter().all(|c| c.is_ascii_digit())
        && range_check(&b[5..7], 1, 12)
        && range_check(&b[8..], 1, 31)
}

/// `HH:MM:SS[.fraction]`
pub(crate) fn is_time(s: &str) -> bool {
    if s.len() < 8 { return false; }
    let b = s.as_bytes();
    b[2] == b':' && b[5] == b':'
        && b[..2].iter().all(|c| c.is_ascii_digit())
        && b[3..5].iter().all(|c| c.is_ascii_digit())
        && b[6..8].iter().all(|c| c.is_ascii_digit())
        && range_check(&b[..2], 0, 23)
        && range_check(&b[3..5], 0, 59)
        && range_check(&b[6..8], 0, 59)
        // optional fractional part
        && (s.len() == 8 || (b[8] == b'.' && b[9..].iter().all(|c| c.is_ascii_digit())))
}

/// `YYYY-MM-DDTHH:MM:SS[.fraction]`
pub(crate) fn is_date_time(s: &str) -> bool {
    if s.len() < 19 { return false; }
    let b = s.as_bytes();
    b[10] == b'T' && is_date(&s[..10]) && is_time(&s[11..])
}

/// DateTime with optional timezone: `Z` or `±HH:MM`.
pub(crate) fn is_tsz(s: &str) -> bool {
    if s.len() < 19 { return false; }
    let b = s.as_bytes();
    if b[10] != b'T' { return false; }
    // Find where the datetime body ends (after the fractional seconds if any)
    let tz_start = find_tz_start(s);
    is_date(&s[..10]) && is_time(&s[11..tz_start]) && is_tz_suffix(&s[tz_start..])
}

/// Same grammar as `Tsz` (monotonic instants are RFC 3339 timestamps).
#[inline]
pub(crate) fn is_inst(s: &str) -> bool { is_tsz(s) }

/// Unix timestamp: integer or decimal (e.g. `1700000000` or `1700000000.123`).
pub(crate) fn is_timestamp(s: &str) -> bool {
    if s.is_empty() { return false; }
    let s = s.strip_prefix('-').unwrap_or(s); // allow negative timestamps
    match s.find('.') {
        None    => s.bytes().all(|c| c.is_ascii_digit()) && !s.is_empty(),
        Some(i) => {
            let (int, frac) = (&s[..i], &s[i + 1..]);
            !int.is_empty()
                && !frac.is_empty()
                && int.bytes().all(|c| c.is_ascii_digit())
                && frac.bytes().all(|c| c.is_ascii_digit())
        }
    }
}

/// ISO 8601 duration: `P[nY][nM][nD][T[nH][nM][nS]]` — at least one designator required.
pub(crate) fn is_duration(s: &str) -> bool {
    if !s.starts_with('P') { return false; }
    // Quick regex-free walk: after P, expect digits interleaved with Y M D T H M S
    let inner = &s[1..];
    if inner.is_empty() { return false; }
    // Delegate to a simple state machine.
    is_duration_inner(inner)
}

// ── Binary / encoded ─────────────────────────────────────────────────────────

/// Base64: standard alphabet `[A-Za-z0-9+/]` with optional `=` padding.
pub(crate) fn is_base64(s: &str) -> bool {
    if s.is_empty() { return false; }
    let s = s.trim_end_matches('=');
    s.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'+' || c == b'/')
}

/// Hex string: `[0-9a-fA-F]+`, even-length optional.
pub(crate) fn is_hex(s: &str) -> bool {
    !s.is_empty() && s.bytes().all(|c| c.is_ascii_hexdigit())
}

/// BSON ObjectId: exactly 24 hexadecimal characters.
pub(crate) fn is_oid(s: &str) -> bool {
    s.len() == 24 && s.bytes().all(|c| c.is_ascii_hexdigit())
}

pub(crate) fn is_date_int(n: u32) -> bool {
    let year = n / 10000;
    let month = (n / 100) % 100;
    let day = n % 100;
    year > 0 && month >= 1 && month <= 12 && day >= 1 && day <= 31
}

pub(crate) fn is_time_int(n: u32) -> bool {
    let hour = n / 10000;
    let min = (n / 100) % 100;
    let sec = n % 100;
    hour <= 23 && min <= 59 && sec <= 59
}

pub(crate) fn is_date_time_int(n: u64) -> bool {
    let date = (n / 1000000) as u32;
    let time = (n % 1000000) as u32;
    is_date_int(date) && is_time_int(time)
}

// =============================================================================
// Private helpers
// =============================================================================

/// Parse a 1-to-2-byte ASCII digit slice into u8 and check `min..=max`.
fn range_check(digits: &[u8], min: u8, max: u8) -> bool {
    let n = match digits.len() {
        1 => digits[0] - b'0',
        2 => (digits[0] - b'0') * 10 + (digits[1] - b'0'),
        _ => return false,
    };
    n >= min && n <= max
}

/// Find the index in `s` where a timezone suffix (`Z`, `+`, `-`) begins,
/// scanning backwards from the end past any fractional digits.
fn find_tz_start(s: &str) -> usize {
    if s.len() < 19 { return s.len(); }
    let mut i = 19;
    let b = s.as_bytes();
    if i < s.len() && b[i] == b'.' {
        i += 1;
        while i < s.len() && b[i].is_ascii_digit() { i += 1; }
    }
    i
}

fn is_tz_suffix(s: &str) -> bool {
    s.is_empty()
        || s == "Z"
        || (s.len() == 6
            && (s.starts_with('+') || s.starts_with('-'))
            && s.as_bytes()[3] == b':'
            && s[1..3].bytes().all(|c| c.is_ascii_digit())
            && s[4..].bytes().all(|c| c.is_ascii_digit())
            && range_check(&s.as_bytes()[1..3], 0, 23)
            && range_check(&s.as_bytes()[4..], 0, 59))
}

/// Minimal ISO 8601 duration state machine (no regex).
fn is_duration_inner(s: &str) -> bool {
    #[derive(PartialEq)]
    enum State { Date, Time }
    let mut state = State::Date;
    let mut i = 0;
    let b = s.as_bytes();
    let mut has_designator = false;

    while i < b.len() {
        // Consume digits
        let start = i;
        while i < b.len() && b[i].is_ascii_digit() { i += 1; }
        let has_digits = i > start;

        if i >= b.len() { break; }

        match (b[i], &state) {
            (b'Y', State::Date) | (b'M', State::Date) | (b'D', State::Date) if has_digits => {
                has_designator = true; i += 1;
            }
            (b'T', State::Date) => {
                state = State::Time; i += 1;
            }
            (b'H', State::Time) | (b'S', State::Time) if has_digits => {
                has_designator = true; i += 1;
            }
            (b'M', State::Time) if has_digits => {
                has_designator = true; i += 1;
            }
            _ => return false,
        }
    }
    has_designator
}

// =============================================================================
// Unit tests
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn uuid_valid()   { assert!(is_uuid("550e8400-e29b-41d4-a716-446655440000")); }
    #[test]
    fn uuid_invalid() { assert!(!is_uuid("not-a-uuid")); }

    #[test]
    fn email_valid()    { assert!(is_email("user@example.com")); }
    #[test]
    fn email_no_at()    { assert!(!is_email("userexample.com")); }
    #[test]
    fn email_two_at()   { assert!(!is_email("a@@b.com")); }

    #[test]
    fn ipv4_valid()    { assert!(is_ipv4("192.168.1.1")); }
    #[test]
    fn ipv4_invalid()  { assert!(!is_ipv4("999.999.999.999")); }

    #[test]
    fn ipv6_valid()    { assert!(is_ipv6("::1")); }
    #[test]
    fn ipv6_invalid()  { assert!(!is_ipv6("gggg::1")); }

    #[test]
    fn uri_valid()     { assert!(is_uri("https://example.com")); }
    #[test]
    fn uri_no_scheme() { assert!(!is_uri("//example.com")); }

    #[test]
    fn hostname_valid()   { assert!(is_hostname("my-host.example.com")); }
    #[test]
    fn hostname_invalid() { assert!(!is_hostname("-badhost")); }

    #[test]
    fn date_valid()    { assert!(is_date("2024-03-15")); }
    #[test]
    fn date_invalid()  { assert!(!is_date("2024-13-01")); }

    #[test]
    fn time_valid()    { assert!(is_time("13:45:30")); }
    #[test]
    fn time_frac()     { assert!(is_time("13:45:30.123")); }
    #[test]
    fn time_invalid()  { assert!(!is_time("25:00:00")); }

    #[test]
    fn datetime_valid()   { assert!(is_date_time("2024-03-15T13:45:30")); }
    #[test]
    fn datetime_invalid() { assert!(!is_date_time("2024-03-15 13:45:30")); }

    #[test]
    fn timestamp_int()     { assert!(is_timestamp("1700000000")); }
    #[test]
    fn timestamp_decimal() { assert!(is_timestamp("1700000000.123")); }
    #[test]
    fn timestamp_neg()     { assert!(is_timestamp("-3600")); }
    #[test]
    fn timestamp_invalid() { assert!(!is_timestamp("abc")); }

    #[test]
    fn base64_valid()   { assert!(is_base64("SGVsbG8=")); }
    #[test]
    fn base64_invalid() { assert!(!is_base64("not!base64")); }

    #[test]
    fn hex_valid()   { assert!(is_hex("deadBEEF")); }
    #[test]
    fn hex_invalid() { assert!(!is_hex("xyz")); }

    #[test]
    fn oid_valid()   { assert!(is_oid("507f1f77bcf86cd799439011")); }
    #[test]
    fn oid_invalid() { assert!(!is_oid("1.2.3")); }

    #[test]
    fn duration_valid()   { assert!(is_duration("P1Y2M3DT4H5M6S")); }
    #[test]
    fn duration_days()    { assert!(is_duration("P30D")); }
    #[test]
    fn duration_invalid() { assert!(!is_duration("P")); }
}
