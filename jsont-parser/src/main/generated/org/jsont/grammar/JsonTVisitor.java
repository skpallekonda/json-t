// Generated from org\jsont\grammar\JsonT.g4 by ANTLR 4.13.0
package org.jsont.grammar;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link JsonTParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 *            operations with no return type.
 */
public interface JsonTVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link JsonTParser#jsonT}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitJsonT(JsonTParser.JsonTContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#catalog}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitCatalog(JsonTParser.CatalogContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#data}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitData(JsonTParser.DataContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#schemasSection}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSchemasSection(JsonTParser.SchemasSectionContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#schemaEntry}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSchemaEntry(JsonTParser.SchemaEntryContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#schemaNode}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSchemaNode(JsonTParser.SchemaNodeContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#fieldDecl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitFieldDecl(JsonTParser.FieldDeclContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#optionalMark}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitOptionalMark(JsonTParser.OptionalMarkContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#constraintsSection}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitConstraintsSection(JsonTParser.ConstraintsSectionContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#constraint}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitConstraint(JsonTParser.ConstraintContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#constraintName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitConstraintName(JsonTParser.ConstraintNameContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#constraintValue}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitConstraintValue(JsonTParser.ConstraintValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#enumsSection}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitEnumsSection(JsonTParser.EnumsSectionContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#enumDef}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitEnumDef(JsonTParser.EnumDefContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#enumBody}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitEnumBody(JsonTParser.EnumBodyContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#enumValue}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitEnumValue(JsonTParser.EnumValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#dataSchemaSection}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitDataSchemaSection(JsonTParser.DataSchemaSectionContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#dataSection}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitDataSection(JsonTParser.DataSectionContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#dataRow}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitDataRow(JsonTParser.DataRowContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#value}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitValue(JsonTParser.ValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#scalarValue}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitScalarValue(JsonTParser.ScalarValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#objectValue}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitObjectValue(JsonTParser.ObjectValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#arrayValue}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArrayValue(JsonTParser.ArrayValueContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#typeRef}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTypeRef(JsonTParser.TypeRefContext ctx);

    /**
     * Visit a parse tree produced by {@link JsonTParser#arraySuffix}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArraySuffix(JsonTParser.ArraySuffixContext ctx);
}