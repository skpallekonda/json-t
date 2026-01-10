package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

import java.util.List;

public interface EmitterContext {
    void visitedFieldType(String type);

    void visitedSchemas(String type, String schemaString);

    void visitedEnums(String type, String schemaString);

    List<SchemaModel> missingSchemas();

    List<EnumModel> missingEnums();

    String emitRequiredCatalog();
}
