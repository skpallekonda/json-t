package org.jsont.execution;

import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.raw.EnumNode;
import org.jsont.grammar.schema.raw.SchemaNode;

import java.util.List;

public class JsonTNode {
    private final List<SchemaNode> schemaNodeList;
    private final List<EnumNode> enumDefs;
    private final SchemaNode dataSchema;
    private final String dataSchemaName;
    private final List<RowNode> dataRows;


    private JsonTNode(JsonTNodeBuilder builder) {
        this.schemaNodeList = builder.schemaNodeList;
        this.enumDefs = builder.enumDefs;
        this.dataSchema = builder.dataSchema;
        this.dataSchemaName = builder.dataSchemaName;
        this.dataRows = builder.dataRows;
    }

    public static JsonTNodeBuilder builder() {
        return new JsonTNodeBuilder();
    }

    public List<SchemaNode> getSchemaNodeList() {
        return schemaNodeList;
    }

    public List<EnumNode> getEnumDefs() {
        return enumDefs;
    }

    public SchemaNode getDataSchema() {
        return dataSchema;
    }

    public List<RowNode> getDataRows() {
        return dataRows;
    }

    public String getDataSchemaName() {
        return dataSchemaName;
    }

    static class JsonTNodeBuilder {
        private List<SchemaNode> schemaNodeList;
        private List<EnumNode> enumDefs;
        private SchemaNode dataSchema;
        private String dataSchemaName;
        private List<RowNode> dataRows;

        public JsonTNodeBuilder schemaNodeList(List<SchemaNode> schemaNodeList) {
            this.schemaNodeList = schemaNodeList;
            return this;
        }

        public JsonTNodeBuilder enumDefs(List<EnumNode> enumDefs) {
            this.enumDefs = enumDefs;
            return this;
        }

        public JsonTNodeBuilder dataSchema(SchemaNode dataSchema) {
            this.dataSchema = dataSchema;
            return this;
        }

        public JsonTNodeBuilder dataSchemaName(String dataSchemaName) {
            this.dataSchemaName = dataSchemaName;
            return this;
        }

        public JsonTNodeBuilder dataRows(List<RowNode> dataRows) {
            this.dataRows = dataRows;
            return this;
        }

        public JsonTNode build() {
            return new JsonTNode(this);
        }
    }

}
