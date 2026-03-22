package io.github.datakore.jsont.errors;

import io.github.datakore.jsont.util.StringUtils;

public class ErrorLocation {
    long row;
    String location;
    private String field;
    private int column;
    private String schema;

    public ErrorLocation(String location) {
        this.location = location;
    }

    public ErrorLocation(long row, String schema) {
        this.row = row;
        this.schema = schema;
    }

    public ErrorLocation(long row, int column, String fieldName) {
        this.row = row;
        this.column = column;
        this.field = fieldName;
    }

    public ErrorLocation(long row, int column, String fieldName, String nestedSchema) {
        this.row = row;
        this.column = column;
        this.field = fieldName;
        this.schema = nestedSchema;
    }

    public static ErrorLocation withRow(String location, int row) {
        ErrorLocation errorLocation = new ErrorLocation(location);
        errorLocation.row = row;
        return errorLocation;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        if (this.row > 0) {
            sb.append(" row: ").append(this.row).append(",");
        }
        if (this.column > 0) {
            sb.append(" column: ").append(this.column).append(",");
        }
        if (!StringUtils.isBlank(this.field)) {
            sb.append(" field: ").append(this.field).append(",");
        }
        if (!StringUtils.isBlank(this.schema)) {
            sb.append(" of schema : [ ").append(this.schema).append("],");
        }
        if (!StringUtils.isBlank(this.location)) {
            sb.append(" location: ").append(this.location).append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt((sb.length() - 1));
        }
        sb.append("}");
        return sb.toString();
    }

    public long row() {
        return row;
    }

    public String location() {
        return StringUtils.isBlank(location) ? toString() : location;
    }
}
