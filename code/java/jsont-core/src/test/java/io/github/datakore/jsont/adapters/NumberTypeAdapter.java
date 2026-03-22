package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.datagen.DataGen;
import io.github.datakore.jsont.datagen.RandomDataGenerator;
import io.github.datakore.jsont.entity.NumberEntry;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MaxPrecisionConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MaxValueConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.number.MinValueConstraint;

import java.util.LinkedHashMap;
import java.util.Map;

public class NumberTypeAdapter implements DataGen<NumberEntry>, SchemaAdapter<NumberEntry> {


    private final Map<String, FieldConstraint[]> map;
    private final RandomDataGenerator generator = new RandomDataGenerator();

    public NumberTypeAdapter() {
        this.map = new LinkedHashMap<>();
        map.put("t_i16", new FieldConstraint[]{new MinValueConstraint(FieldConstraint.ConstraitType.MinValue, -10)});
        map.put("t_i32", new FieldConstraint[]{new MaxValueConstraint(FieldConstraint.ConstraitType.MaxValue, 100)});
        map.put("t_i64", new FieldConstraint[]{new MinValueConstraint(FieldConstraint.ConstraitType.MinValue, -9999),
                new MaxValueConstraint(FieldConstraint.ConstraitType.MaxValue, -99)});
        map.put("t_u16", new FieldConstraint[]{new MaxValueConstraint(FieldConstraint.ConstraitType.MaxValue, 5)});
        map.put("t_u32", new FieldConstraint[]{new MinValueConstraint(FieldConstraint.ConstraitType.MinValue, 25)});
        map.put("t_u64", null);
        map.put("t_d32", new FieldConstraint[]{new MinValueConstraint(FieldConstraint.ConstraitType.MinValue, -1),
                new MaxValueConstraint(FieldConstraint.ConstraitType.MaxValue, 1),
                new MaxPrecisionConstraint(FieldConstraint.ConstraitType.MaxPrecision, 5)});
        map.put("t_d64", new FieldConstraint[]{new MaxPrecisionConstraint(FieldConstraint.ConstraitType.MaxPrecision, 3)});
        map.put("t_d128", new FieldConstraint[]{new MaxPrecisionConstraint(FieldConstraint.ConstraitType.MaxPrecision, 6)});
    }


    @Override
    public Class<NumberEntry> logicalType() {
        return NumberEntry.class;
    }

    @Override
    public NumberEntry createTarget() {
        return new NumberEntry();
    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {
        NumberEntry entry = (NumberEntry) target;
        switch (fieldName) {
            case "t_i16":
                entry.setI16((short) valuee);
                break;
            case "t_i32":
                entry.setI32((int) valuee);
                break;
            case "t_i64":
                entry.setI64((long) valuee);
                break;
            case "t_u16":
                entry.setU16((short) valuee);
                break;
            case "t_u32":
                entry.setU32((int) valuee);
                break;
            case "t_u64":
                entry.setU64((long) valuee);
                break;
            case "t_d32":
                entry.setD32((float) valuee);
                break;
            case "t_d64":
                entry.setD64((double) valuee);
                break;
            default:
                entry.setD128((java.math.BigDecimal) valuee);
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        NumberEntry entry = (NumberEntry) target;
        switch (fieldName) {
            case "t_i16":
                return entry.getI16();
            case "t_i32":
                return entry.getI32();
            case "t_i64":
                return entry.getI64();
            case "t_u16":
                return entry.getU16();
            case "t_u32":
                return entry.getU32();
            case "t_u64":
                return entry.getU64();
            case "t_d32":
                return entry.getD32();
            case "t_d64":
                return entry.getD64();
            default:
                return entry.getD128();
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
    public NumberEntry generate() {
        NumberEntry entry = new NumberEntry();
        for (String fieldName : fieldNames()) {
            FieldConstraint[] constraints = fieldConstraints(fieldName);
            Double minValue = null;
            Double maxValue = null;
            int precision = 0;
            if (constraints != null) {
                for (FieldConstraint constraint : constraints) {
                    if (constraint instanceof MinValueConstraint) {
                        minValue = (double) ((MinValueConstraint) constraint).constraintValue();
                    }
                    if (constraint instanceof MaxValueConstraint) {
                        maxValue = (double) ((MaxValueConstraint) constraint).constraintValue();
                    }
                    if (constraint instanceof MaxPrecisionConstraint) {
                        precision = (int) ((MaxPrecisionConstraint) constraint).constraintValue();
                    }
                }

            }
            Object value = generator.generateNumber(fieldName, minValue, maxValue, precision);
            set(entry, fieldName, value);
        }
        return entry;
    }
}
