package org.jsont.grammar.data;

import java.util.List;

public class ArrayNode implements ValueNode {

    private final List<ValueNode> elements;

    public ArrayNode(List<ValueNode> elements) {
        this.elements = elements;
    }

    public List<ValueNode> elements() {
        return elements;
    }

    public ValueNode get(int index) {
        return elements.get(index);
    }

    public void add(ValueNode element) {
        elements.add(element);
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.ARRAY;
    }

}
