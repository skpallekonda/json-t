package org.jsont.codec;

import org.jsont.adapters.AdapterContext;
import org.jsont.grammar.types.EnumType;
import org.jsont.grammar.types.ValueType;

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
