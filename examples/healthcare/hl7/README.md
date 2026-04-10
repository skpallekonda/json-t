# HL7 834 JsonT Schema

This directory contains JsonT schemas and sample data for HL7 834 (Benefit Enrollment and Maintenance) syntactical checks.

## Files

- [hl7_834.jsont](file:///c:/Users/sasik/github/jsont-rust/examples/healthcare/hl7/hl7_834.jsont): The JsonT schema definition for HL7 834 segments, loops, and validation rules.
- [sample_834.jsont.data](file:///c:/Users/sasik/github/jsont-rust/examples/healthcare/hl7/sample_834.jsont.data): A sample data file in JsonT row format that matches the schema.

## Features

- **Segment Definitions**: ISA, ST, BGN, INS, REF, DTP, NM1, DMG, HD segments are defined with proper field types and constraints.
- **Loop Hierarchies**: Supports nested loops like 1000A (Sponsor), 1000B (Payer), 2000 (Member), and 2300 (Health Coverage).
- **Validation Rules**: Includes length constraints, constant value checks, and enums for code sets (e.g., Purpose Code, Maintenance Type, Gender).
- **Enums**: Comprehensive enums for common HL7/X12 code fields to ensure data quality.

## Usage

You can use the JsonT CLI or API to validate HL7 data against these schemas.

### Example (Rust)

```rust
let ns = JsonTNamespace::parse(include_str!("hl7_834.jsont"))?;
let registry = SchemaRegistry::from_namespace(&ns);
let schema = registry.get("HL7_834").unwrap().clone();
let pipeline = ValidationPipeline::builder(schema).build();

parse_rows(include_str!("sample_834.jsont.data"), |row| {
    pipeline.validate_one(row, |clean| println!("Valid Row: {:?}", clean));
})?;
```

### Example (Java)

```java
JsonTNamespace ns = JsonT.parseNamespace(Files.readString(Path.of("hl7_834.jsont")));
JsonTSchema schema = ns.findSchema("HL7_834").get();
ValidationPipeline pipeline = ValidationPipeline.builder(schema).build();

JsonT.parseRows(Files.readString(Path.of("sample_834.jsont.data")), row -> {
    pipeline.validate_one(row, clean -> System.out.println("Valid Row"));
});
```
