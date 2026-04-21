package io.github.datakore.jsont.builder;

// =============================================================================
// SensitiveFieldTest — Step 10.1: Model + Builder (Java)
// =============================================================================
// Covers:
//   • JsonTValue.Encrypted construction and predicates
//   • sensitive flag on JsonTField
//   • Builder: .sensitive() sets flag; rejects non-scalar kinds
//   • SchemaOperation.Decrypt construction and static factory
//   • JsonTSchemaBuilder.decrypt() appends Decrypt op to derived schema
//   • JsonTSchemaBuilder.decrypt() errors on straight schema
// =============================================================================

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveFieldTest {

    // =========================================================================
    // JsonTValue.Encrypted
    // =========================================================================

    @Test
    void encrypted_factory_stores_bytes() {
        byte[] envelope = {0x01, 0x02, 0x03};
        JsonTValue v = JsonTValue.encrypted(envelope);
        assertInstanceOf(JsonTValue.Encrypted.class, v);
        assertArrayEquals(envelope, ((JsonTValue.Encrypted) v).envelope());
    }

    @Test
    void encrypted_is_defensive_copy() {
        byte[] original = {0x01, 0x02};
        JsonTValue v = JsonTValue.encrypted(original);
        original[0] = (byte) 0x99; // mutate original
        assertNotEquals(original[0], ((JsonTValue.Encrypted) v).envelope()[0],
                "Encrypted should hold a defensive copy of the envelope bytes");
    }

    @Test
    void encrypted_isEncrypted_returns_true() {
        JsonTValue v = JsonTValue.encrypted(new byte[]{1, 2, 3});
        assertTrue(v.isEncrypted());
    }

    @Test
    void encrypted_other_predicates_are_false() {
        JsonTValue v = JsonTValue.encrypted(new byte[]{1});
        assertFalse(v.isNull());
        assertFalse(v.isNumeric());
        assertFalse(v.isStringLike());
        assertFalse(v.isUnspecified());
    }

    @Test
    void non_encrypted_values_isEncrypted_false() {
        assertFalse(JsonTValue.nullValue().isEncrypted());
        assertFalse(JsonTValue.text("hello").isEncrypted());
        assertFalse(JsonTValue.i32(42).isEncrypted());
        assertFalse(JsonTValue.bool(true).isEncrypted());
    }

    @Test
    void encrypted_toString_is_placeholder() {
        JsonTValue v = JsonTValue.encrypted(new byte[]{0x00});
        assertEquals("<encrypted>", v.toString());
    }

    @Test
    void encrypted_empty_envelope_is_valid() {
        JsonTValue v = JsonTValue.encrypted(new byte[0]);
        assertTrue(v.isEncrypted());
        assertArrayEquals(new byte[0], ((JsonTValue.Encrypted) v).envelope());
    }

    // =========================================================================
    // sensitive flag — JsonTField
    // =========================================================================

    @Test
    void scalar_field_sensitive_defaults_to_false() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("name", ScalarType.STR).build();
        assertFalse(field.sensitive());
    }

    @Test
    void sensitive_builder_sets_flag_on_scalar() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("ssn", ScalarType.STR)
                .sensitive()
                .build();
        assertTrue(field.sensitive());
    }

    @Test
    void sensitive_field_name_and_type_preserved() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("dob", ScalarType.DATE)
                .sensitive()
                .build();
        assertEquals("dob", field.name());
        assertEquals(ScalarType.DATE, field.scalarType());
        assertTrue(field.sensitive());
    }

    @Test
    void sensitive_combined_with_optional() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("middleName", ScalarType.STR)
                .sensitive()
                .optional()
                .build();
        assertTrue(field.sensitive());
        assertTrue(field.optional());
    }

    @Test
    void sensitive_combined_with_as_array() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("tags", ScalarType.STR)
                .sensitive()
                .asArray()
                .build();
        assertTrue(field.sensitive());
        assertTrue(field.kind().isArray());
    }

    // =========================================================================
    // Builder guards: sensitive() rejected on non-scalar kinds
    // =========================================================================

    @Test
    void sensitive_on_object_field_is_build_error() {
        BuildError err = assertThrows(BuildError.class, () ->
                JsonTFieldBuilder.object("address", "Address")
                        .sensitive()
                        .build());
        String msg = err.getMessage();
        assertTrue(msg.contains("sensitive") || msg.contains("scalar"),
                "error message should mention sensitive/scalar: " + msg);
    }

    @Test
    void sensitive_on_any_of_field_is_build_error() {
        BuildError err = assertThrows(BuildError.class, () ->
                JsonTFieldBuilder.anyOf("value",
                                List.of(AnyOfVariant.scalar(ScalarType.STR),
                                        AnyOfVariant.scalar(ScalarType.I32)))
                        .sensitive()
                        .build());
        String msg = err.getMessage();
        assertTrue(msg.contains("sensitive") || msg.contains("scalar"),
                "error message should mention sensitive/scalar: " + msg);
    }

    // =========================================================================
    // SchemaOperation.Decrypt — record + static factories
    // =========================================================================

    @Test
    void decrypt_op_record_stores_fields() {
        SchemaOperation op = new SchemaOperation.Decrypt(List.of("firstName", "dob"));
        assertInstanceOf(SchemaOperation.Decrypt.class, op);
        assertEquals(List.of("firstName", "dob"), ((SchemaOperation.Decrypt) op).fields());
    }

    @Test
    void decrypt_static_factory_list() {
        SchemaOperation op = SchemaOperation.decrypt(List.of("ssn", "creditCard"));
        assertInstanceOf(SchemaOperation.Decrypt.class, op);
        assertEquals(2, ((SchemaOperation.Decrypt) op).fields().size());
    }

    @Test
    void decrypt_static_factory_varargs() {
        SchemaOperation op = SchemaOperation.decrypt("ssn", "creditCard");
        assertInstanceOf(SchemaOperation.Decrypt.class, op);
        assertEquals(List.of("ssn", "creditCard"), ((SchemaOperation.Decrypt) op).fields());
    }

    @Test
    void decrypt_op_empty_fields_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaOperation.Decrypt(List.of()));
    }

    @Test
    void decrypt_op_fields_list_is_immutable() {
        SchemaOperation op = SchemaOperation.decrypt("a", "b");
        assertThrows(UnsupportedOperationException.class,
                () -> ((SchemaOperation.Decrypt) op).fields().add("c"));
    }

    // =========================================================================
    // JsonTSchemaBuilder.decrypt() — derived schema
    // =========================================================================

    @Test
    void schema_builder_decrypt_appends_decrypt_op() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.derived("EmployeeView", "Employee")
                .decrypt("ssn", "salary")
                .build();

        assertEquals(1, schema.operations().size());
        SchemaOperation op = schema.operations().get(0);
        assertInstanceOf(SchemaOperation.Decrypt.class, op);
        assertEquals(List.of("ssn", "salary"), ((SchemaOperation.Decrypt) op).fields());
    }

    @Test
    void schema_builder_decrypt_on_straight_schema_throws() {
        assertThrows(BuildError.class, () ->
                JsonTSchemaBuilder.straight("Person")
                        .decrypt("ssn"));
    }

    @Test
    void schema_builder_multiple_decrypt_ops_accumulate() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.derived("View", "Base")
                .decrypt("fieldA")
                .decrypt("fieldB")
                .build();

        assertEquals(2, schema.operations().size());
        assertInstanceOf(SchemaOperation.Decrypt.class, schema.operations().get(0));
        assertInstanceOf(SchemaOperation.Decrypt.class, schema.operations().get(1));
        assertEquals(List.of("fieldA"), ((SchemaOperation.Decrypt) schema.operations().get(0)).fields());
        assertEquals(List.of("fieldB"), ((SchemaOperation.Decrypt) schema.operations().get(1)).fields());
    }

    @Test
    void schema_builder_decrypt_mixed_with_other_ops() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.derived("SummaryView", "Person")
                .decrypt("ssn")
                .operation(SchemaOperation.exclude(FieldPath.single("internalId")))
                .build();

        assertEquals(2, schema.operations().size());
        assertInstanceOf(SchemaOperation.Decrypt.class, schema.operations().get(0));
        assertInstanceOf(SchemaOperation.Exclude.class, schema.operations().get(1));
    }
}
