# Technical Map

## Purpose

Maps JsonT capabilities to full API signatures across Rust and Java.

**Use this first for all code and implementation questions.**
Only go to source code if a detail is missing here.

---

## Capability Overview

| Capability | Description | Memory |
| --- | --- | --- |
| Parse | Convert raw input into rows | O(1) streaming |
| Validate | Enforce schema constraints + rules | O(1) streaming |
| Transform | Apply derived schema operations | Per-row |
| Stringify | Serialize models back to wire format | O(1) |

All capabilities support streaming. Full dataset loading is never required.

---

## Key Differences: Rust vs Java

| Aspect | Rust | Java |
| --- | --- | --- |
| Entry point | Traits + free functions | `JsonT` static API |
| Schema parser | `pest` PEG grammar | ANTLR4 (schema DSL only) |
| Row parser | byte-based hand-written scanner | char-based bulk-buffered scanner |
| Error model | `Result<T, JsonTError>` enum | Unchecked exceptions (`JsonTError` hierarchy) |
| Null/optional | `Option<T>` | `Optional<T>` |
| Data types | Sealed enums (inline storage) | Sealed interfaces + records |
| Parallelism | Explicit `sync_channel` + threads | Built-in worker pool in `ValidationPipeline` |
| Memory | Manual, ~15–42 KB peak regardless of N | GC-managed, variable (bounded by queue size) |
| Decimal | `rust_decimal::Decimal` | `java.math.BigDecimal` |

---

## 🟥 Rust API

**Crate root:** `code/rust/jsont/src/`

### Core Traits (`lib.rs`)

```rust
trait Parseable {
    fn parse(input: &str) -> Result<Self, JsonTError>;
}

trait Stringification {
    fn stringify(&self, options: StringifyOptions) -> String;
}

trait Evaluatable {
    fn evaluate(&self, ctx: &EvalContext) -> Result<JsonTValue, JsonTError>;
}

trait RowTransformer {
    fn transform(&self, row: JsonTRow, registry: &SchemaRegistry) -> Result<JsonTRow, JsonTError>;
}

trait DiagnosticSink {
    fn emit(&mut self, event: DiagnosticEvent);
    fn flush(&mut self) -> Result<(), SinkError>;
}
```

---

### Entry Points

```rust
// Parse
fn parse_rows(input: &str, on_row: impl FnMut(JsonTRow)) -> Result<usize, JsonTError>
fn parse_rows_streaming<R: BufRead>(reader: R, on_row: impl FnMut(JsonTRow)) -> Result<usize, JsonTError>
struct RowIter  // lazy iterator over BufRead source

// Stringify
fn write_row<W: Write>(row: &JsonTRow, w: &mut W) -> io::Result<()>
fn write_rows<W: Write>(rows: &[JsonTRow], w: &mut W) -> io::Result<()>

// Schema parse (via trait)
JsonTNamespace::parse(input: &str) -> Result<JsonTNamespace, JsonTError>
```

---

### Model Types

#### `JsonTNamespace`

```rust
pub struct JsonTNamespace {
    pub base_url: String,
    pub version: String,
    pub catalogs: Vec<JsonTCatalog>,
    pub data_schema: String,   // name of root data schema
}
```

#### `JsonTCatalog`

```rust
pub struct JsonTCatalog {
    pub schemas: Vec<JsonTSchema>,
    pub enums: Vec<JsonTEnum>,
}
```

#### `JsonTSchema`

```rust
pub struct JsonTSchema {
    pub name: String,
    pub kind: SchemaKind,
    pub validation: Option<JsonTValidationBlock>,
}

pub enum SchemaKind {
    Straight { fields: Vec<JsonTField> },
    Derived { from: String, operations: Vec<SchemaOperation> },
}
```

#### `SchemaOperation`

```rust
pub enum SchemaOperation {
    Rename(Vec<RenamePair>),
    Exclude(Vec<FieldPath>),
    Project(Vec<FieldPath>),
    Filter  { expr: JsonTExpression, refs: Vec<String> },    // refs pre-computed at build time
    Transform { target: FieldPath, expr: JsonTExpression, refs: Vec<String> },
}

pub struct RenamePair { pub from: FieldPath, pub to: String }
pub struct FieldPath(pub Vec<String>);   // methods: new(), single(), join() -> String
```

#### `JsonTField`

