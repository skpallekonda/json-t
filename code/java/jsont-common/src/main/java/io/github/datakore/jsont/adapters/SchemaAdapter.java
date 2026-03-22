package io.github.datakore.jsont.adapters;

public interface SchemaAdapter<T> {
    Class<T> logicalType();

    T createTarget();

    void set(Object target, String fieldName, Object valuee);

    Object get(Object target, String fieldName);

}
