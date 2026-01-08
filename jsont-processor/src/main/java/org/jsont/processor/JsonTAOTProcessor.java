package org.jsont.processor;

import org.jsont.annotations.JsonTField;
import org.jsont.annotations.JsonTSerializable;
import org.jsont.processor.generator.AdapterGenerator;
import org.jsont.processor.model.AnnoFieldModel;
import org.jsont.processor.model.AnnoTypeModel;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

@SupportedAnnotationTypes("org.jsont.annotations.JsonTSerializable")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class JsonTAOTProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Messager messager;
    private Types typeUtils;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(JsonTSerializable.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                error(element, "Only classes can be annotated with @JsonTSerializable");
            }
            TypeElement typeElement = (TypeElement) element;

            processSerializableType(typeElement);
        }
        return false;
    }

    private void processSerializableType(TypeElement typeElement) {
        JsonTSerializable ann = typeElement.getAnnotation(JsonTSerializable.class);

        String schemaName = ann.schema().isEmpty()
                ? typeElement.getSimpleName().toString()
                : ann.schema();
        // Build compile-time model
        AnnoTypeModel model = buildTypeModel(typeElement, schemaName);

        // Generate adapter
        generateAdapter(typeElement, model);
    }

    private void generateAdapter(TypeElement typeElement, AnnoTypeModel model) {
        try {
            AdapterGenerator.generate(model, filer, elementUtils);
        } catch (Exception e) {
            error(typeElement, e.getMessage());
        }
    }

    private void note(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg, e);
    }

    private void validateAgainstSchema(AnnoTypeModel model) {
        // do nothing right now
    }

    private boolean externalSchemaPresent(String schemaName) {
        return false;
    }

    private AnnoTypeModel buildTypeModel(TypeElement typeElement,
                                         String schemaName) {
        List<AnnoFieldModel> fields = new ArrayList<>();
        Map<String, TypeMirror> fieldTypes = new LinkedHashMap<>();
        Map<String, String> getters = new LinkedHashMap<>();
        Map<String, String> setters = new LinkedHashMap<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                JsonTField fa = field.getAnnotation(JsonTField.class);
                if (fa != null) {
                    fields.add(AnnoFieldModel.from(field, fa));
                } else {
                    // Include field with default settings (implied annotation)
                    fields.add(AnnoFieldModel.from(field));
                }
                fieldTypes.put(field.getSimpleName().toString(), field.asType());
            } else if (enclosed.getKind() == ElementKind.METHOD) {
                String methodName = enclosed.getSimpleName().toString();
                if (methodName.startsWith("get") || methodName.startsWith("is")) {
                    String fieldName = methodName.startsWith("is") ? methodName.substring(2) : methodName.substring(3);
                    getters.put(fieldName, methodName);
                } else if (methodName.startsWith("set")) {
                    String fieldName = methodName.substring(3);
                    setters.put(fieldName, methodName);
                }
            }
        }
        for (AnnoFieldModel afm : fields) {
            TypeMirror typeMirror = fieldTypes.get(afm.javaName());
            switch (typeMirror.toString()) {
                case "byte":
                    afm.setDeclaredType("Byte");
                    break;
                case "int":
                    afm.setDeclaredType("Integer");
                    break;
                case "short":
                    afm.setDeclaredType("Short");
                    break;
                case "long":
                    afm.setDeclaredType("Long");
                    break;
                case "boolean":
                    afm.setDeclaredType("Boolean");
                case "float":
                    afm.setDeclaredType("Float");
                    break;
                case "double":
                    afm.setDeclaredType("Double");
                    break;
                case "char":
                    afm.setDeclaredType("Character");
                    break;
                default:
                    afm.setDeclaredType(typeMirror.toString());
            }
            Optional<String> optionalGet = getters.keySet().stream().filter(k -> k.equalsIgnoreCase(afm.javaName())).findFirst();
            if (optionalGet.isPresent()) {
                afm.setGetterMethod(getters.get(optionalGet.get()));
            } else {
                afm.setGetterMethod(null);
            }
            Optional<String> optionalSet = setters.keySet().stream().filter(k -> k.equalsIgnoreCase(afm.javaName())).findFirst();
            if (optionalSet.isPresent()) {
                afm.setSetterMethod(setters.get(optionalSet.get()));
            } else {
                afm.setSetterMethod(null);
            }
        }
        PackageElement pkg = elementUtils.getPackageOf(typeElement);
        String packageName = pkg.getQualifiedName().toString();
        String modelName = typeElement.getSimpleName().toString();

        return new AnnoTypeModel(packageName, modelName, schemaName, fields);
    }

    private void error(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

}
