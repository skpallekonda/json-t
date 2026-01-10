package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.codec.Codec;
import io.github.datakore.jsont.codec.CodecRegistry;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.types.ValueHolder;

public class DefaultValueConverter implements ValueConverter {

    private final CodecRegistry codecRegistry;

    public DefaultValueConverter(CodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    @Override
    public Object convert(ValueHolder value, AdapterContext context) {
        // 1️⃣ Type + constraint validation
        value.validate();

        // 2️⃣ Conversion via codec
        Codec codec = codecRegistry.get(value.valueType());
        Object converted = null;
        Object raw = value.value();
        if (raw instanceof RowNode) {
            ValueHolder vh = new ValueHolder(value.valueType(), raw);
            converted = codec.decode(vh, value.valueType());
        } else if (raw instanceof ValueHolder) {
            converted = codec.decode(raw, value.valueType());
        } else {
            converted = codec.decode(value.value(), value.valueType());
        }
        // 3️⃣ Runtime cell
        return converted;
    }
}
