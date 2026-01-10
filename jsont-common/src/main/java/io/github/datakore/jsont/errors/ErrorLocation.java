package io.github.datakore.jsont.errors;

public class ErrorLocation {
    private String field;
    private int row;
    private int column;
    private String schema;
    private String location;

    private ErrorLocation(String location) {
        this.location = location;
    }

    public ErrorLocation(String schema, String field) {
        this.schema = schema;
        this.field = field;
    }

    public ErrorLocation(String schema, String field, int row, int column) {
        this.schema = schema;
        this.field = field;
        this.row = row;
        this.column = column;
    }

    public static ErrorLocation withSchema(String schema) {
        return new ErrorLocation("schema : " + schema);
    }

    public static ErrorLocation withLocation(String location) {
        return new ErrorLocation(location);
    }

    public static ErrorLocation withField(String schema, String field) {
        return new ErrorLocation(schema, field);
    }

    public static ErrorLocation withRow(String location, int position) {
        ErrorLocation loc = new ErrorLocation(location);
        loc.column = position;
        return loc;
    }

    public static ErrorLocation withCell(int row, int column) {
        ErrorLocation loc = new ErrorLocation("cell : ");
        loc.row = row;
        loc.column = column;
        return loc;
    }

    public static ErrorLocation withRow(String schema, String field, int row) {
        return new ErrorLocation(schema, field, row, 0);
    }

    public static ErrorLocation withAll(String schema, String field, int row, int column) {
        return new ErrorLocation(schema, field, row, column);
    }

    public String location() {
        return location;
    }

    public String schema() {
        return schema;
    }

    public String field() {
        return field;
    }

    public int row() {
        return row;
    }

    public int column() {
        return column;
    }

    private void appendPrefix(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
    }

    private void appendSuffix(StringBuilder sb) {
        sb.append("}");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (schema != null) {
            sb.append("schema : ").append(schema);
        }
        if (field != null) {
            appendPrefix(sb);
            sb.append("field :").append(field);
        }
        if (row != 0) {
            appendPrefix(sb);
            sb.append("row : ").append(row);
        }
        if (column != 0) {
            appendPrefix(sb);
            sb.append("column : ").append(column);
        }
        if (location != null) {
            appendPrefix(sb);
            sb.append("location : ").append(location);
        }
        appendSuffix(sb);
        return sb.toString();
    }
}
