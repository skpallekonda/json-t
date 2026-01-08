# JSON-T

**JSON-T** is a **schema-driven, positional data language** designed to reduce payload size and enforce strong, symmetric data validation — especially for **large datasets, batch APIs, and streaming use cases**.

Unlike JSON, where field names are repeated in every record, JSON-T defines structure once using a schema and encodes data as compact positional tuples.

---

## Why JSON-T?
JSON is flexible and human-readable, but it has well-known limitations at scale:

* Repeated field names inflate payload size
* Validation is external, duplicated, and inconsistent
* Schema drift is hard to detect
* Streaming validation is inefficient

JSON-T addresses these problems directly.

---

## Design Inspiration

JSON-T combines the best properties of existing data formats while avoiding their limitations:

- **JSON**: Retains support for nested structures, complex objects, and arrays.
- **CSV**: Adopts positional fields and removes repeating identifiers to significantly reduce payload size.
- **Protocol Buffers**: Utilizes multiple scalar types (boolean, number, string) with strict typing.
- **Apache Avro**: Supports embedding schemas alongside data for self-describing catalogs.
- **BSON**: Provides a foundation for rich data types and efficient traversal.

---

## The Result: JSON-T

JSON-T is the result of combining these ideas:

* CSV-like **positional compactness**
* Protobuf-like **strict typing**
* Avro-like **schema-first validation**
* JSON-like **human-readable text**

The result is a data format that is:

* Smaller than JSON at scale
* Safer than JSON by design
* Easier to validate symmetrically
* Still inspectable and tooling-friendly

> JSON-T is not trying to replace JSON everywhere.
> It is designed for **large, structured, validated data exchange**.

---

## Key Features

* **Schema-first design**
  Structure is defined once and reused for all data.

* **Positional encoding**
  Data is encoded as ordered tuples, eliminating repeated keys.

* **Strong validation**
  Types, optionality, constraints, and nullability are enforced by the language.

* **Smaller payloads**
  Typically **45–55% smaller than JSON** for large datasets.

* **Streaming-friendly**
  Records can be validated incrementally without loading full objects.

* **Text-based (not binary)**
  Human-inspectable, diff-friendly, and tooling-friendly.

---

## Quick Example

### Schema Definition
Define your data structure once.
```jsont
{
    schemas: {
        User: {
            int: id,
            str: username,
            str: email?,
            <Address>: address,
            str[]: tags?
        },
        Address: {
            str: street,
            str: city,
            zip: zipCode
       }
    }
}
```

### Data Payload
Transmit data as compact tuples.
```jsont
{
	data-schema: User,
	data: [
        { 123456, "sasikp0", "test@sasikp.com0", { "34a Perumbakkam0", "Chennai0", "600150"}, ["developer0", "admin0"]},
        { 123457, "sasikp1", "test@sasikp.com1", { "34a Perumbakkam1", "Chennai1", "600151"}, ["developer1", "admin1"]}
    ]
}
```

---

## Java Integration

JSON-T provides a Java API for parsing and generating data.

### Maven Dependency
```xml
<dependency>
    <groupId>org.jsont</groupId>
    <artifactId>jsont-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Reading Data
```java
JsonTContext ctx = JsonT.builder()
        .withAdapter(new AddressAdapter())
        .withAdapter(new UserAdapter())
        .withErrorCollector(new DefaultErrorCollector())
        .parseCatalog(Paths.get("schema.jsont"));

CharStream dataStream = CharStreams.fromPath(Paths.get("data.jsont"));
List<User> userList = ctx.withData(dataStream).as(User.class).list();
```

### Writing Data
```java
JsonTContext ctx = JsonT.builder()
        .withAdapter(new AddressAdapter())
        .withAdapter(new UserAdapter())
        .withErrorCollector(new DefaultErrorCollector())
        .parseCatalog(Paths.get("schema.jsont"));

List<User> users = getUsers(); // Your data source
String output = ctx.stringify(users);
```

---

## When to Use JSON-T

JSON-T is a good fit for:

* Large API responses (thousands+ records)
* Batch ingestion pipelines
* Event streams and logs
* Analytics exports
* Mobile or WAN-constrained environments
* Systems requiring strict producer/consumer contracts

JSON remains better for small, ad-hoc, or human-authored payloads.

---

## Validation Model

JSON-T validation is **built-in**, not optional:

* Required vs optional fields
* Primitive and structured types
* Value ranges and patterns
* Array size and nullability rules
* Positional correctness

If a document parses successfully, it is structurally valid.

---

## Tooling

This repository contains:

* A **Langium-based grammar**
* TypeScript/JavaScript lexer & parser
* AST generation
* Semantic validation hooks
* CLI and programmatic APIs (work in progress)

---

## Status

🚧 **Work in progress**

Current focus:

* Grammar stabilization
* Parser and AST validation
* Schema and data consistency checks

Future work:

* Binary encoding
* Schema evolution rules
* Streaming decoder
* JSON ↔ JSON-T converters

---

## Philosophy

> JSON is a notation.
> JSON-T is a data contract with a transport.
