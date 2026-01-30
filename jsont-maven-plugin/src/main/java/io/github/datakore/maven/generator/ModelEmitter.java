package io.github.datakore.maven.generator;

import com.squareup.javapoet.*;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import org.apache.maven.plugin.logging.Log;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModelEmitter {
    private final GeneratorContext context;
    private final Log log;
    private final TypeResolver typeResolver;

    public ModelEmitter(GeneratorContext context, Log log, TypeResolver typeResolver) {
        this.context = context;
        this.log = log;
        this.typeResolver = typeResolver;
    }

    public void emit(SchemaModel sm) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(typeResolver.getClassName(sm.name()))
                .addModifiers(Modifier.PUBLIC);

        List<FieldSpec> fields = new ArrayList<>();
        List<MethodSpec> methods = new ArrayList<>();

        // Constructor with all fields
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        // No-arg constructor
        MethodSpec noArgConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build();
        methods.add(noArgConstructor);

        for (FieldModel fm : sm.fields()) {
            String fieldName = fm.getFieldName();
            TypeName typeName = typeResolver.getTypeName(fm.getFieldType());

            // Field
            FieldSpec field = FieldSpec.builder(typeName, fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    .build();
            fields.add(field);

            // Constructor parameter and assignment
            constructorBuilder.addParameter(typeName, fieldName);
            constructorBuilder.addStatement("this.$N = $N", fieldName, fieldName);

            // Getter
            String getterName = "get" + typeResolver.capitalize(fieldName);
            if (TypeName.BOOLEAN.equals(typeName) && fieldName.startsWith("is")) {
                getterName = fieldName;
            }

            MethodSpec getter = MethodSpec.methodBuilder(getterName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(typeName)
                    .addStatement("return $N", fieldName)
                    .build();
            methods.add(getter);

            // Setter
            String setterName = "set" + typeResolver.capitalize(fieldName);
            MethodSpec setter = MethodSpec.methodBuilder(setterName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(typeName, fieldName)
                    .addStatement("this.$N = $N", fieldName, fieldName)
                    .build();
            methods.add(setter);
        }

        classBuilder.addFields(fields);
        classBuilder.addMethod(constructorBuilder.build());
        classBuilder.addMethods(methods);

        JavaFile javaFile = JavaFile.builder(context.getPackageName(), classBuilder.build())
                .build();

        try {
            javaFile.writeTo(context.getOutputDirectory());
        } catch (IOException e) {
            log.error("Failed to write model class " + sm.name(), e);
        }
    }
}
