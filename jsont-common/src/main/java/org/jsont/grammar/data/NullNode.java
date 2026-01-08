package org.jsont.grammar.data;

public class NullNode implements ValueNode {

    public NullNode() {
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.NULL;
    }

}
