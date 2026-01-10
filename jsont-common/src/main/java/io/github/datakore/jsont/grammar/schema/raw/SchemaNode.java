package io.github.datakore.jsont.grammar.schema.raw;

import java.util.List;

public class SchemaNode {
    private String name;
    private List<FieldNode> fields;

    public List<FieldNode> getFields() {
        return fields;
    }

    public void setFields(List<FieldNode> fields) {
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
