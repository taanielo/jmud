package io.taanielo.jmud.core.character;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

public class BaseCharacter implements Character {

    private final CharacterId id;
    private final String name;
    private final RoomId currentRoomId;
    private final Inventory inventory;
    private final Stats stats;
    private final List<StatusEffect> statusEffects;

    public BaseCharacter(
        CharacterId id,
        String name,
        RoomId currentRoomId,
        Inventory inventory,
        Stats stats,
        List<StatusEffect> statusEffects
    ) {
        this.id = Objects.requireNonNull(id, "Character id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Character name must not be blank");
        }
        this.name = name;
        this.currentRoomId = Objects.requireNonNull(currentRoomId, "Current room id is required");
        this.inventory = Objects.requireNonNull(inventory, "Inventory is required");
        this.stats = Objects.requireNonNull(stats, "Stats are required");
        this.statusEffects = List.copyOf(Objects.requireNonNull(statusEffects, "Status effects are required"));
    }

    @Override
    public CharacterId id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RoomId currentRoomId() {
        return currentRoomId;
    }

    @Override
    public Inventory inventory() {
        return inventory;
    }

    @Override
    public Stats stats() {
        return stats;
    }

    @Override
    public List<StatusEffect> statusEffects() {
        return statusEffects;
    }
}
