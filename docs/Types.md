JsonT handles type(s) of data as per the below levels

- **Lexer level** → only raw tokens (NUMBER, STRING, BOOLEAN, NULL, UNSPECIFIED).
- **Parser level** → interprets those tokens into structured base types (i32, date, bin, etc.).
- **Semantic level** → adds real‑world meaning (uuid, zip, mail, geo, etc.) by constraining base types.

---

# 📌 Level 1: Lexer Types

| Lexer Type    | Notes                                    |
|---------------|------------------------------------------|
| `NUMBER`      | Raw numeric literal (integer or decimal) |
| `STRING`      | Quoted text literal                      |
| `BOOLEAN`     | `true` / `false`                         |
| `NULL`        | `null` / `nil`                           |
| `UNSPECIFIED` | Placeholder `_`                          |

---

# 📌 Level 2: Parser Base Types

| Parser Type       | short code | Possible Lexer Type(s)      | Format / Pattern                                       | Notes                   |
|-------------------|------------|-----------------------------|--------------------------------------------------------|-------------------------|
| string            | `str`      | STRING                      | free text                                              | generic text            |
| normalizedString  | `nstr`     | STRING                      | free text                                              | normalized string       |
| anyURI            | `uri`      | STRING                      | URI syntax                                             | URI reference           |
| boolean           | `bool`     | BOOLEAN or STRING or NUMBER | `true` / `false` / `1` / `0`/ `yes` / `no` / `t` / `f` | logical values          |
| int16             | `i16`      | NUMBER                      | `-?\d{1,5}`                                            | 16‑bit signed           |
| int32             | `i32`      | NUMBER                      | `-?\d{1,10}`                                           | 32‑bit signed           |
| int64             | `i64`      | NUMBER                      | `-?\d{1,19}`                                           | 64‑bit signed           |
| unsigned int16    | `u16`      | NUMBER                      | `\d{1,5}`                                              | 16‑bit unsigned         |
| unsigned int32    | `u32`      | NUMBER                      | `\d{1,10}`                                             | 32‑bit unsigned         |
| unsigned int64    | `u64`      | NUMBER                      | `\d{1,19}`                                             | 64‑bit unsigned         |
| decimal32         | `d32`      | NUMBER                      | `-?\d+(\.\d+)?`                                        | float precision         |
| decimal64         | `d64`      | NUMBER                      | `-?\d+(\.\d+)?`                                        | double precision        |
| decimal128        | `d128`     | NUMBER                      | arbitrary precision                                    | high precision          |
| date              | `date`     | NUMBER                      | `YYYYMMDD`                                             | calendar date           |
| time              | `time`     | NUMBER                      | `HHmmss`                                               | clock time              |
| datetime          | `dtm`      | NUMBER                      | `YYYYMMDDHHmmss`                                       | combined date+time      |
| timestamp (epoch) | `ts`       | NUMBER                      | epoch seconds/millis                                   | numeric timestamp       |
| timestamp (TZ)    | `tsz`      | STRING                      | `YYYY-MM-DDTHH:mm:ssZ`                                 | UTC timestamp           |
| moment            | `inst`     | STRING                      | `YYYY-MM-DDTHH:mm:ss`                                  | local instant           |
| moment (TZ)       | `insz`     | STRING                      | `YYYY-MM-DDTHH:mm:ss±HH:mm`                            | instant with offset     |
| Year              | `yr`       | STRING                      | `YYYY`                                                 | year only               |
| Month             | `mon`      | STRING                      | `MM`                                                   | month only              |
| Day               | `day`      | STRING                      | `DD`                                                   | day only                |
| YearMonth         | `ym`       | STRING                      | `YYYY-MM`                                              | year + month            |
| MonthDay          | `md`       | STRING                      | `MM-DD`                                                | month + day             |
| binary/base64     | `b64`      | STRING                      | hex/base64                                             | raw binary              |
| binary/base64     | `hex`      | STRING                      | hex/base64                                             | raw binary              |
| objectId (BSON)   | `oid`      | STRING                      | 24 hex chars                                           | BSON object identifier  |
| duration          | `dur`      | STRING                      | ISO 8601 duration                                      | e.g. `P3Y6M4DT12H30M5S` |

---

# 📌 Level 3: Semantic Types

| Semantic Type   | short code         | Possible Base Type(s) | Regex (example)                                                                                   | Notes                            |
|-----------------|--------------------|-----------------------|---------------------------------------------------------------------------------------------------|----------------------------------|
| zip code        | `zip`              | int, str              | `^\d{5}(-\d{4})?$`                                                                                | US ZIP format                    |
| uuid            | `uuid`             | str, bin              | `^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`                       | RFC 4122 UUID                    |
| email           | `mail`             | str                   | `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`                                                | simplified email                 |
| phone number    | `tel`              | str, int              | `^\+?[0-9]{7,15}$`                                                                                | E.164 format                     |
| ip address      | `ip` /`ip4`/ `ip6` | str                   | IPv4: `^(?:\d{1,3}\.){3}\d{1,3}$` <br> IPv6: `^(?:[A-Fa-f0-9]{0,4}:){2,7}[A-Fa-f0-9]{0,4}$`       | IPv4 or IPv6                     |
| mac address     | `mac`              | str, bin              | `^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$`                                                            | colon‑separated MAC              |
| url             | `url`              | str, uri              | `^https?:\/\/[^\s/$.?#].[^\s]*$`                                                                  | HTTP/HTTPS URL                   |
| geo coordinates | `geo`              | str, dec              | `^-?\d{1,2}\.\d+,\s*-?\d{1,3}\.\d+$`                                                              | lat,long pair                    |
| country code    | `ctry`             | str                   | `^[A-Z]{2}$`                                                                                      | ISO 3166‑1 alpha‑2               |
| currency code   | `cur`              | str                   | `^[A-Z]{3}$`                                                                                      | ISO 4217                         |
| iban            | `iban`             | str                   | `^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$`                                                               | bank account number              |
| credit card     | `cc`               | str, int              | visa `^4[0-9]{12}(?:[0-9]{3})?$` <br/> mastercard `^5[1-5][0-9]{14}$` <br/> ae `^3[47][0-9]{13}$` | Visa, MasterCard, AmEx, Discover |
| hash            | `hash`             | str, bin              | `^[A-Fa-f0-9]{32,64}$`                                                                            | MD5/SHA256 hex                   |
| jwt             | `jwt`              | str                   | `^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`                                                | JSON Web Token                   |
| mime type       | `mime`             | str                   | `^[a-z]+\/[a-z0-9\-\.+]+$`                                                                        | media type identifier            |

---
If there are other type(s) to be included, they will be added at future releases.