```rust
pub struct JsonTField {
    pub name: String,
    pub kind: JsonTFieldKind,
}

pub enum JsonTFieldKind {
    Scalar {
        field_type: JsonTFieldType,
        optional: bool,
        default: Option<JsonTValue>,
        constant: Option<JsonTValue>,
        constraints: Vec<JsonTConstraint>,
    },
    Object {
        schema_ref: String,
        is_array: bool,
        optional: bool,
        constraints: Vec<JsonTConstraint>,
    },
}

pub struct JsonTFieldType {
    pub scalar: ScalarType,
    pub is_array: bool,    // methods: new(), simple()
}
```

#### `ScalarType` (Rust, 28 variants)

```rust
pub enum ScalarType {
    // Numeric
    I16, I32, I64, U16, U32, U64, D32, D64, D128,
    // Boolean
    Bool,
    // String-like
    Str, NStr, Uri, Uuid, Email, Hostname, Ipv4, Ipv6,
    // Temporal (integer on wire)
    Date, Time, DateTime, Timestamp,
    // Temporal (string on wire)
    Tsz, Inst, Duration,
    // Binary
    Base64, Hex, Oid,
}
// Methods: keyword(), from_keyword(), supports_value_constraints(), supports_length_constraints(), supports_regex_constraints()
```

#### `JsonTConstraint`

```rust
pub enum JsonTConstraint {
    Required(bool),
    Value { key: ValueConstraintKey, value: f64 },        // MinValue, MaxValue, MinPrecision, MaxPrecision
    Length { key: LengthConstraintKey, value: u64 },      // MinLength, MaxLength
    ArrayItems(ArrayItemsConstraint),                      // MinItems, MaxItems, MaxNullItems, AllowNullItems
    Regex(String),
}
```

#### `JsonTValidationBlock`

```rust
pub struct JsonTValidationBlock {
    pub rules: Vec<JsonTRule>,
    pub unique: Vec<Vec<FieldPath>>,
    pub dataset: Vec<JsonTExpression>,
}

pub enum JsonTRule {
    Expression(JsonTExpression),
    ConditionalRequirement { condition: JsonTExpression, required_fields: Vec<FieldPath> },
}
```

#### `JsonTExpression`

```rust
pub enum JsonTExpression {
    Literal(JsonTValue),
    FieldRef(FieldPath),
    FunctionCall { name: String, args: Vec<JsonTExpression> },
    UnaryOp  { op: UnaryOp, operand: Box<JsonTExpression> },
    BinaryOp { op: BinaryOp, left: Box<JsonTExpression>, right: Box<JsonTExpression> },
}
// Convenience: literal(), field(), field_name(), call(), not(), negate(), binary(),
//              and(), or(), eq(), neq(), lt(), le(), gt(), ge(), add(), sub(), mul(), div()

pub enum UnaryOp  { Not, Neg }
pub enum BinaryOp { Or, And, Eq, Neq, Lt, Le, Gt, Ge, Add, Sub, Mul, Div }
```

#### Data Values

```rust
pub enum JsonTValue {
    Null,
    Unspecified,             // wire: `_`
    Number(JsonTNumber),
    Bool(bool),
    Str(JsonTString),
    Enum(String),
    Object(JsonTRow),
    Array(JsonTArray),
}
// Factories: null(), unspecified(), bool(), str(), enum_val(),
//            i16()..d128(), nstr(), uuid(), uri(), email(), hostname(), ipv4(), ipv6(),
//            date_int(), time_int(), datetime_int(), timestamp_int()
// Queries:   is_null(), is_numeric(), is_string(), as_f64(), as_bool(), as_str(), type_name()

pub enum JsonTNumber { I16(i16), I32(i32), I64(i64), U16(u16), U32(u32), U64(u64),
                       D32(f32), D64(f64), D128(Decimal), Date(u32), Time(u32), DateTime(u64), Timestamp(i64) }

pub enum JsonTString { Plain(String), Nstr(String), Uuid(String), Uri(String), Email(String),
                       Hostname(String), Ipv4(String), Ipv6(String), Date(String), Time(String),
                       DateTime(String), Timestamp(String), Tsz(String), Inst(String), Duration(String),
                       Base64(String), Hex(String), Oid(String) }

pub struct JsonTRow   { pub fields: Vec<JsonTValue> }   // new(), empty(), len(), get(usize)
pub struct JsonTArray { pub items: Vec<JsonTValue> }    // new(), empty(), len(), get(usize)
```

