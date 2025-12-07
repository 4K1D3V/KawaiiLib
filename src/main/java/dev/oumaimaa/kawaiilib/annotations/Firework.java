package dev.oumaimaa.kawaiilib.annotations;

import org.bukkit.FireworkEffect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Firework {
    FireworkEffect.Type type() default FireworkEffect.Type.BALL;

    int power() default 1;
}