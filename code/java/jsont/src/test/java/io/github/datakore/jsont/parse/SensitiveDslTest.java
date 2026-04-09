package io.github.datakore.jsont.parse;

// =============================================================================
// SensitiveDslTest — Step 10.2: DSL Round-trip (Java)
// =============================================================================
// Covers:
//   • Parse DSL with `~` sensitive marker → sensitive: true in model
//   • Parse DSL with `decrypt(...)` operation → SchemaOperation.Decrypt
//   • Stringify back → `~` and `decrypt(...)` appear in output
//   • Round-trip: parse → stringify → re-parse → model unchanged
//   • Combinations: sensitive + optional, sensitive + array
//   • Straight schema with sensitive fields (no decrypt op)
//   • Derived schema with decrypt op
// =============================================================================

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.stringify.JsonTStringifier;
import io.github.datakore.jsont.stringify.StringifyOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDslTest {

    // =========================================================================
    // Helpers
    // =========================================================================

    static final String NS_TEMPLATE_PERSON = """
            {
              namespace: {
                baseUrl: "https://example.com",
                version: "1.0",
                catalogs: [
                  {
                    schemas: [
                      %s
                    ]
                  }
                ],
                data-schema: Person
              }
            }
            """;

    static JsonTNamespace parseNs(String schemaDsl) {
        return JsonTParser.parseNamespace(NS_TEMPLATE_PERSON.formatted(schemaDsl));
    }

    static JsonTSchema firstSchema(JsonTNamespace ns) {
        return ns.catalogs().get(0).schemas().get(0);
    }

    static JsonTField firstField(JsonTNamespace ns) {
        JsonTSchema schema = firstSchema(ns);
        assertTrue(schema.isStraight(), "expected straight schema");
        return schema.fields().get(0);
    }

    static String compact(JsonTSchema schema) {
        return JsonTStringifier.stringify(schema, StringifyOptions.compact());
    }

    // =========================================================================
    // Parse: sensitive_mark → sensitive: true
    // =========================================================================

    @Test
    void parse_sensitive_scalar_field() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str~: ssn,
                    str: name
                  }
                }
                """);

        JsonTSchema schema = firstSchema(ns);
        assertTrue(schema.isStraight());
        List<JsonTField> fields = schema.fields();

        assertEquals("ssn", fields.get(0).name());
        assertTrue(fields.get(0).sensitive(), "ssn should be sensitive");

        assertEquals("name", fields.get(1).name());
        assertFalse(fields.get(1).sensitive(), "name should not be sensitive");
    }

    @Test
    void parse_non_sensitive_field_is_false() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str: name,
                    i32: age
                  }
                }
                """);

        JsonTField f = firstField(ns);
        assertFalse(f.sensitive());
    }

    @Test
    void parse_sensitive_preserves_scalar_type() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    date~: dob
                  }
                }
                """);

        JsonTField f = firstField(ns);
        assertTrue(f.sensitive());
        assertEquals(ScalarType.DATE, f.scalarType());
    }

    @Test
    void parse_sensitive_array_field() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str[]~: tags
                  }
                }
                """);

        JsonTField f = firstField(ns);
        assertTrue(f.sensitive());
        assertTrue(f.kind().isArray());
    }

    @Test
    void parse_multiple_sensitive_fields() {
        JsonTNamespace ns = parseNs("""
                Employee: {
                  fields: {
                    str~: ssn,
                    d64~: salary,
                    str: name
                  }
                }
                """);

        List<JsonTField> fields = firstSchema(ns).fields();
        assertEquals(3, fields.size());
        assertTrue(fields.get(0).sensitive(), "ssn sensitive");
        assertTrue(fields.get(1).sensitive(), "salary sensitive");
        assertFalse(fields.get(2).sensitive(), "name not sensitive");
    }

    // =========================================================================
    // Parse: decrypt operation
    // =========================================================================

    @Test
    void parse_decrypt_single_field() {
        JsonTNamespace ns = parseNs("""
                EmployeeView: FROM Employee {
                  operations: (
                    decrypt(ssn)
                  )
                }
                """);

        JsonTSchema schema = firstSchema(ns);
        assertEquals(1, schema.operations().size());
        SchemaOperation op = schema.operations().get(0);
        assertInstanceOf(SchemaOperation.Decrypt.class, op);
        assertEquals(List.of("ssn"), ((SchemaOperation.Decrypt) op).fields());
    }

    @Test
    void parse_decrypt_multiple_fields() {
        JsonTNamespace ns = parseNs("""
                EmployeeView: FROM Employee {
                  operations: (
                    decrypt(ssn, salary)
                  )
                }
                """);

        SchemaOperation.Decrypt op =
                (SchemaOperation.Decrypt) firstSchema(ns).operations().get(0);
        assertEquals(List.of("ssn", "salary"), op.fields());
    }

    @Test
    void parse_decrypt_mixed_with_other_ops() {
        JsonTNamespace ns = parseNs("""
                View: FROM Base {
                  operations: (
                    decrypt(sensitiveField),
                    exclude(internalId)
                  )
                }
                """);

        List<SchemaOperation> ops = firstSchema(ns).operations();
        assertEquals(2, ops.size());
        assertInstanceOf(SchemaOperation.Decrypt.class, ops.get(0));
        assertInstanceOf(SchemaOperation.Exclude.class, ops.get(1));
    }

    @Test
    void parse_multiple_decrypt_ops() {
        JsonTNamespace ns = parseNs("""
                View: FROM Base {
                  operations: (
                    decrypt(fieldA),
                    decrypt(fieldB)
                  )
                }
                """);

        List<SchemaOperation> ops = firstSchema(ns).operations();
        assertEquals(2, ops.size());
        assertEquals(List.of("fieldA"), ((SchemaOperation.Decrypt) ops.get(0)).fields());
        assertEquals(List.of("fieldB"), ((SchemaOperation.Decrypt) ops.get(1)).fields());
    }

    // =========================================================================
    // Stringify: sensitive fields emit `~`
    // =========================================================================

    @Test
    void stringify_sensitive_scalar_emits_tilde() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str~: ssn
                  }
                }
                """);

        String out = compact(firstSchema(ns));
        assertTrue(out.contains("str~:"), "expected 'str~:' in: " + out);
    }

    @Test
    void stringify_non_sensitive_no_tilde() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str: name
                  }
                }
                """);

        String out = compact(firstSchema(ns));
        assertFalse(out.contains("~"), "unexpected '~' in: " + out);
    }

    @Test
    void stringify_sensitive_array_emits_tilde() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str[]~: tags
                  }
                }
                """);

        String out = compact(firstSchema(ns));
        assertTrue(out.contains("~"), "expected tilde in: " + out);
    }

    @Test
    void stringify_decrypt_op_emits_decrypt() {
        JsonTNamespace ns = parseNs("""
                View: FROM Base {
                  operations: (
                    decrypt(ssn, salary)
                  )
                }
                """);

        String out = compact(firstSchema(ns));
        assertTrue(
                out.contains("decrypt(ssn, salary)") || out.contains("decrypt(ssn,salary)"),
                "expected decrypt op in: " + out
        );
    }

    // =========================================================================
    // Round-trip: parse → stringify → re-parse → model identical
    // =========================================================================

    @Test
    void roundtrip_sensitive_field_preserved() {
        JsonTNamespace ns1 = parseNs("""
                Employee: {
                  fields: {
                    str~: ssn,
                    d64~: salary,
                    str: name
                  }
                }
                """);

        JsonTSchema schema1 = firstSchema(ns1);
        String stringified = compact(schema1);

        // Wrap in a fresh namespace for re-parse
        JsonTNamespace ns2 = JsonTParser.parseNamespace(
                NS_TEMPLATE_PERSON.formatted(stringified));
        JsonTSchema schema2 = firstSchema(ns2);

        List<JsonTField> f1 = schema1.fields();
        List<JsonTField> f2 = schema2.fields();
        assertEquals(f1.size(), f2.size(), "field count mismatch");
        for (int i = 0; i < f1.size(); i++) {
            assertEquals(f1.get(i).name(), f2.get(i).name(), "name mismatch at " + i);
            assertEquals(f1.get(i).sensitive(), f2.get(i).sensitive(),
                    "sensitive mismatch for field '" + f1.get(i).name() + "'");
        }
    }

    @Test
    void roundtrip_decrypt_op_preserved() {
        JsonTNamespace ns1 = parseNs("""
                EmployeeView: FROM Employee {
                  operations: (
                    decrypt(ssn, salary)
                  )
                }
                """);

        JsonTSchema schema1 = firstSchema(ns1);
        String stringified = compact(schema1);

        JsonTNamespace ns2 = JsonTParser.parseNamespace(
                NS_TEMPLATE_PERSON.formatted(stringified));
        JsonTSchema schema2 = firstSchema(ns2);

        List<SchemaOperation> ops1 = schema1.operations();
        List<SchemaOperation> ops2 = schema2.operations();
        assertEquals(ops1.size(), ops2.size());
        SchemaOperation.Decrypt d1 = (SchemaOperation.Decrypt) ops1.get(0);
        SchemaOperation.Decrypt d2 = (SchemaOperation.Decrypt) ops2.get(0);
        assertEquals(d1.fields(), d2.fields());
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void parse_sensitive_field_with_constraints() {
        JsonTNamespace ns = parseNs("""
                Person: {
                  fields: {
                    str~: ssn [(minLength=9, maxLength=11)]
                  }
                }
                """);

        JsonTField f = firstField(ns);
        assertTrue(f.sensitive());
        assertEquals("ssn", f.name());
    }

    @Test
    void schema_with_only_non_sensitive_fields_stringifies_without_tilde() {
        JsonTNamespace ns = parseNs("""
                Address: {
                  fields: {
                    str: street,
                    str: city,
                    str: zip
                  }
                }
                """);

        String out = compact(firstSchema(ns));
        assertFalse(out.contains("~"), "unexpected ~ in non-sensitive schema: " + out);
    }

    @Test
    void parse_invalid_sensitive_on_object_field_fails() {
        // `<Address>~: addr` is not valid grammar — sensitive_mark is only in scalar_field_decl
        // ANTLR should produce a parse error
        assertThrows(JsonTError.Parse.class, () ->
                parseNs("""
                        Person: {
                          fields: {
                            <Address>~: addr
                          }
                        }
                        """)
        );
    }
}
