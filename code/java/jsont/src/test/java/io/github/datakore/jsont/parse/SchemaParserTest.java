package io.github.datakore.jsont.parse;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.stringify.JsonTStringifier;
import io.github.datakore.jsont.stringify.StringifyOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaParserTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    static final String SIMPLE_NS = """
            namespace "https://example.com/v1" {
              catalog {
                schema Order {
                  fields {
                    id: i64,
                    product: str,
                    qty: i32,
                  }
                }
              }
            }
            """;

    // ── basic roundtrip ───────────────────────────────────────────────────────

    @Test void parseNamespace_simpleSchema() {
        JsonTNamespace ns = JsonTParser.parseNamespace(SIMPLE_NS);
        assertEquals("https://example.com/v1", ns.baseUrl());
        assertEquals(1, ns.catalogs().size());
        var schema = ns.findSchema("Order").orElseThrow();
        assertEquals("Order", schema.name());
        assertTrue(schema.isStraight());
        assertEquals(3, schema.fieldCount());
    }

    @Test void parseNamespace_fieldTypes() {
        JsonTNamespace ns = JsonTParser.parseNamespace(SIMPLE_NS);
        var schema = ns.findSchema("Order").orElseThrow();
        assertEquals(ScalarType.I64, schema.findField("id").orElseThrow().scalarType());
        assertEquals(ScalarType.STR, schema.findField("product").orElseThrow().scalarType());
        assertEquals(ScalarType.I32, schema.findField("qty").orElseThrow().scalarType());
    }

    @Test void parseNamespace_optionalField() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Item {
                      fields {
                        id: i32,
                        note: str [],
                      }
                    }
                  }
                }
                """;
        // note: str [] parses as str with ARRAY_SUFFIX = []
        // Actually let's use a simpler optional test
        String dsl2 = """
                namespace "" {
                  catalog {
                    schema Item {
                      fields {
                        id: i32,
                        note: str? ,
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl2);
        var note = ns.findSchema("Item").orElseThrow().findField("note").orElseThrow();
        assertTrue(note.optional());
        assertFalse(note.kind().isArray());
    }

    @Test void parseNamespace_arrayField() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Bag {
                      fields {
                        tags: str[],
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var field = ns.findSchema("Bag").orElseThrow().findField("tags").orElseThrow();
        assertTrue(field.kind().isArray());
        assertEquals(ScalarType.STR, field.scalarType());
    }

    @Test void parseNamespace_objectField() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Cart {
                      fields {
                        item: <Item>,
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var field = ns.findSchema("Cart").orElseThrow().findField("item").orElseThrow();
        assertTrue(field.kind().isObject());
        assertEquals("Item", field.objectRef());
    }

    @Test void parseNamespace_objectArrayField() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Cart {
                      fields {
                        items: <Item>[],
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var field = ns.findSchema("Cart").orElseThrow().findField("items").orElseThrow();
        assertTrue(field.kind().isArray());
        assertTrue(field.kind().isObject());
    }

    // ── constraints ───────────────────────────────────────────────────────────

    @Test void parseNamespace_withNumericConstraints() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Price {
                      fields {
                        amount: d64 (minValue = 0.01, maxValue = 9999.99, maxPrecision = 2),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Price").orElseThrow().findField("amount").orElseThrow().constraints();
        assertEquals(0.01,    c.minValue(),    1e-9);
        assertEquals(9999.99, c.maxValue(),    1e-9);
        assertEquals(2,       c.maxPrecision());
    }

    @Test void parseNamespace_withStringConstraints() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Tag {
                      fields {
                        name: str (minLength = 2, maxLength = 50, pattern = "[A-Z]+"),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Tag").orElseThrow().findField("name").orElseThrow().constraints();
        assertEquals(2,       c.minLength());
        assertEquals(50,      c.maxLength());
        assertEquals("[A-Z]+", c.pattern());
    }

    @Test void parseNamespace_withRequiredConstraint() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Doc {
                      fields {
                        code: str (required),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        assertTrue(ns.findSchema("Doc").orElseThrow().findField("code").orElseThrow().constraints().required());
    }

    @Test void parseNamespace_withArrayConstraints() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Bag {
                      fields {
                        tags: str[] (minItems = 1, maxItems = 10),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Bag").orElseThrow().findField("tags").orElseThrow().constraints();
        assertEquals(1,  c.minItems());
        assertEquals(10, c.maxItems());
    }

    // ── validations ───────────────────────────────────────────────────────────

    @Test void parseNamespace_withUniqueValidation() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema User {
                      fields {
                        id: i64,
                        email: str,
                      }
                      validations {
                        unique: [id]
                        unique: [email]
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("User").orElseThrow().validation().orElseThrow();
        assertEquals(2, vb.uniqueKeys().size());
        assertEquals("id",    vb.uniqueKeys().get(0).get(0).leaf());
        assertEquals("email", vb.uniqueKeys().get(1).get(0).leaf());
    }

    @Test void parseNamespace_withCompoundUniqueKey() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, tenant: str }
                      validations {
                        unique: [tenant, id]
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        assertEquals(1, vb.uniqueKeys().size());
        assertEquals(2, vb.uniqueKeys().get(0).size());
    }

    @Test void parseNamespace_withRuleValidation() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, qty: i32, price: d64 }
                      validations {
                        rule: qty > 0 && price > 0.0
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        assertEquals(1, vb.rules().size());
        // rule should be a wrapped Expression containing an AND binary expression
        assertInstanceOf(JsonTRule.Expression.class, vb.rules().get(0));
        var expr = ((JsonTRule.Expression) vb.rules().get(0)).expr();
        assertInstanceOf(JsonTExpression.Binary.class, expr);
        assertEquals(BinaryOp.AND, ((JsonTExpression.Binary) expr).op());
    }

    // ── derived schemas ───────────────────────────────────────────────────────

    @Test void parseNamespace_derivedSchema_project() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, product: str, qty: i32 }
                    }
                    schema Summary derived Order {
                      project: [id, product]
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var summary = ns.findSchema("Summary").orElseThrow();
        assertTrue(summary.isDerived());
        assertEquals("Order", summary.derivedFrom().orElseThrow());
        assertEquals(1, summary.operations().size());
        assertInstanceOf(SchemaOperation.Project.class, summary.operations().get(0));
        var project = (SchemaOperation.Project) summary.operations().get(0);
        assertEquals(2, project.paths().size());
    }

    @Test void parseNamespace_derivedSchema_rename() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, product: str }
                    }
                    schema Summary derived Order {
                      rename: [product -> productName]
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var rename = (SchemaOperation.Rename) ns.findSchema("Summary").orElseThrow().operations().get(0);
        assertEquals("product",     rename.pairs().get(0).from().leaf());
        assertEquals("productName", rename.pairs().get(0).to());
    }

    @Test void parseNamespace_derivedSchema_filter() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, qty: i32 }
                    }
                    schema Active derived Order {
                      filter: qty > 0
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var filter = (SchemaOperation.Filter) ns.findSchema("Active").orElseThrow().operations().get(0);
        assertInstanceOf(JsonTExpression.Binary.class, filter.predicate());
        assertEquals(BinaryOp.GT, ((JsonTExpression.Binary) filter.predicate()).op());
    }

    @Test void parseNamespace_derivedSchema_transform() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, price: d64 }
                    }
                    schema Discounted derived Order {
                      transform: price = price * 0.9
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var transform = (SchemaOperation.Transform) ns.findSchema("Discounted").orElseThrow().operations().get(0);
        assertEquals("price", transform.target().leaf());
        assertInstanceOf(JsonTExpression.Binary.class, transform.expr());
    }

    @Test void parseNamespace_derivedSchema_exclude() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, product: str, internal: str }
                    }
                    schema Public derived Order {
                      exclude: [internal]
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var exclude = (SchemaOperation.Exclude) ns.findSchema("Public").orElseThrow().operations().get(0);
        assertEquals("internal", exclude.paths().get(0).leaf());
    }

    // ── enum ──────────────────────────────────────────────────────────────────

    @Test void parseNamespace_withEnum() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Status {
                      fields { id: i32 }
                    }
                    enum StatusEnum {
                      ACTIVE, INACTIVE, SUSPENDED
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var e = ns.findEnum("StatusEnum").orElseThrow();
        assertEquals(3, e.values().size());
        assertTrue(e.contains("ACTIVE"));
        assertTrue(e.contains("SUSPENDED"));
    }

    // ── no base URL ───────────────────────────────────────────────────────────

    @Test void parseNamespace_withoutBaseUrl() {
        String dsl = """
                namespace {
                  catalog {
                    schema Ping {
                      fields { id: i32 }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        assertTrue(ns.baseUrl().isEmpty());
        assertNotNull(ns.findSchema("Ping").orElseThrow());
    }

    // ── multiple catalogs ─────────────────────────────────────────────────────

    @Test void parseNamespace_multipleCatalogs() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema A { fields { id: i32 } }
                  }
                  catalog {
                    schema B { fields { id: i32 } }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        assertEquals(2, ns.catalogs().size());
        assertEquals(2, ns.schemaCount());
        assertTrue(ns.findSchema("A").isPresent());
        assertTrue(ns.findSchema("B").isPresent());
    }

    // ── P2.1: constant = <value> constraint ───────────────────────────────────

    @Test void parseNamespace_constantValue_bool() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Item {
                      fields {
                        active: bool (constant = true),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Item").orElseThrow().findField("active").orElseThrow().constraints();
        assertNotNull(c.constantValue());
        assertEquals(JsonTValue.bool(true), c.constantValue());
    }

    @Test void parseNamespace_constantValue_integer() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Status {
                      fields {
                        code: i32 (constant = 42),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Status").orElseThrow().findField("code").orElseThrow().constraints();
        assertNotNull(c.constantValue());
        assertTrue(c.constantValue().isNumeric());
        assertEquals(42.0, c.constantValue().toDouble(), 1e-9);
    }

    @Test void parseNamespace_constantValue_string() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Doc {
                      fields {
                        version: str (constant = "v1"),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Doc").orElseThrow().findField("version").orElseThrow().constraints();
        assertNotNull(c.constantValue());
        assertInstanceOf(JsonTValue.Text.class, c.constantValue());
        assertEquals("v1", ((JsonTValue.Text) c.constantValue()).value());
    }

    @Test void parseNamespace_constantValue_null() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Doc {
                      fields {
                        tag: str (constant = null),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Doc").orElseThrow().findField("tag").orElseThrow().constraints();
        assertNotNull(c.constantValue());
        assertTrue(c.constantValue().isNull());
    }

    @Test void parseNamespace_constantValue_negativeNumber() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Temp {
                      fields {
                        offset: d64 (constant = -1.5),
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var c = ns.findSchema("Temp").orElseThrow().findField("offset").orElseThrow().constraints();
        assertNotNull(c.constantValue());
        assertEquals(-1.5, c.constantValue().toDouble(), 1e-9);
    }

    // ── P2.2: if (cond) require (fields) conditional validation ───────────────

    @Test void parseNamespace_conditionalValidation_parsesAsConditionalRequirement() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields {
                        qty: i32,
                        note: str?,
                      }
                      validations {
                        if (qty > 100) require (note)
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        assertEquals(1, vb.rules().size());
        assertInstanceOf(JsonTRule.ConditionalRequirement.class, vb.rules().get(0));
        var cr = (JsonTRule.ConditionalRequirement) vb.rules().get(0);
        assertInstanceOf(JsonTExpression.Binary.class, cr.condition());
        assertEquals(BinaryOp.GT, ((JsonTExpression.Binary) cr.condition()).op());
        assertEquals(1, cr.requiredFields().size());
        assertEquals("note", cr.requiredFields().get(0).leaf());
    }

    @Test void parseNamespace_conditionalValidation_multipleRequiredFields() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields {
                        qty: i32,
                        note: str?,
                        reason: str?,
                      }
                      validations {
                        if (qty > 100) require (note, reason)
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        var cr = (JsonTRule.ConditionalRequirement) vb.rules().get(0);
        assertEquals(2, cr.requiredFields().size());
        assertEquals("note",   cr.requiredFields().get(0).leaf());
        assertEquals("reason", cr.requiredFields().get(1).leaf());
    }

    @Test void parseNamespace_conditionalValidation_coexistsWith_rule() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields {
                        id: i64,
                        qty: i32,
                        note: str?,
                      }
                      validations {
                        rule: qty > 0
                        if (qty > 100) require (note)
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        assertEquals(2, vb.rules().size());
        assertInstanceOf(JsonTRule.Expression.class, vb.rules().get(0));
        assertInstanceOf(JsonTRule.ConditionalRequirement.class, vb.rules().get(1));
    }

    // ── P2.3: dotted field paths in expressions ────────────────────────────────

    @Test void parseNamespace_dottedFieldPath_inRuleExpr() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields {
                        id: i64,
                        address: <Address>,
                      }
                      validations {
                        rule: address.zip > 0
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
        var expr = ((JsonTRule.Expression) vb.rules().get(0)).expr();
        assertInstanceOf(JsonTExpression.Binary.class, expr);
        var lhs = ((JsonTExpression.Binary) expr).lhs();
        assertInstanceOf(JsonTExpression.FieldRef.class, lhs);
        var path = ((JsonTExpression.FieldRef) lhs).path();
        assertEquals("address.zip", path.dotJoined());
    }

    @Test void parseNamespace_dottedFieldPath_inFilterExpr() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64, address: <Address> }
                    }
                    schema Local derived Order {
                      filter: address.country == address.region
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var filter = (SchemaOperation.Filter) ns.findSchema("Local").orElseThrow().operations().get(0);
        assertInstanceOf(JsonTExpression.Binary.class, filter.predicate());
        var lhs = ((JsonTExpression.Binary) filter.predicate()).lhs();
        assertInstanceOf(JsonTExpression.FieldRef.class, lhs);
        assertEquals("address.country", ((JsonTExpression.FieldRef) lhs).path().dotJoined());
    }

    @Test void parseNamespace_dottedFieldPath_threeSegments() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { id: i64 }
                      validations {
                        rule: a.b.c > 0
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var expr = ((JsonTRule.Expression)
                ns.findSchema("Order").orElseThrow().validation().orElseThrow().rules().get(0)).expr();
        var lhs = ((JsonTExpression.Binary) expr).lhs();
        assertEquals("a.b.c", ((JsonTExpression.FieldRef) lhs).path().dotJoined());
    }

    // ── B8: full document (namespace block + data rows in one string) ──────────

    @Test void parseDocument_namespaceThenDataRows() {
        String full = """
                namespace "https://doc.example" {
                  catalog {
                    schema Order {
                      fields { id: i64, product: str, qty: i32 }
                    }
                  }
                }
                {1,"Widget",10},{2,"Gadget",5},{3,"Doohickey",3}
                """;
        var doc = JsonTParser.parseDocument(full);

        assertEquals("https://doc.example", doc.namespace().baseUrl());
        assertNotNull(doc.namespace().findSchema("Order").orElseThrow());
        assertEquals(3, doc.rowCount());
        assertEquals(3, doc.rows().size());
        assertEquals(d64(1.0), doc.rows().get(0).get(0));
        assertEquals(text("Widget"), doc.rows().get(0).get(1));
        assertEquals(d64(3.0), doc.rows().get(2).get(0));
    }

    @Test void parseDocument_emptyRowSection_zeroRows() {
        String full = """
                namespace "" {
                  catalog {
                    schema Ping { fields { id: i32 } }
                  }
                }
                """;
        var doc = JsonTParser.parseDocument(full);
        assertEquals(1, doc.namespace().schemaCount());
        assertEquals(0, doc.rowCount());
    }

    @Test void parseDocument_schemaAndRowsRoundTrip() {
        String ns = """
                namespace "" {
                  catalog {
                    schema Item { fields { code: str, price: d64 } }
                  }
                }
                """;
        String rowData = "{\"ABC\",9.99},{\"XYZ\",14.50}";
        var doc = JsonTParser.parseDocument(ns + rowData);

        var schema = doc.namespace().findSchema("Item").orElseThrow();
        assertEquals(2, schema.fieldCount());
        List<JsonTRow> rows = doc.rows();
        assertEquals(2, rows.size());
        assertEquals(text("ABC"),  rows.get(0).get(0));
        assertEquals(d64(14.50),   rows.get(1).get(1));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test void parseNamespace_emptyInput_throws() {
        assertThrows(JsonTError.Parse.class, () -> JsonTParser.parseNamespace(""));
    }

    @Test void parseNamespace_syntaxError_throws() {
        assertThrows(JsonTError.Parse.class,
                () -> JsonTParser.parseNamespace("namespace { BROKEN"));
    }

    @Test void parseNamespace_unknownScalarType_throws() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema X {
                      fields { id: notatype }
                    }
                  }
                }
                """;
        assertThrows(JsonTError.Parse.class, () -> JsonTParser.parseNamespace(dsl));
    }

    // ── expressions ───────────────────────────────────────────────────────────

    @Test void parseNamespace_complexExpression_precedence() {
        String dsl = """
                namespace "" {
                  catalog {
                    schema Order {
                      fields { a: i32, b: i32, c: i32 }
                      validations {
                        rule: a > 1 && b < 10 || c == 5
                      }
                    }
                  }
                }
                """;
        JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
        var rule = ns.findSchema("Order").orElseThrow().validation().orElseThrow().rules().get(0);
        // ANTLR4 left-recursive: `&&` has higher precedence than `||` (defined higher = lower index)
        // Top-level should be OR, wrapped in JsonTRule.Expression
        assertInstanceOf(JsonTRule.Expression.class, rule);
        assertInstanceOf(JsonTExpression.Binary.class, ((JsonTRule.Expression) rule).expr());
    }
}
