package dev.oumaimaa.kawaiilib.annotations;

import org.bukkit.SoundCategory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sound {
    String sound();

    float volume() default 1.0f;

    float pitch() default 1.0f;

    SoundCategory category() default SoundCategory.MASTER;
}