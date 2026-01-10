package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.execution.ValueConverter;
import io.github.datakore.jsont.grammar.types.ArrayType;
import io.github.datakore.jsont.grammar.types.ValueHolder;
import io.github.datakore.jsont.grammar.types.ValueType;

import java.util.ArrayList;
import java.util.List;

public class ArrayCodec implements Codec<ArrayType> {

    private final ValueConverter converter;
    private final AdapterContext adapterContext;

    public ArrayCodec(
            ValueConverter converter,
            AdapterContext adapterContext) {
        this.converter = converter;
        this.adapterContext = adapterContext;
    }

    @Override
    public Object decode(Object raw, ValueType type) {
        if (raw == null) {
            return null;
        }
        ArrayType arrayType = (ArrayType) type;
        List<Object> rawList = (List<Object>) raw;

        List<Object> result = new ArrayList<>(rawList.size());
        ValueType elementType = arrayType.elementType();

        for (Object element : rawList) {
            Object adapted = converter.convert(new ValueHolder(elementType, element), adapterContext);
            result.add(adapted);
        }

        return result;
    }
}
