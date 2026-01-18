package io.github.datakore.jsont.grammar.schema.constraints;


import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.AllowNullElementsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MaxItemsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MaxNullElementsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MinItemsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.general.MandatoryFieldConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MaxPrecisionConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MaxValueConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MinValueConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.MaxLengthConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.MinLengthConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.RegexPatternConstraint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface FieldConstraint {
    static ConstraitType byType(String identifier) {
        for (ConstraitType type : ConstraitType.values()) {
            Optional<String> opt = type.identifier.stream().filter(t -> t.equalsIgnoreCase(identifier)).findAny();
            if (opt.isPresent()) {
                return type;
            }
        }
        throw new SchemaException("Invalid constraint type: " + identifier);
    }

    String checkConstraint(Object value);

    ValidationError makeError(int rowIndex, FieldModel field, String errorMessage);

    enum ConstraitType {
        AllowNullElements("allowNulls", AllowNullElementsConstraint.class),
        MaxNullElements("maxNullItems", MaxNullElementsConstraint.class),
        MaxPrecision("maxPrecision", MaxPrecisionConstraint.class),
        MaxValue("maxValue", MaxValueConstraint.class),
        MaxLength("maxLength", MaxLengthConstraint.class),
        MaxItems("maxItems", MaxItemsConstraint.class),
        MinValue("minValue", MinValueConstraint.class),
        MinItems("minItems", MinItemsConstraint.class),
        MinLength("minLength", MinLengthConstraint.class),
        MandatoryField("required", MandatoryFieldConstraint.class),
        Pattern(new String[]{"regex", "pattern"}, RegexPatternConstraint.class);

        private final Set<String> identifier;
        private final Class<? extends FieldConstraint> type;

        ConstraitType(String identifier, Class<? extends FieldConstraint> clazz) {
            this.identifier = new HashSet<>();
            this.identifier.add(identifier);
            this.type = clazz;
        }

        ConstraitType(String[] identifier, Class<? extends FieldConstraint> clazz) {
            this.identifier = new HashSet<>();
            this.identifier.addAll(Arrays.asList(identifier));
            this.type = clazz;
        }

        public Set<String> getIdentifier() {
            return identifier;
        }
    }
}
