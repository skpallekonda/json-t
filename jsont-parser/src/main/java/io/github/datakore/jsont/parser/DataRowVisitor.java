package io.github.datakore.jsont.parser;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.DataStream;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.schema.coded.BooleanEncodeDecoder;
import io.github.datakore.jsont.grammar.schema.coded.NumberEncodeDecoder;
import io.github.datakore.jsont.grammar.schema.coded.StringEncodeDecoder;
import io.github.datakore.jsont.grammar.types.*;
import io.github.datakore.jsont.util.StringUtils;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.datakore.jsont.util.Constants.UNSPECIFIED_TOKEN;

public final class DataRowVisitor extends SchemaCatalogVisitor {

    private final DataStream pipeline;
    private final AtomicInteger rowIndex = new AtomicInteger();
    private final NamespaceT givenNamespace;
    // The Stack handles Recursion and Nesting naturally
    private final Stack<ParsingContext> stack = new Stack<>();
    private final BooleanEncodeDecoder booleanDecoder = new BooleanEncodeDecoder();
    private final NumberEncodeDecoder numberDecoder = new NumberEncodeDecoder();
    private final StringEncodeDecoder stringDecoder = new StringEncodeDecoder();
    private SchemaModel dataSchema;


    public DataRowVisitor(ErrorCollector errorCollector, NamespaceT givenNamespace, DataStream stream) {
        super(errorCollector);
        this.givenNamespace = givenNamespace;
        this.pipeline = stream;
    }

    NamespaceT resolvedNamespace() {
        NamespaceT namespace = null;
        if (super.getNamespaceT() != null) {
            namespace = super.getNamespaceT();
        } else {
            namespace = givenNamespace;
        }
        if (namespace == null) {
            throw new SchemaException("Namespace cannot be null");
        }
        return namespace;
    }

    SchemaModel resolveSchema(String schemaName) {
        NamespaceT ns = resolvedNamespace();
        for (SchemaCatalog sc : ns.getCatalogs()) {
            SchemaModel model = sc.getSchema(schemaName);
            if (model != null) {
                return model;
            }
        }
        throw new ParseCancellationException("Unbale to resolve dataschema from Catalog supplied, cannot read data recordds");
    }

    @Override
    public void exitDataSchemaSection(JsonTParser.DataSchemaSectionContext ctx) {
        super.exitDataSchemaSection(ctx);
        String schema = ctx.SCHEMAID().getText();
        dataSchema = resolveSchema(schema);
    }

    @Override
    public void exitDataSection(JsonTParser.DataSectionContext ctx) {
        pipeline.onEOF();
    }

    @Override
    public void enterDataRow(JsonTParser.DataRowContext ctx) {
        super.clearErrors();
        rowIndex.getAndIncrement();
        // We do NOT push context here anymore. We let enterObjectValue handle it.
    }

    @Override
    public void exitDataRow(JsonTParser.DataRowContext ctx) {
        // Nothing to do here, as the row is emitted in exitObjectValue when stack becomes empty
    }

    @Override
    public void enterObjectValue(JsonTParser.ObjectValueContext ctx) {
        // If stack is empty, this is the root object of the row
        if (stack.isEmpty()) {
            stack.push(new StructContext(rowIndex.get(), dataSchema));
            return;
        }

        ValueType expected = stack.peek().getExpectedType();

        // Handle null/unspecified for ObjectType
        // Note: This logic is tricky because enterObjectValue is called for '{', but null is a scalar token.
        // However, if the parser grammar allows 'null' where an object is expected, it might come in as a scalar value context, not object value context.
        // But if we are here, we saw '{'.

        if (!(expected instanceof ObjectType)) {
            super.addError(Severity.FATAL, "Found Object '{...}' but expected " + expected, stack.peek().getLocation());
        }

        assert expected instanceof ObjectType;
        ObjectType type = (ObjectType) expected;
        SchemaModel nestedSchema = resolveSchema(type.type()); // Recursive resolution
        stack.push(new StructContext(this.rowIndex.get(), expected.colPosition(), expected.fieldName(), nestedSchema));
    }

    @Override
    public void exitObjectValue(JsonTParser.ObjectValueContext ctx) {
        // Defensive check: If stack is empty or top is not StructContext, we shouldn't pop.
        // This can happen if enterObjectValue failed to push (e.g. error) but exit was called.
        if (stack.isEmpty() || !(stack.peek() instanceof StructContext)) {
            return;
        }

        // Finished parsing object
        StructContext completed = (StructContext) stack.pop();

        // Optional: Validate if all required fields were filled
        if (completed.fieldIndex < completed.schema.fields().size()) {
            super.addError(Severity.FATAL, "Not all fields have been sent", completed.getLocation());
        }

        if (stack.isEmpty()) {
            // This was the root object, emit the row
            List<ValidationError> errors = super.getRowErrors();
            if (errors.stream().anyMatch(err -> err.severity().isFatal())) {
                pipeline.onRowError(this.rowIndex.get(), errors);
            } else {
                RowNode row = new RowNode(this.rowIndex.get(), completed.values);
                pipeline.onRowParsed(row);
            }
        } else {
            // This was a nested object, add to parent
            stack.peek().addValue(completed.values);
        }
    }

