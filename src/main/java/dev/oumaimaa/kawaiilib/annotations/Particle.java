package dev.oumaimaa.kawaiilib.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Particle {
    org.bukkit.Particle particle();

    int count() default 10;

    double offsetX() default 0.0;

    double offsetY() default 0.0;

    double offsetZ() default 0.0;

    double extra() default 0.0;
}