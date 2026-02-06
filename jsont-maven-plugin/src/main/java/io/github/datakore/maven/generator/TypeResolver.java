package io.github.datakore.maven.generator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.types.*;

import java.util.List;

public class TypeResolver {
    private final GeneratorContext context;

    public TypeResolver(GeneratorContext context) {
        this.context = context;
    }

    public TypeName getTypeName(ValueType valueType) {
        if (valueType instanceof ScalarType) {
            ScalarType scalar = (ScalarType) valueType;
            if (isTemporalMapped(scalar)) {
                return ClassName.bestGuess(context.getTemporalTypeMapping().get(scalar.elementType().identifier()));
            }
            return getScalarTypeName(scalar.elementType());
        } else if (valueType instanceof ObjectType) {
            ObjectType obj = (ObjectType) valueType;
            return ClassName.get(context.getPackageName(), getClassName(obj.type()));
        } else if (valueType instanceof ArrayType) {
            ArrayType array = (ArrayType) valueType;
            TypeName componentType = getTypeName(array.getElementType());
            if ("Set".equalsIgnoreCase(context.getArrayHandler())) {
                return ParameterizedTypeName.get(ClassName.get(java.util.Set.class), componentType.box());
            } else if ("Array".equalsIgnoreCase(context.getArrayHandler())) {
                return ArrayTypeName.of(componentType);
            } else { // List is default
                return ParameterizedTypeName.get(ClassName.get(List.class), componentType.box());
            }
        } else if (valueType instanceof EnumType) {
            EnumType enumType = (EnumType) valueType;
            return ClassName.get(context.getPackageName(), getClassName(enumType.type()));
        }
        return TypeName.OBJECT;
    }

    public boolean isTemporalMapped(ScalarType scalarType) {
        return context.getTemporalTypeMapping() != null && context.getTemporalTypeMapping().containsKey(scalarType.elementType().identifier());
    }

    private TypeName getScalarTypeName(JsonBaseType baseType) {
        switch (baseType.name()) {
            case "I16":
            case "U16":
                return TypeName.SHORT;
            case "I32":
            case "U32":
                return TypeName.INT;
            case "I64":
            case "U64":
                return TypeName.LONG;
            case "D32":
                return TypeName.FLOAT;
            case "D64":
                return TypeName.DOUBLE;
            case "D128":
                return ClassName.get(java.math.BigDecimal.class);
            case "BOOLEAN":
                return TypeName.BOOLEAN;
            case "STRING":
            case "NSTR":
            case "URI":
                return ClassName.get(String.class);
            case "UUID":
                return ClassName.get(java.util.UUID.class);
            case "BIN":
            case "OID":
            case "HEX":
                return ArrayTypeName.of(TypeName.BYTE);
            case "DATE":
                return ClassName.get(java.time.LocalDate.class);
            case "TIME":
                return ClassName.get(java.time.LocalTime.class);
            case "DATETIME":
                return ClassName.get(java.time.LocalDateTime.class);
            case "TIMESTAMP":
                return ClassName.get(java.sql.Timestamp.class);
            case "TSZ":
            case "INSTZ":
                return ClassName.get(java.time.ZonedDateTime.class);
            case "INST":
                return ClassName.get(java.time.Instant.class);
            case "YEAR":
                return ClassName.get(java.time.Year.class);
            case "MON":
                return ClassName.get(java.time.Month.class);
            case "DAY":
            case "MNDAY":
                return ClassName.get(java.time.MonthDay.class);
            case "YEARMON":
                return ClassName.get(java.time.YearMonth.class);
            default:
                return ClassName.get(Object.class);
        }
    }

    public String getClassName(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public String capitalize(String str) {
        return getClassName(str);
    }
}
