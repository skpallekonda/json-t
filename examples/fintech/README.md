# Fintech JsonT Examples

This directory contains JsonT schemas for standard financial data exchange formats.

## 1. ISO 20022 (Payment Initiation)

- **File**: [iso20022_pain001.jsont](file:///c:/Users/sasik/github/jsont-rust/examples/fintech/iso20022_pain001.jsont)
- **Description**: Models the modern global standard for payment initiation (`pain.001`).
- **Features**: 
    - IBAN regex validation.
    - Currency code constraints.
    - Hierarchical structure mapping XML-based ISO 20022 to JsonT objects.
    - Party and Agent definitions.

## 2. NACHA (ACH)

- **File**: [nacha.jsont](file:///c:/Users/sasik/github/jsont-rust/examples/fintech/nacha.jsont)
- **Description**: Models the US Automated Clearing House (ACH) file format.
- **Features**:
    - Record-type level constant checks (1, 5, 6, 8, 9).
    - Fixed-length constraints to mimic positional data requirements.
    - Cross-record validation mapping (Batch Header IDs matching Batch Control).

## 3. FIX Protocol

- **File**: [fix_protocol.jsont](file:///c:/Users/sasik/github/jsont-rust/examples/fintech/fix_protocol.jsont)
- **Description**: Models the Financial Information eXchange (FIX) protocol for trading.
- **Features**:
    - **Conditional Requirements**: Uses `ordType == "2" -> required(price)` to enforce FIX business logic where Limit orders must have a Price.
    - **Header/Trailer Pattern**: Standard modeling of the FIX messaging envelope.
    - **Tag Mapping**: Direct mapping of standard FIX tags to JsonT field names and types.

---

### Usage

You can use these schemas to validate flat-file or JSON-mapped financial records before transmission or submission to clearing systems.

```bash
# Example validation (concept)
jsont validate --schema examples/fintech/iso20022_pain001.jsont examples/fintech/sample_iso20022.jsont.data
```
