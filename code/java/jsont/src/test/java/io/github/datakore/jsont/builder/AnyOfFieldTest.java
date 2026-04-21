package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.parse.SchemaVisitor;
import io.github.datakore.jsont.internal.stringify.SchemaStringifier;
import io.github.datakore.jsont.json.JsonInputMode;
import io.github.datakore.jsont.json.JsonReader;
import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.stringify.StringifyOptions;
import io.github.datakore.jsont.validate.ValidationPipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code anyOf} field feature.
 *
 * Covers: model construction, builder validation, schema parse round-trip,
 * stringify, JSON reader dispatch (scalar + enum combinations).
 */
class AnyOfFieldTest {

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    void builder_requiresAtLeastTwoVariants() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonTFieldBuilder.anyOf("f", List.of(AnyOfVariant.scalar(ScalarType.STR))));
    }

    @Test
    void builder_rejectsNullVariants() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonTFieldBuilder.anyOf("f", null));
    }

    @Test
    void builder_scalarUnion_builds() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("score",
                List.of(AnyOfVariant.scalar(ScalarType.I32), AnyOfVariant.scalar(ScalarType.D64)))
                .build();

        assertEquals("score", f.name());
        assertEquals(FieldKind.ANY_OF, f.kind());
        assertTrue(f.kind().isAnyOf());
        assertFalse(f.kind().isScalar());
        assertFalse(f.kind().isObject());
        assertEquals(2, f.anyOfVariants().size());
        assertNull(f.discriminator());
        assertFalse(f.optional());
    }

    @Test
    void builder_withOptionalAndArray() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("tags",
                List.of(AnyOfVariant.scalar(ScalarType.STR), AnyOfVariant.schemaRef("Tag")))
                .optional()
                .asArray()
                .build();

        assertEquals(FieldKind.ARRAY_ANY_OF, f.kind());
        assertTrue(f.kind().isArray());
        assertTrue(f.kind().isAnyOf());
        assertTrue(f.optional());
    }

    @Test
    void builder_withDiscriminator() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("payload",
                List.of(AnyOfVariant.schemaRef("Person"), AnyOfVariant.schemaRef("Customer")))
                .discriminator("type")
                .build();

        assertEquals("type", f.discriminator());
        assertEquals(2, f.anyOfVariants().stream().filter(AnyOfVariant::isSchemaRef).count());
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    @Test
    void fieldKind_anyOfHelpers() {
        assertTrue(FieldKind.ANY_OF.isAnyOf());
        assertTrue(FieldKind.ARRAY_ANY_OF.isAnyOf());
        assertTrue(FieldKind.ARRAY_ANY_OF.isArray());
        assertFalse(FieldKind.ANY_OF.isScalar());
        assertFalse(FieldKind.ANY_OF.isObject());
    }

    @Test
    void anyOfVariant_factories() {
        AnyOfVariant scalar = AnyOfVariant.scalar(ScalarType.NSTR);
        AnyOfVariant ref    = AnyOfVariant.schemaRef("Status");

        assertTrue(scalar.isScalar());
        assertFalse(scalar.isSchemaRef());
        assertFalse(ref.isScalar());
        assertTrue(ref.isSchemaRef());

        assertEquals(ScalarType.NSTR, ((AnyOfVariant.Scalar) scalar).type());
        assertEquals("Status",        ((AnyOfVariant.SchemaRef) ref).name());
    }

    // ── Stringify ─────────────────────────────────────────────────────────────

    @Test
    void stringify_scalarUnion() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("score",
                List.of(AnyOfVariant.scalar(ScalarType.I32), AnyOfVariant.scalar(ScalarType.D64)))
                .build();
        // compact mode: no space after ":"
        String out = SchemaStringifier.stringifyField(f, StringifyOptions.compact());
        assertEquals("anyOf(i32 | d64):score", out);
    }

    @Test
    void stringify_mixedUnionOptional() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("phase",
                List.of(AnyOfVariant.schemaRef("TournamentPhase"), AnyOfVariant.scalar(ScalarType.NSTR)))
                .optional()
                .build();
        String out = SchemaStringifier.stringifyField(f, StringifyOptions.compact());
        assertEquals("anyOf(<TournamentPhase> | nstr)?:phase", out);
    }

    @Test
    void stringify_withDiscriminator() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("payload",
                List.of(AnyOfVariant.schemaRef("Person"), AnyOfVariant.schemaRef("Customer")))
                .discriminator("type")
                .build();
        String out = SchemaStringifier.stringifyField(f, StringifyOptions.compact());
        assertEquals("anyOf(<Person> | <Customer>) on \"type\":payload", out);
    }

    @Test
    void stringify_arrayUnion() throws BuildError {
        JsonTField f = JsonTFieldBuilder.anyOf("items",
                List.of(AnyOfVariant.scalar(ScalarType.STR), AnyOfVariant.schemaRef("Tag")))
                .asArray()
                .build();
        String out = SchemaStringifier.stringifyField(f, StringifyOptions.compact());
        assertEquals("anyOf(str | <Tag>)[]:items", out);
    }

    // ── Schema parse round-trip ───────────────────────────────────────────────

    @Test
    void parseDsl_scalarAnyOf() {
        String dsl = """
                {namespace:{baseUrl:"http://test",version:"1",catalogs:[{schemas:[
                  Event:{fields:{
                    anyOf(i32|str):score,
                    str:name
                  }}
                ]}],data-schema: Event}}
                """;
        JsonTNamespace ns = SchemaVisitor.parseNamespace(dsl);
        JsonTSchema schema = ns.findSchema("Event").orElseThrow();
        JsonTField score = schema.findField("score").orElseThrow();

        assertEquals(FieldKind.ANY_OF, score.kind());
        assertEquals(2, score.anyOfVariants().size());
        assertInstanceOf(AnyOfVariant.Scalar.class, score.anyOfVariants().get(0));
        assertInstanceOf(AnyOfVariant.Scalar.class, score.anyOfVariants().get(1));
        assertEquals(ScalarType.I32, ((AnyOfVariant.Scalar) score.anyOfVariants().get(0)).type());
        assertEquals(ScalarType.STR, ((AnyOfVariant.Scalar) score.anyOfVariants().get(1)).type());
    }

    @Test
    void parseDsl_mixedEnumAndScalar() {
        String dsl = """
                {namespace:{baseUrl:"http://test",version:"1",catalogs:[{schemas:[
                  Match:{fields:{
                    anyOf(<Phase>|nstr):phase
                  }}
                ],enums:[Phase:[FIRST,SECOND,FINAL]]}],data-schema: Match}}
                """;
        JsonTNamespace ns = SchemaVisitor.parseNamespace(dsl);
        JsonTField phase = ns.findSchema("Match").orElseThrow().findField("phase").orElseThrow();

        assertEquals(FieldKind.ANY_OF, phase.kind());
        assertInstanceOf(AnyOfVariant.SchemaRef.class, phase.anyOfVariants().get(0));
        assertEquals("Phase", ((AnyOfVariant.SchemaRef) phase.anyOfVariants().get(0)).name());
        assertInstanceOf(AnyOfVariant.Scalar.class, phase.anyOfVariants().get(1));
    }

    @Test
    void parseDsl_withDiscriminator() {
        String dsl = """
                {namespace:{baseUrl:"http://test",version:"1",catalogs:[{schemas:[
                  Request:{fields:{
                    anyOf(<Person>|<Customer>) on "type":payload
                  }}
                ]}],data-schema: Request}}
                """;
        JsonTNamespace ns = SchemaVisitor.parseNamespace(dsl);
        JsonTField payload = ns.findSchema("Request").orElseThrow().findField("payload").orElseThrow();

        assertEquals(FieldKind.ANY_OF, payload.kind());
        assertEquals("type", payload.discriminator());
        assertEquals(2, payload.anyOfVariants().size());
    }

    @Test
    void parseDsl_arrayAnyOf() {
        String dsl = """
                {namespace:{baseUrl:"http://test",version:"1",catalogs:[{schemas:[
                  Doc:{fields:{
                    anyOf(str|i32)[]:values
                  }}
                ]}],data-schema: Doc}}
                """;
        JsonTField values = SchemaVisitor.parseNamespace(dsl)
                .findSchema("Doc").orElseThrow().findField("values").orElseThrow();

        assertEquals(FieldKind.ARRAY_ANY_OF, values.kind());
        assertTrue(values.kind().isArray());
    }

    // ── JSON reader — scalar dispatch ─────────────────────────────────────────

    @Test
    void jsonReader_intMatchesFirstNumericVariant() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.I32), AnyOfVariant.scalar(ScalarType.STR))))
                .build();

        List<JsonTRow> rows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build()
                .read("{\"v\":42}", rows::add);

        assertEquals(1, rows.size());
        assertInstanceOf(JsonTNumber.I32.class, rows.get(0).get(0));
        assertEquals(42, ((JsonTNumber.I32) rows.get(0).get(0)).value());
    }

    @Test
    void jsonReader_stringMatchesScalarVariant() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.I32), AnyOfVariant.scalar(ScalarType.STR))))
                .build();

        List<JsonTRow> rows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build()
                .read("{\"v\":\"hello\"}", rows::add);

        assertEquals(1, rows.size());
        assertInstanceOf(JsonTString.Plain.class, rows.get(0).get(0));
        assertEquals("hello", ((JsonTString.Plain) rows.get(0).get(0)).value());
    }

    @Test
    void jsonReader_schemaRefWinsOverScalarForString() throws BuildError {
        // anyOf(<Phase>, nstr) — SchemaRef declared first → string becomes Enum
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("phase",
                        List.of(AnyOfVariant.schemaRef("Phase"), AnyOfVariant.scalar(ScalarType.NSTR))))
                .build();

        List<JsonTRow> rows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build()
                .read("{\"phase\":\"FINAL\"}", rows::add);

        assertEquals(1, rows.size());
        assertInstanceOf(JsonTValue.Enum.class, rows.get(0).get(0));
        assertEquals("FINAL", ((JsonTValue.Enum) rows.get(0).get(0)).value());
    }

    @Test
    void jsonReader_scalarDeclaredFirst_stringBecomesNstr() throws BuildError {
        // anyOf(nstr, <Phase>) — scalar declared first → string becomes Nstr
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("phase",
                        List.of(AnyOfVariant.scalar(ScalarType.NSTR), AnyOfVariant.schemaRef("Phase"))))
                .build();

        List<JsonTRow> rows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build()
                .read("{\"phase\":\"FINAL\"}", rows::add);

        assertEquals(1, rows.size());
        assertInstanceOf(JsonTString.Nstr.class, rows.get(0).get(0));
    }

    @Test
    void jsonReader_noMatchingVariantThrows() throws BuildError {
        // anyOf(i32, i64) — bool has no numeric variant match path
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.I32), AnyOfVariant.scalar(ScalarType.I64))))
                .build();

        var reader = JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build();
        assertThrows(io.github.datakore.jsont.error.JsonTError.Parse.class,
                () -> reader.read("{\"v\":true}", row -> {}));
    }

    // ── Native JsonT format — promoteRow ──────────────────────────────────────

    @Test
    void promoteRow_anyOf_stringPromotedToNstr() throws BuildError {
        // anyOf(nstr | i32): a quoted string should be promoted to Nstr
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.NSTR), AnyOfVariant.scalar(ScalarType.I32))))
                .build();

        List<JsonTRow> out = new ArrayList<>();
        ValidationPipeline.builder(schema).withoutConsole().build()
                .validateEach(List.of(JsonTRow.of(JsonTValue.text("hello"))), out::add);

        assertEquals(1, out.size());
        assertInstanceOf(JsonTString.Nstr.class, out.get(0).get(0));
        assertEquals("hello", ((JsonTString.Nstr) out.get(0).get(0)).value());
    }

    @Test
    void promoteRow_anyOf_stringVariantWinsInDeclarationOrder() throws BuildError {
        // anyOf(str | nstr): str declared first → Plain wins, not Nstr
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.STR), AnyOfVariant.scalar(ScalarType.NSTR))))
                .build();

        List<JsonTRow> out = new ArrayList<>();
        ValidationPipeline.builder(schema).withoutConsole().build()
                .validateEach(List.of(JsonTRow.of(JsonTValue.text("hello"))), out::add);

        assertEquals(1, out.size());
        assertInstanceOf(JsonTString.Plain.class, out.get(0).get(0));
    }

    @Test
    void promoteRow_anyOf_enumPassesThrough() throws BuildError {
        // anyOf(<Phase> | nstr): enum constants already parse as Enum, no promotion needed
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.schemaRef("Phase"), AnyOfVariant.scalar(ScalarType.NSTR))))
                .build();

        List<JsonTRow> out = new ArrayList<>();
        ValidationPipeline.builder(schema).withoutConsole().build()
                .validateEach(List.of(JsonTRow.of(JsonTValue.enumValue("FINAL"))), out::add);

        assertEquals(1, out.size());
        assertInstanceOf(JsonTValue.Enum.class, out.get(0).get(0));
        assertEquals("FINAL", ((JsonTValue.Enum) out.get(0).get(0)).value());
    }

    @Test
    void promoteRow_anyOf_invalidFormatFallsToNextVariant() throws BuildError {
        // anyOf(uuid | str): "hello" is not a valid UUID → falls through to str → Plain
        JsonTSchema schema = JsonTSchemaBuilder.straight("S")
                .fieldFrom(JsonTFieldBuilder.anyOf("v",
                        List.of(AnyOfVariant.scalar(ScalarType.UUID), AnyOfVariant.scalar(ScalarType.STR))))
                .build();

        List<JsonTRow> out = new ArrayList<>();
        ValidationPipeline.builder(schema).withoutConsole().build()
                .validateEach(List.of(JsonTRow.of(JsonTValue.text("hello"))), out::add);

        assertEquals(1, out.size());
        assertInstanceOf(JsonTString.Plain.class, out.get(0).get(0));
    }
}
