package io.github.datakore.jsont.model;

/**
 * One arm of an {@code anyOf} field declaration.
 *
 * <p>A variant is either a scalar type (e.g. {@code str}, {@code i32}) or a
 * schema reference (e.g. {@code <Person>}, {@code <TournamentPhase>}).
 *
 * <p>At parse/read time, variants are tried left-to-right within each JSON
 * token-type category; the first match wins.  Schema designers are responsible
 * for ensuring that variant values are sufficiently disjoint for their use case.
 */
public sealed interface AnyOfVariant {

    /** A scalar type arm (e.g. {@code str}, {@code i32}). */
    record Scalar(ScalarType type) implements AnyOfVariant {}

    /**
     * A schema-reference arm (e.g. {@code <Person>}, {@code <TournamentPhase>}).
     * The name refers to either an enum or an object schema in the same namespace.
     */
    record SchemaRef(String name) implements AnyOfVariant {}

    /** Factory: scalar arm. */
    static AnyOfVariant scalar(ScalarType type) {
        return new Scalar(type);
    }

    /** Factory: schema-reference arm. */
    static AnyOfVariant schemaRef(String name) {
        return new SchemaRef(name);
    }

    /** Returns {@code true} if this is a scalar arm. */
    default boolean isScalar() {
        return this instanceof Scalar;
    }

    /** Returns {@code true} if this is a schema-ref arm. */
    default boolean isSchemaRef() {
        return this instanceof SchemaRef;
    }
}
