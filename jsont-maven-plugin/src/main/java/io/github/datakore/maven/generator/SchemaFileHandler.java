package io.github.datakore.maven.generator;

import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.file.JsonTStructureAnalyzer;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class SchemaFileHandler {
    private final File schemaFile;
    private final CatalogHandler catalogHandler;
    private final Log log;

    public SchemaFileHandler(File schemaFile, GeneratorContext context, Log log) {
        this.schemaFile = schemaFile;
        this.log = log;
        this.catalogHandler = new CatalogHandler(context, log);
    }

    public void process() throws MojoExecutionException, MojoFailureException {
        NamespaceT ns = extractNamespace();
        for (SchemaCatalog catalog : ns.getCatalogs()) {
            catalogHandler.handle(catalog);
        }
    }

    private NamespaceT extractNamespace() throws MojoExecutionException, MojoFailureException {
        JsonTStructureAnalyzer jsonTStructureAnalyzer = new JsonTStructureAnalyzer();
        try {
            AnalysisResult result = jsonTStructureAnalyzer.analyze(schemaFile.toPath());
            if (result == null || !AnalysisResult.FileVariant.containsSchema(result.getVariant())) {
                throw new MojoFailureException("The file " + schemaFile.getAbsolutePath() + " does not contain a schema");
            }
            NamespaceT ns = ParserExecutor.validateSchema(result, new DefaultErrorCollector());

            assert ns != null;
            return ns;
        } catch (Exception e) {
            throw new MojoExecutionException("Error in analyzing namespace file", e);
        }
    }
}
