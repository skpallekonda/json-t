package io.github.datakore.maven;

import io.github.datakore.maven.generator.GeneratorContext;
import io.github.datakore.maven.generator.SchemaFileHandler;
import io.github.datakore.maven.generator.SchemaValidator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "generate-models", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JsonTGeneratorMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "namespaceFile")
    private File namespaceFile;

    @Parameter(property = "schemaFolder")
    private File schemaFolder;

    @Parameter(property = "packageName", required = true)
    private String packageName;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/jsont")
    private File outputDirectory;

    @Parameter
    private Map<String, String> temporalTypeMapping;

    @Parameter(defaultValue = "List")
    private String arrayHandler;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<File> schemaFiles = discoverSchemaFiles();
        if (schemaFiles.isEmpty()) {
            getLog().info("No schema files found to process.");
            return;
        }

        GeneratorContext context = new GeneratorContext(packageName, outputDirectory, temporalTypeMapping, arrayHandler);
        
        // Validate for duplicates before processing
        SchemaValidator validator = new SchemaValidator(getLog());
        validator.validate(schemaFiles);

        for (File file : schemaFiles) {
            SchemaFileHandler handler = new SchemaFileHandler(file, context, getLog());
            handler.process();
        }

        // Add generated source root to Maven project
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }

    private List<File> discoverSchemaFiles() throws MojoExecutionException {
        if (schemaFolder != null) {
            if (!schemaFolder.isDirectory()) {
                throw new MojoExecutionException("schemaFolder path is not a directory: " + schemaFolder.getAbsolutePath());
            }
            try (Stream<Path> paths = Files.walk(schemaFolder.toPath())) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".jsont"))
                        .map(Path::toFile)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new MojoExecutionException("Error scanning schemaFolder: " + schemaFolder.getAbsolutePath(), e);
            }
        } else if (namespaceFile != null) {
            return Collections.singletonList(namespaceFile);
        }
        return new ArrayList<>();
    }
}
