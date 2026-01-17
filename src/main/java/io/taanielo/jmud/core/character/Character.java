package io.taanielo.jmud.core.character;

import java.util.List;

import io.taanielo.jmud.core.world.RoomId;

public interface Character {
    CharacterId id();
    String name();
    RoomId currentRoomId();
    Inventory inventory();
    Stats stats();
    List<StatusEffect> statusEffects();
}
