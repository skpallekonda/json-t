package org.jsont.codec;

import org.jsont.adapters.AdapterContext;
import org.jsont.execution.RowMapper;
import org.jsont.execution.ValueConverter;
import org.jsont.grammar.types.ArrayType;
import org.jsont.grammar.types.EnumType;
import org.jsont.grammar.types.ScalarType;
import org.jsont.grammar.types.ValueType;
import org.jsont.values.DataValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodecRegistry {
    private final Map<Class<? extends ValueType>, Codec<?>> codecs = new LinkedHashMap<>();

    public void initialize(
            ValueConverter converter,
            RowMapper rowMapper, AdapterContext adapterContext) {
        codecs.put(ScalarType.class, new ScalarCodec());
        codecs.put(EnumType.class, new EnumCodec(adapterContext));
        codecs.put(ArrayType.class, new ArrayCodec(converter, adapterContext));
        codecs.put(org.jsont.grammar.types.ObjectType.class, new ObjectCodec(rowMapper, adapterContext));
    }

    @SuppressWarnings("unchecked")
    public <T extends ValueType> Codec<T> get(T type) {
        Codec<?> codec = codecs.get(type.getClass());
        if (codec == null) {
            throw new IllegalStateException(
                    "No codec for type: " + type.getClass());
        }
        return (Codec<T>) codec;
    }

    public Object adapt(DataValue value) {
        ValueType valueType = value.type();
        return codecs.get(valueType.getClass()).decode(value.raw(), valueType);
    }

    public Object adaptObject(DataValue value) {
        ValueType valueType = value.type();
        return codecs.get(valueType.getClass()).decode(value.raw(), valueType);
    }

    public List<?> adaptArray(DataValue value) {
        ValueType valueType = value.type();
        return (List<?>) codecs.get(valueType.getClass()).decode(value.raw(), valueType);
    }
}