---

### Builders

```rust
// Schema
JsonTSchemaBuilder::straight(name) -> Self
JsonTSchemaBuilder::derived(name, from) -> Self
  .field(field) / .field_from(builder)        // straight only
  .operation(op)                               // derived only
  .validation(block) / .validation_from(b)
  .build() -> Result<JsonTSchema, JsonTError>

// Field
JsonTFieldBuilder::scalar(name, ScalarType) -> Self
JsonTFieldBuilder::object(name, schema_ref) -> Self
  .optional() .as_array() .required(bool)
  .min_value(f64) .max_value(f64) .min_precision(f64) .max_precision(f64)
  .min_length(u64) .max_length(u64) .regex(pattern)
  .array_items_count(key, u64) .allow_null_items(bool)
  .default_value(v) .constant_value(v)
  .build() -> Result<JsonTField, JsonTError>

// Validation
JsonTValidationBlockBuilder
  .rule_expr(expr)
  .rule_conditional(condition, required_fields)
  .unique_fields(field_paths)
  .dataset_expr(expr)
  .build() -> Result<JsonTValidationBlock, JsonTError>

// Catalog
JsonTCatalogBuilder::new()
  .schema(s) / .schema_from(builder)
  .enum_def(e) / .enum_from(builder)
  .build() -> Result<JsonTCatalog, JsonTError>

// Namespace
JsonTNamespaceBuilder::new(base_url, version)
  .data_schema(name) .catalog(c) / .catalog_from(builder)
  .build() -> Result<JsonTNamespace, JsonTError>

// Enum
JsonTEnumBuilder::new(name).value(constant).build() -> Result<JsonTEnum, JsonTError>

// Data
JsonTRowBuilder::new().push(v).build() -> JsonTRow
JsonTRowBuilder::with_schema(schema).push_checked(v).build_checked() -> Result<JsonTRow, JsonTError>
JsonTArrayBuilder::new().push(v).build() -> JsonTArray

// Schema inference
SchemaInferrer::new()
  .sample_size(n) .nullable_threshold(f64) .schema_name(name)
  .infer(rows) / .infer_with_names(rows, names) -> Result<JsonTSchema, JsonTError>
```

---

### Validation Pipeline

```rust
ValidationPipeline::builder(schema: JsonTSchema) -> ValidationPipelineBuilder

ValidationPipelineBuilder
  .without_console()
  .with_sink(Box<dyn DiagnosticSink + Send>)
  .build() -> ValidationPipeline

ValidationPipeline
  .validate_each<I, F>(rows: I, on_clean: F)       // streaming, O(1)
  .validate_rows(rows) -> Vec<JsonTRow>             // collects clean rows
  .validate_one(row, on_clean: impl FnOnce)         // single row
  .finish(self) -> Result<(), SinkError>            // flush + join thread
```

---

### Diagnostics

```rust
pub enum Severity { Fatal, Warning, Info }

pub enum SinkError { Io(String), Other(String) }

// Built-in sinks
struct ConsoleSink    // prints to stderr
struct MemorySink     // collects events in Vec
struct FileSink       // writes to file

// DiagnosticEvent
DiagnosticEvent::fatal(kind) / warning(kind) / info(kind) -> Self
  .at_row(usize) .with_source(String)
  .is_fatal() -> bool
```

Key `EventKind` variants:

- `TypeMismatch`, `ShapeMismatch`, `RequiredFieldMissing`, `ConstraintViolation`
- `RuleViolation`, `ConditionalRequirementViolation`, `UniqueViolation`, `FormatViolation`
- `DatasetRuleViolation`, `ParseFailure`
- `RowAccepted`, `RowRejected`, `RowAcceptedWithWarnings`
- `ProcessStarted`, `ProcessCompleted`, `Notice`

---

### Error Types

```rust
pub enum JsonTError {
    Parse(ParseError),
    Eval(EvalError),
    Transform(TransformError),
    Stringify(StringifyError),
}

ParseError:    Pest(String), UnknownSchemaRef, UnknownFieldType, InvalidConstraint, ExpectedSchemaid, Unexpected
EvalError:     UnboundField, TypeMismatch, DivisionByZero, UnknownFunction, ArityMismatch, InvalidExpression
TransformError: UnknownSchema, FieldNotFound, FilterFailed, TransformFailed, CyclicDerivation, Filtered  // Filtered = row skipped, not error
StringifyError: UnresolvedSchemaRef, UnstringifiableValue
BuildError:    MissingField, InvalidConstraintForType, DuplicateFieldName, DuplicateSchemaName,
               DuplicateEnumValue, NameHintMismatch, RowTypeMismatch, TooManyValues
```

