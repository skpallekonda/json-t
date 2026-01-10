package io.github.datakore.jsont.io;

import io.github.datakore.jsont.grammar.schema.ast.ConstraintModel;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseWriter {


    final StringBuilder buffer = new StringBuilder();
    final AtomicInteger tabs = new AtomicInteger();
    final char tabChar = '\t';
    final int tabSize = 2;
    private final boolean useSpaceAsTab;


    public BaseWriter(boolean useSpacesAsTab) {
        this.useSpaceAsTab = useSpacesAsTab;
    }

    void pintStatement(String statement) {
        printTabs();
        buffer.append(statement);
        buffer.append("\n");
    }

    void printTabs() {
        String printChars = null;
        if (useSpaceAsTab) {
            printChars = " ".repeat(tabSize);
        } else {
            printChars = String.valueOf(tabChar);
        }
        buffer.append(printChars.repeat(tabs.get()));
    }

    void incrTab() {
        tabs.incrementAndGet();
    }

    void decrTab() {
        tabs.decrementAndGet();
    }

    void printImports(List<String> imports) {
        tabs.set(0);
        if (imports != null && !imports.isEmpty()) {
            imports.stream().forEach(this::pintStatement);
        }
    }

    public String toString() {
        return buffer.toString();
    }

    void printBlockBeginOrEnd(String chars) {
        printTabs();
        buffer.append(chars);
        buffer.append("\n");
        incrTab();
    }

    void printKeyValueEntry(String key, Object value) {
        if (key != null && !key.isEmpty()) {
            printTabs();
            buffer.append(key);
            buffer.append(" : ");
        }
        writeValue(value);
        buffer.append(",");
    }

    void writeObject(List<Object> fields) {
        printBlockBeginOrEnd("{");
        fields.forEach(this::writeValue);
        printBlockBeginOrEnd("}");
    }

    void writeArrays(List<String> arrays) {
        printBlockBeginOrEnd("[");
        for (String array : arrays) {
            writeValue(array);
        }
        printBlockBeginOrEnd("]");
    }

    void writeValue(Object value) {
        if (value == null) {
            buffer.append("null");
        } else if (value instanceof SchemaModel) {
            writeSchema((SchemaModel) value);
        } else if (value instanceof FieldModel) {
            writeField((FieldModel) value);
        } else if (value instanceof EnumModel) {
            writeEnum((EnumModel) value);
        } else if (value instanceof ConstraintModel) {
            writeConstraint((ConstraintModel) value);
        } else {
            buffer.append(value);
        }
    }

    abstract void writeSchema(SchemaModel value);

    abstract void writeField(FieldModel value);

    abstract void writeConstraint(ConstraintModel value);

    abstract void writeEnum(EnumModel value);


}
