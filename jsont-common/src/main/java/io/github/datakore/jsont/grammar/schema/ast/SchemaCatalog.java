package io.github.datakore.jsont.grammar.schema.ast;

import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.data.ValueNodeKind;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class SchemaCatalog {
    private final Map<String, SchemaModel> schemaModelMap = new LinkedHashMap<>();
    private final Map<String, EnumModel> enumModelMap = new LinkedHashMap<>();
    private final List<SchemaNode> schemaNodes;
    private final List<EnumNode> enumNodes;

    public SchemaCatalog(List<SchemaNode> schemaNodes, List<EnumNode> enumNodes) throws SchemaException {
        this.schemaNodes = schemaNodes;
        this.enumNodes = enumNodes;
    }

    public SchemaCatalog() throws SchemaException {
        this.schemaNodes = Collections.emptyList();
        this.enumNodes = Collections.emptyList();
    }

    private void resolveEnumModels() {
        enumNodes.stream().map(this::resolveEnum).forEach(m -> enumModelMap.putIfAbsent(m.name(), m));
        enumNodes.clear();
    }

    private void resolveSchemas() {
        schemaNodes.stream().map(this::resolveSchema).forEach(m -> schemaModelMap.putIfAbsent(m.name(), m));
        schemaNodes.clear();
    }

    private SchemaModel resolveSchema(SchemaNode schemaNode) throws SchemaException {
        if (schemaModelMap.containsKey(schemaNode.getName())) {
            return schemaModelMap.get(schemaNode.getName());
        } else {
            int fieldCount = schemaNode.getFields().size();
            List<FieldModel> fieldModels = new ArrayList<>(fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                FieldNode fieldNode = schemaNode.getFields().get(i);
                ValueType valueType = handleObjectField(i, fieldNode.getFieldName(), fieldNode.getTypeRef().getTypeName());
                fieldModels.add(convertField(schemaNode.getName(), fieldNode, i, valueType));
            }
            return new SchemaModel(schemaNode.getName(), fieldModels);
        }
    }

    private ValueType handleObjectField(int col, String fieldName, String objectTypeName) {
        ValueType result = null;
        SchemaNode node = schemaNodes.stream().filter(s -> s.getName().equals(objectTypeName)).findFirst().orElse(null);
//        EnumNode enumNode = enumNodes.stream().filter(s -> s.getName().equals(objectTypeName)).findFirst().orElse(null);
        EnumModel enumModel = enumModelMap.get(objectTypeName);
        if (node != null) {
            result = new ObjectType(col, fieldName, resolveSchema(node));
        } else if (enumModel != null) {
            result = new EnumType(col, fieldName, enumModel);
        }
        return result;
    }


    private EnumModel resolveEnum(EnumNode enumNode) throws SchemaException {
        if (enumModelMap.containsKey(enumNode.getName())) {
            return enumModelMap.get(enumNode.getName());
        }
        Set<String> enumValues = enumNode.getValues().stream().collect(Collectors.toSet());
        return new EnumModel(enumNode.getName(), enumValues);
    }

    private ValueNodeKind isTypeEnumOrObject(String typeName) {
        if (schemaNodes.stream().anyMatch(s -> s.getName().equals(typeName))) {
            return ValueNodeKind.OBJECT;
        } else if (enumNodes.stream().anyMatch(s -> s.getName().equals(typeName))) {
            return ValueNodeKind.ENUM;
        } else {
            return null;
        }
    }

    /**
     * --------------------- Internal methods
     */
    private FieldModel convertField(String schema, FieldNode fieldNode, int position, ValueType valueType) {
        String name = fieldNode.getFieldName();
        ValueType type = convertValueType(position, fieldNode.getFieldName(), schema, fieldNode.getTypeRef(), valueType);
        boolean optional = fieldNode.getTypeRef().isOptional();
        List<FieldConstraint> constraints = fieldNode
                .constraints().stream()
                .map(c -> this.convertConstraint(schema, c)).collect(Collectors.toList());
        return new FieldModel(schema, position, name, type, optional, constraints);
    }

    private ValueType convertValueType(int colPosition, String fieldName, String schema, FieldTypeNode fieldType, ValueType valueType) {
        ValueType result;
        switch (fieldType.getKind().name()) {
            case "ENUM":
            case "OBJECT":
                if (valueType == null) {
                    throw new SchemaException("Cannot resolve type " + fieldType.getTypeName());
                }
                result = valueType;
                break;
            case "ARRAY":
                ValueNodeKind elementTypeKind = isTypeEnumOrObject(fieldType.getTypeName());
                ValueType base = null;
                if (elementTypeKind == null) {
                    base = new ScalarType(colPosition, fieldName, JsonBaseType.byIdentifier(fieldType.getTypeName()), ValueNodeKind.SCALAR);
                } else {
                    base = handleObjectField(colPosition, fieldName, fieldType.getTypeName());
                    if (base == null) {
                        throw new SchemaException("Cannot resolve type " + fieldType.getTypeName());
                    }
                }
                result = new ArrayType(colPosition, fieldName, base);
                break;
            default:
                JsonBaseType baseType = JsonBaseType.byIdentifier(fieldType.getTypeName());
                result = new ScalarType(colPosition, fieldName, baseType, fieldType.getKind());
        }
        return result;
    }


    private <T> T handleConstraintValue(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isAssignableFrom(Boolean.class)) {
            if (value instanceof Boolean) {
                return type.cast(value);
            } else if (value instanceof BigDecimal) {
                return type.cast(((BigDecimal) value).intValue() == 1);
            } else if (value instanceof String) {
                return type.cast("true".equals(value.toString()));
            }
        } else if (type.isAssignableFrom(Integer.class)) {
            if (value instanceof Boolean) {
                return type.cast((Boolean) value ? 1 : 0);
            } else if (value instanceof BigDecimal) {
                return type.cast(((BigDecimal) value).intValue());
            } else if (value instanceof String) {
                return type.cast(Integer.parseInt((String) value));
            }
        } else if (type.isAssignableFrom(String.class)) {
            if (value instanceof Boolean) {
                return type.cast((Boolean) value ? "true" : "false");
            } else if (value instanceof BigDecimal) {
                return type.cast(((BigDecimal) value).toPlainString());
            } else {
                return type.cast(value);
            }
        } else if (type.isAssignableFrom(BigDecimal.class)) {
            if (value instanceof Boolean) {
                return type.cast(BigDecimal.valueOf((Boolean) value ? 1 : 0));
            } else if (value instanceof BigDecimal) {
                return type.cast(value);
            } else {
                return type.cast(new BigDecimal((String) value));
            }
        } else {
            return type.cast(value);
        }
        return null;
    }

    private FieldConstraint convertConstraint(String schema, ConstraintNode constraintNode) {
        FieldConstraint.ConstraitType constraintType = constraintNode.getName();
        switch (constraintType.name()) {
            case "AllowNullElements":
                Boolean allowNull = handleConstraintValue(constraintNode.getValue(), Boolean.class);
                return new AllowNullElementsConstraint(FieldConstraint.ConstraitType.AllowNullElements, allowNull);
            case "MandatoryField":
                Boolean mandatory = handleConstraintValue(constraintNode.getValue(), Boolean.class);
                return new MandatoryFieldConstraint(FieldConstraint.ConstraitType.MandatoryField, mandatory);
            case "MaxItems":
                Integer maxItems = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MaxItemsConstraint(FieldConstraint.ConstraitType.MaxItems, maxItems);
            case "MaxLength":
                Integer maxLength = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MaxLengthConstraint(FieldConstraint.ConstraitType.MaxLength, maxLength);
            case "MaxNullElements":
                Integer maxNullElements = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MaxNullElementsConstraint(FieldConstraint.ConstraitType.MaxNullElements, maxNullElements);
            case "MaxPrecision":
                Integer maxPrecision = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MaxPrecisionConstraint(FieldConstraint.ConstraitType.MaxPrecision, maxPrecision);
            case "MaxValue":
                BigDecimal maxValue = handleConstraintValue(constraintNode.getValue(), BigDecimal.class);
                return new MaxValueConstraint(FieldConstraint.ConstraitType.MaxValue, maxValue.doubleValue());
            case "MinValue":
                BigDecimal minValue = handleConstraintValue(constraintNode.getValue(), BigDecimal.class);
                return new MinValueConstraint(FieldConstraint.ConstraitType.MinValue, minValue.doubleValue());
            case "MinItems":
                Integer minItems = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MinItemsConstraint(FieldConstraint.ConstraitType.MinItems, minItems);
            case "MinLength":
                Integer minLength = handleConstraintValue(constraintNode.getValue(), Integer.class);
                return new MinLengthConstraint(FieldConstraint.ConstraitType.MinLength, minLength);
            case "Pattern":
                String pattern = (String) constraintNode.getValue();
                return new RegexPatternConstraint(FieldConstraint.ConstraitType.Pattern, pattern);
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

    public void resolve() {
        resolveEnumModels();
        resolveSchemas();
    }

    public SchemaModel resolveSchema(String name) {
        return schemaModelMap.get(name);
    }

    public EnumModel resolveEnum(String name) {
        return enumModelMap.get(name);
    }

    public void addEnum(String name, EnumModel model) {
        enumModelMap.putIfAbsent(name, model);
    }

    @Override
    public String toString() {
        /**
         * {
         *         schemas: [
         *           User: {
         *             i32: id,
         *             str: username(minLength=5,maxLength='10'),
         *             str: email?(minLength=8)
         *           },
         *           Address: {
         *              str: city,
         *              str: zipCode
         *           }
         *         ],
         *         enums: [
         *           Status: [ ACTIVE, INACTIVE, SUSPENDED ],
         *           Role: [ ADMIN, USER ]
         *         ]
         *       }
         */
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        printSchemas(sb);
        printEnums(sb);
        sb.append("}");
        return sb.toString();
    }

    private void printEnums(StringBuilder sb) {
        if (enumModelMap.size() > 0) {
            sb.append(",enums: [");
            StringBuilder enums = new StringBuilder();
            for (Map.Entry<String, EnumModel> entry : enumModelMap.entrySet()) {
                if (enums.length() > 0) {
                    enums.append(", ");
                }
                enums.append(entry.getValue());
            }
            sb.append(enums);
            sb.append("]");
        }
    }

    private void printSchemas(StringBuilder sb) {
        sb.append("schemas: [");
        StringBuilder schemas = new StringBuilder();
        for (Map.Entry<String, SchemaModel> entry : schemaModelMap.entrySet()) {
            if (schemas.length() > 0) {
                schemas.append(", ");
            }
            schemas.append(entry.getValue());
        }
        sb.append(schemas);
        sb.append("]");
    }

    public void addSchema(SchemaModel schema) {
        this.schemaModelMap.putIfAbsent(schema.name(), schema);
    }
}
