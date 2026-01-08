package org.jsont.processor.model;

import java.util.List;

public final class AnnoTypeModel {
    private final String packageName;
    private final String modelName;
    private final String schemaName;
    private final List<AnnoFieldModel> fields;

    public AnnoTypeModel(String packageName, String modelName, String schemaName, List<AnnoFieldModel> fields) {
        this.packageName = packageName;
        this.modelName = modelName;
        this.schemaName = schemaName;
        this.fields = fields;
    }

    public boolean hasAnnotatedFields() {
        return fields != null && !fields.isEmpty();
    }

    public String packageName() {
        return packageName;
    }

    public String modelName() {
        return modelName;
    }


    public String schemaName() {
        return schemaName;
    }

    public List<AnnoFieldModel> fields() {
        return fields;
    }
}
