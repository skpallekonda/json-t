package io.github.datakore.jsont.annotations;

public enum FieldKind {
    e_int("int"),
    e_long("long"),
    e_float("flt"),
    e_double("dbl"),
    e_date("date"),
    e_time("time"),
    e_datetime("datetime"),
    e_timestamp("timestamp"),
    e_boolean("bool"),
    e_str("str"),
    e_uuid("uuid"),
    e_uri("uri"),
    e_zip("zip"),
    e_zip5("zip5"),
    e_pin("pin"),
    e_email("email"),
    e_map("map"),
    e_object("");

    private final String type;

    FieldKind(String type) {
        this.type = type;
    }


    public String type() {
        return this.type;
    }
}
