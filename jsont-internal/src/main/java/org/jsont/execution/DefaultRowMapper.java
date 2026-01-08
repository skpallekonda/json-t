package org.jsont.execution;

import org.jsont.adapters.AdapterContext;
import org.jsont.adapters.SchemaAdapter;
import org.jsont.extractors.ValueNodeExtractor;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.ast.FieldModel;
import org.jsont.grammar.schema.ast.SchemaModel;
import org.jsont.grammar.types.ValueHolder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRowMapper implements RowMapper {
    private static final Map<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private final ValueNodeExtractor extractor;
    private final ValueConverter valueConverter;

    public DefaultRowMapper(
            ValueNodeExtractor extractor,
            ValueConverter valueConverter) {
        this.extractor = extractor;
        this.valueConverter = valueConverter;
    }

    @Override
    public Object mapRow(RowNode row, SchemaModel schema, AdapterContext context) {
        Map<String, ValueNode> nodes = row.values();
        List<FieldModel> fields = schema.fields();

        if (nodes.size() != fields.size()) {
            throw new RuntimeException(
                    "Column count mismatch: expected "
                            + fields.size() + ", got " + nodes.size());
        }

        SchemaAdapter<?> adapter = context.adapterRegistry().resolve(schema.name());

        Object target = adapter.createTarget();

        fields.forEach(fieldModel -> {
            ValueHolder valueHolder = extractor.extract(nodes.get(String.valueOf(fieldModel.position())), fieldModel.type(), fieldModel.optional());
            Object value = valueConverter.convert(valueHolder, context);
            adapter.set(target, fieldModel.name(), value);
        });

        return target;
    }


}
