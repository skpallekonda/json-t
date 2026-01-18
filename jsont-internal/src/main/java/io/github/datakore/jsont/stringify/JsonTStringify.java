package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.schema.ast.*;
import io.github.datakore.jsont.grammar.schema.coded.*;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.types.*;
import io.github.datakore.jsont.util.CollectionUtils;
import io.github.datakore.jsont.util.Constants;
import io.github.datakore.jsont.util.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class JsonTStringify {
    private final AdapterRegistry registry;
    private final BooleanEncodeDecoder booleanEncoder = new BooleanEncodeDecoder();
    private final StringEncodeDecoder stringEncoder = new StringEncodeDecoder();
    private final NumberEncodeDecoder numberEncoder = new NumberEncodeDecoder();
    private final DateEncodeDecoder dateEncoder = new DateEncodeDecoder();
    private final BinaryEncodeDecoder binEncoder = new BinaryEncodeDecoder();
    private final NamespaceT namespace;

    public JsonTStringify(AdapterRegistry registry, NamespaceT namespaceT) {
        this.namespace = namespaceT;
        this.registry = registry;
    }

    void writeSchema(String schema, Writer writer) throws IOException {
        NamespaceT ns = new NamespaceT(this.namespace.getBaseUrl());
        SchemaCatalog newCatalog = findRelevantCatalog(schema);
        ns.addCatalog(newCatalog);
        writer.write(ns.toString());
    }

    private SchemaCatalog findRelevantCatalog(String schemaName) {
        Set<String> schemas = new HashSet<>();
        schemas.addAll(findSchemasOf(schemaName));
        Set<String> enums = new HashSet<>();
        SchemaCatalog newCatalog = new SchemaCatalog();
        schemas.forEach(s -> {
            SchemaModel schema1 = namespace.findSchema(s);
            if (schema1 != null) {
                enums.addAll(findEnumsOf(schema1.name()));
                newCatalog.addSchema(schema1);
            }
        });
        enums.forEach(e -> {
            EnumModel enumModel = namespace.findEnum(e);
            if (enumModel != null) {
                newCatalog.addEnum(e, enumModel);
            }
        });
        return newCatalog;
    }

    private List<String> findEnumsOf(String simpleName) {
        SchemaModel schema = namespace.findSchema(simpleName);
        if (schema != null) {
            List<String> types = schema.referencedEnums();
            return types;
        }
        return Collections.emptyList();
    }

    private List<String> findSchemasOf(String schemaName) {
        SchemaModel schema = namespace.findSchema(schemaName);
        if (schema != null) {
            List<String> types = new ArrayList<>();
            types.add(schemaName);
            schema.referencedTypes().forEach(t -> types.addAll(findSchemasOf(t)));
            return types;
        }
        return Collections.emptyList();
    }

    <T> void stringify(T object, Writer writer) throws IOException {
        if (object == null) {
            return;
        }
        String schemaName = object.getClass().getSimpleName();
        SchemaModel schema = namespace.findSchema(schemaName);
        if (schema == null) {
            throw new SchemaException("Data schema not found for type " + schemaName + ", please supply a valid catalog/schema");
        }
        emitObjectData(object, writer, null);
    }


    private <T> void emitListData(List<T> listObject, Writer writer, FieldModel fieldModel) throws IOException {
        if (listObject == null || listObject.isEmpty()) {
            if (fieldModel != null) writer.write("[]");
        } else {
            writer.write("[");
            int size = listObject.size();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    writer.write(",");
                }
                T object = listObject.get(i);
                emitObjectData(object, writer, fieldModel);
            }
            writer.write("]");
        }
    }

    private <T> void emitObjectData(T object, Writer writer, FieldModel fieldModel) throws IOException {
        if (object == null) {
            writer.write("{}");
            return;
        }

        // If fieldModel is provided, we might be in a nested context (Array of Scalars or Objects)
        // If it's a scalar array, we should just write the value.
        if (fieldModel != null && fieldModel.getFieldType() instanceof ScalarType) {
            writer.write(handleScalarType(object, fieldModel));
            return;
        }

        String schemaName = object.getClass().getSimpleName();
        SchemaModel schema = namespace.findSchema(schemaName);

        // Fallback for unknown types or scalars in mixed contexts (though JsonT is strict)
        if (schema == null && fieldModel != null) {
            writer.write(StringUtils.wrapInQuotes(object.toString()));
            return;
        }

        SchemaAdapter<?> adapter = registry.resolve(schemaName);
        writer.write("{");
        for (int i = 0; i < schema.fieldCount(); i++) {
            if (i > 0) {
                writer.write(",");
            }
            FieldModel fm = schema.fields().get(i);
            Object fieldValue = adapter.get(object, fm.getFieldName());

            // Constraint Checking
            validateConstraints(fm, fieldValue);

            if (fieldValue == null || fm.getFieldType() instanceof NullType) {
                writer.write("null");
            } else if (fm.getFieldType() instanceof UnspecifiedType) {
                writer.write(Constants.UNSPECIFIED_TYPE);
            } else if (fm.getFieldType() instanceof ObjectType) {
                emitObjectData(fieldValue, writer, fm);
            } else if (fm.getFieldType() instanceof ArrayType) {
                // For arrays, we need to pass the field model to handle scalar arrays correctly
                emitListData(CollectionUtils.toObjectList(fieldValue), writer, fm);
            } else if (fm.getFieldType() instanceof ScalarType) {
                writer.write(handleScalarType(fieldValue, fm));
            } else if (fm.getFieldType() instanceof EnumType) {
                writer.write(fieldValue.toString());
            }
        }
        writer.write("}");
    }

    private void validateConstraints(FieldModel fm, Object fieldValue) {
        if (fm.getConstraints() != null && !fm.getConstraints().isEmpty() && fieldValue != null) {
            List<String> errors = new ArrayList<>();
            for (FieldConstraint constraint : fm.getConstraints()) {
                String error = constraint.checkConstraint(fieldValue);
                if (!StringUtils.isBlank(error)) {
                    errors.add(error);
                }
            }
            if (!errors.isEmpty()) {
                // TODO throw new DataValidationException(fm, fieldValue, errors);
//                System.err.println(errors);
            }
        }
    }


    private String handleScalarType(Object fieldValue, FieldModel fm) {
        ScalarType scalarType = (ScalarType) fm.getFieldType();
        JsonBaseType jsonBaseType = scalarType.elementType();
        String lexerType = jsonBaseType.lexerValueType();
        switch (lexerType) {
            case "Boolean":
                return booleanEncoder.encode(fm, fieldValue);
            case "String":
                return stringEncoder.encode(fm, fieldValue);
            case "Number":
                return numberEncoder.encode(fm, fieldValue);
            case "Date":
                return dateEncoder.encode(fm, fieldValue);
            case "Binary":
                return binEncoder.encode(fm, fieldValue);
            default:
                return fieldValue.toString();
        }
    }

    void writeDataBlockPrefix(String dataSchema, Writer writer) throws IOException {
        writer.write("\n{");
        writer.write("data-schema:");
        writer.write(dataSchema);
        writer.write(",data: [");
    }

    void writeDataBlockSuffix(Writer writer) throws IOException {
        writer.write("\n]\n}");
    }
}
