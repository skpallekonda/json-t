package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.builder.*;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.datakore.jsont.model.JsonTExpression.*;
import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTSchemaStringifyTest {

    // ── Straight schema ────────────────────────────────────────────────────────

    @Test void straightSchema_compact_simple() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Order:{fields:{i64:id,str:product,i32:qty}}", text);
    }

    @Test void straightSchema_compact_optionalField() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Item")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("note", ScalarType.STR).optional())
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Item:{fields:{i32:id,str?:note}}", text);
    }

    @Test void straightSchema_compact_withConstraints() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Price")
                .fieldFrom(JsonTFieldBuilder.scalar("amount", ScalarType.D64)
                        .minValue(0.01).maxValue(9999.99).maxPrecision(2))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Price:{fields:{d64:amount [min = 0.01, max = 9999.99, maxPrecision = 2]}}", text);
    }

    @Test void straightSchema_compact_withObjectField() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Cart")
                .fieldFrom(JsonTFieldBuilder.object("items", "Item").asArray().minItems(1))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Cart:{fields:{<Item>[]:items [minItems = 1]}}", text);
    }

    @Test void straightSchema_compact_withValidation() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("User")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("age", ScalarType.I32))
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .unique(FieldPath.single("id"))
                        .rule(binary(BinaryOp.GE, fieldName("age"), literal(i32(0)))))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.startsWith("User:{fields:{i64:id,i32:age}"));
        assertTrue(text.contains("validations:{"));
        assertTrue(text.contains("rules:{age >= 0}"));
        assertTrue(text.contains("unique:{(id)}"));
    }

    // ── Derived schema ─────────────────────────────────────────────────────────

    @Test void derivedSchema_compact_project() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.derived("OrderSummary", "Order")
                .operation(SchemaOperation.project(
                        FieldPath.single("id"), FieldPath.single("product")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("OrderSummary:FROM Order{operations:(project(id, product))}", text);
    }

    @Test void derivedSchema_compact_rename() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.derived("Summary", "Order")
                .operation(SchemaOperation.rename(RenamePair.of("product", "productName")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Summary:FROM Order{operations:(rename(product as productName))}", text);
    }

    @Test void derivedSchema_compact_exclude() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.derived("Lite", "Order")
                .operation(SchemaOperation.exclude(FieldPath.single("id"), FieldPath.single("qty")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Lite:FROM Order{operations:(exclude(id, qty))}", text);
    }

    @Test void derivedSchema_compact_filter() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.derived("Active", "Order")
                .operation(SchemaOperation.filter(
                        binary(BinaryOp.GT, fieldName("qty"), literal(i32(0)))))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Active:FROM Order{operations:(filter qty > 0)}", text);
    }

    @Test void derivedSchema_compact_transform() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.derived("Discounted", "Order")
                .operation(SchemaOperation.transform("price",
                        binary(BinaryOp.MUL, fieldName("price"), literal(d64(0.9)))))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals("Discounted:FROM Order{operations:(transform price = price * 0.9)}", text);
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    @Test void enum_compact() {
        JsonTEnum e = new JsonTEnum("Status", List.of("ACTIVE", "INACTIVE", "SUSPENDED"));
        String text = JsonTStringifier.stringify(e, StringifyOptions.compact());
        assertEquals("Status:[ACTIVE, INACTIVE, SUSPENDED]", text);
    }

    @Test void enum_pretty() {
        JsonTEnum e = new JsonTEnum("Status", List.of("ACTIVE", "INACTIVE"));
        String text = JsonTStringifier.stringify(e, StringifyOptions.pretty());
        assertTrue(text.contains("Status:"));
        assertTrue(text.contains("[ACTIVE, INACTIVE]"));
    }

    // ── Namespace ──────────────────────────────────────────────────────────────

    @Test void namespace_compact_singleSchema() throws Exception {
        JsonTNamespace ns = JsonTNamespaceBuilder.create()
                .baseUrl("https://example.com/v1")
                .catalog(JsonTCatalogBuilder.create()
                        .schema(JsonTSchemaBuilder.straight("Ping")
                                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32))
                                .build())
                        .build())
                .build();
        String text = JsonTStringifier.stringify(ns, StringifyOptions.compact());
        assertTrue(text.startsWith("{namespace:{baseUrl:\"https://example.com/v1\""));
        assertTrue(text.contains("Ping:{fields:{i32:id}}"));
    }

    @Test void namespace_pretty_containsNewlines() throws Exception {
        JsonTNamespace ns = JsonTNamespaceBuilder.create()
                .baseUrl("https://example.com")
                .catalog(JsonTCatalogBuilder.create()
                        .schema(JsonTSchemaBuilder.straight("A")
                                .fieldFrom(JsonTFieldBuilder.scalar("x", ScalarType.I32))
                                .build())
                        .build())
                .build();
        String text = JsonTStringifier.stringify(ns, StringifyOptions.pretty());
        assertTrue(text.contains("\n"));
        assertTrue(text.contains("baseUrl:"));
        assertTrue(text.contains("catalogs:"));
    }

    // ── Constraint keywords ────────────────────────────────────────────────────

    @Test void constraintKeywords_stringLength() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Tag")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR).minLength(2).maxLength(50))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("minLength = 2"));
        assertTrue(text.contains("maxLength = 50"));
    }

    @Test void constraintKeywords_pattern() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Tag")
                .fieldFrom(JsonTFieldBuilder.scalar("code", ScalarType.STR).pattern("[A-Z]+"))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("regex = \"[A-Z]+\""));
    }

    @Test void constraintKeywords_required() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Tag")
                .fieldFrom(JsonTFieldBuilder.scalar("code", ScalarType.STR).required())
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("required = true"));
    }

    @Test void constraintKeywords_arrayItems() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Bag")
                .fieldFrom(JsonTFieldBuilder.scalar("tags", ScalarType.STR).asArray().minItems(1).maxItems(10))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("minItems = 1"));
        assertTrue(text.contains("maxItems = 10"));
    }

    // ── B5: constantValue constraint stringification ───────────────────────────

    @Test void constantValue_numeric() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Status")
                .fieldFrom(JsonTFieldBuilder.scalar("code", ScalarType.I32).constantValue(JsonTValue.i32(42)))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("constant = 42"), text);
    }

    @Test void constantValue_string() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Doc")
                .fieldFrom(JsonTFieldBuilder.scalar("version", ScalarType.STR).constantValue(JsonTValue.text("v1")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("constant = \"v1\""), text);
    }

    @Test void constantValue_bool() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Flag")
                .fieldFrom(JsonTFieldBuilder.scalar("active", ScalarType.BOOL).constantValue(JsonTValue.bool(true)))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("constant = true"), text);
    }

    @Test void constantValue_null() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Tag")
                .fieldFrom(JsonTFieldBuilder.scalar("note", ScalarType.STR).constantValue(JsonTValue.nullValue()))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("constant = null"), text);
    }

    // ── B4: ConditionalRequirement stringification ────────────────────────────

    @Test void conditionalRequirement_stringified() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("qty",  ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("note", ScalarType.STR).optional())
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .conditionalRule(
                                JsonTExpression.binary(BinaryOp.GT,
                                        JsonTExpression.fieldName("qty"),
                                        JsonTExpression.literal(JsonTValue.i32(100))),
                                FieldPath.single("note")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("if (qty > 100) require (note)"), text);
    }

    @Test void conditionalRequirement_multipleFields_stringified() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("qty",    ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("note",   ScalarType.STR).optional())
                .fieldFrom(JsonTFieldBuilder.scalar("reason", ScalarType.STR).optional())
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .conditionalRule(
                                JsonTExpression.binary(BinaryOp.GT,
                                        JsonTExpression.fieldName("qty"),
                                        JsonTExpression.literal(JsonTValue.i32(100))),
                                FieldPath.single("note"), FieldPath.single("reason")))
                .build();
        String text = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertTrue(text.contains("if (qty > 100) require (note, reason)"), text);
    }

    // ── B3: Schema stringify round-trip ───────────────────────────────────────

    @Test void roundTrip_straightSchema_stableOnSecondStringify() throws Exception {
        JsonTSchema s = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).optional())
                .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1.0).maxValue(999.0))
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .unique("id")
                        .rule(JsonTExpression.binary(BinaryOp.GT,
                                JsonTExpression.fieldName("qty"),
                                JsonTExpression.literal(JsonTValue.i32(0)))))
                .build();
        String first  = JsonTStringifier.stringify(s, StringifyOptions.compact());
        String second = JsonTStringifier.stringify(s, StringifyOptions.compact());
        assertEquals(first, second, "Schema stringification must be stable (idempotent)");
    }

    @Test void roundTrip_namespace_stringifyIsStable() throws Exception {
        // The stringify output is stable: stringifying the same model twice yields
        // identical text (idempotent serialisation).
        JsonTNamespace ns = JsonTNamespaceBuilder.create()
                .baseUrl("https://test.example")
                .catalog(JsonTCatalogBuilder.create()
                        .schema(JsonTSchemaBuilder.straight("Item")
                                .fieldFrom(JsonTFieldBuilder.scalar("id",    ScalarType.I64))
                                .fieldFrom(JsonTFieldBuilder.scalar("label", ScalarType.STR).minLength(1).maxLength(100))
                                .build())
                        .build())
                .build();
        String first  = JsonTStringifier.stringify(ns, StringifyOptions.compact());
        String second = JsonTStringifier.stringify(ns, StringifyOptions.compact());
        assertEquals(first, second);
        assertTrue(first.contains("\"https://test.example\""));
        assertTrue(first.contains("minLength = 1"));
        assertTrue(first.contains("maxLength = 100"));
    }
}
