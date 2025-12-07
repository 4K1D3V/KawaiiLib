package dev.oumaimaa.kawaiilib.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {

    private final Map<String, Map<UUID, Instant>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Check if a player is on cooldown for a specific action
     */
    public boolean isOnCooldown(UUID uuid, long seconds) {
        return isOnCooldown("default", uuid, Duration.ofSeconds(seconds));
    }

    /**
     * Check if a player is on cooldown for a named action
     */
    public boolean isOnCooldown(String key, UUID uuid, Duration duration) {
        Map<UUID, Instant> keyCooldowns = cooldowns.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        Instant lastUse = keyCooldowns.get(uuid);
        if (lastUse == null) {
            return false;
        }

        Instant now = Instant.now();
        return Duration.between(lastUse, now).compareTo(duration) < 0;
    }

    /**
     * Set a cooldown for a player
     */
    public void setCooldown(UUID uuid, long seconds) {
        setCooldown("default", uuid);
    }

    /**
     * Set a cooldown for a named action
     */
    public void setCooldown(String key, UUID uuid) {
        Map<UUID, Instant> keyCooldowns = cooldowns.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        keyCooldowns.put(uuid, Instant.now());
    }

    /**
     * Get remaining cooldown time in seconds
     */
    public long getRemaining(UUID uuid) {
        return getRemaining("default", uuid);
    }

    /**
     * Get remaining cooldown time for a named action
     */
    public long getRemaining(String key, UUID uuid) {
        Map<UUID, Instant> keyCooldowns = cooldowns.get(key);
        if (keyCooldowns == null) {
            return 0;
        }

        Instant lastUse = keyCooldowns.get(uuid);
        if (lastUse == null) {
            return 0;
        }

        long elapsed = Duration.between(lastUse, Instant.now()).toSeconds();
        return Math.max(0, elapsed);
    }

    /**
     * Reset a cooldown for a player
     */
    public void resetCooldown(String key, UUID uuid) {
        Map<UUID, Instant> keyCooldowns = cooldowns.get(key);
        if (keyCooldowns != null) {
            keyCooldowns.remove(uuid);
        }
    }

    /**
     * Reset all cooldowns for a player
     */
    public void resetAllCooldowns(UUID uuid) {
        cooldowns.values().forEach(map -> map.remove(uuid));
    }

    /**
     * Clear all cooldowns (useful for cleanup)
     */
    public void clearAll() {
        cooldowns.clear();
    }

    /**
     * Remove expired cooldowns for cleanup
     */
    public void cleanupExpired(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        cooldowns.values().forEach(map ->
                map.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff))
        );
    }
}