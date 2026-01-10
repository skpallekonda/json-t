package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.EmitterContext;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.grammar.data.JsontScalarType;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.types.ValueType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultSchemaEmitter implements SchemaEmitter {

    private final SchemaCatalog catalog;
    private final AdapterRegistry adapterRegistry;

    public DefaultSchemaEmitter(AdapterRegistry adapterRegistry, SchemaCatalog catalog) {
        this.catalog = catalog;
        this.adapterRegistry = adapterRegistry;
    }

    private String emitFiedldIdentifier(FieldModel field) {
        StringBuilder sb = new StringBuilder();
        sb.append(field.name());
        if (field.optional()) {
            sb.append("?");
        }
        return sb.toString();
    }

    private String emitFieldType(FieldModel field, EmitterContext context) {
        StringBuilder sb = new StringBuilder();
        switch (field.type().valueType()) {
            case ENUM:
            case OBJECT:
                sb.append(field.type().name());
                String typeName = field.type().name();
                if (typeName.startsWith("<") && typeName.endsWith(">")) {
                    typeName = typeName.substring(1, typeName.length() - 1);
                }
                context.visitedFieldType(typeName);
                break;
            default:
                sb.append(field.type().valueType().getIdentifier());
        }
        return sb.toString();
    }

    @Override
    public void emitSchema(SchemaModel schema, EmitterContext context) {
        StringBuilder builder = new StringBuilder();
        if (schema == null) {
            return;
        }
        builder.append("{\n");
        int colIndex = 0;
        for (FieldModel field : schema.fields()) {
            if (colIndex > 0) {
                builder.append(",\n");
            }
            builder.append("\t\t").append(emitFieldType(field, context)).append(":").append(emitFiedldIdentifier(field));
            colIndex++;
        }
        builder.append("\n\t}");
        context.visitedSchemas(schema.name(), builder.toString());
    }

    @Override
    public void emitEnum(EnumModel enumModel, EmitterContext context) {
        StringBuilder builder = new StringBuilder();
        if (enumModel == null) {
            return;
        }
        builder.append("{").append(enumModel.name()).append("\n");
        builder.append(enumModel.values().stream().collect(Collectors.joining(",\n\t")));
        builder.append("}\n");
        context.visitedEnums(enumModel.name(), builder.toString());
    }

    @Override
    public String emitRequiredCatalog(SchemaModel dataSchema, EmitterContext context) {
        processCatalogSection(dataSchema, context);
        return context.emitRequiredCatalog();
    }

    @Override
    public <T> String stringify(EmitterContext emitterContext, T target, Class<T> targetClass) {
        SchemaModel schema = catalog.getSchema(targetClass.getSimpleName());
        StringBuilder sb = new StringBuilder();
        processDataSection(sb, schema, target, emitterContext);
        return sb.toString();
    }

    private <T> void processDataSection(StringBuilder sb, SchemaModel schema, T target, EmitterContext emitterContext) {
        SchemaAdapter<?> adapter = adapterRegistry.resolve(schema.name());
        /**
         * {
         *             1,
         *             "alice_dev",
         *             "alice@example.com",
         *             { "123 Silicon Way", "Tech City", 94000 },
         *             [ "developer", "admin" ]
         *         }
         */
        sb.append(" { ");
        AtomicInteger colIndex = new AtomicInteger();
        for (FieldModel field : schema.fields()) {
            Object fieldObj = adapter.get(target, field.name());
            if (colIndex.getAndIncrement() > 0) {
                sb.append(",");
            }
            if (field.arrays()) {
                handleDataArray(sb, fieldObj, field.type(), emitterContext);
            } else if (field.type().valueType() == JsontScalarType.OBJECT) {
                handleDataObject(sb, fieldObj, field.type().name(), emitterContext);
            } else {
                handleDataScalar(sb, fieldObj, field.type(), emitterContext);
            }
        }
        sb.append("}");
    }

    private <T> void handleDataObject(StringBuilder sb, Object fieldObj, String typeName, EmitterContext emitterContext) {
        SchemaModel fieldSchema = catalog.getSchema(typeName);
        processDataSection(sb, fieldSchema, fieldObj, emitterContext);
    }

    private void handleDataScalar(StringBuilder sb, Object fieldObj, ValueType type, EmitterContext emitterContext) {
        switch (type.valueType()) {
            case NULL:
                sb.append("null");
                break;
            case INT:
            case LONG:
            case FLOAT:
            case DECIMAL:
            case BOOLEAN:
                sb.append(fieldObj);
                break;
            default:
                sb.append("\"").append(fieldObj).append("\"");
        }
    }

    private void handleDataArray(StringBuilder sb, Object fieldObj, ValueType type, EmitterContext emitterContext) {
        if (fieldObj == null) {
            sb.append("null");
        } else {
            Stream<?> stream = null;
            if (fieldObj instanceof List) {
                List<?> list = (List<?>) fieldObj;
                stream = list.stream();
            } else if (fieldObj instanceof Set) {
                Set<?> set = (Set<?>) fieldObj;
                stream = set.stream();
            } else if (fieldObj instanceof Object[]) {
                Object[] array = (Object[]) fieldObj;
                stream = Arrays.stream(array);
            } else if (fieldObj instanceof Collection) {
                Collection<?> collection = (Collection<?>) fieldObj;
                stream = collection.stream();
            }
            if (stream != null) {
                sb.append("[");
                AtomicInteger index = new AtomicInteger();
                stream.forEach(obj -> {
                    if (index.getAndIncrement() > 0) {
                        sb.append(", ");
                    }
                    if (type.valueType() == JsontScalarType.OBJECT) {
                        handleDataObject(sb, obj, type.name(), emitterContext);
                    } else {
                        handleDataScalar(sb, obj, type, emitterContext);
                    }
                });
                sb.append("]");
            }
        }
    }

    private void processCatalogSection(SchemaModel schema, EmitterContext emitterContext) {
        List<SchemaModel> missingSchemas = null;
        List<EnumModel> missingEnums = null;
        StringBuilder sb = new StringBuilder();
        do {
            if (missingSchemas == null) {
                this.emitSchema(schema, emitterContext);
            } else {
                missingSchemas.forEach(s -> this.emitSchema(s, emitterContext));
            }
            if (missingEnums != null) {
                missingEnums.forEach(e -> this.emitEnum(e, emitterContext));
            }
            missingSchemas = emitterContext.missingSchemas();
            missingEnums = emitterContext.missingEnums();
        } while (!missingSchemas.isEmpty() || !missingEnums.isEmpty());
    }
}
