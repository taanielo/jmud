package io.taanielo.jmud.core.guild;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;

/**
 * A single entry in a guild's shared item vault: the stored {@link Item} together with the
 * {@link Username} of the member who deposited it.
 *
 * <p>Instances are immutable value objects. The item itself is the same domain object that was moved
 * out of a member's inventory, so vault operations move items rather than copy them — no item is ever
 * duplicated or destroyed by storing or claiming.
 *
 * @param item      the item held in the vault
 * @param depositor the member who deposited the item
 */
public record VaultedItem(Item item, Username depositor) {

    public VaultedItem {
        Objects.requireNonNull(item, "item is required");
        Objects.requireNonNull(depositor, "depositor is required");
    }
}
