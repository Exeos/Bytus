package com.bytus.core.transformer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TInfo {
    String name() default "Name not defined.";
    String desc() default "Desc not defined.";
    int priority() default 0;
}
