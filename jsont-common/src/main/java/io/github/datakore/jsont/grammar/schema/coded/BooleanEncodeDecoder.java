package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;

public class BooleanEncodeDecoder implements EncodeDecoder {
    final String[] trueValues = new String[]{"Yes", "1", "y", "true"};

    @Override
    public Object decode(JsonBaseType jsonBaseType, String raw) {
        if (raw == null) {
            return null;
        }
        raw = StringUtils.removeQuotes(raw);
        String lexerType = jsonBaseType.lexerValueType();
        switch (lexerType) {
            case "Boolean":
                return Boolean.valueOf(raw);
            default:
                String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
                throw new DataException(message);
        }
    }

    @Override
    public String encode(FieldModel fieldModel, Object object) {
        if (object == null) {
            return "nil";
        }
        JsonBaseType jsonBaseType = ((ScalarType) fieldModel.getFieldType()).elementType();
        if (object instanceof Boolean) {
            return ((Boolean) object).toString();
        } else if (object instanceof String) {
            if (Arrays.asList(trueValues).contains((String) object)) {
                return "true";
            } else {
                return "false";
            }
        } else if (object instanceof Number) {
            BigDecimal bd = new BigDecimal(object.toString());
            return bd.longValue() > 0 ? "true" : "false";
        }
        String message = String.format("Invalid type %s, to encode < %s >", jsonBaseType.identifier(), object);
        throw new DataException(message);
    }
}
