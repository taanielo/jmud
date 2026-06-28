package io.taanielo.jmud.core.bank;

import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of a bank NPC loaded from data files.
 *
 * <p>A bank is associated with a single room via {@link #roomId()}.
 * Players interact with the bank to safely store and retrieve gold.
 */
public record Bank(
    BankId id,
    String name,
    RoomId roomId
) {
    public Bank {
        Objects.requireNonNull(id, "Bank id is required");
        Objects.requireNonNull(name, "Bank name is required");
        Objects.requireNonNull(roomId, "Bank roomId is required");
    }
}
