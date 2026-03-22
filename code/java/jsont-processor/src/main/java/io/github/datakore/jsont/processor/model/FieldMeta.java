package io.github.datakore.jsont.processor.model;

public class FieldMeta {

    private final AnnoFieldModel afm;
    private final String elementType;
    private final String type;

    public FieldMeta(AnnoFieldModel afm, String key, String value) {
        this.afm = afm;
        this.type = key;
        this.elementType = value;
    }

    public AnnoFieldModel getAfm() {
        return afm;
    }

    public String getFieldType() {
        return this.afm.getFieldType();
    }

    public String getType() {
        return this.type;
    }

    public String getJsonName() {
        return afm.javaName();
    }

    public boolean getArray() {
        return afm.isArray();
    }

    public String getElementType() {
        return this.elementType;
    }

    public boolean getObject() {
        return "".equals(this.afm.getFieldType());
    }

    public boolean getOptional() {
        return this.afm.optional();
    }

    public String getComponentType() {
        return this.elementType;
    }

    public String getSetterMethod() {
        return this.afm.getSetterMethod();
    }

    public String getGetterMethod() {
        return this.afm.getGetterMethod();
    }

}
