package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.grammar.types.ObjectType;
import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.execution.RowMapper;
import io.github.datakore.jsont.execution.ValueConverter;
import io.github.datakore.jsont.grammar.types.ArrayType;
import io.github.datakore.jsont.grammar.types.EnumType;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.grammar.types.ValueType;
import io.github.datakore.jsont.values.DataValue;

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
        codecs.put(ObjectType.class, new ObjectCodec(rowMapper, adapterContext));
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
