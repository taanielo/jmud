package io.taanielo.jmud.core.bank;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Application service for bank interactions: depositing and withdrawing gold, and
 * storing and claiming items in a player's personal vault.
 *
 * <p>All operations are stateless with respect to the bank; the {@link Player}
 * passed in is never mutated. Callers receive an updated {@link Player} in the
 * returned {@link BankTransactionResult} on success.
 *
 * <p>Banked gold survives death and is never lost to mob drops or corpse decay.
 * Gold can only be moved between carried and banked balances — no gold is ever
 * created or destroyed. The same guarantee applies to vaulted items: they are
 * simply moved between carried inventory and the vault, never duplicated or lost.
 */
@Slf4j
public class BankService {

    private final BankRepository bankRepository;
    private final int vaultCapacity;

    public BankService(BankRepository bankRepository) {
        this(bankRepository, BankSettings.vaultCapacity());
    }

    public BankService(BankRepository bankRepository, int vaultCapacity) {
        this.bankRepository = Objects.requireNonNull(bankRepository, "bankRepository is required");
        this.vaultCapacity = Math.max(0, vaultCapacity);
    }

    /**
     * Returns the base (tier-0) maximum number of items any player may keep in their vault before
     * purchasing any {@code VAULT UPGRADE} tier.
     *
     * @return the configured base vault capacity
     */
    public int vaultCapacity() {
        return vaultCapacity;
    }

    /**
     * Returns the effective vault capacity for the given player: the base capacity plus the slot
     * bonus of the player's purchased {@link VaultUpgradeTier}.
     *
     * @param player the player whose capacity to compute
     * @return the number of item slots this player's vault can hold right now
     */
    public int effectiveVaultCapacity(Player player) {
        Objects.requireNonNull(player, "player is required");
        return vaultCapacity + VaultUpgradeTier.forRank(player.vault().capacityTier()).slotBonus();
    }

    /**
     * Attempts to buy the next {@link VaultUpgradeTier} for the given player, permanently raising
     * their personal vault capacity in exchange for carried gold.
     *
     * <p>Fails without any state change when the player is already at the top tier or is not
     * carrying enough gold. On success the cost is deducted from carried gold only and the player's
     * persisted capacity tier advances by one; purchased capacity is never lost.
     *
     * @param player the upgrading player; never mutated
     * @return a result describing success or failure
     */
    public BankTransactionResult upgradeVault(Player player) {
        Objects.requireNonNull(player, "player is required");
        VaultUpgradeTier current = VaultUpgradeTier.forRank(player.vault().capacityTier());
        Optional<VaultUpgradeTier> nextTier = current.next();
        if (nextTier.isEmpty()) {
            return BankTransactionResult.failure(
                "Your vault is already at its maximum capacity of " + effectiveVaultCapacity(player)
                    + " slots. There is nothing more to upgrade.");
        }
        VaultUpgradeTier next = nextTier.get();
        int cost = next.upgradeCost();
        if (player.getGold() < cost) {
            return BankTransactionResult.failure(
                "Upgrading your vault costs " + cost + " gold, but you are only carrying "
                    + player.getGold() + " gold.");
        }
        int newCapacity = vaultCapacity + next.slotBonus();
        Player updated = player.addGold(-cost).withVault(player.vault().withCapacityTier(next.rank()));
        return BankTransactionResult.success(
            "You pay " + cost + " gold to expand your vault to " + newCapacity + " slots. "
                + "Carried: " + updated.getGold() + " gold.",
            updated
        );
    }

