# Getting Started with JsonT

JsonT is a high-performance, compact data exchange format. It separates schema definitions from the data rows to reduce redundancy and improve parsing speed.

## Requirements

- Java 11+
- Maven or Gradle

## Maven Dependency

Add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>org.jsont</groupId>
  <artifactId>jsont-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Basic Usage

The typical flow involves parsing a catalog (schema + enums) and then processing data rows against that context.

```java
import org.jsont.JsonT;
import org.jsont.core.JsonTContext;
import org.antlr.v4.runtime.CharStreams;
import java.nio.file.Path;
import java.util.List;

// 1. Parse the catalog (schemas and enums)
JsonTContext ctx = JsonT.parseCatalog(Path.of("schema.jsont"));

// 2. Process data rows using a CharStream
List<Customer> customers = ctx.withData(CharStreams.fromPath(Path.of("data.jsont")))
                               .as(Customer.class)
                               .list();
```

---

## Example `.jsont` file (Catalog)

```jsont
{
  schemas: {
    Customer: {
      int: id,
      str: name,
      CustomerStatus: status,
      <Address>: address?,
      str[]: tags
    },
    Address: {
      str[]: street?,
      str: city,
      str: state,
      zip5: zipCode
    }
  },
  enums: [
    CustomerStatus {
      vip,
      active,
      guest
    }
  ]
}
```

---

## Streaming Usage (Reactive)

JsonT supports reactive processing via Project Reactor. This is recommended for large datasets.

```java
import reactor.core.publisher.Flux;

ctx.withData(CharStreams.fromPath(Path.of("large-data.jsont")))
   .as(Customer.class)
   .stream()
   .subscribe(customer -> {
       System.out.println("Processing: " + customer.getName());
   });
```

---

## Adapter Generation (AOT)

JsonT uses Ahead-of-Time (AOT) source generation for maximum performance. Instead of using reflection at runtime, it generates efficient adapters for your POJOs.

### Using `@JsonTSerializable`

The easiest way to generate adapters is to annotate your model classes:

```java
import org.jsont.annotations.JsonTSerializable;
import org.jsont.annotations.JsonTField;

@JsonTSerializable(schema = "Customer")
public class Customer {
    @JsonTField
    private int id;
    
    @JsonTField
    private String name;
    
    // Getters and Setters...
}
```

### How `AdapterGenerator` Works

The `AdapterGenerator` is the engine behind this generation. During the build process, the `JsonTAOTProcessor` (annotation processor) identifies classes with `@JsonTSerializable` and uses `AdapterGenerator` to produce a `*SchemaAdapter.java` source file.

- **Automated Usage**: Simply include `jsont-processor` in your annotation processor path. The generator will create adapters automatically during compilation.
- **Manual Usage**: While not common, `AdapterGenerator.generate(AnnoTypeModel, Filer, Elements)` can be called programmatically if you are building custom tooling to generate adapters from non-Java schema sources.

---

## Custom Type Adapters

You can also register manual adapters if you need custom logic for specific types:

```java
JsonTContext ctx = JsonT.builder()
                        .withAdapter(new MyCustomAdapter())
                        .parseCatalog(Path.of("schema.jsont"));
```

---

## Error Handling

JsonT provides validation and fails fast on:
- Schema mismatches
- Invalid enum values
- Missing required fields
- Constraint violations