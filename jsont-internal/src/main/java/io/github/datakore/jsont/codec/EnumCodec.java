package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.grammar.types.EnumType;
import io.github.datakore.jsont.grammar.types.ValueType;

public class EnumCodec implements Codec<EnumType> {

    private final AdapterContext adapterContext;

    public EnumCodec(AdapterContext adapterContext) {
        this.adapterContext = adapterContext;
    }

    @Override
    public Object decode(Object raw, ValueType type) {
        if (raw == null) {
            return null;
        }
        EnumType enumType = (EnumType) type;
        String symbol = (String) raw;
        return adapterContext.adaptEnum(enumType.enumName(), symbol);
    }
}
