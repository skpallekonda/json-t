package io.github.datakore.maven.generator;

import com.squareup.javapoet.*;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.schema.coded.CollectionAdapter;
import io.github.datakore.jsont.grammar.schema.coded.CollectionAdapterRegistry;
import io.github.datakore.jsont.grammar.schema.coded.TemporalAdapter;
import io.github.datakore.jsont.grammar.schema.coded.TemporalAdapterRegistry;
import io.github.datakore.jsont.grammar.types.ArrayType;
import io.github.datakore.jsont.grammar.types.EnumType;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.util.CollectionUtils;
import org.apache.maven.plugin.logging.Log;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.time.temporal.TemporalAccessor;

public class AdapterEmitter {
    private final GeneratorContext context;
    private final Log log;
    private final TypeResolver typeResolver;

    public AdapterEmitter(GeneratorContext context, Log log, TypeResolver typeResolver) {
        this.context = context;
        this.log = log;
        this.typeResolver = typeResolver;
    }

    public void emit(SchemaModel sm) {
        String modelClassName = typeResolver.getClassName(sm.name());
        ClassName modelType = ClassName.get(context.getPackageName(), modelClassName);
        String adapterClassName = modelClassName + "Adapter";
        String adapterPackage = context.getPackageName() + ".adapters";

        MethodSpec.Builder setMethodBuilder = MethodSpec.methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "target")
                .addParameter(String.class, "fieldName")
                .addParameter(Object.class, "value")
                .addStatement("$T entity = ($T) target", modelType, modelType);

        CodeBlock.Builder setSwitchBuilder = CodeBlock.builder().beginControlFlow("switch (fieldName)");

        MethodSpec.Builder getMethodBuilder = MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "target")
                .addParameter(String.class, "fieldName")
                .returns(Object.class)
                .addStatement("$T entity = ($T) target", modelType, modelType);

        CodeBlock.Builder getSwitchBuilder = CodeBlock.builder().beginControlFlow("switch (fieldName)");

        for (FieldModel fm : sm.fields()) {
            String fieldName = fm.getFieldName();
            TypeName fieldType = typeResolver.getTypeName(fm.getFieldType());

            // SET
            setSwitchBuilder.add("case $S:\n", fieldName).indent();
            if (fm.getFieldType() instanceof EnumType) {
                setSwitchBuilder.beginControlFlow("if (value instanceof String)")
                        .addStatement("entity.set$L($T.valueOf((String) value))", typeResolver.capitalize(fieldName), fieldType)
                        .nextControlFlow("else")
                        .addStatement("entity.set$L(($T) value)", typeResolver.capitalize(fieldName), fieldType)
                        .endControlFlow();
            } else if (fm.getFieldType() instanceof ArrayType) {
                // Use CollectionAdapterRegistry
                setSwitchBuilder.addStatement("$T adapter = $T.getAdapter($T.class)",
                        ParameterizedTypeName.get(ClassName.get(CollectionAdapter.class), fieldType),
                        ClassName.get(CollectionAdapterRegistry.class),
                        fieldType);
                setSwitchBuilder.addStatement("entity.set$L(adapter.fromList((java.util.List) value))", typeResolver.capitalize(fieldName));
            } else if (fm.getFieldType() instanceof ScalarType && typeResolver.isTemporalMapped((ScalarType) fm.getFieldType())) {
                // Use TemporalAdapterRegistry
                setSwitchBuilder.addStatement("$T adapter = $T.getAdapter($T.class)",
                        ParameterizedTypeName.get(ClassName.get(TemporalAdapter.class), fieldType),
                        ClassName.get(TemporalAdapterRegistry.class),
                        fieldType);
                setSwitchBuilder.addStatement("entity.set$L(adapter.fromTemporal(($T) value))", typeResolver.capitalize(fieldName), ClassName.get(TemporalAccessor.class));
            } else {
                setSwitchBuilder.addStatement("entity.set$L(($T) value)", typeResolver.capitalize(fieldName), fieldType.box());
            }
            setSwitchBuilder.addStatement("break").unindent();

            // GET
            getSwitchBuilder.add("case $S:\n", fieldName).indent()
                    .addStatement("return entity.get$L()", typeResolver.capitalize(fieldName))
                    .unindent();
        }

        setSwitchBuilder.add("default:\n").indent().addStatement("break").unindent().endControlFlow();
        getSwitchBuilder.add("default:\n").indent().addStatement("return null").unindent().endControlFlow();

        setMethodBuilder.addCode(setSwitchBuilder.build());
        getMethodBuilder.addCode(getSwitchBuilder.build());

        TypeSpec adapter = TypeSpec.classBuilder(adapterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(SchemaAdapter.class), modelType))
                .addMethod(MethodSpec.methodBuilder("logicalType")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ParameterizedTypeName.get(ClassName.get(Class.class), modelType))
                        .addStatement("return $T.class", modelType)
                        .build())
                .addMethod(MethodSpec.methodBuilder("createTarget")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(modelType)
                        .addStatement("return new $T()", modelType)
                        .build())
                .addMethod(setMethodBuilder.build())
                .addMethod(getMethodBuilder.build())
                .build();

        JavaFile javaFile = JavaFile.builder(adapterPackage, adapter).build();
        try {
            javaFile.writeTo(context.getOutputDirectory());
        } catch (IOException e) {
            log.error("Failed to write adapter class " + adapterClassName, e);
        }
    }
}
