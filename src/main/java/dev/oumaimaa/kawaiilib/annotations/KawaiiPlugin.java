package dev.oumaimaa.kawaiilib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KawaiiPlugin {
    String name();

    String version();

    String[] authors() default {};

    String description() default "";
}