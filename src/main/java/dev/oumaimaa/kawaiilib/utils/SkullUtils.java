package dev.oumaimaa.kawaiilib.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class SkullUtils {
    public static @NotNull ItemStack createSkull(String owner) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        skull.setItemMeta(meta);
        return skull;
    }

    @Contract("_ -> new")
    public static @NotNull ItemStack createSkullFromUrl(String url) {
        // Use reflection for GameProfile if needed, but avoid NMS
        // For simplicity, use base64 texture
        // Implement if required
        return new ItemStack(Material.PLAYER_HEAD);
    }
}