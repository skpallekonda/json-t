package org.jsont.grammar.data;

import org.jsont.exception.SchemaException;

public enum JsontScalarType {
    INT("int"), LONG("long"), FLOAT("flt"), DECIMAL("dbl"), ENUM(""),
    DATE("date"), TIME("time"), DATETIME("datetime"), TIMESTAMP("timestamp"),
    OBJECT(""), NULL(""), BOOLEAN("bool"),
    STRING("str"), UUID("uuid"), EMAIL("email"),
    MAP("map"),
    URI("uri"), ZIP("zip"), ZIP5("zip5"), ZIP6("pin");

    private final String identifier;

    JsontScalarType(String identifier) {
        this.identifier = identifier;
    }

    public static JsontScalarType byType(String type) {
        for (JsontScalarType scalarType : JsontScalarType.values()) {
            if (scalarType.identifier.equalsIgnoreCase(type)) {
                return scalarType;
            }
        }
        throw new SchemaException("Invalid type: " + type);
    }

    public String getIdentifier() {
        return identifier;
    }
}
