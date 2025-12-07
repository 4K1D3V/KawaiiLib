package dev.oumaimaa.kawaiilib.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record CooldownManager(Map<UUID, Long> cooldowns) {

    public CooldownManager() {
        this(new HashMap<>());
    }

    public boolean isOnCooldown(UUID uuid, long seconds) {
        long now = System.currentTimeMillis();
        return cooldowns.containsKey(uuid) && now - cooldowns.get(uuid) < seconds * 1000;
    }

    public void setCooldown(UUID uuid, long seconds) {
        cooldowns.put(uuid, System.currentTimeMillis() + seconds * 1000);
    }

    public long getRemaining(UUID uuid) {
        if (!cooldowns.containsKey(uuid)) return 0;
        return (cooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
    }
}