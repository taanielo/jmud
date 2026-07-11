package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves the shield-block profile contributed by a defender's {@link EquipmentSlot#OFFHAND} item.
 *
 * <p>An off-hand item may carry a {@code "block_chance"} key inside its
 * {@code attributes.stats} map, granting the wearer a percentage chance to block an incoming
 * attack that would otherwise land as a hit. A blocked hit deals reduced (not zeroed) damage.
 * The reduction percentage is read from an optional {@code "block_reduction"} stat on the same
 * item; when absent it falls back to {@link CombatSettings#defaultBlockReductionPercent()}.
 *
 * <p>Only the off-hand slot is inspected, so a shield confers a mechanical benefit that a plain
 * armour piece (resolved by {@link EquipmentArmorResolver}) does not. Off-hand items without a
 * {@code block_chance} stat (e.g. a light trinket or an off-hand weapon) contribute {@link #none()},
 * leaving combat resolution identical to the pre-shield behaviour.
 */
public class ShieldBlockResolver {

    private static final String BLOCK_CHANCE_STAT = "block_chance";
    private static final String BLOCK_REDUCTION_STAT = "block_reduction";

    private final ItemRepository itemRepository;

    /**
     * Creates a resolver backed by the provided item repository.
     *
     * @param itemRepository repository used to load the off-hand item definition
     */
    public ShieldBlockResolver(ItemRepository itemRepository) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Resolves the block profile of the item currently equipped in the player's off-hand slot.
     *
     * <p>Returns {@link ShieldBlock#none()} when the off-hand slot is empty, the item cannot be
     * loaded, or the item carries no positive {@code block_chance} stat. When a positive
     * {@code block_chance} is present, the block chance is clamped to {@code [0, 100]} and the
     * reduction is taken from the {@code block_reduction} stat (clamped to {@code [0, 100]}) or the
     * configured default when that stat is absent or non-positive.
     *
     * @param player the defending player whose off-hand slot is inspected
     * @return the resolved shield-block profile; never {@code null}
     */
    public ShieldBlock resolve(Player player) {
        Objects.requireNonNull(player, "Player is required");
        ItemId offhandId = player.getEquipment().equipped(EquipmentSlot.OFFHAND);
        if (offhandId == null) {
            return ShieldBlock.none();
        }
        try {
            Item item = itemRepository.findById(offhandId).orElse(null);
            if (item == null) {
                return ShieldBlock.none();
            }
            Integer blockChance = item.getAttributes().getStats().get(BLOCK_CHANCE_STAT);
            if (blockChance == null || blockChance <= 0) {
                return ShieldBlock.none();
            }
            Integer blockReduction = item.getAttributes().getStats().get(BLOCK_REDUCTION_STAT);
            int reduction = (blockReduction != null && blockReduction > 0)
                ? Math.min(100, blockReduction)
                : CombatSettings.defaultBlockReductionPercent();
            return new ShieldBlock(Math.min(100, blockChance), reduction);
        } catch (RepositoryException e) {
            // A missing off-hand item should never abort combat; treat as no shield.
            return ShieldBlock.none();
        }
    }

    /**
     * Returns a no-op resolver that never grants a block chance.
     * Intended for use in test contexts where item data is unavailable.
     *
     * @return a resolver whose {@link #resolve(Player)} always returns {@link ShieldBlock#none()}
     */
    public static ShieldBlockResolver noOp() {
        return new ShieldBlockResolver(new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public java.util.Optional<Item> findById(ItemId id) {
                return java.util.Optional.empty();
            }
        });
    }

    /**
     * The shield-block profile of an off-hand item: the percentage chance to block an otherwise-landing
     * hit and the percentage by which a successful block reduces the incoming damage.
     *
     * @param chancePercent    chance to block, in {@code [0, 100]}; {@code 0} means no block is possible
     * @param reductionPercent damage reduction applied on a successful block, in {@code [0, 100]}
     */
    public record ShieldBlock(int chancePercent, int reductionPercent) {

        private static final ShieldBlock NONE = new ShieldBlock(0, 0);

        /**
         * Returns the empty profile granting no block chance.
         *
         * @return a profile whose {@link #chancePercent()} is {@code 0}
         */
        public static ShieldBlock none() {
            return NONE;
        }

        /**
         * Whether this profile can produce a block.
         *
         * @return {@code true} when {@link #chancePercent()} is greater than {@code 0}
         */
        public boolean canBlock() {
            return chancePercent > 0;
        }
    }
}
