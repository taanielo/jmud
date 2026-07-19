package io.taanielo.jmud.core.combat;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves the total armour class (AC) contributed by a player's equipped armour items.
 *
 * <p>Each equipped item may carry an {@code "ac"} key inside its
 * {@code attributes.stats} map. This resolver sums those values across all
 * armour slots ({@link EquipmentSlot#HEAD}, {@link EquipmentSlot#CHEST},
 * {@link EquipmentSlot#LEGS}) and returns the total. The result is used by
 * {@link CombatEngine} to reduce the attacker's effective hit chance against
 * the defending player, stacking with the racial armour bonus from
 * {@link RaceArmorBonusResolver}.
 */
public class EquipmentArmorResolver {

    private static final String AC_STAT = "ac";

    private final ItemRepository itemRepository;
    private final @Nullable SetBonusResolver setBonusResolver;

    /**
     * Creates a resolver backed by the provided item repository, contributing no item-set AC.
     *
     * @param itemRepository repository used to load item definitions for equipped slots
     */
    public EquipmentArmorResolver(ItemRepository itemRepository) {
        this(itemRepository, null);
    }

    /**
     * Creates a resolver that also folds any {@code "ac"} bonus from worn item sets into
     * {@link #totalAc(Player)}, so set armour stacks with per-item and racial/class AC at the single
     * aggregation point {@link CombatEngine} and SCORE already read (issue #771).
     *
     * @param itemRepository    repository used to load item definitions for equipped slots
     * @param setBonusResolver  resolver for item-set stat bonuses, or {@code null} to ignore sets
     */
    public EquipmentArmorResolver(ItemRepository itemRepository, @Nullable SetBonusResolver setBonusResolver) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.setBonusResolver = setBonusResolver;
    }

    /**
     * Returns the total AC contributed by all armour items currently equipped by the player.
     *
     * <p>Slots that are empty or whose items have no {@code "ac"} stat contribute zero.
     * If item lookup fails for a slot the slot is silently skipped rather than aborting combat.
     *
     * @param player the defending player whose equipment is inspected
     * @return the summed AC value across all armour slots; never negative
     */
    public int totalAc(Player player) {
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
                Integer ac = item.getAttributes().getStats().get(AC_STAT);
                if (ac != null && ac > 0) {
                    total += ac;
                }
            } catch (RepositoryException e) {
                // Skip this slot; a missing item should not abort combat
            }
        }
        if (setBonusResolver != null) {
            Integer setAc = setBonusResolver.bonusStats(player).get(AC_STAT);
            if (setAc != null && setAc > 0) {
                total += setAc;
            }
        }
        return total;
    }

    /**
     * Returns a no-op resolver that always contributes zero AC.
     * Intended for use in test contexts where item data is unavailable.
     *
     * @return a resolver that always returns {@code 0}
     */
    public static EquipmentArmorResolver noOp() {
        return new EquipmentArmorResolver(new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public java.util.Optional<Item> findById(ItemId id) {
                return java.util.Optional.empty();
            }
        });
    }
}
