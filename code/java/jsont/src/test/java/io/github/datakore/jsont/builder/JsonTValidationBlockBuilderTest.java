package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.JsonTValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonTValidationBlockBuilderTest {

    @Test
    void unique_singleField() throws BuildError {
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique("id")
                .build();

        assertEquals(1, vb.uniqueKeys().size());
        assertEquals("id", vb.uniqueKeys().get(0).get(0).leaf());
        assertTrue(vb.rules().isEmpty());
    }

    @Test
    void unique_multipleFields_separateConstraints() throws BuildError {
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique("id")
                .unique("email")
                .build();

        assertEquals(2, vb.uniqueKeys().size());
    }

    @Test
    void unique_compoundKey_varargs() throws BuildError {
        // compound key: (tenantId, orderId) must be unique together
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique(FieldPath.single("tenantId"), FieldPath.single("orderId"))
                .build();

        assertEquals(1, vb.uniqueKeys().size());
        assertEquals(2, vb.uniqueKeys().get(0).size());
    }

    @Test
    void unique_compoundKey_list() throws BuildError {
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique(List.of(FieldPath.single("a"), FieldPath.single("b")))
                .build();

        assertEquals(1, vb.uniqueKeys().size());
        assertEquals(2, vb.uniqueKeys().get(0).size());
    }

    @Test
    void rule_singleExpression() throws BuildError {
        var expr = JsonTExpression.binary(
                BinaryOp.GT,
                JsonTExpression.fieldName("price"),
                JsonTExpression.literal(JsonTValue.d64(0.0)));

        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .rule(expr)
                .build();

        assertEquals(1, vb.rules().size());
        assertSame(expr, vb.rules().get(0));
        assertTrue(vb.uniqueKeys().isEmpty());
    }

    @Test
    void rule_multipleExpressions() throws BuildError {
        var e1 = JsonTExpression.literal(JsonTValue.bool(true));
        var e2 = JsonTExpression.literal(JsonTValue.bool(false));

        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .rule(e1)
                .rule(e2)
                .build();

        assertEquals(2, vb.rules().size());
    }

    @Test
    void combined_uniqueAndRule() throws BuildError {
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique("id")
                .rule(JsonTExpression.binary(
                        BinaryOp.GT,
                        JsonTExpression.fieldName("qty"),
                        JsonTExpression.literal(JsonTValue.i32(0))))
                .build();

        assertEquals(1, vb.uniqueKeys().size());
        assertEquals(1, vb.rules().size());
        assertFalse(vb.isEmpty());
    }

    @Test
    void empty_throwsBuildError() {
        assertThrows(BuildError.class,
                () -> JsonTValidationBlockBuilder.create().build());
    }

    @Test
    void unique_emptyList_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonTValidationBlockBuilder.create().unique(List.of()));
    }

    @Test
    void rule_nullExpression_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonTValidationBlockBuilder.create().rule(null));
    }

    @Test
    void built_block_isImmutable() throws BuildError {
        JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
                .unique("id")
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> vb.uniqueKeys().add(List.of()));
    }
}
