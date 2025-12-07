package dev.oumaimaa.kawaiilib.annotations;

import org.bukkit.event.inventory.InventoryType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Menu {
    String title();

    int rows() default 3;

    InventoryType type() default InventoryType.CHEST;
}