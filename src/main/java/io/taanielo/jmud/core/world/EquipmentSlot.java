package io.taanielo.jmud.core.world;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EquipmentSlot {
    WEAPON("weapon"),
    OFFHAND("offhand"),
    HEAD("head"),
    CHEST("chest"),
    LEGS("legs"),
    HANDS("hands"),
    FEET("feet");

    private static final Map<String, EquipmentSlot> LOOKUP = new ConcurrentHashMap<>();

    static {
        for (EquipmentSlot slot : values()) {
            LOOKUP.put(slot.id, slot);
        }
    }

    private final String id;

    EquipmentSlot(String id) {
        this.id = Objects.requireNonNull(id, "Slot id is required");
    }

    public String id() {
        return id;
    }

    @JsonValue
    public String jsonValue() {
        return id;
    }

    @JsonCreator
    public static EquipmentSlot fromJson(String raw) {
        return fromId(raw);
    }

    public static EquipmentSlot fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return LOOKUP.get(normalized);
    }
}
