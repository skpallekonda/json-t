package org.jsont.grammar.schema.ast;

import java.util.Collections;
import java.util.Map;

public class ConstraintModel {
    private final String name;
    private final Map<String, Object> arguments;

    public ConstraintModel(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments != null ? arguments : Collections.emptyMap();
    }

    public String name() {
        return name;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }
}
