package io.github.datakore.jsont.parser;

import io.github.datakore.jsont.JsonTProperties;
import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.JsonTBaseListener;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.data.ValueNodeKind;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaCatalog;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.schema.raw.*;
import io.github.datakore.jsont.util.StringUtils;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SchemaCatalogVisitor extends JsonTBaseListener {
    private final ErrorCollector errorCollector;
    private final List<SchemaNode> schemaNodes = null;
    private final AtomicInteger rowIndex = new AtomicInteger(0);
    private final Set<String> unresolvedTypes = new HashSet<>();
    private NamespaceT namespaceT;
    private SchemaCatalog schemaCatalog;
    private List<SchemaNode> schemaList;
    private SchemaNode currentSchema;
    private List<ConstraintNode> currentFieldConstraints;
    private List<EnumNode> enumList;
    private Map<String, Object> fieldInfo;
    private Map<String, List<String>> enumInfo;

    public SchemaCatalogVisitor(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    @Override
    public void exitNsBaseUrl(JsonTParser.NsBaseUrlContext ctx) {
        String urlString = StringUtils.removeQuotes(ctx.STRING().getText());
        try {
            if (StringUtils.isBlank(urlString)) {
                throw new SchemaException("Namespace URL cannot be empty");
            }
            URL url = new URL(urlString);
            namespaceT = new NamespaceT(url);
        } catch (MalformedURLException e) {
            throw new SchemaException("Invalid URL", e);
        }
    }

    public NamespaceT getNamespaceT() {
        return namespaceT;
    }

    @Override
    public void exitNameSpace(JsonTParser.NameSpaceContext ctx) {
        // addError(Severity.WARNING, "Exiting namespace without any errors", new ErrorLocation("Exit Namespace"));
    }

    @Override
    public void enterCatalog(JsonTParser.CatalogContext ctx) {
        schemaList = new ArrayList<>();
        enumList = new ArrayList<>();
        schemaCatalog = new SchemaCatalog(schemaList, enumList);
    }

    @Override
    public void exitCatalog(JsonTParser.CatalogContext ctx) {
        try {
            schemaCatalog.resolve();
            if (unresolvedTypes != null && !unresolvedTypes.isEmpty()) {
                boolean result = unresolvedTypes.stream().noneMatch(unresolvedType -> {
                    boolean r1 = schemaCatalog.getSchema(unresolvedType) != null;
                    boolean r2 = schemaCatalog.getEnum(unresolvedType) != null;
                    return r1 || r2;
                });
                if (result) {
                    throw new SchemaException("Unresolved types referenced in schema");
                }
            }
            namespaceT.addCatalog(schemaCatalog);
        } catch (Exception e) {
            String location = String.format("ExitCatalog of catalog # %d", namespaceT.getCatalogs().size() + 1);
            addError(Severity.FATAL, "Schema validation failed -> " + e.getMessage(), new ErrorLocation(location));
        }
    }

    @Override
    public void exitNsSchemaName(JsonTParser.NsSchemaNameContext ctx) {
        String name = StringUtils.removeQuotes(ctx.SCHEMAID().getText());
        if (StringUtils.isBlank(name)) {
            String location = String.format("Schema name %s cannot be null or blank", name);
            errorCollector.report(new ValidationError(Severity.FATAL, "Schema name cannot be empty", new ErrorLocation(location)));
        } else if (name.length() > JsonTProperties.getMaxLengthOfIdentifier()) {
            String location = String.format("Schema name %s cannot be longer than %d", name, JsonTProperties.getMaxLengthOfIdentifier());
            errorCollector.report(new ValidationError(Severity.FATAL, "Schema Identifier Length Exceeded", new ErrorLocation(location)));
        }
        currentSchema = new SchemaNode(name);
    }

    @Override
    public void enterSchemaEntry(JsonTParser.SchemaEntryContext ctx) {
        super.enterSchemaEntry(ctx);
    }

    @Override
    public void exitSchemaEntry(JsonTParser.SchemaEntryContext ctx) {
        schemaList.add(currentSchema);
        currentSchema = null;
    }

    @Override
    public void exitNsEnumName(JsonTParser.NsEnumNameContext ctx) {
        if (enumInfo == null) {
            enumInfo = new LinkedHashMap<>();
        } else {
            enumInfo.clear();
        }
        String enumName = StringUtils.removeQuotes(ctx.getText());
        if (StringUtils.isBlank(enumName)) {
            String location = "Enum definition";
            addError(Severity.FATAL, "Enum name cannot be null or blank", new ErrorLocation(location));
        } else {
            enumInfo.put(enumName, new ArrayList<>());
        }
    }

    @Override
    public void exitEnumValueConstant(JsonTParser.EnumValueConstantContext ctx) {
        String enumName = enumInfo.entrySet().stream().findFirst().get().getKey();
        String value = StringUtils.removeQuotes(ctx.getText());
        if (StringUtils.isBlank(value)) {
            String location = "Enum definition";
            addError(Severity.FATAL, "Enum value cannot be null or blank for enum type " + enumName, new ErrorLocation(location));
        } else {
            enumInfo.get(enumName).add(value);
        }
    }

    @Override
    public void exitEnumDef(JsonTParser.EnumDefContext ctx) {
        String name = enumInfo.entrySet().stream().findFirst().get().getKey();
        List<String> list = enumInfo.get(name);
        Set<String> set = list.stream().distinct().collect(Collectors.toSet());
        if (set.size() != list.size()) {
            String location = String.format("Enum name %s", name);
            errorCollector.report(new ValidationError(Severity.FATAL, "Enum values must be unique", new ErrorLocation(location)));
        }
        EnumModel model = new EnumModel(name, set);
        schemaCatalog.addEnum(name, model);
        enumList.add(new EnumNode(name, list));
        list.clear();
    }

    @Override
    public void exitNsFieldName(JsonTParser.NsFieldNameContext ctx) {
        String currentFieldName = StringUtils.removeQuotes(ctx.getText());
        if (StringUtils.isBlank(currentFieldName)) {
            String location = String.format("Schema %s, field position %d", currentSchema.getName(), currentSchema.getFields().size() + 1);
            errorCollector.report(new ValidationError(Severity.FATAL, "Field Name Min Length not met", new ErrorLocation(location)));
        }
        fieldInfo.put("fieldName", currentFieldName);
    }

    @Override
    public void enterConstraintsSection(JsonTParser.ConstraintsSectionContext ctx) {
        currentFieldConstraints = new ArrayList<>();
    }

    @Override
    public void enterConstraint(JsonTParser.ConstraintContext ctx) {
        fieldInfo.put("constraint", new LinkedHashMap<>());
    }

    @Override
    public void exitConstraintName(JsonTParser.ConstraintNameContext ctx) {
        Map<String, Object> map = (Map<String, Object>) fieldInfo.get("constraint");
        map.put("constraintName", StringUtils.removeQuotes(ctx.getText()));
    }

    @Override
    public void exitConstraintValue(JsonTParser.ConstraintValueContext ctx) {
        Map<String, Object> map = (Map<String, Object>) fieldInfo.get("constraint");
        if (ctx.BOOLEAN() != null) {
            map.put("constraintValue", Boolean.valueOf(ctx.BOOLEAN().getText()));
        } else if (ctx.NUMBER() != null) {
            try {
                map.put("constraintValue", new BigDecimal(ctx.NUMBER().getText()));
            } catch (NumberFormatException e) {
                addError(Severity.FATAL, "Invalid Constraint value" + ctx.NUMBER().getText(), new ErrorLocation(String.format("Schema %s, field position %d", currentSchema.getName(), currentSchema.getFields().size() + 1)));
            }
        } else if (ctx.STRING() != null) {
            map.put("constraintValue", StringUtils.removeQuotes(ctx.STRING().getText()));
        }
    }

    @Override
    public void exitConstraint(JsonTParser.ConstraintContext ctx) {
        Map<String, Object> map = (Map<String, Object>) fieldInfo.get("constraint");
        String name = (String) map.get("constraintName");
        if (StringUtils.isBlank(name)) {
            String location = String.format("Schema %s, field position %d", currentSchema.getName(), currentSchema.getFields().size() + 1);
            errorCollector.report(new ValidationError(Severity.FATAL, "Constraint Name Min Length not met", new ErrorLocation(location)));
        }
        FieldConstraint.ConstraitType type = FieldConstraint.byType(name);
        Object value = map.get("constraintValue");
        currentFieldConstraints.add(new ConstraintNode(type, value));
    }

    @Override
    public void enterFieldDecl(JsonTParser.FieldDeclContext ctx) {
        fieldInfo = new LinkedHashMap<>();
        currentFieldConstraints = null;
    }

    @Override
    public void exitFieldDecl(JsonTParser.FieldDeclContext ctx) {
        FieldTypeNode ftn = createFieldTypeNode(fieldInfo);
        String fieldName = (String) fieldInfo.get("fieldName");
        currentSchema.addField(new FieldNode(currentSchema.getName(), fieldName, ftn, currentFieldConstraints));
    }

    private FieldTypeNode createFieldTypeNode(Map<String, Object> fieldInfo) {
        ValueNodeKind kind = determineValueKind();
        String type = determineFieldType();
        FieldTypeNode ftn = new FieldTypeNode(type, kind);
        boolean isOptional = (Boolean) fieldInfo.getOrDefault("optional", false);
        ftn.setOptional(isOptional);
        return ftn;
    }

    private String determineFieldType() {
        String type = (String) fieldInfo.get("baseType");
        if (StringUtils.isBlank(type)) {
            type = (String) fieldInfo.get("objectType");
        } else {
            JsonBaseType baseType = JsonBaseType.byIdentifier(type);
            if (baseType == null) {
                String location = String.format("Schema %s, field position %d", currentSchema.getName(), currentSchema.getFields().size() + 1);
                addError(Severity.FATAL, "Invalid Field Type", new ErrorLocation(location));
            }
        }
        return type;
    }

    private ValueNodeKind determineValueKind() {
        ValueNodeKind result = null;
        if (fieldInfo.containsKey("objectType")) {
            result = ValueNodeKind.OBJECT;
        } else {
            result = ValueNodeKind.SCALAR;
        }
        if (fieldInfo.containsKey("arraySuffix")) {
            result = ValueNodeKind.ARRAY;
        }
        return result;
    }

    @Override
    public void exitBaseType(JsonTParser.BaseTypeContext ctx) {
        if (!StringUtils.isBlank(ctx.getText())) {
            String currentFieldType = StringUtils.removeQuotes(ctx.getText());
            fieldInfo.put("baseType", currentFieldType);
            fieldInfo.put("fieldType", "scalar");
        }
    }

    @Override
    public void exitObjectTypeName(JsonTParser.ObjectTypeNameContext ctx) {
        if (!StringUtils.isBlank(ctx.getText())) {
            String currentFieldType = StringUtils.removeQuotes(ctx.getText());
            fieldInfo.put("objectType", currentFieldType);
            fieldInfo.put("fieldType", "object");
            unresolvedTypes.add(currentFieldType);
        }
    }

    @Override
    public void exitOptionalMark(JsonTParser.OptionalMarkContext ctx) {
        if (!StringUtils.isBlank(ctx.getText())) {
            fieldInfo.put("optional", true);
        }
    }

    @Override
    public void exitArraySuffix(JsonTParser.ArraySuffixContext ctx) {
        if (!StringUtils.isBlank(ctx.getText())) {
            fieldInfo.put("arraySuffix", true);
        }
    }

    protected void addError(Severity severity, String s, ErrorLocation location) {
        this.errorCollector.report(new ValidationError(severity, s, location));
        if (severity.isFatal()) {
            throw new ParseCancellationException("Fatal error occured at " + location + ", with message: " + s + ".");
        }
    }

    protected void clearErrors() {
        errorCollector.clearErrors();
    }

    protected List<ValidationError> getRowErrors() {
        return errorCollector.all();
    }
}
