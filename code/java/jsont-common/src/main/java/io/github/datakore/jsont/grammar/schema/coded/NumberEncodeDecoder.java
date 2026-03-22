package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.schema.constraints.number.MaxPrecisionConstraint;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

public class NumberEncodeDecoder implements EncodeDecoder {

    private final DateEncodeDecoder dateEncodeDecoder = new DateEncodeDecoder();
    private final StringEncodeDecoder stringEncodeDecoder = new StringEncodeDecoder();

    private static Object handlePureNumberTypes(JsonBaseType jsonBaseType, String raw, BigDecimal bd) {
        switch (jsonBaseType) {
            case I16:
                return bd.shortValue();
            case I32:
                return bd.intValue();
            case I64:
                return bd.longValue();
            case U16:
                if (bd.compareTo(BigDecimal.ZERO) < 0) {
                    throw new DataException("Invalid scalar value: " + bd);
                }
                return bd.shortValue();
            case U32:
                if (bd.compareTo(BigDecimal.ZERO) < 0) {
                    throw new DataException("Invalid scalar value: " + bd);
                }
                return bd.intValue();
            case U64:
                if (bd.compareTo(BigDecimal.ZERO) < 0) {
                    throw new DataException("Invalid scalar value: " + bd);
                }
                return bd.longValue();
            case D32:
                return bd.floatValue();
            case D64:
                return bd.doubleValue();
            case D128:
                return bd;
            default:
                String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
                throw new DataException(message);
        }
    }

    @Override
    public Object decode(JsonBaseType jsonBaseType, String raw) {
        if (raw == null) {
            return null;
        }
        raw = StringUtils.removeQuotes(raw);
        assert raw != null;
        BigDecimal bd = new BigDecimal(raw);
        if ("Number".equals(jsonBaseType.lexerValueType())) {
            return handlePureNumberTypes(jsonBaseType, raw, bd);
        } else if ("Date".equals(jsonBaseType.lexerValueType())) {
            return dateEncodeDecoder.decode(jsonBaseType, raw);
        } else if ("String".equals(jsonBaseType.lexerValueType())) {
            return stringEncodeDecoder.decode(jsonBaseType, raw);
        } else if ("Boolean".equals(jsonBaseType.lexerValueType())) {
            return Boolean.valueOf(raw);
        }
        String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
        throw new DataException(message);
    }

    @Override
    public String encode(FieldModel fieldModel, Object object) {
        if (object == null) {
            return "null";
        }
        JsonBaseType jsonBaseType = ((ScalarType) fieldModel.getFieldType()).elementType();
        if (object instanceof Number) {
            return toTightString(fieldModel, object);
        } else if (object instanceof String) {
            return StringUtils.wrapInQuotes((String) object);
        }
        String message = String.format("Invalid type %s, to encode < %s >", jsonBaseType.identifier(), object);
        throw new DataException(message);
    }

    String toTightString(FieldModel fieldModel, Object obj) {
        if (obj == null) {
            return null;
        }
        final AtomicInteger precision = new AtomicInteger(4); // Default precision is 4
        if (fieldModel.getConstraints() != null) {
            fieldModel.getConstraints().stream().forEach(c -> {
                if (c instanceof MaxPrecisionConstraint) {
                    precision.set(((MaxPrecisionConstraint) c).getMaxPrecision());
                }
            });
        }

        // 1. Handle BigDecimal directly
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).setScale(precision.get(), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }

        // 2. Handle Floating Point types (Double, Float)
        if (obj instanceof Double || obj instanceof Float) {
            double val = ((Number) obj).doubleValue();

            // Check for special cases that crash BigDecimal
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return obj.toString();
            }

            return BigDecimal.valueOf(val).setScale(precision.get(), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }

        if (obj instanceof BigInteger) {
            return obj.toString();
        }

        if (obj instanceof Number) {
            return obj.toString();
        }

        throw new DataException("Unsupported type: " + obj.getClass());
    }
}
