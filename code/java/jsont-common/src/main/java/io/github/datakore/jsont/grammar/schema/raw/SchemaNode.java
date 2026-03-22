package io.github.datakore.jsont.grammar.schema.raw;

import java.util.ArrayList;
import java.util.List;

public class SchemaNode {
    private final String name;
    private final List<FieldNode> fields;

    public SchemaNode(String name) {
        this.name = name;
        this.fields = new ArrayList<>();
    }

    public List<FieldNode> getFields() {
        return fields;
    }

    public void addField(FieldNode field) {
        this.fields.add(field);
    }

    public String getName() {
        return name;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (this == obj) {
            return true;
        } else if (!(obj instanceof SchemaNode)) {
            return false;
        }
        SchemaNode that = (SchemaNode) obj;
        if (this.name == null || that.name == null) {
            return false;
        }
        return this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 17 * this.name.hashCode();
    }

}