---

### Utility

```rust
pub struct StringifyOptions { pub pretty: bool, pub indent: usize }
// Factories: compact(), pretty(), pretty_with_indent(usize)

pub struct EvalContext { pub bindings: HashMap<String, JsonTValue> }
// Methods: new(), bind(key, value) -> Self, get(key) -> Option<&JsonTValue>

pub struct SchemaRegistry { pub schemas: HashMap<String, JsonTSchema> }
// Methods: new(), register(schema), get(name), from_namespace(ns)
```

---

## 🟦 Java API

**Root package:** `io.github.datakore.jsont`
**Source:** `code/java/jsont/src/main/java/`

### Entry Point: `JsonT` (static facade)

```java
// Parse
JsonTNamespace   parseNamespace(String input)
int              parseRows(String input, RowConsumer consumer)
int              parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException
RowIter          rowIter(Reader reader)    // must close

// Stringify
String           stringify(JsonTSchema schema)
String           stringify(JsonTSchema schema, StringifyOptions opts)
String           stringify(JsonTNamespace namespace)
String           stringify(JsonTNamespace namespace, StringifyOptions opts)
String           stringifyRow(JsonTRow row)
void             writeRow(JsonTRow row, Writer w) throws IOException
void             writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException
```

---

### Java Model Types

#### Java `JsonTNamespace` (package: `model`)

```java
public class JsonTNamespace {
    String baseUrl(); String version(); String dataSchema();
    List<JsonTCatalog> catalogs();
    Optional<JsonTSchema> findSchema(String name);
    Optional<JsonTEnum> findEnum(String name);
    long schemaCount(); long enumCount();
}
```

#### Java `JsonTCatalog`

```java
public class JsonTCatalog {
    List<JsonTSchema> schemas(); List<JsonTEnum> enums();
    Optional<JsonTSchema> findSchema(String name);
    Optional<JsonTEnum> findEnum(String name);
}
```

#### Java `JsonTSchema`

```java
public class JsonTSchema {
    String name(); SchemaKind kind();
    List<JsonTField> fields();
    Optional<String> derivedFrom(); List<SchemaOperation> operations();
    Optional<JsonTValidationBlock> validation();
    boolean isStraight(); boolean isDerived();
    Optional<JsonTField> findField(String fieldName);
    int fieldCount();
}

enum SchemaKind { STRAIGHT, DERIVED }
```

#### `SchemaOperation` (sealed interface)

```java
sealed interface SchemaOperation {
    record Rename(List<RenamePair> pairs) implements SchemaOperation {}
    record Exclude(List<FieldPath> paths) implements SchemaOperation {}
    record Project(List<FieldPath> paths) implements SchemaOperation {}
    record Filter(JsonTExpression predicate) implements SchemaOperation {}
    record Transform(FieldPath target, JsonTExpression expr) implements SchemaOperation {}

    // Static factories
    static SchemaOperation rename(List<RenamePair> pairs)
    static SchemaOperation rename(RenamePair... pairs)
    static SchemaOperation exclude(FieldPath... paths)
    static SchemaOperation project(FieldPath... paths)
    static SchemaOperation filter(JsonTExpression predicate)
    static SchemaOperation transform(FieldPath target, JsonTExpression expr)
    static SchemaOperation transform(String targetField, JsonTExpression expr)
}
```

#### Java `JsonTField`

```java
public class JsonTField {
    String name(); FieldKind kind();
    ScalarType scalarType();   // throws if object field
    String objectRef();        // throws if scalar field
    boolean optional(); FieldConstraints constraints();
}

enum FieldKind { SCALAR, OBJECT, ARRAY_SCALAR, ARRAY_OBJECT }
  // isArray(), isScalar(), isObject()

record FieldConstraints(
    Double minValue, Double maxValue,
    Integer minLength, Integer maxLength, String pattern,
    boolean required, Integer maxPrecision,
    Integer minItems, Integer maxItems,
    boolean allowNullElements, Integer maxNullElements,
    JsonTValue constantValue
) {
    static FieldConstraints NONE;   // fully unconstrained sentinel
    boolean hasAny();
}
```

