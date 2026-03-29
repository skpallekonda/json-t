package io.github.datakore.jsont.model;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Static factory methods that validate a raw string and, if valid,
 * return the corresponding {@link JsonTString} semantic variant.
 */
public final class JsonTStrings {

    private JsonTStrings() {}

    // ── Promotion ────────────────────────────────────────────────────────────

    public static Optional<JsonTString> promote(String s, ScalarType type) {
        return switch (type) {
            case STR       -> Optional.of(new JsonTString.Plain(s));
            case NSTR      -> nstr(s);
            case UUID      -> uuid(s);
            case URI       -> uri(s);
            case EMAIL     -> email(s);
            case HOSTNAME  -> hostname(s);
            case IPV4      -> ipv4(s);
            case IPV6      -> ipv6(s);
            case DATE      -> date(s);
            case TIME      -> time(s);
            case DATETIME  -> dateTime(s);
            case TIMESTAMP -> timestamp(s);
            case TSZ       -> tsz(s);
            case INST      -> inst(s);
            case DURATION  -> duration(s);
            case BASE64    -> base64(s);
            case HEX       -> hex(s);
            case OID       -> oid(s);
            default        -> Optional.empty();
        };
    }

    public static Optional<JsonTValue> promoteTemporal(JsonTValue value, ScalarType type) {
        try {
            long val = (long) value.toDouble();
            return switch (type) {
                case DATE      -> isDateInt(val) ? Optional.of(new JsonTValue.Date((int) val)) : Optional.empty();
                case TIME      -> isTimeInt(val) ? Optional.of(new JsonTValue.Time((int) val)) : Optional.empty();
                case DATETIME  -> isDateTimeInt(val) ? Optional.of(new JsonTValue.DateTime(val)) : Optional.empty();
                case TIMESTAMP -> Optional.of(new JsonTValue.Timestamp(val));
                default        -> Optional.empty();
            };
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── NStr ─────────────────────────────────────────────────────────────────

    /** NStr accepts any non-null string (normalisation is caller's concern). */
    public static Optional<JsonTString> nstr(String s) {
        return s != null ? Optional.of(new JsonTString.Nstr(s)) : Optional.empty();
    }

    // ── UUID ─────────────────────────────────────────────────────────────────

    public static Optional<JsonTString> uuid(String s) {
        if (s == null || s.length() != 36) return Optional.empty();
        if (s.charAt(8) != '-' || s.charAt(13) != '-'
                || s.charAt(18) != '-' || s.charAt(23) != '-') return Optional.empty();
        for (int i = 0; i < 36; i++) {
            if (i == 8 || i == 13 || i == 18 || i == 23) continue;
            char c = s.charAt(i);
            if (!isHexChar(c)) return Optional.empty();
        }
        return Optional.of(new JsonTString.Uuid(s));
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    public static Optional<JsonTString> email(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        int at = s.indexOf('@');
        if (at <= 0 || at == s.length() - 1) return Optional.empty();
        if (s.indexOf('@', at + 1) >= 0) return Optional.empty();
        String local  = s.substring(0, at);
        String domain = s.substring(at + 1);
        if (!domain.contains(".") || domain.startsWith(".")
                || domain.endsWith(".") || domain.contains("..")) return Optional.empty();
        if (!local.chars().allMatch(c -> Character.isLetterOrDigit(c)
                || "!#$%&'*+/=?^_`{|}~.-".indexOf(c) >= 0)) return Optional.empty();
        if (!domain.chars().allMatch(c -> Character.isLetterOrDigit(c)
                || c == '.' || c == '-')) return Optional.empty();
        return Optional.of(new JsonTString.Email(s));
    }

    // ── URI ───────────────────────────────────────────────────────────────────

    public static Optional<JsonTString> uri(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        try {
            URI parsed = new URI(s);
            return parsed.isAbsolute() ? Optional.of(new JsonTString.Uri(s)) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    // ── Hostname ─────────────────────────────────────────────────────────────

    public static Optional<JsonTString> hostname(String s) {
        if (s == null || s.isEmpty() || s.length() > 253) return Optional.empty();
        for (String label : s.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63) return Optional.empty();
            if (label.startsWith("-") || label.endsWith("-")) return Optional.empty();
            if (!label.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-'))
                return Optional.empty();
        }
        return Optional.of(new JsonTString.Hostname(s));
    }

    // ── IPv4 / IPv6 ───────────────────────────────────────────────────────────

    public static Optional<JsonTString> ipv4(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        try {
            InetAddress addr = InetAddress.getByName(s);
            return (addr instanceof Inet4Address && addr.getHostAddress().equals(s))
                    ? Optional.of(new JsonTString.Ipv4(s))
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<JsonTString> ipv6(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        String bare = s.startsWith("[") && s.endsWith("]") ? s.substring(1, s.length() - 1) : s;
        try {
            InetAddress addr = InetAddress.getByName(bare);
            return (addr instanceof Inet6Address)
                    ? Optional.of(new JsonTString.Ipv6(s))
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── Temporal ─────────────────────────────────────────────────────────────

    // ── Temporal ─────────────────────────────────────────────────────────────

    public static Optional<JsonTString> date(String s) {
        if (s == null || s.length() != 10) return Optional.empty();
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return Optional.empty();
        if (!isDigit(s, 0, 4) || !isDigit(s, 5, 7) || !isDigit(s, 8, 10)) return Optional.empty();
        if (!rangeCheck(s, 5, 7, 1, 12) || !rangeCheck(s, 8, 10, 1, 31)) return Optional.empty();
        return Optional.of(new JsonTString.Date(s));
    }

    public static Optional<JsonTString> time(String s) {
        if (s == null || s.length() < 8) return Optional.empty();
        if (s.charAt(2) != ':' || s.charAt(5) != ':') return Optional.empty();
        if (!isDigit(s, 0, 2) || !isDigit(s, 3, 5) || !isDigit(s, 6, 8)) return Optional.empty();
        if (!rangeCheck(s, 0, 2, 0, 23) || !rangeCheck(s, 3, 5, 0, 59) || !rangeCheck(s, 6, 8, 0, 59)) return Optional.empty();
        if (s.length() > 8) {
            if (s.charAt(8) != '.' || !isDigit(s, 9, s.length())) return Optional.empty();
        }
        return Optional.of(new JsonTString.Time(s));
    }

    public static Optional<JsonTString> dateTime(String s) {
        if (s == null || s.length() < 19 || s.charAt(10) != 'T') return Optional.empty();
        return (date(s.substring(0, 10)).isPresent() && time(s.substring(11)).isPresent())
                ? Optional.of(new JsonTString.DateTime(s))
                : Optional.empty();
    }

    public static Optional<JsonTString> tsz(String s) {
        if (s == null || s.length() < 19 || s.charAt(10) != 'T') return Optional.empty();
        int tzStart = findTzStart(s);
        if (tzStart == s.length()) return Optional.empty();
        return (date(s.substring(0, 10)).isPresent()
                && time(s.substring(11, tzStart)).isPresent()
                && isTzSuffix(s.substring(tzStart)))
                ? Optional.of(new JsonTString.Tsz(s))
                : Optional.empty();
    }

    public static Optional<JsonTString> inst(String s) {
        // inst uses same grammar as tsz (RFC 3339)
        return tsz(s).map(v -> new JsonTString.Inst(v.value()));
    }

    public static Optional<JsonTString> timestamp(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        String target = s.startsWith("-") ? s.substring(1) : s;
        if (target.isEmpty()) return Optional.empty();
        int dot = target.indexOf('.');
        if (dot == -1) {
            return isDigit(target, 0, target.length()) ? Optional.of(new JsonTString.Timestamp(s)) : Optional.empty();
        }
        String intPart = target.substring(0, dot);
        String fracPart = target.substring(dot + 1);
        return (!intPart.isEmpty() && !fracPart.isEmpty() && isDigit(intPart, 0, intPart.length()) && isDigit(fracPart, 0, fracPart.length()))
                ? Optional.of(new JsonTString.Timestamp(s))
                : Optional.empty();
    }

    public static Optional<JsonTString> duration(String s) {
        if (s == null || !s.startsWith("P") || s.length() < 2) return Optional.empty();
        return isDurationInner(s.substring(1)) ? Optional.of(new JsonTString.Duration(s)) : Optional.empty();
    }

    // ── Binary / encoded ─────────────────────────────────────────────────────

    public static Optional<JsonTString> base64(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        try {
            java.util.Base64.getDecoder().decode(s);
            return Optional.of(new JsonTString.Base64(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<JsonTString> hex(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        for (char c : s.toCharArray()) {
            if (!isHexChar(c)) return Optional.empty();
        }
        return Optional.of(new JsonTString.Hex(s));
    }

    public static Optional<JsonTString> oid(String s) {
        if (s == null || s.length() != 24) return Optional.empty();
        for (int i = 0; i < 24; i++) {
            if (!isHexChar(s.charAt(i))) return Optional.empty();
        }
        return Optional.of(new JsonTString.Oid(s));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isDigit(String s, int start, int end) {
        if (start >= end) return false;
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean rangeCheck(String s, int start, int end, int min, int max) {
        try {
            int val = Integer.parseInt(s.substring(start, end));
            return val >= min && val <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int findTzStart(String s) {
        if (s.length() < 19) return s.length();
        // Time part HH:MM:SS ends at index 19. 
        // Fractional part starts if there is a '.' at index 19.
        int i = 19;
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        }
        return i;
    }

    private static boolean isTzSuffix(String s) {
        if (s.isEmpty()) return true;
        if (s.equals("Z")) return true;
        if (s.length() == 6 && (s.startsWith("+") || s.startsWith("-")) && s.charAt(3) == ':') {
            return isDigit(s, 1, 3) && isDigit(s, 4, 6)
                    && rangeCheck(s, 1, 3, 0, 23) && rangeCheck(s, 4, 6, 0, 59);
        }
        return false;
    }

    private static boolean isDurationInner(String s) {
        enum State { DATE, TIME }
        State state = State.DATE;
        int i = 0;
        boolean hasDesignator = false;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean hasDigits = i > start;
            if (i >= s.length()) break;
            char designator = s.charAt(i);
            if (designator == 'T' && state == State.DATE) {
                state = State.TIME;
                i++;
                continue;
            }
            if (hasDigits) {
                if (state == State.DATE) {
                    if (designator == 'Y' || designator == 'M' || designator == 'D') {
                        hasDesignator = true; i++; continue;
                    }
                } else {
                    if (designator == 'H' || designator == 'M' || designator == 'S') {
                        hasDesignator = true; i++; continue;
                    }
                }
            }
            return false;
        }
        return hasDesignator;
    }

    private static boolean isDateInt(long n) {
        long year = n / 10000;
        long month = (n / 100) % 100;
        long day = n % 100;
        return year > 0 && month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    private static boolean isTimeInt(long n) {
        long hour = n / 10000;
        long min = (n / 100) % 100;
        long sec = n % 100;
        return hour <= 23 && min <= 59 && sec <= 59;
    }

    private static boolean isDateTimeInt(long n) {
        long date = n / 1000000;
        long time = n % 1000000;
        return isDateInt(date) && isTimeInt(time);
    }
}
