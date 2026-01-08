package org.jsont.schema;

import org.jsont.JsonTConfig;
import org.jsont.exception.SchemaException;
import org.jsont.grammar.schema.ast.SchemaCatalog;
import org.jsont.grammar.schema.raw.EnumNode;
import org.jsont.grammar.schema.raw.SchemaNode;

import java.util.List;

public final class SchemaResolver {
    public SchemaCatalog resolve(
            List<SchemaNode> schemaNodes,
            List<EnumNode> enumNodes
    ) {
        if (schemaNodes == null || schemaNodes.isEmpty()) {
            throw new SchemaException("No schemas found");
        } else if (schemaNodes.size() > JsonTConfig.getMaxAllowedSchemas()) {
            throw new SchemaException(String.format("More than supported schemas (%d > %d)", schemaNodes.size(), JsonTConfig.getMaxAllowedSchemas()));
        }
        SchemaCatalog catalog = new SchemaCatalog();
        schemaNodes.stream()
                .map(catalog::convertSchema)
                .forEach(sm -> catalog.addModel(sm.name(), sm));
        enumNodes.stream()
                .map(catalog::convertEnum)
                .forEach(em -> catalog.addEnum(em.name(), em));
        return catalog;
    }
}