#### `ScalarType` (Java, 27 variants)

```java
enum ScalarType {
    I16, I32, I64, U16, U32, U64, D32, D64, D128,
    BOOL,
    STR, NSTR, URI, UUID, EMAIL, HOSTNAME, IPV4, IPV6,
    DATE, TIME, DATETIME, TIMESTAMP, TSZ, DURATION, INST,
    BASE64, OID, HEX;

    String keyword();
    static ScalarType fromKeyword(String keyword);  // throws IllegalArgumentException
    boolean isNumeric(); boolean isStringLike();
}
```

#### Java `JsonTValidationBlock`

```java
record JsonTValidationBlock(List<List<FieldPath>> uniqueKeys, List<JsonTRule> rules) {
    boolean isEmpty();
}

sealed interface JsonTRule {
    record Expression(JsonTExpression expr) implements JsonTRule {}
    record ConditionalRequirement(JsonTExpression condition, List<FieldPath> requiredFields) implements JsonTRule {}

    static JsonTRule expression(JsonTExpression expr)
    static JsonTRule conditionalRequirement(JsonTExpression condition, List<FieldPath> requiredFields)
}
```

#### `JsonTExpression` (sealed interface)

```java
sealed interface JsonTExpression {
    record Literal(JsonTValue value) implements JsonTExpression {}
    record FieldRef(FieldPath path) implements JsonTExpression {}
    record Binary(BinaryOp op, JsonTExpression lhs, JsonTExpression rhs) implements JsonTExpression {}
    record Unary(UnaryOp op, JsonTExpression operand) implements JsonTExpression {}

    JsonTValue evaluate(EvalContext ctx);  // throws JsonTError.Eval

    static JsonTExpression literal(JsonTValue value)
    static JsonTExpression fieldName(String name)
    static JsonTExpression field(FieldPath path)
    static JsonTExpression binary(BinaryOp op, JsonTExpression lhs, JsonTExpression rhs)
    static JsonTExpression not(JsonTExpression operand)
    static JsonTExpression neg(JsonTExpression operand)
}

enum BinaryOp { EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL, DIV }
  // isLogical(), isArithmetic()
enum UnaryOp { NOT, NEG }
```

#### `FieldPath`

```java
public class FieldPath {
    static FieldPath single(String name)
    static FieldPath of(String first, String... rest)
    static FieldPath parse(String dotted)

    List<String> segments(); String leaf(); boolean isSimple(); String dotJoined();
}
```

#### Java Data Values

```java
sealed interface JsonTValue {
    record Null() implements JsonTValue {}
    record Unspecified() implements JsonTValue {}     // wire: `_`
    record Bool(boolean value) implements JsonTValue {}
    record Enum(String value) implements JsonTValue {}
    record Array(List<JsonTValue> elements) implements JsonTValue {}
    // + JsonTNumber (14 records), JsonTString (18 records) — see below

    // Static factories
    static JsonTValue nullValue()
    static JsonTValue unspecified()
    static JsonTValue bool(boolean v)
    static JsonTValue enumValue(String v)
    static JsonTValue i16(short v) ... d128(BigDecimal v)    // 9 numeric
    static JsonTValue text(String v)
    static JsonTValue nstr(String v), uuid(String v), uri(String v), email(String v),
                      hostname(String v), ipv4(String v), ipv6(String v)  // semantic strings
    static JsonTValue dateInt(int v), timeInt(int v), datetimeInt(long v), timestampInt(long v)
    static JsonTValue array(List<JsonTValue> elements)

    // Default methods
    boolean isNumeric(); boolean isNull(); boolean isUnspecified(); boolean isStringLike();
    double toDouble();
    Optional<JsonTString> asStr(); Optional<String> asRawStr();
    String asText();
    static JsonTValue promote(JsonTValue value, ScalarType type)
}

sealed interface JsonTNumber {
    record I16(short value), I32(int value), I64(long value),
           U16(int value), U32(long value), U64(long value),
           D32(float value), D64(double value), D128(BigDecimal value),
           Date(int value), Time(int value), DateTime(long value), Timestamp(long value)
}

sealed interface JsonTString {
    // 18 records: Plain, Nstr, Uuid, Uri, Email, Hostname, Ipv4, Ipv6,
    //             Date, Time, DateTime, Timestamp, Tsz, Inst, Duration,
    //             Base64, Hex, Oid  — each wraps String value()
    String value();
    default String typeName();
    static String quote(String s);   // escape quotes and backslashes
}

record JsonTRow(long index, List<JsonTValue> values) {
    static JsonTRow of(JsonTValue... values)
    static JsonTRow at(long index, JsonTValue... values)
    static JsonTRow at(long index, List<JsonTValue> values)
    int size(); JsonTValue get(int i); boolean isEmpty();
    JsonTRow withIndex(long newIndex); JsonTRow withValues(List<JsonTValue> newValues);
}

record JsonTEnum(String name, List<String> values) {
    boolean contains(String value);
}
```

