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
      {
        namespace: {
          baseUrl: "https://example.com/v1",
          version: "1.0",
          catalogs: [
            {
              schemas: [
                Order: {
                  fields: {
                    i64: id,
                    str: product,
                    i32: qty
                  }
                }
              ]
            }
          ],
          data-schema: Order
        }
      }
      """;

  // ── basic roundtrip ───────────────────────────────────────────────────────

  @Test
  void parseNamespace_simpleSchema() {
    JsonTNamespace ns = JsonTParser.parseNamespace(SIMPLE_NS);
    assertEquals("https://example.com/v1", ns.baseUrl());
    assertEquals("1.0", ns.version());
    assertEquals(1, ns.catalogs().size());
    var schema = ns.findSchema("Order").orElseThrow();
    assertEquals("Order", schema.name());
    assertTrue(schema.isStraight());
    assertEquals(3, schema.fieldCount());
  }

  @Test
  void parseNamespace_fieldTypes() {
    JsonTNamespace ns = JsonTParser.parseNamespace(SIMPLE_NS);
    var schema = ns.findSchema("Order").orElseThrow();
    assertEquals(ScalarType.I64, schema.findField("id").orElseThrow().scalarType());
    assertEquals(ScalarType.STR, schema.findField("product").orElseThrow().scalarType());
    assertEquals(ScalarType.I32, schema.findField("qty").orElseThrow().scalarType());
  }

  @Test
  void parseNamespace_optionalField() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Item: {
                    fields: {
                      i32: id,
                      str: note?
                    }
                  }
                ]
              }
            ],
            data-schema: Item
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var note = ns.findSchema("Item").orElseThrow().findField("note").orElseThrow();
    assertTrue(note.optional());
    assertFalse(note.kind().isArray());
  }

  @Test
  void parseNamespace_arrayField() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Bag: {
                    fields: {
                      str[]: tags
                    }
                  }
                ]
              }
            ],
            data-schema: Bag
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var field = ns.findSchema("Bag").orElseThrow().findField("tags").orElseThrow();
    assertTrue(field.kind().isArray());
    assertEquals(ScalarType.STR, field.scalarType());
  }

  @Test
  void parseNamespace_objectField() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Cart: {
                    fields: {
                      <Item>: item
                    }
                  }
                ]
              }
            ],
            data-schema: Cart
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var field = ns.findSchema("Cart").orElseThrow().findField("item").orElseThrow();
    assertTrue(field.kind().isObject());
    assertEquals("Item", field.objectRef());
  }

  @Test
  void parseNamespace_objectArrayField() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Cart: {
                    fields: {
                      <Item>[]: items
                    }
                  }
                ]
              }
            ],
            data-schema: Cart
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var field = ns.findSchema("Cart").orElseThrow().findField("items").orElseThrow();
    assertTrue(field.kind().isArray());
    assertTrue(field.kind().isObject());
  }

  // ── constraints ───────────────────────────────────────────────────────────

  @Test
  void parseNamespace_withNumericConstraints() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Price: {
                    fields: {
                      d64: amount [ (minValue=0.01, maxValue=9999.99, maxPrecision=2) ]
                    }
                  }
                ]
              }
            ],
            data-schema: Price
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Price").orElseThrow().findField("amount").orElseThrow().constraints();
    assertEquals(0.01, c.minValue(), 1e-9);
    assertEquals(9999.99, c.maxValue(), 1e-9);
    assertEquals(2, c.maxPrecision());
  }

  @Test
  void parseNamespace_withStringConstraints() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Tag: {
                    fields: {
                      str: name [ (minLength=2, maxLength=50), regex="[A-Z]+" ]
                    }
                  }
                ]
              }
            ],
            data-schema: Tag
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Tag").orElseThrow().findField("name").orElseThrow().constraints();
    assertEquals(2, c.minLength());
    assertEquals(50, c.maxLength());
    assertEquals("[A-Z]+", c.pattern());
  }

  @Test
  void parseNamespace_withRequiredConstraint() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Doc: {
                    fields: {
                      str: code [required=true]
                    }
                  }
                ]
              }
            ],
            data-schema: Doc
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    assertTrue(ns.findSchema("Doc").orElseThrow().findField("code").orElseThrow().constraints().required());
  }

  @Test
  void parseNamespace_withArrayConstraints() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Bag: {
                    fields: {
                      str[]: tags [ (minItems=1, maxItems=10) ]
                    }
                  }
                ]
              }
            ],
            data-schema: Bag
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Bag").orElseThrow().findField("tags").orElseThrow().constraints();
    assertEquals(1, c.minItems());
    assertEquals(10, c.maxItems());
  }

  // ── validations ───────────────────────────────────────────────────────────

  @Test
  void parseNamespace_withUniqueValidation() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  User: {
                    fields: {
                      i64: id,
                      str: email
                    },
                    validations: {
                      unique { (id), (email) }
                    }
                  }
                ]
              }
            ],
            data-schema: User
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var vb = ns.findSchema("User").orElseThrow().validation().orElseThrow();
    assertEquals(2, vb.uniqueKeys().size());
    assertEquals("id", vb.uniqueKeys().get(0).get(0).leaf());
    assertEquals("email", vb.uniqueKeys().get(1).get(0).leaf());
  }

  @Test
  void parseNamespace_withCompoundUniqueKey() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, str: tenant },
                    validations: {
                      unique { (tenant, id) }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
    assertEquals(1, vb.uniqueKeys().size());
    assertEquals(2, vb.uniqueKeys().get(0).size());
  }

  @Test
  void parseNamespace_withRuleValidation() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, i32: qty, d64: price },
                    validations: {
                      rules { qty > 0 && price > 0.0 }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
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

  @Test
  void parseNamespace_derivedSchema_project() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, str: product, i32: qty }
                  },
                  Summary: FROM Order {
                    operations: ( project(id, product) )
                  }
                ]
              }
            ],
            data-schema: Summary
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

  @Test
  void parseNamespace_derivedSchema_rename() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, str: product }
                  },
                  Summary: FROM Order {
                    operations: ( rename(product as productName) )
                  }
                ]
              }
            ],
            data-schema: Summary
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var rename = (SchemaOperation.Rename) ns.findSchema("Summary").orElseThrow().operations().get(0);
    assertEquals("product", rename.pairs().get(0).from().leaf());
    assertEquals("productName", rename.pairs().get(0).to());
  }

  @Test
  void parseNamespace_derivedSchema_filter() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, i32: qty }
                  },
                  Active: FROM Order {
                    operations: ( filter qty > 0 )
                  }
                ]
              }
            ],
            data-schema: Active
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var filter = (SchemaOperation.Filter) ns.findSchema("Active").orElseThrow().operations().get(0);
    assertInstanceOf(JsonTExpression.Binary.class, filter.predicate());
    assertEquals(BinaryOp.GT, ((JsonTExpression.Binary) filter.predicate()).op());
  }

  @Test
  void parseNamespace_derivedSchema_transform() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, d64: price }
                  },
                  Discounted: FROM Order {
                    operations: ( transform price = price * 0.9 )
                  }
                ]
              }
            ],
            data-schema: Discounted
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var transform = (SchemaOperation.Transform) ns.findSchema("Discounted").orElseThrow().operations().get(0);
    assertEquals("price", transform.target().leaf());
    assertInstanceOf(JsonTExpression.Binary.class, transform.expr());
  }

  @Test
  void parseNamespace_derivedSchema_exclude() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, str: product, str: internal }
                  },
                  Public: FROM Order {
                    operations: ( exclude(internal) )
                  }
                ]
              }
            ],
            data-schema: Public
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var exclude = (SchemaOperation.Exclude) ns.findSchema("Public").orElseThrow().operations().get(0);
    assertEquals("internal", exclude.paths().get(0).leaf());
  }

  // ── enum ──────────────────────────────────────────────────────────────────

  @Test
  void parseNamespace_withEnum() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Status: { fields: { i32: id } }
                ],
                enums: [
                  StatusEnum: [ACTIVE, INACTIVE, SUSPENDED]
                ]
              }
            ],
            data-schema: Status
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var e = ns.findEnum("StatusEnum").orElseThrow();
    assertEquals(3, e.values().size());
    assertTrue(e.contains("ACTIVE"));
    assertTrue(e.contains("SUSPENDED"));
  }

  // ── empty string base URL ─────────────────────────────────────────────────

  @Test
  void parseNamespace_emptyBaseUrl() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Ping: { fields: { i32: id } }
                ]
              }
            ],
            data-schema: Ping
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    assertTrue(ns.baseUrl().isEmpty());
    assertNotNull(ns.findSchema("Ping").orElseThrow());
  }

  // ── multiple catalogs ─────────────────────────────────────────────────────

  @Test
  void parseNamespace_multipleCatalogs() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  A: { fields: { i32: id } }
                ]
              },
              {
                schemas: [
                  B: { fields: { i32: id } }
                ]
              }
            ],
            data-schema: A
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    assertEquals(2, ns.catalogs().size());
    assertEquals(2, ns.schemaCount());
    assertTrue(ns.findSchema("A").isPresent());
    assertTrue(ns.findSchema("B").isPresent());
  }

  // ── P2.1: constant constraint ───────────────────────────────────

  @Test
  void parseNamespace_constantValue_bool() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Item: {
                    fields: {
                      bool: active [ const true ]
                    }
                  }
                ]
              }
            ],
            data-schema: Item
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Item").orElseThrow().findField("active").orElseThrow().constraints();
    assertNotNull(c.constantValue());
    assertEquals(JsonTValue.bool(true), c.constantValue());
  }

  @Test
  void parseNamespace_constantValue_integer() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Status: {
                    fields: {
                      i32: code [ const 42 ]
                    }
                  }
                ]
              }
            ],
            data-schema: Status
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Status").orElseThrow().findField("code").orElseThrow().constraints();
    assertNotNull(c.constantValue());
    assertTrue(c.constantValue().isNumeric());
    assertEquals(42.0, c.constantValue().toDouble(), 1e-9);
  }

  @Test
  void parseNamespace_constantValue_string() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Doc: {
                    fields: {
                      str: version [ const "v1" ]
                    }
                  }
                ]
              }
            ],
            data-schema: Doc
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Doc").orElseThrow().findField("version").orElseThrow().constraints();
    assertNotNull(c.constantValue());
    assertInstanceOf(JsonTValue.Str.class, c.constantValue());
    assertEquals("v1", c.constantValue().asText());
  }

  @Test
  void parseNamespace_constantValue_null() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Doc: {
                    fields: {
                      str: tag [ const null ]
                    }
                  }
                ]
              }
            ],
            data-schema: Doc
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Doc").orElseThrow().findField("tag").orElseThrow().constraints();
    assertNotNull(c.constantValue());
    assertTrue(c.constantValue().isNull());
  }

  @Test
  void parseNamespace_constantValue_decimalNumber() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Temp: {
                    fields: {
                      d64: offset [ const 1.5 ]
                    }
                  }
                ]
              }
            ],
            data-schema: Temp
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var c = ns.findSchema("Temp").orElseThrow().findField("offset").orElseThrow().constraints();
    assertNotNull(c.constantValue());
    assertEquals(1.5, c.constantValue().toDouble(), 1e-9);
  }

  // ── P2.2: conditional validation ───────────────

  @Test
  void parseNamespace_conditionalValidation_parsesAsConditionalRequirement() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: {
                      i32: qty,
                      str: note?
                    },
                    validations: {
                      rules { qty > 100 -> required (note) }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
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

  @Test
  void parseNamespace_conditionalValidation_multipleRequiredFields() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: {
                      i32: qty,
                      str: note?,
                      str: reason?
                    },
                    validations: {
                      rules { qty > 100 -> required (note, reason) }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var vb = ns.findSchema("Order").orElseThrow().validation().orElseThrow();
    var cr = (JsonTRule.ConditionalRequirement) vb.rules().get(0);
    assertEquals(2, cr.requiredFields().size());
    assertEquals("note", cr.requiredFields().get(0).leaf());
    assertEquals("reason", cr.requiredFields().get(1).leaf());
  }

  @Test
  void parseNamespace_conditionalValidation_coexistsWith_rule() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: {
                      i64: id,
                      i32: qty,
                      str: note?
                    },
                    validations: {
                      rules {
                        qty > 0,
                        qty > 100 -> required (note)
                      }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
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

  @Test
  void parseNamespace_dottedFieldPath_inRuleExpr() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: {
                      i64: id,
                      <Address>: address
                    },
                    validations: {
                      rules { address.zip > 0 }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
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

  @Test
  void parseNamespace_dottedFieldPath_inFilterExpr() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, <Address>: address }
                  },
                  Local: FROM Order {
                    operations: ( filter address.country == address.region )
                  }
                ]
              }
            ],
            data-schema: Local
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

  @Test
  void parseNamespace_dottedFieldPath_threeSegments() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id },
                    validations: {
                      rules { a.b.c > 0 }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var expr = ((JsonTRule.Expression) ns.findSchema("Order").orElseThrow().validation().orElseThrow().rules().get(0))
        .expr();
    var lhs = ((JsonTExpression.Binary) expr).lhs();
    assertEquals("a.b.c", ((JsonTExpression.FieldRef) lhs).path().dotJoined());
  }

  // ── B8: full document (namespace block + data rows in one string) ──────────

  @Test
  void parseDocument_namespaceThenDataRows() {
    String full = """
        {
          namespace: {
            baseUrl: "https://doc.example",
            version: "1.0",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i64: id, str: product, i32: qty }
                  }
                ]
              }
            ],
            data-schema: Order
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

  @Test
  void parseDocument_emptyRowSection_zeroRows() {
    String full = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Ping: { fields: { i32: id } }
                ]
              }
            ],
            data-schema: Ping
          }
        }
        """;
    var doc = JsonTParser.parseDocument(full);
    assertEquals(1, doc.namespace().schemaCount());
    assertEquals(0, doc.rowCount());
  }

  @Test
  void parseDocument_schemaAndRowsRoundTrip() {
    String ns = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Item: { fields: { str: code, d64: price } }
                ]
              }
            ],
            data-schema: Item
          }
        }
        """;
    String rowData = "{\"ABC\",9.99},{\"XYZ\",14.50}";
    var doc = JsonTParser.parseDocument(ns + rowData);

    var schema = doc.namespace().findSchema("Item").orElseThrow();
    assertEquals(2, schema.fieldCount());
    List<JsonTRow> rows = doc.rows();
    assertEquals(2, rows.size());
    assertEquals(text("ABC"), rows.get(0).get(0));
    assertEquals(d64(14.50), rows.get(1).get(1));
  }

  // ── error cases ───────────────────────────────────────────────────────────

  @Test
  void parseNamespace_emptyInput_throws() {
    assertThrows(JsonTError.Parse.class, () -> JsonTParser.parseNamespace(""));
  }

  @Test
  void parseNamespace_syntaxError_throws() {
    assertThrows(JsonTError.Parse.class,
        () -> JsonTParser.parseNamespace("{ namespace: { BROKEN }"));
  }

  @Test
  void parseNamespace_unknownScalarType_throws() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  X: {
                    fields: { notatype: id }
                  }
                ]
              }
            ],
            data-schema: X
          }
        }
        """;
    assertThrows(JsonTError.Parse.class, () -> JsonTParser.parseNamespace(dsl));
  }

  // ── expressions ───────────────────────────────────────────────────────────

  @Test
  void parseNamespace_complexExpression_precedence() {
    String dsl = """
        {
          namespace: {
            baseUrl: "",
            version: "",
            catalogs: [
              {
                schemas: [
                  Order: {
                    fields: { i32: a, i32: b, i32: c },
                    validations: {
                      rules { a > 1 && b < 10 || c == 5 }
                    }
                  }
                ]
              }
            ],
            data-schema: Order
          }
        }
        """;
    JsonTNamespace ns = JsonTParser.parseNamespace(dsl);
    var rule = ns.findSchema("Order").orElseThrow().validation().orElseThrow().rules().get(0);
    // ANTLR4 left-recursive: `&&` has higher precedence than `||`
    assertInstanceOf(JsonTRule.Expression.class, rule);
    assertInstanceOf(JsonTExpression.Binary.class, ((JsonTRule.Expression) rule).expr());
  }
}
