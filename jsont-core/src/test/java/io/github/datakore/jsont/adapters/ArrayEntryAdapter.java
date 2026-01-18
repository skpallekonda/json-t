package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.datagen.DataGen;
import io.github.datakore.jsont.datagen.RandomDataGenerator;
import io.github.datakore.jsont.entity.ArrayEntry;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.AllowNullElementsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MaxItemsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MaxNullElementsConstraint;
import io.github.datakore.jsont.grammar.schema.constraints.arrays.MinItemsConstraint;
import io.github.datakore.jsont.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ArrayEntryAdapter implements SchemaAdapter<ArrayEntry>, DataGen<ArrayEntry> {
    private final LinkedHashMap<String, FieldConstraint[]> map;
    private RandomDataGenerator randomDataGenerator = new RandomDataGenerator();


    public ArrayEntryAdapter() {
        this.map = new LinkedHashMap<String, FieldConstraint[]>();
        map.put("t_allNull", new FieldConstraint[]{new AllowNullElementsConstraint(FieldConstraint.ConstraitType.AllowNullElements, true)});
        map.put("t_noNull", new FieldConstraint[]{new AllowNullElementsConstraint(FieldConstraint.ConstraitType.AllowNullElements, false)});
        map.put("t_minItems", new FieldConstraint[]{new MinItemsConstraint(FieldConstraint.ConstraitType.MinItems, 2)});
        map.put("t_maxItems", new FieldConstraint[]{new MaxItemsConstraint(FieldConstraint.ConstraitType.MaxItems, 1)});
        map.put("t_maxNulls", new FieldConstraint[]{new MaxNullElementsConstraint(FieldConstraint.ConstraitType.MaxNullElements, 2)});
    }

    @Override
    public Class<ArrayEntry> logicalType() {
        return ArrayEntry.class;
    }

    @Override
    public ArrayEntry createTarget() {
        return new ArrayEntry();
    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {
        ArrayEntry entry = (ArrayEntry) target;
        switch (fieldName) {
            case "t_allNull":
                entry.setStrAllNull(CollectionUtils.toArray(valuee, String.class));
                break;
            case "t_noNull":
                entry.setStrNoNull(CollectionUtils.toSet(valuee, String.class));
                break;
            case "t_minItems":
                List<Integer> list = CollectionUtils.toList(valuee, Integer.class);
                if (!list.isEmpty()) {
                    int[] items = new int[list.size()];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = list.get(i);
                    }
                    entry.setI16MinItems(items);
                }
                break;
            case "t_maxItems":
                entry.setStrMaxItems(CollectionUtils.toList(valuee, String.class));
                break;
            case "t_maxNulls":
                entry.setStrMaxNulls((CollectionUtils.toList(valuee, String.class)));
                break;
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        ArrayEntry entry = (ArrayEntry) target;
        switch (fieldName) {
            case "t_allNull":
                return entry.getStrAllNull();
            case "t_noNull":
                return entry.getStrNoNull();
            case "t_minItems":
                return entry.getI16MinItems();
            case "t_maxItems":
                return entry.getStrMaxItems();
            case "t_maxNulls":
                return entry.getStrMaxNulls();
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
    public ArrayEntry generate() {
        ArrayEntry entry = new ArrayEntry();
        for (String fieldName : fieldNames()) {
            FieldConstraint[] constraints = fieldConstraints(fieldName);
            Object value = null;
            switch (fieldName) {
                case "t_allNull":
                case "t_noNull":
                case "t_maxItems":
                case "t_maxNulls":
                    value = generateListOfStrings(constraints);
                    break;
                case "t_minItems":
                default:
                    value = generateListOfInts(constraints);
            }
            set(entry, fieldName, value);
        }
        return entry;
    }

    private List<String> generateListOfStrings(FieldConstraint[] constraints) {
        int items = getInteger(constraints);
        if (items < 1) return null;
        List<String> list = new ArrayList<>(items);
        for (int i = 0; i < items; i++) {
            list.add(randomDataGenerator.generateString(5, 25));
        }
        return list;
    }

    private List<Integer> generateListOfInts(FieldConstraint[] constraints) {
        int items = getInteger(constraints);
        if (items < 1) return null;
        List<Integer> list = new ArrayList<>(items);
        for (int i = 0; i < items; i++) {
            list.add(randomDataGenerator.generateInt(-100, 100));
        }
        return list;
    }

    private Integer getInteger(FieldConstraint[] constraints) {
        Integer minItems = null;
        Integer maxItems = null;
        for (FieldConstraint constraint : constraints) {
            if (constraint instanceof MinItemsConstraint)
                minItems = (int) ((MinItemsConstraint) constraint).constraintValue();
            if (constraint instanceof MaxItemsConstraint)
                maxItems = (int) ((MaxItemsConstraint) constraint).constraintValue();
        }
        return randomDataGenerator.generateInt(minItems == null ? 1 : minItems, maxItems == null ? 10 : maxItems);
    }
}
