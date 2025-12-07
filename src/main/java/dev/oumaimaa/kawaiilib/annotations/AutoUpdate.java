package dev.oumaimaa.kawaiilib.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoUpdate {
    String platform(); // "SPIGOT", "HANGAR", "MODRINTH"

    String resourceId();

    boolean autoDownload() default false;
}