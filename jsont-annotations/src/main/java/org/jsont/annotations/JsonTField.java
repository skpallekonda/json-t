package org.jsont.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface JsonTField {
    String name() default "";

    FieldKind kind() default FieldKind.e_object;

    boolean array() default false;

    Constraint[] constraints() default {};

    String[] aliases() default {};

    boolean optional() default false;
}
