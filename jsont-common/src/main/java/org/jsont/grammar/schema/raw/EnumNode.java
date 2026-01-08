package org.jsont.grammar.schema.raw;


import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.data.ValueNodeKind;

import java.util.List;

public class EnumNode implements ValueNode {
    private final String name;
    private final List<String> values;

    public EnumNode(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public ValueNodeKind kind() {
        return ValueNodeKind.ENUM;
    }
}
