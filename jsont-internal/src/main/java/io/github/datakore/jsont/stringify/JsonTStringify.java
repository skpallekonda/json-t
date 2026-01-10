package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.JsonTType;
import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.grammar.data.JsontScalarType;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.types.*;
import io.github.datakore.jsont.grammar.types.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class JsonTStringify {
    private final AdapterRegistry registry;
    private final SchemaCatalog catalog;

    public JsonTStringify(AdapterRegistry registry, SchemaCatalog catalog) {
        this.catalog = catalog;
        this.registry = registry;
    }

    public <T extends JsonTType> String stringifySchema(Class<T> type) {
//        if (catalog != null && catalog.getSchema(type.getSimpleName()) != null) {
//            return catalog.toString();
//        }
        Map<String, String> schemaContent = new LinkedHashMap<>();
        emitSchema(schemaContent, type.getSimpleName());
        return emitSchemas(schemaContent);
    }

    private String emitSchemas(Map<String, String> schemaContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
                .append("\tschemas: {\n")
                .append(schemaContent.keySet().stream().map(k -> schemaContent.get(k)).collect(Collectors.joining(",")))
                .append("\n\t}\n")
                .append("}");
        return sb.toString();
    }

    private void emitSchema(Map<String, String> list, String schemaName) {
        if (list.containsKey(schemaName)) {
            return;
        }
        SchemaAdapter<?> adapter = registry.resolve(schemaName);
        list.putIfAbsent(schemaName, adapter.toSchemaDef());
        List<Class<?>> children = adapter.childrenTypes();
        if (children != null && !children.isEmpty()) {
            children.stream().forEach(clz -> emitSchema(list, clz.getSimpleName()));
        }
    }

    public <T extends JsonTType> String stringifyData(List<T> listObject, StringifyMode mode) {
        if (listObject == null || listObject.isEmpty()) {
            return "";
        }
        String schemaName = listObject.get(0).getClass().getSimpleName();
        StringBuilder sb = new StringBuilder();
        if (mode == StringifyMode.SCHEMA_AND_DATA) {
            Map<String, String> schemasContentMap = new LinkedHashMap<>();
            emitSchema(schemasContentMap, schemaName);
            sb.append(emitSchemas(schemasContentMap));
        }
        sb.append(emitDataSection(listObject, schemaName));
        return sb.toString();
    }

    private <T extends JsonTType> String emitDataSection(List<T> listObject, String schemaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("data-schema:").append(schemaName);
        sb.append(",data: [");
        emitListData(listObject, sb);
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private <T extends JsonTType> void emitListData(List<T> listObject, StringBuilder sb) {
        String schemaName = listObject.get(0).getClass().getSimpleName();
        AtomicInteger rowCounter = new AtomicInteger();
        Map<String, AtomicInteger> counterMap = new LinkedHashMap<>();
        counterMap.putIfAbsent(String.format("%s-", schemaName), rowCounter);
        listObject.stream().forEach(obj -> emitData(counterMap, schemaName, obj, sb, ""));
    }

    private void emitData(Map<String, AtomicInteger> counter, String schema, JsonTType object, StringBuilder sb, String fieldName) {
        SchemaModel model = catalog.getSchema(schema);
        SchemaAdapter<?> adapter = registry.resolve(schema);
        int rowIndex = getCounter(counter, schema, fieldName);
        if (getCounter(counter, schema, fieldName) > 1) {
            sb.append("\n,");
        }
        sb.append("{");
        StringBuilder nsb = new StringBuilder();
        for (int i = 0; i < model.fieldCount(); i++) {
            FieldModel fm = model.fields().get(i);
            if (nsb.length() > 0) {
                nsb.append(",");
            }
            if (fm.type() instanceof NullType) {
                nsb.append("null");
            } else if (fm.type() instanceof ObjectType) {
                ObjectType objectType = (ObjectType) fm.type();
                String fieldName1 = String.format("%d%s", rowIndex, fm.name());
                String key = String.format("%s-%s", objectType.schema(), fieldName1);
                counter.putIfAbsent(key, new AtomicInteger());
                JsonTType element = (JsonTType) adapter.get(object, fm.name());
                emitData(counter, objectType.schema(), element, nsb, fieldName1);
            } else if (fm.type() instanceof ArrayType) {
                ArrayType at = (ArrayType) fm.type();
                Object o = adapter.get(object, fm.name());
                nsb.append("[");
                if (at.elementType() instanceof ObjectType) {
                    emitListData((List<JsonTType>) o, nsb);
                } else if (at.elementType() instanceof ScalarType) {
                    StringBuilder arrB = new StringBuilder();
                    if (o instanceof List || o instanceof Set || o instanceof Collection) {
                        handleCollection(at.elementType(), o, nsb);
                    } else if (o instanceof Object[]) {
                        handleArrays(at.elementType(), o, nsb);
                    }
                }
                nsb.append("]");
            } else if (fm.type() instanceof ScalarType) {
                Object o = adapter.get(object, fm.name());
                Object oo = handleScalar(((ScalarType) fm.type()).valueType(), o);
                nsb.append(oo);
            }
        }
        sb.append(nsb);
        sb.append("}");
    }

    private int getCounter(Map<String, AtomicInteger> counter, String schema, String fieldName) {
        String key = String.format("%s-%s", schema, fieldName);
        return counter.get(key).getAndIncrement();
    }

    private Object handleScalar(JsontScalarType type, Object o) {
        if (o == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case TIME:
            case STRING:
            case ZIP:
            case ZIP5:
            case ZIP6:
            case UUID:
            case URI:
            case EMAIL:
                return sb.append("\"").append(o).append("\"").toString();
            default:
                return sb.append(o).toString();
        }
    }

    private void handleArrays(ValueType valueType, Object o, StringBuilder nsb) {
        List<Object> finalList = List.of((Object[]) o);
        StringBuilder arrB = new StringBuilder();
        for (Object oo : (List) finalList) {
            if (arrB.length() > 0) {
                arrB.append(",");
            }
            arrB.append(handleScalar(valueType.valueType(), oo));
        }
        nsb.append(arrB);
    }

    private void handleCollection(ValueType valueType, Object o, StringBuilder nsb) {
        List<Object> finalList = new ArrayList<>();
        finalList.addAll((Collection<?>) o);
        StringBuilder arrB = new StringBuilder();
        for (Object oo : (List) finalList) {
            if (arrB.length() > 0) {
                arrB.append(",");
            }
            arrB.append(handleScalar(valueType.valueType(), oo));
        }
        nsb.append(arrB);
    }


}
