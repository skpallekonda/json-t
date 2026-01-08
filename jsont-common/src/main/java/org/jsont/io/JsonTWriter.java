package org.jsont.io;

import org.jsont.grammar.schema.ast.*;

public class JsonTWriter extends BaseWriter implements FieldWriter {

    public JsonTWriter(boolean useSpacesAsTab) {
        super(useSpacesAsTab);
    }

    public void writeCatalog(SchemaCatalog schemas) {
//        catalog.
//                printBlockBeginOrEnd("schemas");
    }

    void writeSchema(SchemaModel schea) {

    }

    void writeField(FieldModel field) {

    }

    void writeConstraint(ConstraintModel constraint) {

    }

    void writeJsonMapEntry(String key, Object value) {
        printKeyValueEntry(key, value);
    }


    void writeEnum(EnumModel value) {

    }

    @Override
    public void writeDataValue(Object value) {
        writeValue(value);
    }
}
