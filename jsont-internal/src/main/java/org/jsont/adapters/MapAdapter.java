package org.jsont.adapters;

import org.jsont.io.JsonTWriter;

import java.util.List;

public class MapAdapter implements SchemaAdapter<JsonTMap<String, Object>> {

    @Override
    public String toSchemaDef() {
        return "";
    }

    @Override
    public List<Class<?>> childrenTypes() {
        return List.of();
    }

    @Override
    public Class<JsonTMap<String, Object>> logicalType() {
        return null;
    }

    @Override
    public JsonTMap<String, Object> createTarget() {
        return null;
    }

    @Override
    public void writeObject(Object target, JsonTWriter writer) {

    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {

    }

    @Override
    public Object get(Object target, String fieldName) {
        return null;
    }
}
