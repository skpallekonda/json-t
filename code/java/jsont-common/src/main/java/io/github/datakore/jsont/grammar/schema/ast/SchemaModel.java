package io.github.datakore.jsont.grammar.schema.ast;

import java.util.List;
import java.util.stream.Collectors;

public final class SchemaModel {
    private final String name;
    private final List<FieldModel> fields;

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

    public List<String> referencedTypes() {
        return this.fields.stream()
                .filter(f -> f.getFieldType().isObject())
                .map(f -> f.getFieldType().type())
                .collect(Collectors.toList());
    }

    public List<String> referencedEnums() {
        return this.fields.stream()
                .filter(f -> f.getFieldType().isEnum())
                .map(f -> f.getFieldType().type())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        /**
         * User: {
         *             i32: id,
         *             str: username?(minLength=5,maxLength='10'),
         *             str: email?(minLength=8)
         *           }
         */
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(": {");
        StringBuilder values = new StringBuilder();
        for (FieldModel value : fields) {
            if (values.length() > 0) {
                values.append(",");
            }
            values.append(value);
        }
        sb.append(values);
        sb.append("}");
        return sb.toString();
    }

}
