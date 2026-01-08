package org.jsont.annotations;

import java.util.Set;

public enum ConstraintName {
    AllowNullElements("allowNulls"),
    MaxNullElements("maxNullItems"),
    MaxPrecision("maxPrecision"),
    MaxValue("maxValue"),
    MaxLength("maxLength"),
    MaxItems("maxItems"),
    MinValue("minValue"),
    MinItems("minItems"),
    MinVLength("minLength"),
    MandatoryField("required"),
    Pattern(new String[]{"regex", "pattern"});

    private final Set<String> names;

    ConstraintName(String... strings) {
        this.names = Set.of(strings);
    }

    public Set<String> getNames() {
        return names;
    }

    public String preferred() {
        return names.stream().findFirst().get();
    }
}
