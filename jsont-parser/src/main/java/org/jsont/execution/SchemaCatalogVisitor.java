package org.jsont.execution;

import org.jsont.JsonTConfig;
import org.jsont.exception.SchemaException;
import org.jsont.grammar.JsonTBaseVisitor;
import org.jsont.grammar.JsonTParser;
import org.jsont.grammar.JsonTParser.ConstraintValueContext;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.raw.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class SchemaCatalogVisitor extends JsonTBaseVisitor<Object> {
    private List<SchemaNode> schemaNodes = null;
    private AtomicInteger rowIndex = new AtomicInteger(0);
    private JsonTNode jsonTNode;

    private static JsonTParser.SchemasSectionContext validateAndGetRootEntry(JsonTParser.JsonTContext ctx) {
        Objects.requireNonNull(ctx.catalog());
        Objects.requireNonNull(ctx.catalog().schemasSection());
        Objects.requireNonNull(ctx.catalog().schemasSection().schemaEntry(), "SchemasEntry must be present");
        return ctx.catalog().schemasSection();
    }

    public JsonTNode getJsonTDef() {
        return jsonTNode;
    }

    @Override
    public Object visitJsonT(JsonTParser.JsonTContext ctx) {
        JsonTNode.JsonTNodeBuilder builder = JsonTNode.builder();
        if (ctx.catalog() != null) {
            JsonTParser.SchemasSectionContext re = validateAndGetRootEntry(ctx);
            List<SchemaNode> schemaNodeList = visitSchemas(re);
            builder = builder.schemaNodeList(schemaNodeList);
            List<EnumNode> enumDefs = visitEnums(ctx.catalog().enumsSection());
            builder = builder.enumDefs(enumDefs);
        }
        jsonTNode = builder.build();
        return jsonTNode;
    }

    @Override
    public Object visitCatalog(JsonTParser.CatalogContext ctx) {
        JsonTNode.JsonTNodeBuilder builder = JsonTNode.builder();
        List<SchemaNode> schemaList = visitSchemas(ctx.schemasSection());
        builder = builder.schemaNodeList(schemaList);
        List<EnumNode> enumsList = visitEnums(ctx.enumsSection());
        builder = builder.enumDefs(enumsList);
        jsonTNode = builder.build();
        return jsonTNode;
    }

    private List<EnumNode> visitEnums(JsonTParser.EnumsSectionContext enumsSectionContext) {
        if (enumsSectionContext == null || enumsSectionContext.enumDef() == null || enumsSectionContext.enumDef().size() < 1) {
            return Collections.emptyList();
        }
        List<EnumNode> enumDefs = new ArrayList<>(enumsSectionContext.enumDef().size());
        for (JsonTParser.EnumDefContext edc : enumsSectionContext.enumDef()) {
            EnumNode enumDef = (EnumNode) visitEnumDef(edc);
            if (enumDef != null) {
                enumDefs.add(enumDef);
            }
        }
        return enumDefs;
    }

    @Override
    public Object visitEnumDef(JsonTParser.EnumDefContext ctx) {
        if (ctx == null || ctx.isEmpty() || ctx.IDENT() == null) {
            return null;
        }
        String enumName = ctx.IDENT().getText();
        if (enumName == null || enumName.isBlank() || enumName.isEmpty()) {
            throw new IllegalStateException("Enum name must be present");
        }
        if (enumName.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
            throw new SchemaException("Enum name is too long" + enumName);
        }
        if (ctx.enumBody() == null || ctx.enumBody().enumValue() == null || ctx.enumBody().enumValue().size() < 1) {
            throw new IllegalStateException("Enum body must not be empty");
        }
        List<String> values = new ArrayList<>(ctx.enumBody().enumValue().size());

        for (JsonTParser.EnumValueContext valueContext : ctx.enumBody().enumValue()) {
            String value = valueContext.IDENT().getText();
            if (value.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
                throw new SchemaException("Enum value is too long" + enumName);
            }
            values.add(value);
        }
        return new EnumNode(enumName, values);
    }

    private List<SchemaNode> visitSchemas(JsonTParser.SchemasSectionContext re) {
        schemaNodes = new ArrayList<>(re.schemaEntry().size());
        for (JsonTParser.SchemaEntryContext sec : re.schemaEntry()) {
            schemaNodes.add((SchemaNode) visitSchemaEntry(sec));
        }
        return schemaNodes;
    }

    @Override
    public Object visitSchemaEntry(JsonTParser.SchemaEntryContext ctx) {
        String schema = ctx.IDENT().getText();
        if (schema.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
            throw new SchemaException("Schema name is too long" + schema);
        }
        SchemaNode schemaSymbol = new SchemaNode();
        schemaSymbol.setName(schema);
        if (schemaNodes.contains(schemaSymbol)) {
            throw new IllegalStateException("Duplicate schema present");
        }
        List<JsonTParser.FieldDeclContext> fieldCtxList = ctx.schemaNode().fieldDecl();
        if (fieldCtxList == null || fieldCtxList.size() < 1) {
            throw new IllegalStateException("No fields present in a schema" + schema);
        }
        List<FieldNode> fieldNodes = new ArrayList<>(fieldCtxList.size());
        for (int index = 0; index < fieldCtxList.size(); index++) {
            JsonTParser.FieldDeclContext fdc = fieldCtxList.get(index);
            fieldNodes.add((FieldNode) visitFieldDecl(fdc));
        }
        schemaSymbol.setFields(fieldNodes);
        return schemaSymbol;
    }

    @Override
    public Object visitFieldDecl(JsonTParser.FieldDeclContext ctx) {

        FieldTypeNode fieldTypeNode = (FieldTypeNode) visitTypeRef(ctx.typeRef());
        // example: int[]?: customerName (minlen=10)
        String fieldName = ctx.IDENT().getText();
        if (fieldName.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
            throw new SchemaException("Field name is too long" + fieldName);
        }
        String schemaName = ((JsonTParser.SchemaEntryContext) ctx.getParent().getParent()).IDENT().getText();
        List<ConstraintNode> constraints = (List<ConstraintNode>) visitConstraintsSection(ctx.constraintsSection());
        boolean optional = ctx.optionalMark() != null && ctx.optionalMark().QMARK() != null;

        return new FieldNode(
                schemaName,
                fieldName,
                fieldTypeNode,
                constraints,
                optional);
    }

    @Override
    public Object visitTypeRef(JsonTParser.TypeRefContext ctx) {
        String typeName = ctx.IDENT().getText();
        if (typeName.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
            throw new SchemaException("Type name is too long" + typeName);
        }
        boolean array = ctx.arraySuffix() != null;
        boolean isObject = ctx.LT() != null;
        return new FieldTypeNode(typeName, array, isObject);
    }


    @Override
    public Object visitConstraintsSection(JsonTParser.ConstraintsSectionContext ctx) {
        if (ctx == null || ctx.constraint() == null || ctx.constraint().size() < 1)
            return Collections.emptyList();
        List<ConstraintNode> list = new ArrayList<>(ctx.constraint().size());
        for (int i = 0; i < ctx.constraint().size(); i++) {
            list.add((ConstraintNode) visitConstraint(ctx.constraint(i)));
        }
        return list;
    }

    @Override
    public Object visitConstraint(JsonTParser.ConstraintContext ctx) {
        String name = ctx.constraintName().IDENT().getText();
        if (name.length() > JsonTConfig.getMaxLengthOfIdentifier()) {
            throw new SchemaException("Constraint name is too long" + name);
        }
        ValueNode value = (ValueNode) visitConstraintValue(ctx.constraintValue());
        ConstraintNode cn = new ConstraintNode(name, value);
        return cn;
    }

    @Override
    public Object visitConstraintValue(ConstraintValueContext ctx) {
        if (ctx.NUMBER() != null) {
            return new BigDecimal(ctx.NUMBER().getText());
        } else if (ctx.STRING() != null) {
            return ctx.STRING().getText();
        } else if (ctx.BOOLEAN() != null) {
            return Boolean.valueOf(ctx.BOOLEAN().getText());
        }
        return null;
    }
}
