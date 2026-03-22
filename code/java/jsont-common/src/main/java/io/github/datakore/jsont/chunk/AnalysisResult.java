package io.github.datakore.jsont.chunk;

public class AnalysisResult {
    public enum FileVariant {
        SCHEMA_ONLY, FULL_DOCUMENT, DATA_BLOCK, FRAGMENT;

        public static boolean containsSchema(FileVariant variant) {
            return variant == SCHEMA_ONLY || variant == FULL_DOCUMENT;
        }
    }

    private final FileVariant variant;
    private final String namespaceContent;
    private final String dataSchemaName;
    private final long dataStartOffset;

    public AnalysisResult(FileVariant variant, String namespaceContent, String dataSchema, long dataRowStartOffset) {
        this.variant = variant;
        this.namespaceContent = namespaceContent;
        this.dataSchemaName = dataSchema;
        this.dataStartOffset = dataRowStartOffset;
    }

    public FileVariant getVariant() {
        return variant;
    }

    public String getNamespaceContent() {
        return namespaceContent;
    }

    public String getDataSchemaName() {
        return dataSchemaName;
    }

    public long getDataStartOffset() {
        return dataStartOffset;
    }
}
