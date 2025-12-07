package dev.oumaimaa.kawaiilib.annotations;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BossBar {
    String title();

    BarColor color() default BarColor.GREEN;

    BarStyle style() default BarStyle.SOLID;

    float progress() default 1.0f;
}