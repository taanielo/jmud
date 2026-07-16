package io.taanielo.jmud.core.player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves the additional carry-weight capacity contributed by a player's equipped gear.
 *
 * <p>Each equipped item may carry a {@code "carry"} key inside its
 * {@code attributes.stats} map. This resolver sums those values across all
 * equipment slots (in practice only worn packs on {@link EquipmentSlot#BACK}
 * declare it) and returns the total. The result is added by
 * {@link EncumbranceService#maxCarry(Player)} to the racial and class carry
 * base, mirroring exactly how {@code EquipmentArmorResolver} sums the
 * {@code "ac"} stat across armour slots.
 */
public class EquipmentCarryResolver {

    private static final String CARRY_STAT = "carry";

    private final ItemRepository itemRepository;

    /**
     * Creates a resolver backed by the provided item repository.
     *
     * @param itemRepository repository used to load item definitions for equipped slots
     */
    public EquipmentCarryResolver(ItemRepository itemRepository) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Returns the total carry-weight bonus contributed by all items currently equipped by the player.
     *
     * <p>Slots that are empty or whose items have no {@code "carry"} stat contribute zero.
     * If item lookup fails for a slot the slot is silently skipped rather than aborting the
     * carry-weight calculation.
     *
     * @param player the player whose equipment is inspected
     * @return the summed carry-weight bonus across all equipped slots; never negative
     */
    public int totalCarry(Player player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerEquipment equipment = player.getEquipment();
        Map<EquipmentSlot, ItemId> slots = equipment.slots();

        int total = 0;
        for (Map.Entry<EquipmentSlot, ItemId> entry : slots.entrySet()) {
            ItemId itemId = entry.getValue();
            if (itemId == null) {
                continue;
            }
            try {
                Item item = itemRepository.findById(itemId).orElse(null);
                if (item == null) {
                    continue;
                }
                Integer carry = item.getAttributes().getStats().get(CARRY_STAT);
                if (carry != null && carry > 0) {
                    total += carry;
                }
            } catch (RepositoryException e) {
                // Skip this slot; a missing item should not abort the carry-weight calculation
            }
        }
        return total;
    }

    /**
     * Returns a no-op resolver that always contributes zero carry bonus.
     * Intended for use in test contexts where item data is unavailable.
     *
     * @return a resolver that always returns {@code 0}
     */
    public static EquipmentCarryResolver noOp() {
        return new EquipmentCarryResolver(new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return Optional.empty();
            }
        });
    }
}