---

### Builders (package: `builder`)

```java
// Field
JsonTFieldBuilder.scalar(String name, ScalarType type)
JsonTFieldBuilder.object(String name, String schemaRef)
  .optional() .asArray()
  .minValue(double) .maxValue(double)
  .minLength(int) .maxLength(int) .pattern(String)
  .minItems(int) .maxItems(int) .allowNullElements(boolean) .maxNullElements(int)
  .maxPrecision(int)
  .build() throws BuildError

// Schema
JsonTSchemaBuilder.straight(String name)
JsonTSchemaBuilder.derived(String name, String from)
  .fieldFrom(JsonTFieldBuilder) throws BuildError       // straight only
  .operation(SchemaOperation) throws BuildError         // derived only
  .validation(JsonTValidationBlock)
  .validationFrom(JsonTValidationBlockBuilder) throws BuildError
  .build() throws BuildError

// Validation
JsonTValidationBlockBuilder
  .rule(JsonTExpression)
  .conditionalRule(JsonTExpression condition, FieldPath... requiredFields) throws BuildError
  .unique(FieldPath... fieldPaths) throws BuildError
  .datasetExpr(JsonTExpression)
  .build() throws BuildError

// Catalog
JsonTCatalogBuilder
  .schema(JsonTSchema) / .schemaFrom(JsonTSchemaBuilder) throws BuildError
  .enumDef(JsonTEnum) / .enumFrom(JsonTEnumBuilder) throws BuildError
  .build() throws BuildError

// Namespace
JsonTNamespaceBuilder.create()
  .baseUrl(String) .version(String) .dataSchema(String)
  .catalog(JsonTCatalog) / .catalogFrom(JsonTCatalogBuilder) throws BuildError
  .build() throws BuildError

// Enum
JsonTEnumBuilder.value(String constant) throws BuildError  .build() throws BuildError
```

---

### Validation Pipeline (package: `validate`)

```java
ValidationPipeline.builder(JsonTSchema schema) -> ValidationPipelineBuilder

ValidationPipelineBuilder
  .withoutConsole()
  .withSink(DiagnosticSink sink)
  .withWorkers(int n)        // parallel workers; default = 1 at ≤10K, 8 at ≥100K
  .build() -> ValidationPipeline

ValidationPipeline
  .validateEach(Iterable<JsonTRow> rows, Consumer<JsonTRow> onClean)   // O(1) streaming
  .validateRows(List<JsonTRow> rows) -> List<JsonTRow>                  // collects clean rows
  .validateStream(Stream<JsonTRow> rows) -> Stream<JsonTRow>            // stream API
  .finish()                                                              // flush sinks
```

---

### Diagnostics (package: `diagnostic`)

```java
enum DiagnosticSeverity { FATAL, WARNING, INFO }

interface DiagnosticSink {
    void emit(DiagnosticEvent event);
    void flush();
}

// Built-in sinks: ConsoleSink, MemorySink
```

---

### Error Hierarchy (package: `error`)

```java
class JsonTError extends RuntimeException {
    static class Parse      extends JsonTError {}   // namespace/row parse failures
    static class Eval       extends JsonTError {}   // expression evaluation failures
    static class Transform  extends JsonTError {
        static class Filtered      extends Transform {}  // row skipped — not a hard error
        static class FieldNotFound extends Transform {}
        static class UnknownSchema extends Transform {}
        static class CyclicDerivation extends Transform {}
    }
    static class Stringify  extends JsonTError {}
    static class SchemaInvalid extends JsonTError {}
}

class BuildError extends RuntimeException {}
```

---

### Stringify / Parse internals (packages: `stringify`, `parse`)

