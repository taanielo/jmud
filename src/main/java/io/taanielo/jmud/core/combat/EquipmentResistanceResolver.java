package io.taanielo.jmud.core.combat;

import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves the total elemental resistance percentage contributed by a player's equipped armour
 * against a given {@link DamageType}.
 *
 * <p>Each equipped item may carry a resistance key inside its {@code attributes.stats} map — the
 * same free-form mechanism that already holds {@code "ac"} — named by
 * {@link DamageType#resistStatKey()} (e.g. {@code "fire_resist"}). This resolver sums those values
 * across every equipped slot, exactly mirroring {@link EquipmentArmorResolver#totalAc(Player)}, and
 * returns the total as a whole-number percentage. {@link CombatEngine} then reduces incoming
 * non-physical damage by that percentage, capped by
 * {@link CombatSettings#maxResistancePercent()} so resistance can never fully negate a blow.
 */
public class EquipmentResistanceResolver {

    private final ItemRepository itemRepository;

    /**
     * Creates a resolver backed by the provided item repository.
     *
     * @param itemRepository repository used to load item definitions for equipped slots
     */
    public EquipmentResistanceResolver(ItemRepository itemRepository) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Returns the total resistance percentage the player's equipped armour provides against the
     * given damage type.
     *
     * <p>{@link DamageType#PHYSICAL} (and any type without a resist stat key) always returns
     * {@code 0}: physical damage is never resisted. Slots that are empty or whose items carry no
     * matching resist stat contribute zero. If item lookup fails for a slot the slot is silently
     * skipped rather than aborting combat. The returned value is uncapped; the cap is applied by
     * {@link CombatEngine} at the point of mitigation.
     *
     * @param player     the defending player whose equipment is inspected
     * @param damageType the elemental type of the incoming attack
     * @return the summed resistance percentage across all slots; never negative
     */
    public int totalResistance(Player player, DamageType damageType) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(damageType, "Damage type is required");
        String statKey = damageType.resistStatKey();
        if (statKey == null) {
            return 0;
        }
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
                Integer resist = item.getAttributes().getStats().get(statKey);
                if (resist != null && resist > 0) {
                    total += resist;
                }
            } catch (RepositoryException e) {
                // Skip this slot; a missing item should not abort combat
            }
        }
        return total;
    }

    /**
     * Returns a no-op resolver that always contributes zero resistance.
     * Intended for use in test contexts where item data is unavailable.
     *
     * @return a resolver that always returns {@code 0}
     */
    public static EquipmentResistanceResolver noOp() {
        return new EquipmentResistanceResolver(new ItemRepository() {
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
