package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Domain service that resolves the affixes attached to an {@link Item} into concrete stat bonuses,
 * and combines them with the item's base {@link ItemAttributes} to yield its effective stats.
 *
 * <p>Analogous to {@link ItemDurabilityService}, this service is a pure function over immutable
 * {@link Item} values plus the injected {@link AffixRepository}; it never mutates an item in place.
 * Affix definitions are data-driven ({@code data/item-affixes.json}); an item that references an
 * unknown affix id simply contributes no bonus for that id.
 */
public class ItemAffixService {

    private final AffixRepository affixRepository;

    /**
     * Creates an affix service backed by the given affix definitions.
     *
     * @param affixRepository the source of affix definitions; must not be null
     */
    public ItemAffixService(AffixRepository affixRepository) {
        this.affixRepository = Objects.requireNonNull(affixRepository, "Affix repository is required");
    }

    /**
     * Resolves the item's affix ids into their definitions, skipping any id with no matching
     * definition. Order follows the item's affix list.
     *
     * @param item the item whose affixes to resolve
     * @return the resolved affix definitions, never null (empty when the item has no affixes)
     * @throws RepositoryException if the affix data cannot be read
     */
    public List<ItemAffix> resolve(Item item) throws RepositoryException {
        Objects.requireNonNull(item, "Item is required");
        List<ItemAffix> resolved = new ArrayList<>();
        for (AffixId affixId : item.getAffixes()) {
            affixRepository.findById(affixId).ifPresent(resolved::add);
        }
        return resolved;
    }

    /**
     * Computes the additive stat bonuses contributed by the item's affixes, summing per stat across
     * every resolved affix.
     *
     * @param item the item whose affix bonuses to total
     * @return the bonus stats keyed by stat name, never null (empty when the item has no affixes)
     * @throws RepositoryException if the affix data cannot be read
     */
    public Map<String, Integer> bonusStats(Item item) throws RepositoryException {
        Map<String, Integer> bonuses = new LinkedHashMap<>();
        for (ItemAffix affix : resolve(item)) {
            for (Map.Entry<String, Integer> entry : affix.stats().entrySet()) {
                bonuses.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return bonuses;
    }

    /**
     * Computes the item's effective stats: its base {@link ItemAttributes#getStats()} plus the
     * combined affix {@link #bonusStats(Item) bonuses}, summed per stat. The item is not mutated.
     *
     * @param item the item whose effective stats to compute
     * @return the effective stats keyed by stat name, never null
     * @throws RepositoryException if the affix data cannot be read
     */
    public Map<String, Integer> effectiveStats(Item item) throws RepositoryException {
        Objects.requireNonNull(item, "Item is required");
        Map<String, Integer> effective = new LinkedHashMap<>(item.getAttributes().getStats());
        for (Map.Entry<String, Integer> entry : bonusStats(item).entrySet()) {
            effective.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return effective;
    }
}
