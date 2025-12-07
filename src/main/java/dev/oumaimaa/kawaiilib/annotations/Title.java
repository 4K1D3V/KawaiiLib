package dev.oumaimaa.kawaiilib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Title {
    String title();

    String subtitle() default "";

    int fadeIn() default 10;

    int stay() default 70;

    int fadeOut() default 20;
}