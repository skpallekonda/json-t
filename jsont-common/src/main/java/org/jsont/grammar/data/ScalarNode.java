package org.jsont.grammar.data;

public final class ScalarNode implements ValueNode {

    private final String raw;
    private final ScalarNodeKind elementType;


    public ScalarNode(String raw, ScalarNodeKind elementType) {
        this.raw = raw;
        this.elementType = elementType;
    }

    public String raw() {
        return raw;
    }

    public ScalarNodeKind scalarType() {
        return this.elementType;
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.SCALAR;
    }
}
