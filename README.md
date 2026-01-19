# JSON-T

**JSON-T** is a **schema-driven, positional data language** designed to reduce payload size and enforce strong,
symmetric data validation — especially for **large datasets, batch APIs, and streaming use cases**.

Unlike JSON, where field names are repeated in every record, JSON-T defines structure once using a schema and encodes
data as compact positional tuples.

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

Define your data structure once.  Expanded schema to include namespace, catalog, and then schemas / enums - to bring parity to Json Schema so that we can use either of them to serialize / deserialize data

```jsont
{
  namespace: {
    baseUrl: "https://api.datakore.com/v1",
    catalogs: [
      {
        schemas: [
          User: {
            i32: id,
            str: username(minLength=5,maxLength='10'),
            str: email?(minLength=8)
          },
          Address: {
             str: city,
             str: zipCode
          }
        ],
        enums: [
          Status: [ ACTIVE, INACTIVE, SUSPENDED ],
          Role: [ ADMIN, USER ]
        ]
      }
    ]
  }
}
```

### Data Payload

Transmit data as compact tuples.

```jsont
{
    data-schema: User,
    data: [
        {
            1,
            "alice_dev",
            "alice@example.com",
            ADMIN, ["t1","t2"],
            {"Chennai","60015",ACTIVE}
        },
        {
            2,
            "bob_guest",
            null,USER,null,{"Delhi","123456",SUSPENDED}
        }
    ]
}
```

---

## Java Integration

JSON-T provides a Java API for parsing and generating data.

### Add maven repository

```xml
<repositories>
    <repository>
        <id>github-datakore</id>
        <url>https://maven.pkg.github.com/datakore/json-t</url>
    </repository>
</repositories>
```

### Maven Dependency

```xml
<dependency>
    <groupId>io.github.datakore</groupId>
    <artifactId>json-t</artifactId>
    <version>0.0.2</version>
</dependency>
```

### Reading Data (Async as Stream) - Use this approach as a default mechanism (or) specifically while handling large batches

1. 

```java
private JsonTConfig getJsonTConfig(Path errorPath) throws IOException {
    // provide schema path
  Path schemaPath = Paths.get("src/test/resources/all-type-schema.jsont");
  assert schemaPath.toFile().exists();
  // Use adapters to read the content
  AllTypeHolderAdapter a1 = new AllTypeHolderAdapter();
  DateTypeAdapter a2 = new DateTypeAdapter();
  NumberTypeAdapter a3 = new NumberTypeAdapter();
  StringEntryAdapter a4 = new StringEntryAdapter();
  ArrayEntryAdapter a5 = new ArrayEntryAdapter();
  JsonTConfigBuilder builder = JsonT.configureBuilder()
          .withErrorCollector(errorCollector).withAdapters(a1).withAdapters(a2).withAdapters(a3).withAdapters(a4).withAdapters(a5);
  if (errorPath != null) {
    builder = builder.withErrorFile(errorPath);
  }
  // Read the schema here
  return builder.source(schemaPath).build();
}

    @Test
void shouldParseUsingJsonTExecution() throws IOException {
  // Supply the data file (or) CharStream  
  String dataFile = "src/test/resources/all-type-sample.jsont";
  Path dataPath = Paths.get(dataFile);
  assert dataPath.toFile().exists();
  // While parsing, if there're any errors, errors are emitted to CSV
  Path errorPath = Paths.get(dataFile.concat(".csv"));
  JsonTConfig config = getJsonTConfig(errorPath);
  // This is a oneliner that creates the execution
  JsonTExecution execution = config.source(dataPath);

  AtomicLong rowsProcessed = new AtomicLong();
  Instant start = Instant.now();
  // Parse the data using parallel threads
  execution.parse(4) // Use 4 parallel threads
          .doOnNext(row -> {
            long count = rowsProcessed.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("Processed %d rows so far - time elapsed %s \n", count, Duration.between(start, Instant.now()));
            }
          })
          .blockLast(); // Wait for completion
  Instant end = Instant.now();
  System.out.printf("Took %s to process %d records", Duration.between(start, end), rowsProcessed.get());
}

```

### Reading Data (Synchronous as List) - Use this approach for smaller payloads

```java
    @Test
    void shouldReadDataAsList() throws IOException {
        JsonTConfig config = JsonT.configureBuilder()
                .withAdapters(new AddressAdapter()).withAdapters(new UserAdapter())
                .withErrorCollector(new DefaultErrorCollector())
                .source(scPath)
                .build();

        CharStream dataStream = CharStreams.fromPath(datPath);
        
        // Collect stream into a list
        List<User> userList = config.source(dataStream)
                                    .convert(User.class, 1)
                                    .collectList()
                                    .block();

        assertEquals(total, userList.size());
        System.out.println(userList);
    }
```