    @Override
    public void enterArrayValue(JsonTParser.ArrayValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof ArrayType)) {
            super.addError(Severity.FATAL, "Found array '[...]' but expected " + expected, stack.peek().getLocation());
        }

        assert expected instanceof ArrayType;
        ArrayType type = (ArrayType) expected;
        // Push Array Context knowing the type of its children
        stack.push(new ArrayContext(this.rowIndex.get(), expected.colPosition(), expected.fieldName(), type.getElementType()));
    }

    @Override
    public void exitArrayValue(JsonTParser.ArrayValueContext ctx) {
        // Defensive check
        if (stack.isEmpty() || !(stack.peek() instanceof ArrayContext)) {
            return;
        }

        ArrayContext completed = (ArrayContext) stack.pop();
        stack.peek().addValue(completed.values);
    }

    @Override
    public void enterEnumValue(JsonTParser.EnumValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof EnumType)) {
            super.addError(Severity.FATAL, "Found enum but expected " + expected, stack.peek().getLocation());
        }
        // We do NOT push a context for Enum, as it is a leaf value (conceptually)
    }

    @Override
    public void exitEnumValue(JsonTParser.EnumValueContext ctx) {
        try {
            // Add value directly to parent
            stack.peek().addValue(ctx.CONSTID().getText());
        } catch (Exception e) {
            super.addError(Severity.WARNING, "Error parsing enum value", stack.peek().getLocation());
            stack.peek().addValue(null);
        }
    }

    @Override
    public void exitNullValue(JsonTParser.NullValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();
        if (ctx.NULL() != null) {
            stack.peek().addValue(null);
        } else if (ctx.UNSPECIFIED() != null) {
            stack.peek().addValue(UNSPECIFIED_TOKEN);
        } else if (StringUtils.isBlank(ctx.getText())) {
            stack.peek().addValue(null);
        }
    }

    @Override
    public void enterScalarValue(JsonTParser.ScalarValueContext ctx) {
        ValueType expected = stack.peek().getExpectedType();

        if (!(expected instanceof ScalarType)) {
            // Special handling: If we expected an ArrayType or ObjectType but got a scalar (that isn't null/unspecified), that's an error.
            // BUT, if we got here, it means the parser saw a scalar token (STRING, NUMBER, BOOLEAN).
            super.addError(Severity.FATAL, "Found scalar value but expected " + expected, stack.peek().getLocation());
        }
        // We do NOT push a context for Scalar, as it is a leaf value
    }

    @Override
    public void exitScalarValue(JsonTParser.ScalarValueContext ctx) {
        String raw = null;
        try {
            ParsingContext parent = stack.peek();
            ValueType expected = parent.getExpectedType();

            // If expected is NOT a ScalarType, we have a mismatch.
            if (!(expected instanceof ScalarType)) {
                super.addError(Severity.FATAL, "Found scalar value but expected " + expected, parent.getLocation());
                return;
            }

            ScalarType type = (ScalarType) expected;
            JsonBaseType jsonBaseType = type.elementType();

            Object value;
            if (ctx.BOOLEAN() != null) {
                raw = ctx.BOOLEAN().getText();
                value = booleanDecoder.decode(jsonBaseType, raw);
            } else if (ctx.NUMBER() != null) {
                raw = ctx.NUMBER().getText();
                value = numberDecoder.decode(jsonBaseType, raw);
            } else {
                raw = ctx.STRING().getText();
                // Fast path for removeQuotes
                if (raw.length() >= 2 && (raw.charAt(0) == '"' || raw.charAt(0) == '\'')) {
                    raw = raw.substring(1, raw.length() - 1);
                }
                value = stringDecoder.decode(jsonBaseType, raw);
            }
            parent.addValue(value);
        } catch (Exception e) {
            super.addError(Severity.WARNING, "Error parsing scalar value", stack.peek().getLocation());
        }
    }

    interface ParsingContext {
        void addValue(Object value);

        ValueType getExpectedType();

        ErrorLocation getLocation();
    }

    private final class StructContext implements ParsingContext {
        final SchemaModel schema;
        final Map<String, Object> values = new LinkedHashMap<>();
        private final ErrorLocation errorLocation;
        int fieldIndex = 0;

        StructContext(int row, SchemaModel schema) {
            this.schema = schema;
            this.errorLocation = new ErrorLocation(row, schema.name());
        }

        StructContext(int row, int col, String field, SchemaModel schema) {
            this.schema = schema;
            this.errorLocation = new ErrorLocation(row, col, field, schema.name());
        }

        @Override
        public void addValue(Object value) {
            if (fieldIndex >= schema.fields().size()) {
                // This can happen if we receive more values than fields defined in schema
                // We should probably log a warning or error, but for now let's just ignore or throw
                // Throwing helps catch schema mismatches early
                throw new SchemaException("Too many fields for schema " + schema.name() + ". Expected " + schema.fields().size());
            }
            values.put(schema.fields().get(fieldIndex).getFieldName(), value);
            fieldIndex++; // Move to next field
        }

        @Override
        public ValueType getExpectedType() {
            if (fieldIndex >= schema.fields().size()) {
                throw new SchemaException("Too many fields for schema " + schema.name());
            }
            return schema.fields().get(fieldIndex).getFieldType();
        }

        @Override
        public ErrorLocation getLocation() {
            return this.errorLocation;
        }
    }

    private final class ArrayContext implements ParsingContext {

        final List<Object> values = new ArrayList<>();
        private final ValueType componentType;
        private final ErrorLocation errorLocation;

        ArrayContext(int row, int col, String field, ValueType componentType) {
            this.componentType = componentType;
            this.errorLocation = new ErrorLocation(row, col, field);
        }

        @Override
        public void addValue(Object value) {
            values.add(value);
        }

        @Override
        public ValueType getExpectedType() {
            return componentType;
        }

        @Override
        public ErrorLocation getLocation() {
            return errorLocation;
        }
    }
}
