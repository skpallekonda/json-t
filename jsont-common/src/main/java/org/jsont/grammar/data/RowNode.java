package org.jsont.grammar.data;

import java.util.Map;

public class RowNode implements ValueNode {
    private final int index; // row number (0-based)
    private final Map<String, ValueNode> nodeMap; // positional values

    public RowNode(int index, Map<String, ValueNode> values) {
        this.index = index;
        this.nodeMap = values;
    }

    public int getRowIndex() {
        return index;
    }

    public Map<String, ValueNode> getNodeMap() {
        return nodeMap;
    }

    public int index() {
        return index;
    }

    public Map<String, ValueNode> values() {
        return nodeMap;
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.OBJECT;
    }
}
