package org.quick.core.model;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Tags a type as a Quick sub model; or tags a field or method in a POJO model as returning a Quick sub model */
@Retention(RUNTIME)
@Target({FIELD, METHOD, TYPE})
public @interface QuickSubModel {
}
