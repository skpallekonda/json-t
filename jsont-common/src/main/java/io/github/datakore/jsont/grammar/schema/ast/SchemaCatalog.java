package io.github.datakore.jsont.grammar.schema.ast;

import io.github.datakore.jsont.JsonTConfig;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.data.JsontScalarType;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
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
import io.github.datakore.jsont.grammar.schema.raw.*;
import io.github.datakore.jsont.grammar.types.*;
import io.github.datakore.jsont.grammar.schema.raw.*;
import io.github.datakore.jsont.grammar.types.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SchemaCatalog {
    private final Map<String, SchemaModel> schemaModelMap = new LinkedHashMap<>();
    private final Map<String, EnumModel> enumModelMap = new LinkedHashMap<>();

    public SchemaModel convertSchema(SchemaNode schemaNode) {
        final AtomicInteger index = new AtomicInteger();
        String name = schemaNode.getName();
        if (schemaNode.getFields().isEmpty() || schemaNode.getFields().size() > JsonTConfig.getMaxFieldsPerSchema()) {
            throw new SchemaException(String.format("%s schema has more than supported fields (%d > %d)", name, schemaNode.getFields().size(), JsonTConfig.getMaxFieldsPerSchema()));
        }
        List<FieldModel> fieldList = schemaNode.getFields().stream()
                .map(f -> this.convertField(name, f, index.getAndIncrement()))
                .collect(Collectors.toList());
        return new SchemaModel(name, fieldList);
    }

    public void addModel(String name, SchemaModel model) {
        schemaModelMap.putIfAbsent(name, model);
    }

    public EnumModel convertEnum(EnumNode enumNode) {
        String name = enumNode.getName();
        return new EnumModel(name, new HashSet<>(enumNode.getValues()));
    }

    public void addEnum(String name, EnumModel model) {
        enumModelMap.putIfAbsent(name, model);
    }

    /**
     * --------------------- Internal methods
     */
    private FieldModel convertField(String schema, FieldNode fieldNode, int position) {
        String name = fieldNode.getFieldName();
        ValueType type = convertValueType(schema, fieldNode.getTypeRef());
        boolean optional = fieldNode.getTypeRef().isOptional();
        boolean arrays = fieldNode.getTypeRef().isArray();
        List<FieldConstraint> constraints = fieldNode
                .constraints().stream()
                .map(c -> this.convertConstraint(schema, c)).collect(Collectors.toList());
        return new FieldModel(position, name, type, optional, arrays, constraints);
    }

    private ValueType convertValueType(String schema, FieldTypeNode fieldType) {
        ValueType base;
        if (fieldType.isObject()) {
            base = new ObjectType(fieldType.getTypeName(), fieldType.isOptional());
        } else {
            JsontScalarType scalarType = JsontScalarType.byType(fieldType.getTypeName());
            switch (scalarType) {
                case NULL:
                    base = new NullType();
                    break;
                case ENUM:
                    base = new EnumType(fieldType.getTypeName(), JsontScalarType.ENUM, fieldType.isOptional());
                    break;
                case OBJECT:
                    base = new ObjectType(schema, fieldType.isOptional());
                    break;
                default:
                    base = new ScalarType(scalarType, fieldType.isOptional());
            }
        }
        if (fieldType.isArray()) {
            base.setOptional(false);
            base = new ArrayType(base, fieldType.isOptional());
        }
        return base;
    }

    private FieldConstraint convertConstraint(String schema, ConstraintNode constraintNode) {
        FieldConstraint.ConstraitType constraintType = FieldConstraint.byType(constraintNode.getName());
        switch (constraintType) {
            case AllowNullElements:
                return new AllowNullElementsConstraint((Boolean) constraintNode.getValue());
            case MandatoryField:
                return new MandatoryFieldConstraint((Boolean) constraintNode.getValue());
            case MaxItems:
                return new MaxItemsConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case MaxLength:
                return new MaxLengthConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case MaxNullElements:
                return new MaxNullElementsConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case MaxPrecision:
                return new MaxPrecisionConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case MaxValue:
                return new MaxValueConstraint(((BigDecimal) constraintNode.getValue()).doubleValue());
            case MinValue:
                return new MinValueConstraint(((BigDecimal) constraintNode.getValue()).doubleValue());
            case MinItems:
                return new MinItemsConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case MinVLength:
                return new MinLengthConstraint(((BigDecimal) constraintNode.getValue()).intValue());
            case Pattern:
                return new RegexPatternConstraint((String) constraintNode.getValue());
            default:
                return null;
        }
    }

    public SchemaModel getSchema(String schema) {
        String _schema = schema;
        if (schema.startsWith("<") && schema.endsWith(">")) {
            _schema = schema.substring(1, schema.length() - 1);
        }
        return schemaModelMap.get(_schema);
    }

    public EnumModel getEnum(String name) {
        return enumModelMap.get(name);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
