package io.github.datakore.jsont.grammar.data;

public class ObjectNode implements ValueNode {

    private final RowNode row;

    public ObjectNode(RowNode row) {
        this.row = row;
    }

    public RowNode raw() {
        return this.row;
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.OBJECT;
    }
}