    /**
     * Returns the bank located in the given room, if any.
     *
     * @param roomId the room to search
     * @return the bank in the room, or empty
     */
    public Optional<Bank> findBankInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        try {
            return bankRepository.findByRoomId(roomId);
        } catch (BankRepositoryException e) {
            log.warn("Failed to look up bank in room {}: {}", roomId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deposits the given amount from the player's carried gold into the bank.
     *
     * <p>Fails if {@code amount} is zero or negative, or if the player does not
     * carry enough gold.
     *
     * @param player the depositing player
     * @param amount the amount to deposit; must be positive
     * @return a result describing success or failure
     */
    public BankTransactionResult deposit(Player player, int amount) {
        Objects.requireNonNull(player, "player is required");
        if (amount <= 0) {
            return BankTransactionResult.failure("You must deposit a positive amount of gold.");
        }
        if (player.getGold() < amount) {
            return BankTransactionResult.failure(
                "You do not have enough gold. You are only carrying "
                    + player.getGold() + " gold.");
        }
        Player updated = player.addGold(-amount).addBankedGold(amount);
        return BankTransactionResult.success(
            "You deposit " + amount + " gold. "
                + "Carried: " + updated.getGold() + " gold. "
                + "Banked: " + updated.getBankedGold() + " gold.",
            updated
        );
    }

    /**
     * Withdraws the given amount from the player's banked gold into carried gold.
     *
     * <p>Fails if {@code amount} is zero or negative, or if the bank does not
     * hold enough gold for this player.
     *
     * @param player the withdrawing player
     * @param amount the amount to withdraw; must be positive
     * @return a result describing success or failure
     */
    public BankTransactionResult withdraw(Player player, int amount) {
        Objects.requireNonNull(player, "player is required");
        if (amount <= 0) {
            return BankTransactionResult.failure("You must withdraw a positive amount of gold.");
        }
        if (player.getBankedGold() < amount) {
            return BankTransactionResult.failure(
                "You do not have that much gold in the bank. Your balance is "
                    + player.getBankedGold() + " gold.");
        }
        Player updated = player.addBankedGold(-amount).addGold(amount);
        return BankTransactionResult.success(
            "You withdraw " + amount + " gold. "
                + "Carried: " + updated.getGold() + " gold. "
                + "Banked: " + updated.getBankedGold() + " gold.",
            updated
        );
    }

    /**
     * Moves an item matching {@code itemName} from the player's carried inventory into their
     * vault, unequipping it first if worn (mirroring GIVE/DROP behaviour).
     *
     * <p>Fails without any state change if the name is blank, no carried item matches, or the
     * vault is already at the player's {@link #effectiveVaultCapacity(Player) effective capacity}.
     *
     * @param player   the storing player; never mutated
     * @param itemName the name or id of the item to store
     * @return a result describing success or failure
     */
    public BankTransactionResult storeItem(Player player, String itemName) {
        Objects.requireNonNull(player, "player is required");
        String normalized = itemName == null ? "" : itemName.trim();
        if (normalized.isEmpty()) {
            return BankTransactionResult.failure("Store what? Usage: STORE <item name>");
        }
        Item item = matchItem(player.getInventory(), normalized);
        if (item == null) {
            return BankTransactionResult.failure("You aren't carrying '" + normalized + "'.");
        }
        if (player.getBankedItems().size() >= effectiveVaultCapacity(player)) {
            return BankTransactionResult.failure("Your vault is full.");
        }
        PlayerEquipment equipment = player.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        Player updated = player.removeItem(item).withEquipment(equipment).addBankedItem(item);
        return BankTransactionResult.success(
            "You store " + item.getName() + " in your vault.",
            updated
        );
    }

    /**
     * Moves an item matching {@code itemName} from the player's vault back into carried inventory.
     *
     * <p>Fails without any state change if the name is blank, no stored item matches, or claiming
     * the item would push the player's carried weight above {@code maxCarry}.
     *
     * @param player   the claiming player; never mutated
     * @param itemName the name or id of the item to claim
     * @param maxCarry the player's maximum carry weight (see {@code EncumbranceService#maxCarry})
     * @return a result describing success or failure
     */
    public BankTransactionResult claimItem(Player player, String itemName, int maxCarry) {
        Objects.requireNonNull(player, "player is required");
        String normalized = itemName == null ? "" : itemName.trim();
        if (normalized.isEmpty()) {
            return BankTransactionResult.failure("Claim what? Usage: CLAIM <item name>");
        }
        Item item = matchItem(player.getBankedItems(), normalized);
        if (item == null) {
            return BankTransactionResult.failure("You don't have '" + normalized + "' stored in your vault.");
        }
        int carried = 0;
        for (Item carriedItem : player.getInventory()) {
            carried += carriedItem.getWeight();
        }
        if (carried + item.getWeight() > maxCarry) {
            return BankTransactionResult.failure(
                "You can't carry " + item.getName() + " right now. Lighten your load first.");
        }
        Player updated = player.removeBankedItem(item).addItem(item);
        return BankTransactionResult.success(
            "You claim " + item.getName() + " from your vault.",
            updated
        );
    }

    /**
     * Finds the first item in {@code items} whose name or id equals or is prefixed by
     * {@code input} (case-insensitive), or {@code null} when none match.
     */
    private static Item matchItem(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }
}
