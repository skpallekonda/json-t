package io.github.datakore.jsont.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.datakore.jsont.model.JsonTExpression.*;
import static io.github.datakore.jsont.model.JsonTValue.*;
import static io.github.datakore.jsont.model.BinaryOp.GT;
import static org.junit.jupiter.api.Assertions.*;

class SchemaOperationTest {

    @Test
    void rename_factoryCreatesRenameOp() {
        var op = SchemaOperation.rename(List.of(RenamePair.of("price", "amount")));
        assertInstanceOf(SchemaOperation.Rename.class, op);
        var rename = (SchemaOperation.Rename) op;
        assertEquals(1, rename.pairs().size());
        assertEquals("price", rename.pairs().get(0).from().leaf());
        assertEquals("amount", rename.pairs().get(0).to());
    }

    @Test
    void rename_varargs() {
        var op = SchemaOperation.rename(
                RenamePair.of("a", "b"),
                RenamePair.of("c", "d"));
        assertEquals(2, ((SchemaOperation.Rename) op).pairs().size());
    }

    @Test
    void rename_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> SchemaOperation.rename(List.of()));
    }

    @Test
    void exclude_factoryCreatesExcludeOp() {
        var op = SchemaOperation.exclude(FieldPath.single("secret"), FieldPath.single("internal"));
        assertInstanceOf(SchemaOperation.Exclude.class, op);
        assertEquals(2, ((SchemaOperation.Exclude) op).paths().size());
    }

    @Test
    void project_factoryCreatesProjectOp() {
        var op = SchemaOperation.project(
                List.of(FieldPath.single("id"), FieldPath.single("name")));
        assertInstanceOf(SchemaOperation.Project.class, op);
        var project = (SchemaOperation.Project) op;
        assertEquals(2, project.paths().size());
        assertEquals("id",   project.paths().get(0).leaf());
        assertEquals("name", project.paths().get(1).leaf());
    }

    @Test
    void filter_factoryCreatesFilterOp() {
        var predicate = binary(GT, fieldName("amount"), literal(d64(0.0)));
        var op = SchemaOperation.filter(predicate);
        assertInstanceOf(SchemaOperation.Filter.class, op);
        assertSame(predicate, ((SchemaOperation.Filter) op).predicate());
    }

    @Test
    void transform_factoryCreatesTransformOp() {
        var expr = binary(GT, fieldName("price"), literal(d64(0.0)));
        var op   = SchemaOperation.transform("price", expr);
        assertInstanceOf(SchemaOperation.Transform.class, op);
        var t = (SchemaOperation.Transform) op;
        assertEquals("price", t.target().leaf());
        assertSame(expr, t.expr());
    }

    @Test
    void operations_areImmutableAfterConstruction() {
        var paths = new java.util.ArrayList<FieldPath>();
        paths.add(FieldPath.single("id"));
        var op = SchemaOperation.project(paths);
        paths.add(FieldPath.single("name")); // mutate original
        assertEquals(1, ((SchemaOperation.Project) op).paths().size()); // unaffected
    }

    @Test
    void filter_rejectsNullPredicate() {
        assertThrows(NullPointerException.class, () -> SchemaOperation.filter(null));
    }

    @Test
    void transform_rejectsNullTarget() {
        assertThrows(NullPointerException.class,
                () -> SchemaOperation.transform((FieldPath) null, literal(i32(0))));
    }

    @Test
    void sealedInstanceofDispatch_correctlyClassifies() {
        SchemaOperation op = SchemaOperation.exclude(FieldPath.single("x"));
        String kind;
        if      (op instanceof SchemaOperation.Rename)    kind = "rename";
        else if (op instanceof SchemaOperation.Exclude)   kind = "exclude";
        else if (op instanceof SchemaOperation.Project)   kind = "project";
        else if (op instanceof SchemaOperation.Filter)    kind = "filter";
        else if (op instanceof SchemaOperation.Transform) kind = "transform";
        else kind = "unknown";
        assertEquals("exclude", kind);
    }
}
