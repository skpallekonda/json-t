package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.datagen.DataGen;
import io.github.datakore.jsont.datagen.RandomDataGenerator;
import io.github.datakore.jsont.entity.StringEntry;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.general.MandatoryFieldConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.MaxLengthConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.MinLengthConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.text.RegexPatternConstraint;
import io.github.datakore.jsont.util.StringUtils;

import java.util.LinkedHashMap;

public class StringEntryAdapter implements DataGen<StringEntry>, SchemaAdapter<StringEntry> {
    private final LinkedHashMap<String, FieldConstraint[]> map;
    private RandomDataGenerator randomDataGenerator = new RandomDataGenerator();

    public StringEntryAdapter() {
        this.map = new LinkedHashMap<String, FieldConstraint[]>();
        map.put("t_min", new FieldConstraint[]{new MinLengthConstraint(FieldConstraint.ConstraitType.MinLength, 5)});
        map.put("t_max", new FieldConstraint[]{new MaxLengthConstraint(FieldConstraint.ConstraitType.MaxValue, 15)});
        map.put("t_man", new FieldConstraint[]{new MandatoryFieldConstraint(FieldConstraint.ConstraitType.MandatoryField, true)});
        map.put("t_zip", new FieldConstraint[]{new RegexPatternConstraint(FieldConstraint.ConstraitType.Pattern, "^\\d{5}$")});
    }

    @Override
    public Class<StringEntry> logicalType() {
        return StringEntry.class;
    }

    @Override
    public StringEntry createTarget() {
        return new StringEntry();
    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {
        StringEntry entry = (StringEntry) target;
        switch (fieldName) {
            case "t_min":
                entry.setT_min((String) valuee);
                break;
            case "t_max":
                entry.setT_max((String) valuee);
                break;
            case "t_man":
                entry.setT_man((String) valuee);
                break;
            case "t_zip":
                entry.setT_zip((String) valuee);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        StringEntry entry = (StringEntry) target;
        switch (fieldName) {
            case "t_min":
                return entry.getT_min();
            case "t_max":
                return entry.getT_max();
            case "t_man":
                return entry.getT_man();
            case "t_zip":
                return entry.getT_zip();
            default:
                return null;
        }
    }

    @Override
    public String[] fieldNames() {
        return map.keySet().toArray(new String[0]);
    }

    @Override
    public FieldConstraint[] fieldConstraints(String fieldName) {
        return map.get(fieldName);
    }

    @Override
    public StringEntry generate() {
        StringEntry entry = new StringEntry();
        for (String fieldName : fieldNames()) {
            FieldConstraint[] constraints = fieldConstraints(fieldName);
            int minLength = 0;
            int maxLength = 255;
            String pattern = null;
            if (constraints != null) {
                for (FieldConstraint constraint : constraints) {
                    if (constraint instanceof MinLengthConstraint) {
                        minLength = (int) ((MinLengthConstraint) constraint).constraintValue();
                    }
                    if (constraint instanceof MaxLengthConstraint) {
                        maxLength = (int) ((MaxLengthConstraint) constraint).constraintValue();
                    }
                    if (constraint instanceof RegexPatternConstraint) {
                        pattern = (String) ((RegexPatternConstraint) constraint).constraintValue();
                    }
                }
            }
            if (StringUtils.isBlank(pattern)) {
                set(entry, fieldName, randomDataGenerator.generateString(minLength, maxLength));
            } else {
                set(entry, fieldName, randomDataGenerator.randomMatchingPattern(pattern));
            }
        }
        return entry;
    }
}
