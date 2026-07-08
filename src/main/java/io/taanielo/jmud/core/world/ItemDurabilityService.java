package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;

/**
 * Domain service governing item durability: how equipped gear wears down as its wearer takes damage
 * in combat, when it breaks, and how a blacksmith restores it.
 *
 * <p>All operations are pure functions over immutable {@link Item}/{@link Player} values — nothing
 * is mutated in place. Callers on the tick thread (AGENTS.md §5) apply the returned copies. The
 * service is stateless apart from its configured degradation rate, so a single instance is safe to
 * share.
 */
public class ItemDurabilityService {

    /** Fraction of an item's {@link Item#getValue()} charged to repair one point of durability. */
    private static final double REPAIR_COST_PER_POINT_RATIO = 0.1;

    private final int degradePerHit;

    /**
     * Creates a durability service.
     *
     * @param degradePerHit the number of durability points a breakable item loses each time its
     *                      wearer is hit in combat; must be positive
     */
    public ItemDurabilityService(int degradePerHit) {
        if (degradePerHit <= 0) {
            throw new IllegalArgumentException("Degrade per hit must be positive");
        }
        this.degradePerHit = degradePerHit;
    }

    /**
     * Reduces the given item's durability by {@code points}, clamped at {@code 0}. Unbreakable items
     * are returned unchanged.
     *
     * @param item   the item to wear down
     * @param points the number of durability points to remove; must be non-negative
     * @return the worn item (a copy), or the same instance when it is unbreakable
     */
    public Item degrade(Item item, int points) {
        Objects.requireNonNull(item, "Item is required");
        if (points < 0) {
            throw new IllegalArgumentException("Points must be non-negative");
        }
        if (!item.isBreakable() || points == 0) {
            return item;
        }
        int current = item.getDurability() == null ? item.getMaxDurability() : item.getDurability();
        return item.withDurability(current - points);
    }

    /**
     * Returns whether the item may be used in combat. Broken items (durability {@code 0}) cannot;
     * everything else can.
     *
     * @param item the item to check
     * @return {@code true} unless the item is broken
     */
    public boolean isUsableInCombat(Item item) {
        Objects.requireNonNull(item, "Item is required");
        return !item.isBroken();
    }

    /**
     * Fully restores a breakable item's durability to its maximum. Unbreakable items are returned
     * unchanged.
     *
     * @param item the item to repair
     * @return the repaired item (a copy), or the same instance when it is unbreakable
     */
    public Item repair(Item item) {
        Objects.requireNonNull(item, "Item is required");
        if (!item.isBreakable()) {
            return item;
        }
        return item.withDurability(item.getMaxDurability());
    }

    /**
     * Calculates the gold cost to fully repair the given item. The cost is proportional to the
     * item's value and the amount of durability missing: {@code value * missing/max * 0.1}, rounded
     * up so any wear costs at least 1 gold. Unbreakable or undamaged items cost {@code 0}.
     *
     * @param item the item to price a repair for
     * @return the repair cost in gold, never negative
     */
    public int calculateRepairCost(Item item) {
        Objects.requireNonNull(item, "Item is required");
        if (!item.isBreakable()) {
            return 0;
        }
        int max = item.getMaxDurability();
        int current = item.getDurability() == null ? max : item.getDurability();
        int missing = max - current;
        if (missing <= 0) {
            return 0;
        }
        double raw = (double) item.getValue() * missing / max * REPAIR_COST_PER_POINT_RATIO;
        return Math.max(1, (int) Math.ceil(raw));
    }

    /**
     * Wears down every breakable, currently-equipped item on the player by the configured
     * per-hit amount, returning the updated player and any player-facing messages. A message is
     * emitted only when an item breaks this call, to avoid per-tick spam during sustained combat.
     *
     * <p>Runs on the tick thread when the player takes combat damage (AGENTS.md §5).
     *
     * @param player the player who was just hit
     * @return the degradation result carrying the updated player and break messages
     */
    public DegradeResult degradeEquipped(Player player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerEquipment equipment = player.getEquipment();
        List<Item> inventory = player.getInventory();
        List<Item> updated = new ArrayList<>(inventory);
        List<String> messages = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            Item item = updated.get(i);
            if (!item.isBreakable() || item.isBroken() || !equipment.isEquipped(item.getId())) {
                continue;
            }
            Item worn = degrade(item, degradePerHit);
            if (!worn.equals(item)) {
                updated.set(i, worn);
                changed = true;
                if (worn.isBroken()) {
                    messages.add("Your " + worn.getName() + " breaks and is now useless in combat!");
                }
            }
        }
        if (!changed) {
            return new DegradeResult(player, List.of());
        }
        return new DegradeResult(player.withInventory(updated), List.copyOf(messages));
    }

    /**
     * Repairs the named item in the player's possession, charging the calculated gold cost.
     *
     * @param player    the player requesting a repair
     * @param itemInput the item name or id (or prefix) to repair
     * @return the outcome describing success or the reason for failure
     */
    public RepairOutcome repair(Player player, @Nullable String itemInput) {
        Objects.requireNonNull(player, "Player is required");
        if (itemInput == null || itemInput.isBlank()) {
            return RepairOutcome.failure("Repair what?");
        }
        String normalized = itemInput.trim().toLowerCase(Locale.ROOT);
        List<Item> inventory = player.getInventory();
        int index = -1;
        for (int i = 0; i < inventory.size(); i++) {
            Item candidate = inventory.get(i);
            String name = candidate.getName().toLowerCase(Locale.ROOT);
            String id = candidate.getId().getValue().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)
                || id.equals(normalized) || id.startsWith(normalized)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return RepairOutcome.failure("You are not carrying '" + itemInput.trim() + "'.");
        }
        Item item = inventory.get(index);
        if (!item.isBreakable()) {
            return RepairOutcome.failure("The " + item.getName() + " cannot be repaired.");
        }
        int cost = calculateRepairCost(item);
        if (cost == 0) {
            return RepairOutcome.failure("The " + item.getName() + " is already in perfect condition.");
        }
        if (player.getGold() < cost) {
            return RepairOutcome.failure(
                "Repairing the " + item.getName() + " costs " + cost + " gold, but you only have "
                    + player.getGold() + " gold.");
        }
        List<Item> updated = new ArrayList<>(inventory);
        updated.set(index, repair(item));
        Player repaired = player.withInventory(updated).addGold(-cost);
        return RepairOutcome.success(
            "The blacksmith repairs your " + item.getName() + " for " + cost + " gold. "
                + "You now have " + repaired.getGold() + " gold.",
            repaired);
    }

    /**
     * Result of {@link #degradeEquipped(Player)}: the updated player and any player-facing messages.
     *
     * @param player   the player with worn-down equipment applied
     * @param messages break messages to deliver to the player (never null, may be empty)
     */
    public record DegradeResult(Player player, List<String> messages) {
        public DegradeResult {
            Objects.requireNonNull(player, "Player is required");
            messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
        }
    }

    /**
     * Outcome of a repair attempt.
     *
     * @param success       whether the repair happened
     * @param message       the player-facing message describing the outcome
     * @param updatedPlayer the player after the repair on success, or the unchanged player on failure
     */
    public record RepairOutcome(boolean success, String message, Player updatedPlayer) {
        static RepairOutcome success(String message, Player updatedPlayer) {
            return new RepairOutcome(true, message, Objects.requireNonNull(updatedPlayer, "Player is required"));
        }

        static RepairOutcome failure(String message) {
            return new RepairOutcome(false, message, null);
        }
    }
}
