package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;

public interface ValueType {
    String fieldName();

    int colPosition();

    String type();

    ValueNodeKind nodeKind();

    boolean isObject();

    boolean isArray();

    boolean isEnum();
}