### Writing Data
There are 3 ways to stringify JsonT data
1. ```java public Mono<Void> stringify(T data, Writer writer, boolean includeSchema) ``` This is to stringify a single record
2. ```java public Mono<Void> stringify(List<T> data, Writer writer, boolean includeSchema)``` This is to stringify a list of records
3. ```java public Mono<Void> stringify(Writer writer, long totalRecords, int batchSize, int flushEveryNBatches, boolean includeSchema)``` This hleps stringify many records as a stream

```java
// This method is to create JsonTWriter
private <T> StreamingJsonTWriter<T> getTypedStreamWriter(String path, Class<T> clazz, DataGenerator<T> gen, SchemaAdapter<?>... adapters) throws IOException {
  JsonTConfig config = getJsonTConfig(path, adapters);
  StreamingJsonTWriterBuilder<T> builder = new StreamingJsonTWriterBuilder<T>()
          .registry(config.getAdapters())
          .namespace(config.getNamespace());
  if (gen != null) {
    builder = builder.generator(gen);
  }
  return builder
          .build(clazz.getSimpleName());
}

// This method is to create JsonTConfig, from schema
private JsonTConfig getJsonTConfig(String schemaPath, SchemaAdapter<?>... adapters) throws IOException {
  Path scPath = Paths.get(schemaPath);
  assert scPath.toFile().exists();
  return JsonT.configureBuilder()
          .withAdapters(adapters)
          .withErrorCollector(errorCollector).source(scPath).build();
}

@Test
void shouldStringifyData() throws IOException {
  Address add = new Address("Dallas", "12345", "ACTIVE");
  User u1 = new User(123, "user001", "ADMIN", add);
  u1.setEmail("alice@example.com");
  u1.setTags(new String[]{"programmer"});
  StreamingJsonTWriter<User> writer = getTypedStreamWriter("src/test/resources/ns-schema.jsont", User.class, null, adapter1, adapter2);
  StringWriter sw = new StringWriter();
  // This is where the actual stringification happens
  writer.stringify(u1, sw, true).block();
  System.out.println(sw);
}

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
## Updates for v0.0.2

1. **Streaming Parser Implementation**: Replaced in-memory parsing with a streaming listener approach to eliminate out-of-memory errors when processing files beyond 700k records. By implementing fixed backpressure and asynchronous subscriber processing, the parser now handles 1.5M+ records (~1.5KB each) efficiently.

2. **Streaming Serialization**: Introduced multiple stringification strategies to avoid loading entire object graphs into memory, providing clients with flexible approaches based on their use case.

3. **Scalability Validation**: Successfully tested generation of 10M records without errors. Performance optimization remains an ongoing focus.

4. **Parser Library Evaluation**: Identified file size limitations in the current parsing library when processing 1M+ record files. Exploring alternative parsing solutions.

5. **Code Refactoring**: Improved code readability and architectural patterns across core modules.

6. **UNSPECIFIED Token**: Added UNSPECIFIED status to the grammar as a distinct state (separate from NULL or explicit values), enabling efficient Change Data Capture (CDC) use cases where unchanged fields can be omitted.

7. **Bidirectional Validation**: Implemented validation for both parsing and serialization paths.

8. **Type System Expansion**: Introduced additional data types. See [Types documentation](/datakore/json-t/blob/main/docs/Types.md) for details.
---

## Status

🚧 **Work in progress**

🚀 Current Development Status

- Performance Engineering: The Java implementation currently handles 750k records in stream mode with high efficiency.
  We are currently optimizing the engine to breach the 1 million record threshold without performance degradation.
- Annotation Processing: Finalizing the Adapter annotation processing templates to allow seamless integration with
  custom application POJOs and frameworks.
- JsonT (namespace) <-> Json Schema conversion

🗺️ Future Roadmap

- The vision for JsonT is to become a cross-language standard for efficient data transformation:
- Tooling & IDE Support: * Development of a formal TextMate Grammar to provide high-quality syntax highlighting across
  VS Code, Sublime Text, and GitHub.
- Multi-Language Support: First-class implementations for Rust (high-performance systems) and TypeScript (web/node.js)
  to enable a truly universal data stack.
- Interoperability: Bidirectional converters for JSON ↔ JSON-T and support for other major data serialization formats
  like YAML and MessagePack.

🤝 Seeking Contributors & Support
JsonT is an ambitious project aimed at redefining data density and validation. We are looking for collaborators to help
accelerate growth in these specific areas:

1. Grammar & Tooling: If you have experience with TextMate grammars or Language Server Protocol (LSP), we need your help
   building the developer experience for the next generation of data tools.
2. Language Ports: Expert Rust or TypeScript developers are needed to help port the core grammar logic, ensuring JsonT
   is available wherever performance matters.
3. Benchmarking & Optimization: Help us profile the engine as we push toward (and beyond) the 1-million-record streaming
   milestone in Java

---

## Philosophy

> JSON is a notation.
> JSON-T is a data contract with a transport.
