package io.github.datakore.jsont.datagen;

import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

public interface DataGen<T> {
    String[] fieldNames();

    FieldConstraint[] fieldConstraints(String fieldName);

    T generate();
}
