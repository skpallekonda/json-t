package org.jsont.codec;

import org.jsont.adapters.AdapterContext;
import org.jsont.execution.RowMapper;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaModel;
import org.jsont.grammar.types.ObjectType;
import org.jsont.grammar.types.ValueHolder;
import org.jsont.grammar.types.ValueType;

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