```java
// Stringify
StringifyOptions.compact() / pretty() / prettyWithIndent(int indent)
JsonTStringifier.stringify(JsonTSchema, StringifyOptions)
JsonTStringifier.stringify(JsonTNamespace, StringifyOptions)
JsonTStringifier.stringify(JsonTRow)
RowWriter.writeRow(JsonTRow row, Writer w) throws IOException
RowWriter.writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException

// Parse
interface RowConsumer { void accept(JsonTRow row); }
interface RowIter extends AutoCloseable { ... }   // lazy, must close
JsonTParser.parseNamespace(String input)
JsonTParser.parseRows(String input, RowConsumer consumer)
JsonTParser.parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException
JsonTParser.rowIter(Reader reader)
```

---

## Typical Pipeline Patterns

### Rust — single-thread streaming

```rust
let ns = JsonTNamespace::parse(source)?;
let registry = SchemaRegistry::from_namespace(&ns);
let schema = registry.get("MySchema").unwrap().clone();
let pipeline = ValidationPipeline::builder(schema).build();

let f = File::open("data.jsont")?;
let reader = BufReader::new(f);
parse_rows_streaming(reader, |row| {
    pipeline.validate_one(row, |clean| { /* consume */ });
})?;
pipeline.finish()?;
```

### Rust — parallel pipeline

```rust
let (tx, rx) = sync_channel(256);
let handle = thread::spawn(move || {
    for row in rx { pipeline.validate_one(row, |clean| sink(clean)); }
    pipeline.finish()
});
parse_rows_streaming(reader, |row| tx.send(row).unwrap())?;
drop(tx);
handle.join().unwrap()?;
```

### Java — streaming

```java
JsonTNamespace ns = JsonT.parseNamespace(source);
JsonTSchema schema = ns.findSchema("MySchema").orElseThrow();
ValidationPipeline pipeline = ValidationPipeline.builder(schema).build();

try (Reader r = new FileReader("data.jsont")) {
    JsonT.parseRowsStreaming(r, row ->
        pipeline.validateEach(List.of(row), clean -> consume(clean))
    );
}
pipeline.finish();
```

---

## JSON Interoperability

Bidirectional conversion between standard JSON and `JsonTRow`.
Schema provides the field-name ↔ position mapping; type coercion is guided by declared `ScalarType`.

**Constraint:** Only **straight** schemas are supported directly. Resolve derived schemas first.

### Type mapping (JSON → JsonT)

| JSON type | Coerced to |
| --- | --- |
| `null` | `Null` (always, regardless of declared type) |
| `boolean` | `Bool` |
| integer number | `I16`–`U64`, `D32`, `D64`, `D128`, `Date`, `Time`, `DateTime`, `Timestamp` |
| float number | `D32`, `D64`, `D128`, `Timestamp` |
| `string` | `Str`, `NStr`, `Uuid`, `Uri`, `Email`, `Hostname`, `Ipv4`, `Ipv6`, `Tsz`, `Inst`, `Duration`, `Base64`, `Hex`, `Oid` |
| `array` | `Array(JsonTArray)` — items coerced per element |
| `object` | Not supported without nested schema resolution |

**`Unspecified` on output:** mapped to JSON `null` (no JsonT wire token in JSON).

---

## 🟥 Rust JSON Interop (`json` module)

**Module:** `code/rust/jsont/src/json/`

```rust
// ── Input/output mode enums ──────────────────────────────────────────────────
pub enum JsonInputMode  { Ndjson, Array, Object }   // Default: Ndjson
pub enum JsonOutputMode { Ndjson, Array }            // Default: Ndjson

// ── Policy enums ─────────────────────────────────────────────────────────────
pub enum UnknownFieldPolicy { Skip, Reject }         // Default: Skip
pub enum MissingFieldPolicy { UseDefault, Reject }   // Default: UseDefault

// ── Reader ────────────────────────────────────────────────────────────────────
JsonReader::with_schema(schema: JsonTSchema) -> JsonReaderBuilder

JsonReaderBuilder
  .mode(JsonInputMode) -> Self
  .unknown_fields(UnknownFieldPolicy) -> Self
  .missing_fields(MissingFieldPolicy) -> Self
  .build() -> JsonReader

JsonReader
  .read(&str, on_row: impl FnMut(JsonTRow)) -> Result<usize, JsonTError>
  .read_streaming<R: BufRead>(reader: R, on_row: impl FnMut(JsonTRow)) -> Result<usize, JsonTError>
  // Ndjson: true O(1) streaming; Array/Object: buffers full input

// ── Writer ────────────────────────────────────────────────────────────────────
JsonWriter::with_schema(schema: JsonTSchema) -> JsonWriterBuilder

JsonWriterBuilder
  .mode(JsonOutputMode) -> Self
  .pretty(bool) -> Self
  .build() -> JsonWriter

JsonWriter
  .write_row<W: Write>(row: &JsonTRow, w: &mut W) -> Result<(), JsonTError>
  .write_rows<W: Write>(rows: &[JsonTRow], w: &mut W) -> Result<(), JsonTError>
  .write_streaming<I, W>(iter: I, w: &mut W) -> Result<(), JsonTError>
      where I: Iterator<Item = JsonTRow>, W: Write
```

