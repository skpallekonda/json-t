package io.github.datakore.jsont.grammar.data;

import java.util.Map;

public class RowNode implements ValueNode {
    private final int index; // row number (0-based)
    private final Map<String, Object> nodeMap; // positional values

    public RowNode(int index, Map<String, Object> values) {
        this.index = index;
        this.nodeMap = values;
    }

    public int getRowIndex() {
        return index;
    }


    public Map<String, Object> values() {
        return nodeMap;
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.OBJECT;
    }
}
