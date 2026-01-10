package io.github.datakore.jsont.extractors;

import io.github.datakore.jsont.grammar.data.*;
import io.github.datakore.jsont.grammar.data.*;
import io.github.datakore.jsont.grammar.types.ArrayType;
import io.github.datakore.jsont.grammar.types.ValueHolder;
import io.github.datakore.jsont.grammar.types.ValueType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DefaultValueNodeExtractor implements ValueNodeExtractor {


    @Override
    public ValueHolder extract(ValueNode node, ValueType valueType, boolean optional) {
        if (node == null || node instanceof NullNode) {
            return new ValueHolder(valueType, null);
        } else if (node instanceof ArrayNode && valueType instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) valueType;
            List<ValueHolder> values = new ArrayList<>();
            for (ValueNode vn : ((ArrayNode) node).elements()) {
                values.add(extract(vn, arrayType.elementType(), optional));
            }
            return new ValueHolder(arrayType, values);
        } else if (node instanceof RowNode) {
            return new ValueHolder(valueType, node);
        } else if (node instanceof ObjectNode) {
            return new ValueHolder(valueType, ((ObjectNode) node).raw());
        } else {
            assert node instanceof ScalarNode;
            return extractScalar((ScalarNode) node, valueType);
        }
    }

    private ValueHolder extractScalar(ScalarNode node, ValueType type) {
        // This method should return the final java Object from raw value
        switch (node.scalarType()) {
            case STRING:
                return new ValueHolder(type, node.raw());
            case NUMBER:
                return new ValueHolder(type, new BigDecimal(node.raw()));
            case BOOLEAN:
                return new ValueHolder(type, Boolean.valueOf(node.raw()));
            default:
                throw new IllegalStateException("Unknown scalar");
        }
    }

}