---

## 🟦 Java JSON Interop (`json` package)

**Package:** `io.github.datakore.jsont.json`

```java
// ── Input/output mode enums ──────────────────────────────────────────────────
enum JsonInputMode  { NDJSON, ARRAY, OBJECT }   // Default: NDJSON
enum JsonOutputMode { NDJSON, ARRAY }            // Default: NDJSON

// ── Policy enums ─────────────────────────────────────────────────────────────
enum UnknownFieldPolicy { SKIP, REJECT }         // Default: SKIP
enum MissingFieldPolicy { USE_DEFAULT, REJECT }  // Default: USE_DEFAULT

// ── Reader ────────────────────────────────────────────────────────────────────
JsonReader.withSchema(JsonTSchema schema) -> JsonReaderBuilder

JsonReaderBuilder
  .mode(JsonInputMode) -> JsonReaderBuilder
  .unknownFields(UnknownFieldPolicy) -> JsonReaderBuilder
  .missingFields(MissingFieldPolicy) -> JsonReaderBuilder
  .build() -> JsonReader

JsonReader
  .read(String input, RowConsumer consumer) -> int
  .readStreaming(Reader reader, RowConsumer consumer) throws IOException -> int
  // Ndjson: true O(1) streaming; Array/Object: buffers full input

// ── Writer ────────────────────────────────────────────────────────────────────
JsonWriter.withSchema(JsonTSchema schema) -> JsonWriterBuilder

JsonWriterBuilder
  .mode(JsonOutputMode) -> JsonWriterBuilder
  .pretty() -> JsonWriterBuilder
  .build() -> JsonWriter

JsonWriter
  .writeRow(JsonTRow row) -> String
  .writeRow(JsonTRow row, Writer w) throws IOException
  .writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException
```

### `JsonT` facade shortcuts

```java
JsonT.jsonReader(JsonTSchema schema) -> JsonReaderBuilder   // = JsonReader.withSchema(schema)
JsonT.jsonWriter(JsonTSchema schema) -> JsonWriterBuilder   // = JsonWriter.withSchema(schema)
JsonT.fromJson(String jsonObject, JsonTSchema schema) -> JsonTRow
JsonT.toJson(JsonTRow row, JsonTSchema schema) -> String
```

### Java JSON pipeline example

```java
JsonTSchema schema = ns.findSchema("Order").orElseThrow();

JsonReader  reader   = JsonT.jsonReader(schema).mode(JsonInputMode.NDJSON).build();
JsonWriter  writer   = JsonT.jsonWriter(schema).mode(JsonOutputMode.NDJSON).build();
ValidationPipeline pipeline = ValidationPipeline.builder(schema).build();

try (Reader  src = new FileReader("orders.json");
     Writer  dst = new FileWriter("orders.jsont")) {
    reader.readStreaming(src, row ->
        pipeline.validateEach(List.of(row), clean -> {
            try { writer.writeRow(clean, dst); dst.write('\n'); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        })
    );
}
pipeline.finish();
```

### Rust JSON pipeline example

```rust
let schema = registry.get("Order").unwrap().clone();
let reader = JsonReader::with_schema(schema.clone()).build();
let writer = JsonWriter::with_schema(schema).build();

let f = File::open("orders.json")?;
let reader_buf = BufReader::new(f);
let mut out = BufWriter::new(File::create("orders.jsont")?);

reader.read_streaming(reader_buf, |row| {
    writer.write_row(&row, &mut out).unwrap();
    out.write_all(b"\n").unwrap();
})?;
```
