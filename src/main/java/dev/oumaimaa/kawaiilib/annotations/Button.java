package dev.oumaimaa.kawaiilib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Button {
    int slot();

    String item(); // Serialized item string, e.g., "DIAMOND_SWORD{name='Sword'}"
}