# Format Comparison: Native vs. JsonT

This document compares the wire-format overhead of standard data exchange formats against the **JsonT Compact Format**.

Because JsonT is **schema-driven**, the data files do not need to store field names, tags, or keys. This results in significant size reductions without sacrificing the hierarchical structure.

| Industry Standard | Native Format Type | JsonT (Compact) Format | Est. Size Reduction | Primary Reason |
| :--- | :--- | :--- | :--- | :--- |
| **HL7 (834) / EDI** | Delimited (Tag+Value) | Positional Row | **5% – 15%** | Removal of segment tags (ISA, ST, INS) and conversion of fixed-width segments to variable length. |
| **ISO 20022 (Fintech)**| XML | Positional Row | **70% – 85%** | Elimination of redundant XML start and end tags for every field. |
| **NACHA (Fintech)** | Fixed-Width (Text) | Positional Row | **40% – 60%** | Elimination of space/zero padding required by legacy record-of-94-characters rules. |
| **FIX Protocol** | Tag=Value | Positional Row | **45% – 65%** | Elimination of numeric tag identifiers (e.g., `35=`, `49=`, `52=`) for every field. |
| **OpenRTB (AdTech)** | Full JSON | Positional Row | **55% – 75%** | Elimination of repeated property keys (e.g., `"bidfloor"`, `"id"`, `"banner"`) across thousands of objects. |

## Why JsonT is Smaller

### 1. Schema-Driven Positional Rows

Unlike JSON or XML, where the data describes itself (key-value), JsonT data assumes the consumer has the schema. A JSON object with 20 fields repeated 1,000 times stores those 20 keys 1,000 times. JsonT stores them **zero** times in the data file.

### 2. No Delimiter Overhead

While CSV is also small, it cannot handle the **nesting** required by HL7 or ISO 20022. JsonT combines the compactness of CSV with the hierarchical power of JSON using a streaming-friendly bracket system (`{`, `}`).

### 3. Binary vs. String (Optional)

While the comparisons above assume the UTF-8 wire format, JsonT's internal representation of numerics (I16, D128, etc.) allows for further optimizations during processing.

## Conclusion

JsonT typically provides a **2x to 5x reduction** in data volume compared to modern JSON/XML-based fintech and adtech formats, while maintaining a size profile competitive with (or better than) legacy binary/delimited formats like EDI and NACHA.
