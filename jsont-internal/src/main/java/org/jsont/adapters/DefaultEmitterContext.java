package org.jsont.adapters;

import org.jsont.execution.SchemaEmitter;
import org.jsont.grammar.schema.ast.EnumModel;
import org.jsont.grammar.schema.ast.SchemaCatalog;
import org.jsont.grammar.schema.ast.SchemaModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DefaultEmitterContext implements EmitterContext {
    private final SchemaCatalog catalog;
    private final SchemaEmitter emitter;
    private Map<String, String> visitedSchemas = new HashMap<>();
    private Map<String, String> visitedEnums = new HashMap<>();
    private Set<String> visitedFieldTypes = new HashSet<>();

    public DefaultEmitterContext(SchemaEmitter emitter, SchemaCatalog catalog) {
        this.emitter = emitter;
        this.catalog = catalog;
    }

    @Override
    public void visitedFieldType(String type) {
        visitedFieldTypes.add(type);
    }

    @Override
    public void visitedSchemas(String type, String schemaString) {
        visitedSchemas.putIfAbsent(type, schemaString);
    }

    @Override
    public void visitedEnums(String type, String schemaString) {
        visitedEnums.putIfAbsent(type, schemaString);
    }

    @Override
    public List<SchemaModel> missingSchemas() {
        return this.visitedFieldTypes.stream()
                .filter(f -> !visitedSchemas.containsKey(f))
                .map(catalog::getSchema).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<EnumModel> missingEnums() {
        return this.visitedFieldTypes.stream()
                .filter(f -> !visitedEnums.containsKey(f))
                .map(catalog::getEnum).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    @Override
    public String emitRequiredCatalog() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append(schemasSection());
        builder.append(",\n");
        builder.append(enumsSection());
        builder.append("\n}");
        return builder.toString();
    }

    private String schemasSection() {
        final StringBuilder builder = new StringBuilder();
        /**
         *     schemas: {
         *         User: {
         */
        builder.append("schemas : {\n");
        final AtomicInteger index = new AtomicInteger();
        visitedSchemas.forEach((schema, content) -> {
            if (index.getAndIncrement() > 0) {
                builder.append(",\n");
            }
            builder.append("\t").append(schema).append(": ").append(content);
        });
        builder.append("\n}");
        return builder.toString();
    }

    private String enumsSection() {
        final StringBuilder builder = new StringBuilder();
        /**
         * enums: [
         *         Role {
         */
        builder.append("enums : [");
        final AtomicInteger index = new AtomicInteger();
        visitedEnums.forEach((schema, content) -> {
            if (index.getAndIncrement() > 0) {
                builder.append(",\n");
            }
            builder.append("\n\t").append(schema).append(": ").append(content);
        });
        builder.append("]");
        return builder.toString();
    }
}
