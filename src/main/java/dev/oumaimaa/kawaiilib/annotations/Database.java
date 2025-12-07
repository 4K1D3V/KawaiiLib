package dev.oumaimaa.kawaiilib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Database {
    String type() default "SQLITE"; // "SQLITE", "MYSQL", "H2"

    String url() default "";

    String user() default "";

    String password() default "";
}