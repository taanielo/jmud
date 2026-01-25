package io.taanielo.jmud.core.tick.system;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.tick.Tickable;

public class CooldownSystem implements Tickable {

    private final Map<String, AtomicInteger> cooldowns = new ConcurrentHashMap<>();

    public void register(String key, int ticks) {
        Objects.requireNonNull(key, "Cooldown key is required");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Cooldown key must not be blank");
        }
        if (ticks <= 0) {
            cooldowns.remove(key);
            return;
        }
        cooldowns.put(key, new AtomicInteger(ticks));
    }

    public boolean isOnCooldown(String key) {
        AtomicInteger remaining = cooldowns.get(key);
        return remaining != null && remaining.get() > 0;
    }

    public int remainingTicks(String key) {
        AtomicInteger remaining = cooldowns.get(key);
        return remaining == null ? 0 : remaining.get();
    }

    public void clear() {
        cooldowns.clear();
    }

    @Override
    public void tick() {
        for (Map.Entry<String, AtomicInteger> entry : cooldowns.entrySet()) {
            int remaining = entry.getValue().decrementAndGet();
            if (remaining <= 0) {
                cooldowns.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
