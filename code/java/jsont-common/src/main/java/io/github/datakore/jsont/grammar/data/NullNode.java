package io.github.datakore.jsont.grammar.data;

public class NullNode implements ValueNode {

    private final String text;
    private final ValueNodeKind kind;

    public NullNode(String text, ValueNodeKind kind) {
        this.text = text;
        this.kind = kind;
    }

    @Override
    public ValueNodeKind kind() {
        return kind;
    }

    public String getText() {
        return text;
    }
}
