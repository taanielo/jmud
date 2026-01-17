package io.taanielo.jmud.core.character;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.world.RoomId;

public class PlayerCharacter implements Character {

    private final User user;
    private final Character delegate;

    public PlayerCharacter(User user, CharacterId id, String name, RoomId roomId, Inventory inventory, Stats stats, List<StatusEffect> statusEffects) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.delegate = new BaseCharacter(id, name, roomId, inventory, stats, statusEffects);
    }

    public User user() {
        return user;
    }

    @Override
    public CharacterId id() {
        return delegate.id();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public RoomId currentRoomId() {
        return delegate.currentRoomId();
    }

    @Override
    public Inventory inventory() {
        return delegate.inventory();
    }

    @Override
    public Stats stats() {
        return delegate.stats();
    }

    @Override
    public List<StatusEffect> statusEffects() {
        return delegate.statusEffects();
    }
}
