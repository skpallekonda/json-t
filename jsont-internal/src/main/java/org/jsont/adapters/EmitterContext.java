package org.jsont.adapters;

import org.jsont.grammar.schema.ast.EnumModel;
import org.jsont.grammar.schema.ast.SchemaModel;

import java.util.List;

public interface EmitterContext {
    void visitedFieldType(String type);

    void visitedSchemas(String type, String schemaString);

    void visitedEnums(String type, String schemaString);

    List<SchemaModel> missingSchemas();

    List<EnumModel> missingEnums();

    String emitRequiredCatalog();
}
