package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.util.regex.Pattern;

public class RegexPatternConstraint extends BaseConstraint {
    private final String patternString;
    private final Pattern pattern;


    public RegexPatternConstraint(ConstraitType type, String pattern) {
        super(type);
        this.patternString = pattern;
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof String && !pattern.matcher((String) value).matches()) {
            return String.format("Field value does not match pattern %s", patternString);
        } else {
            return null;
        }
    }

    @Override
    public Object constraintValue() {
        return this.patternString;
    }
}
