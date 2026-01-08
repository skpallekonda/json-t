package org.jsont.codec;

import org.jsont.exception.DataException;
import org.jsont.grammar.data.JsontScalarType;
import org.jsont.grammar.types.ScalarType;
import org.jsont.grammar.types.ValueHolder;
import org.jsont.grammar.types.ValueType;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

public class ScalarCodec implements Codec<ScalarType> {
    @Override
    public Object decode(Object raw, ValueType type) {
        if (raw == null) {
            return null;
        }
        JsontScalarType targetType = type.valueType();

        if (raw instanceof ValueHolder) {
            return decode(((ValueHolder) raw).value(), type);
        } else if (raw instanceof Boolean) {
            return decodeBoolean((Boolean) raw, targetType);
        } else if (raw instanceof BigDecimal) {
            return decodeNumber((BigDecimal) raw, targetType);
        } else {
            return decodeString((String) raw, targetType);
        }
    }

    private Object decodeString(String raw, JsontScalarType type) {
        switch (type) {
            case NULL:
                return null;
            case INT:
                return Integer.valueOf(raw);
            case LONG:
                return Long.valueOf(raw);
            case DECIMAL:
                return new BigDecimal(raw);
            case FLOAT:
                return Float.valueOf(raw);
            case BOOLEAN:
                return Boolean.valueOf(raw);
            case URI:
                return URI.create(raw);
            case UUID:
                return UUID.fromString(raw);
            case ZIP:
                return ZipSupport.handleZip(raw, 5, 4);
            case ZIP5:
                return ZipSupport.handleZip(raw, 5, 0);
            case ZIP6:
                return ZipSupport.handleZip(raw, 6, 0);
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
                return DateTimeSupport.handleDateTime(raw, type);
            case ENUM:
            case STRING:
                return raw;
            default:
                throw new DataException("Invalid value " + raw + " for type " + type.name());
        }
    }

    private Object decodeNumber(BigDecimal raw, JsontScalarType type) {
        switch (type) {
            case INT:
                return raw.intValue();
            case LONG:
                return raw.longValue();
            case DECIMAL:
                return raw;
            case FLOAT:
                return raw.floatValue();
            case BOOLEAN:
                return raw.intValue() == 1;
            case ZIP:
            case ZIP5:
            case ZIP6:
                return decodeString(String.valueOf(raw), type);
            default:
                throw new DataException("Invalid value " + raw + " for type " + type.name());
        }
    }

    private Object decodeBoolean(Boolean raw, JsontScalarType type) {
        switch (type) {
            case BOOLEAN:
                return raw;
            case INT:
                return raw ? 1 : 0;
            default:
                throw new DataException("Invalid value " + raw + " for type " + type.name());
        }
    }
}
