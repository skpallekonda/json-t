package io.github.datakore.maven.generator;

import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.file.JsonTStructureAnalyzer;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SchemaValidator {
    private final Log log;

    public SchemaValidator(Log log) {
        this.log = log;
    }

    public void validate(List<File> schemaFiles) throws MojoExecutionException {
        Set<String> definedSchemas = new HashSet<>();
        Set<String> definedEnums = new HashSet<>();

        for (File schemaFile : schemaFiles) {
            try {
                NamespaceT ns = extractNamespace(schemaFile);
                for (SchemaCatalog catalog : ns.getCatalogs()) {
                    for (SchemaModel sm : catalog.resolvedSchemaModels()) {
                        if (!definedSchemas.add(sm.name())) {
                            throw new MojoExecutionException("Duplicate schema name found: '" + sm.name() + "' in file " + schemaFile.getName());
                        }
                    }
                    for (EnumModel em : catalog.resolvedEnumModels()) {
                        if (!definedEnums.add(em.name())) {
                            throw new MojoExecutionException("Duplicate enum name found: '" + em.name() + "' in file " + schemaFile.getName());
                        }
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to validate schema file: " + schemaFile.getAbsolutePath(), e);
            }
        }
        log.info("Validation successful: Found " + definedSchemas.size() + " unique schemas and " + definedEnums.size() + " unique enums.");
    }

    private NamespaceT extractNamespace(File schemaFile) throws Exception {
        JsonTStructureAnalyzer jsonTStructureAnalyzer = new JsonTStructureAnalyzer();
        AnalysisResult result = jsonTStructureAnalyzer.analyze(schemaFile.toPath());
        if (result == null || !AnalysisResult.FileVariant.containsSchema(result.getVariant())) {
            log.warn("File does not contain a schema: " + schemaFile.getAbsolutePath());
            return new NamespaceT(null); // Return an empty namespace
        }
        NamespaceT ns = ParserExecutor.validateSchema(result, new DefaultErrorCollector());
        if (ns == null) {
            throw new MojoExecutionException("Failed to parse namespace from " + schemaFile.getAbsolutePath());
        }
        return ns;
    }
}
