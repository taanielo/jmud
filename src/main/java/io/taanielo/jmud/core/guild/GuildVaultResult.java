package io.taanielo.jmud.core.guild;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.Item;

/**
 * Outcome of a guild item-vault operation that returns an item to the caller (currently
 * {@code GUILD CLAIM}). Carries the usual success flag and player-facing message plus, on success, the
 * updated guild snapshot and the claimed {@link Item} so the caller can hand the item to the player
 * without re-querying.
 *
 * @param success {@code true} when the operation succeeded
 * @param message the message to show the invoking player
 * @param guild   the updated guild snapshot, or {@code null} on failure
 * @param item    the item removed from the vault, or {@code null} on failure
 */
public record GuildVaultResult(boolean success, String message, @Nullable Guild guild, @Nullable Item item) {

    /** Creates a failed result with the given message and no guild or item. */
    public static GuildVaultResult failure(String message) {
        return new GuildVaultResult(false, message, null, null);
    }

    /** Creates a successful result carrying the updated guild snapshot and the claimed item. */
    public static GuildVaultResult success(String message, Guild guild, Item item) {
        return new GuildVaultResult(true, message, guild, item);
    }
}
