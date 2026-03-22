package io.github.datakore.jsont.util;

import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

public class ChunkContext {
    private final NamespaceT namespace;
    private final SchemaModel dataSchema;
    private final long dataStartOffset;

    public ChunkContext(NamespaceT namespace, SchemaModel dataSchema, long dataStartOffset) {
        this.namespace = namespace;
        this.dataSchema = dataSchema;
        this.dataStartOffset = dataStartOffset;
    }

    public NamespaceT getNamespace() {
        return namespace;
    }

    public SchemaModel getDataSchema() {
        return dataSchema;
    }

    public long getDataStartOffset() {
        return dataStartOffset;
    }
}
