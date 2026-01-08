package org.jsont.grammar.types;

public class ValueHolder {

    private final Object value;
    private final ValueType valueType;

    public ValueHolder(ValueType valueType, Object value) {
        this.valueType = valueType;
        this.value = value;
    }

    public Object value() {
        return value;
    }

    public ValueType valueType() {
        return valueType;
    }

    public void validate() {
        valueType.validate(value);
    }
}
