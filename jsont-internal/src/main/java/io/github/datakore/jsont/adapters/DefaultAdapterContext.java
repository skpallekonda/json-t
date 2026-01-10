package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.codec.CodecRegistry;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;

public final class DefaultAdapterContext implements AdapterContext {

    private final SchemaCatalog schemaCatalog;
    private final AdapterRegistry adapterRegistry;

    public DefaultAdapterContext(
            CodecRegistry codecRegistry,
            AdapterRegistry adapterRegistry,
            SchemaCatalog schemaCatalog) {
        this.adapterRegistry = adapterRegistry;
        this.schemaCatalog = schemaCatalog;
    }


    @Override
    public Object adaptEnum(String enumName, String symbol) {
        return schemaCatalog
                .getEnum(enumName)
                .resolve(symbol);
    }

    public AdapterRegistry adapterRegistry() {
        return this.adapterRegistry;
    }

    @Override
    public SchemaCatalog schemaCatalog() {
        return schemaCatalog;
    }


}
