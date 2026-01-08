package org.jsont.execution;

import org.jsont.adapters.AdapterContext;
import org.jsont.codec.Codec;
import org.jsont.codec.CodecRegistry;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.types.ValueHolder;

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
