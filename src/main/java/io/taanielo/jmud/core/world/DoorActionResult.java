package io.taanielo.jmud.core.world;

import org.jspecify.annotations.Nullable;

/**
 * Result of a lock or unlock door action.
 *
 * @param success       whether the action was performed
 * @param playerMessage message shown to the acting player
 * @param roomMessage   message broadcast to other room occupants, or {@code null} on failure
 */
public record DoorActionResult(boolean success, String playerMessage, @Nullable String roomMessage) {
}
