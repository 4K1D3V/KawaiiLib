package dev.oumaimaa.kawaiilib.managers.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for creating visual effects
 */
public final class VisualEffects {

    private static final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private VisualEffects() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Send an action bar message to a player
     */
    public static void sendActionBar(@NotNull Player player, @NotNull Component message, int durationTicks) {
        player.sendActionBar(message);

        // Keep resending for the duration
        if (durationTicks > 20) {
            Bukkit.getScheduler().runTaskLater(
                    Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("KawaiiLib")),
                    () -> player.sendActionBar(Component.empty()),
                    durationTicks
            );
        }
    }

    /**
     * Send a title to a player
     */
    public static void sendTitle(@NotNull Player player,
                                 @NotNull Component title,
                                 @NotNull Component subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        Title titleObj = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        );

        player.showTitle(titleObj);
    }

    /**
     * Create a boss bar for a player
     */
    public static @NotNull BossBar createBossBar(@NotNull Player player,
                                                 @NotNull Component title,
                                                 BarColor color,
                                                 BarStyle style,
                                                 float progress) {
        // Remove existing boss bar
        removeBossBar(player);

        BossBar bossBar = Bukkit.createBossBar(
                title.toString(),
                color,
                style
        );

        bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        bossBar.addPlayer(player);

        activeBossBars.put(player.getUniqueId(), bossBar);

        return bossBar;
    }

    /**
     * Update an existing boss bar
     */
    public static void updateBossBar(@NotNull Player player,
                                     @NotNull Component title,
                                     float progress) {
        BossBar bossBar = activeBossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.setTitle(title.toString());
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
    }

    /**
     * Remove a boss bar from a player
     */
    public static void removeBossBar(@NotNull Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    /**
     * Spawn particles at a location
     */
    public static void spawnParticles(@NotNull Location location,
                                      @NotNull Particle particle,
                                      int count,
                                      double offsetX, double offsetY, double offsetZ,
                                      double extra) {
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    /**
     * Create a particle circle effect
     */
    public static void spawnParticleCircle(@NotNull Location center,
                                           @NotNull Particle particle,
                                           double radius,
                                           int points) {
        World world = center.getWorld();
        if (world == null) return;

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location particleLoc = new Location(world, x, center.getY(), z);
            world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Create a particle helix effect
     */
    public static void spawnParticleHelix(@NotNull Location base,
                                          @NotNull Particle particle,
                                          double height,
                                          double radius,
                                          int points) {
        World world = base.getWorld();
        if (world == null) return;

        for (int i = 0; i < points; i++) {
            double y = (height / points) * i;
            double angle = (2 * Math.PI / points) * i * 4; // 4 full rotations

            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);

            Location particleLoc = new Location(world, x, base.getY() + y, z);
            world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawn a firework at a location
     */
    public static void spawnFirework(@NotNull Location location,
                                     FireworkEffect.@NotNull Type type,
                                     @NotNull Color color,
                                     int power) {
        World world = location.getWorld();
        if (world == null) return;

        Firework firework = world.spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();

        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(color)
                .withFade(Color.WHITE)
                .flicker(true)
                .trail(true)
                .build();

        meta.addEffect(effect);
        meta.setPower(power);
        firework.setFireworkMeta(meta);
    }

    /**
     * Play a sound for a player
     * FIXED: Removed deprecated Sound.valueOf() usage
     */
    public static void playSound(@NotNull Player player,
                                 @NotNull String soundName,
                                 float volume,
                                 float pitch,
                                 SoundCategory category) {
        // Try to parse as a Sound key first
        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundName.toLowerCase()));
            if (sound != null) {
                player.playSound(player.getLocation(), sound, category, volume, pitch);
                return;
            }
        } catch (Exception ignored) {
            // If that fails, fall through to custom sound
        }

        // Fallback to custom sound string
        player.playSound(player.getLocation(), soundName, category, volume, pitch);
    }

    /**
     * Play a sound at a specific location
     * FIXED: Removed deprecated Sound.valueOf() usage
     */
    public static void playSoundAtLocation(@NotNull Location location,
                                           @NotNull String soundName,
                                           float volume,
                                           float pitch,
                                           SoundCategory category) {
        World world = location.getWorld();
        if (world == null) return;

        // Try to parse as a Sound key first
        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundName.toLowerCase()));
            if (sound != null) {
                world.playSound(location, sound, category, volume, pitch);
                return;
            }
        } catch (Exception ignored) {
            // If that fails, fall through to custom sound
        }

        // Fallback to custom sound string
        world.playSound(location, soundName, category, volume, pitch);
    }

    /**
     * Cleanup all active boss bars
     */
    public static void cleanup() {
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();
    }
}