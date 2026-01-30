package io.github.datakore.maven.generator;

import java.io.File;
import java.util.Map;

public class GeneratorContext {
    private final String packageName;
    private final File outputDirectory;
    private final Map<String, String> temporalTypeMapping;
    private final String arrayHandler;

    public GeneratorContext(String packageName, File outputDirectory, Map<String, String> temporalTypeMapping, String arrayHandler) {
        this.packageName = packageName;
        this.outputDirectory = outputDirectory;
        this.temporalTypeMapping = temporalTypeMapping;
        this.arrayHandler = arrayHandler;
    }

    public String getPackageName() {
        return packageName;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public Map<String, String> getTemporalTypeMapping() {
        return temporalTypeMapping;
    }

    public String getArrayHandler() {
        return arrayHandler;
    }
}
