package io.github.datakore.jsont.grammar.schema.ast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class NamespaceT {
    private final URL baseUrl;
    private final List<SchemaCatalog> catalogs = new ArrayList<>();

    public NamespaceT(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    public URL getBaseUrl() {
        return baseUrl;
    }

    public List<SchemaCatalog> getCatalogs() {
        return catalogs;
    }

    public void addCatalog(SchemaCatalog catalog) {
        this.catalogs.add(catalog);
    }

    @Override
    public String toString() {
        /**
         * {
         *   namespace: {
         *     baseUrl: "https://api.datakore.com/v1",
         *     catalogs: [
         *     ]
         */
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("namespace: {");
        sb.append("baseUrl: \"").append(baseUrl == null ? "" : baseUrl.toString()).append("\",");
        sb.append("catalogs: [");
        StringBuilder catalogs = new StringBuilder();
        for (SchemaCatalog catalog : this.catalogs) {
            if (catalogs.length() > 0) {
                catalogs.append(",");
            }
            catalogs.append(catalog);
        }
        sb.append(catalogs);
        sb.append("]");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    public SchemaModel findSchema(String schemaName) {
        for (SchemaCatalog catalog : catalogs) {
            SchemaModel model = catalog.getSchema(schemaName);
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    public EnumModel findEnum(String simpleName) {
        for (SchemaCatalog catalog : catalogs) {
            EnumModel model = catalog.getEnum(simpleName);
            if (model != null) {
                return model;
            }
        }
        return null;
    }
}
