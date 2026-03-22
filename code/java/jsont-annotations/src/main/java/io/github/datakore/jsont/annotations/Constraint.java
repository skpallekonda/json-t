package io.github.datakore.jsont.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface Constraint {
    ConstraintName name();

    String value();
}
