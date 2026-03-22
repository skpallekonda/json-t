package io.github.datakore.jsont.grammar.types;

public abstract class BaseType implements ValueType {

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isObject() || isEnum()) {
            sb.append("<");
        }
        sb.append(type());
        if (isObject() || isEnum()) {
            sb.append(">");
        }
        if (isArray()) {
            sb.append("[]");
        }
        return sb.toString();
    }
}
