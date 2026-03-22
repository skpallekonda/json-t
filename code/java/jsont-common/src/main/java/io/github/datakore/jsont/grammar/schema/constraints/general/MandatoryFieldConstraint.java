package io.github.datakore.jsont.grammar.schema.constraints.general;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MandatoryFieldConstraint extends BaseConstraint {
    private final boolean mandatory;

    public MandatoryFieldConstraint(ConstraitType constraitType, boolean mandatory) {
        super(constraitType);
        this.mandatory = mandatory;
    }

    @Override
    public Object constraintValue() {
        return this.mandatory;
    }
}
