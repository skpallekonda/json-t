package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.JsonTConfig;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.JsonTBaseVisitor;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.data.*;
import io.github.datakore.jsont.grammar.data.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DataRowVisitor extends JsonTBaseVisitor<Object> {

    private final DataStream pipeline;
    private final AtomicInteger rowIndex = new AtomicInteger();

    public DataRowVisitor(DataStream stream) {
        this.pipeline = stream;
    }

    @Override
    public Object visitData(JsonTParser.DataContext ctx) {
        if (ctx.dataSchemaSection() != null)
            visitDataSchemaSection(ctx.dataSchemaSection());
        if (ctx.dataSchemaSection() != null)
            visitDataRows(ctx.dataSection());

        this.pipeline.onEOF();

        return null;
    }

    private List<RowNode> visitDataRows(JsonTParser.DataSectionContext dataSectionContext) {
        if (dataSectionContext == null || dataSectionContext.isEmpty()) {
            return Collections.emptyList();
        }
        this.rowIndex.set(1);
        List<RowNode> dataRows = new ArrayList<>();
        for (JsonTParser.DataRowContext drc : dataSectionContext.dataRow()) {
            dataRows.add((RowNode) visitDataRow(drc));
        }
        return dataRows;
    }

    @Override
    public Object visitDataSchemaSection(JsonTParser.DataSchemaSectionContext ctx) {
        String dataSchema = ctx.IDENT().getText();
        if (dataSchema == null || dataSchema.isBlank()) {
            throw new IllegalStateException("Data schema must not be empty");
        }
        this.pipeline.setDataSchema(dataSchema);
        return null;
    }

    @Override
    public Object visitDataRow(JsonTParser.DataRowContext ctx) {
        RowNode rowNode = buildRowNode(ctx);

        if (rowNode != null) {
            this.pipeline.onRowParsed(rowNode);
        }

        return rowNode;
    }

    @Override
    public Object visitValue(JsonTParser.ValueContext ctx) {
        if (ctx.scalarValue() != null && !ctx.scalarValue().isEmpty()) {
            return visitScalarValue(ctx.scalarValue());
        } else if (ctx.objectValue() != null && !ctx.objectValue().isEmpty()) {
            return visitObjectValue(ctx.objectValue());
        } else if (ctx.arrayValue() != null && !ctx.arrayValue().isEmpty()) {
            return visitArrayValue(ctx.arrayValue());
        }
        return null;
    }

    @Override
    public Object visitScalarValue(JsonTParser.ScalarValueContext ctx) {
        if (ctx.IDENT() != null) {
            String raw = ctx.IDENT().getText();
            if (raw.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
                throw new DataException("Identifier Value is too long " + raw);
            }
            return new ScalarNode(ctx.IDENT().getText(), ScalarNodeKind.STRING);
        } else if (ctx.NULL() != null) {
            return new NullNode();
        } else if (ctx.BOOLEAN() != null) {
            return new ScalarNode(ctx.BOOLEAN().getText(), ScalarNodeKind.BOOLEAN);
        } else if (ctx.NUMBER() != null) {
            return new ScalarNode(ctx.NUMBER().getText(), ScalarNodeKind.NUMBER);
        } else {
            String raw = ctx.STRING().getText();
            if (raw == null || raw.trim().length() < 3) {
                raw = null;
            } else {
                raw = raw.trim().substring(1, raw.length() - 1);
            }
            if (!JsonTConfig.getWhitelistCharacters().matcher(raw).matches()) {
                throw new DataException("Invalid characters in string value" + raw);
            } else if (raw.length() > JsonTConfig.getMaxLengthOfString()) {
                throw new DataException("More than allowed no of characters in a string value" + raw);
            }
            return new ScalarNode(raw, ScalarNodeKind.STRING);
        }
    }

    @Override
    public Object visitArrayValue(JsonTParser.ArrayValueContext ctx) {
        List<ValueNode> values = null;
        if (ctx.value() == null || ctx.value().isEmpty()) {
            values = Collections.emptyList();
        } else {
            values = new ArrayList<>(ctx.value().size());
            for (JsonTParser.ValueContext vc : ctx.value()) {
                values.add((ValueNode) visitValue(vc));
            }
        }
        return new ArrayNode(values);
    }

    @Override
    public Object visitObjectValue(JsonTParser.ObjectValueContext ctx) {
        RowNode values = null;
        if (ctx.value() == null || ctx.value().isEmpty()) {
            values = null;
        } else {
            Map<String, ValueNode> list = new HashMap<>();
            int colIndex = 0;
            for (JsonTParser.ValueContext vc : ctx.value()) {
                ValueNode valueNode = (ValueNode) visitValue(vc);
                list.put(String.valueOf(colIndex++), valueNode);
            }
            values = new RowNode(this.rowIndex.get(), list);
        }
        return new ObjectNode(values);
    }

    private RowNode buildRowNode(JsonTParser.DataRowContext ctx) {
        Map<String, ValueNode> values = new HashMap<>();
        RowNode dataRow = new RowNode(this.rowIndex.getAndIncrement(), values);
        int fieldIndex = 0;
        if (ctx.value() != null) {
            for (JsonTParser.ValueContext valueContext : ctx.value()) {
                ValueNode valueNode = (ValueNode) visitValue(valueContext);
                values.put(String.valueOf(fieldIndex++), valueNode);
            }
        }
        return dataRow;
    }

}
