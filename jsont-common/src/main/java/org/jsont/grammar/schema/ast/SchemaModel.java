package org.jsont.grammar.schema.ast;

import java.util.List;

public final class SchemaModel {

    private final String name;
    private final List<FieldModel> fields;
    private Class<?> targetClass;

    public SchemaModel(String name, List<FieldModel> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String name() {
        return name;
    }

    public List<FieldModel> fields() {
        return fields;
    }

    public int fieldCount() {
        return fields == null ? 0 : fields.size();
    }

    public void bindTargetClass(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }
}
