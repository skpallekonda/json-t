package io.github.datakore.jsont.processor.model;

import io.github.datakore.jsont.annotations.FieldKind;
import io.github.datakore.jsont.annotations.JsonTField;

import javax.lang.model.element.VariableElement;

public final class AnnoFieldModel {
    private final String fieldProperty;
    private final String fieldNameInSchema;
    private final String fieldType;
    private final boolean array;
    private final boolean optional;
    private String declaredType;
    private String getterMethod;
    private String setterMethod;
    public AnnoFieldModel(String fieldProperty, String fieldNameInSchema, String declaredType, String fieldType, boolean array, boolean optional) {
        this.fieldProperty = fieldProperty;
        this.fieldNameInSchema = fieldNameInSchema;
        this.declaredType = declaredType;
        this.fieldType = fieldType;
        this.array = array;
        this.optional = optional;
    }

    public static AnnoFieldModel from(VariableElement field, JsonTField jsontAnnot) {
        String fieldProperty = field.getSimpleName().toString();
        String fieldNameInSchema = jsontAnnot.name().isEmpty() ? fieldProperty : jsontAnnot.name();
        String declaredType = field.asType().toString();
        String fieldType = jsontAnnot.kind().type();
        return new AnnoFieldModel(fieldProperty, fieldNameInSchema, declaredType, fieldType, jsontAnnot.array(), jsontAnnot.optional());
    }

    public static AnnoFieldModel from(VariableElement field) {
        String fieldProperty = field.getSimpleName().toString();
        String declaredType = field.asType().toString();
        String fieldType = deriveFieldType(declaredType);
        boolean arrays = deriveArraysType(declaredType);
        return new AnnoFieldModel(fieldProperty, fieldProperty, declaredType, fieldType, arrays, false);
    }

    private static boolean deriveArraysType(String declaredType) {
        switch (declaredType) {
            case "List":
            case "ArrayList":
            case "LinkedList":
            case "Set":
            case "HashSet":
            case "TreeSet":
            case "Collection":
                return true;
        }
        return false;
    }

    private static String deriveFieldType(String declaredType) {
        switch (declaredType) {
            case "int":
            case "Integer":
                return FieldKind.e_int.type();
            case "Long":
                return FieldKind.e_long.type();
            case "Double":
            case "double":
                return FieldKind.e_double.type();
            case "Float":
            case "float":
                return FieldKind.e_float.type();
            case "String":
                return FieldKind.e_str.type();
        }
        return FieldKind.e_str.type();
    }

    public String getFieldType() {
        return fieldType;
    }

    public boolean isArray() {
        return array;
    }

    public String getSetterMethod() {
        return setterMethod;
    }

    public void setSetterMethod(String setterMethod) {
        this.setterMethod = setterMethod;
    }

    public String getGetterMethod() {
        return getterMethod;
    }

    public void setGetterMethod(String getterMethod) {
        this.getterMethod = getterMethod;
    }

    public String getDeclaredType() {
        return declaredType;
    }

    public void setDeclaredType(String declaredType) {
        this.declaredType = declaredType;
    }

    public String javaName() {
        return fieldProperty;
    }

    public String schemaName() {
        return fieldNameInSchema;
    }

    public String declaredType() {
        return declaredType;
    }

    public boolean optional() {
        return optional;
    }
}
