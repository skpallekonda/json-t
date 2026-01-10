package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.execution.RowMapper;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.types.ObjectType;
import io.github.datakore.jsont.grammar.types.ValueHolder;
import io.github.datakore.jsont.grammar.types.ValueType;

public class ObjectCodec implements Codec<ObjectType> {
    private final RowMapper rowMapper;
    private final AdapterContext adapterContext;

    public ObjectCodec(
            RowMapper rowMapper, AdapterContext adapterContext) {
        this.rowMapper = rowMapper;
        this.adapterContext = adapterContext;
    }

    @Override
    public Object decode(Object raw, ValueType type) {
        if (raw == null) {
            return null;
        }
        ObjectType objectType = (ObjectType) type;
        ValueHolder row = (ValueHolder) raw;
        SchemaModel schema = adapterContext.schemaCatalog().getSchema(objectType.schema());

        return rowMapper.mapRow((RowNode) row.value(), schema, adapterContext);
    }
}
