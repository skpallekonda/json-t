package io.github.datakore.maven.generator;

import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public class CatalogHandler {
    private final EnumEmitter enumEmitter;
    private final ModelEmitter modelEmitter;
    private final AdapterEmitter adapterEmitter;
    private final Log log;

    public CatalogHandler(GeneratorContext context, Log log) {
        this.log = log;
        TypeResolver typeResolver = new TypeResolver(context);
        this.enumEmitter = new EnumEmitter(context, log);
        this.modelEmitter = new ModelEmitter(context, log, typeResolver);
        this.adapterEmitter = new AdapterEmitter(context, log, typeResolver);
    }

    public void handle(SchemaCatalog catalog) throws MojoExecutionException {
        if (catalog.unresolvedSchemaModels() != null && !catalog.unresolvedSchemaModels().isEmpty()) {
            throw new MojoExecutionException("Cannot resolve unresolved schema models");
        }
        for (SchemaModel sm : catalog.resolvedSchemaModels()) {
            modelEmitter.emit(sm);
            adapterEmitter.emit(sm);
        }
        for (EnumModel em : catalog.resolvedEnumModels()) {
            enumEmitter.emit(em);
        }
    }
}
