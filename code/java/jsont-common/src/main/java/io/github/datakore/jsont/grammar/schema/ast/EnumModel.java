package io.github.datakore.jsont.grammar.schema.ast;

import java.util.Set;

public class EnumModel {
    private final String enumName;
    private final Set<String> enumValues;

    public EnumModel(String enumName, Set<String> enumValues) {
        this.enumName = enumName;
        this.enumValues = enumValues;
    }

    public Object resolve(String symbol) {
        for (String value : enumValues) {
            if (value.equals(symbol)) {
                return value;
            }
        }
        return null;
    }

    public String name() {
        return enumName;
    }

    public Set<String> values() {
        return enumValues;
    }

    @Override
    public String toString() {
        /**
         * Status: [ ACTIVE, INACTIVE, SUSPENDED ]
         */
        StringBuilder sb = new StringBuilder();
        sb.append(enumName);
        sb.append(": [");
        StringBuilder values = new StringBuilder();
        for (String value : enumValues) {
            if (values.length() > 0) {
                values.append(",");
            }
            values.append(value);
        }
        sb.append(values);
        sb.append("]");
        return sb.toString();
    }
}
