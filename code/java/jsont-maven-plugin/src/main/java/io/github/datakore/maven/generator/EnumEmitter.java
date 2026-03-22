package io.github.datakore.maven.generator;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import org.apache.maven.plugin.logging.Log;

import javax.lang.model.element.Modifier;
import java.io.IOException;

public class EnumEmitter {
    private final GeneratorContext context;
    private final Log log;

    public EnumEmitter(GeneratorContext context, Log log) {
        this.context = context;
        this.log = log;
    }

    public void emit(EnumModel em) {
        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(getClassName(em.name()))
                .addModifiers(Modifier.PUBLIC);

        for (String value : em.values()) {
            enumBuilder.addEnumConstant(value);
        }

        JavaFile javaFile = JavaFile.builder(context.getPackageName(), enumBuilder.build())
                .build();

        try {
            javaFile.writeTo(context.getOutputDirectory());
        } catch (IOException e) {
            log.error("Failed to write enum class " + em.name(), e);
        }
    }

    private String getClassName(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
