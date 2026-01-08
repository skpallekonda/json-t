package org.jsont.adapters;

import org.jsont.JsonTType;
import org.jsont.io.JsonTWriter;

import java.util.List;

public interface SchemaAdapter<T extends JsonTType> {

    String toSchemaDef();

    List<Class<?>> childrenTypes();

    Class<T> logicalType();

    T createTarget();

    void writeObject(Object target, JsonTWriter writer);

    void set(Object target, String fieldName, Object valuee);

    Object get(Object target, String fieldName);

}
