package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Map;

/**
 * Wire representation of a single stat affix definition. Bound by Jackson from
 * {@code data/item-affixes.json}; mapped to the domain {@link io.taanielo.jmud.core.world.ItemAffix}
 * by {@link AffixMapper}.
 *
 * @param id              the affix id (pattern {@code ^[a-z0-9-]+$})
 * @param label           the human-readable label
 * @param stats           the additive stat bonuses keyed by stat name
 * @param allowedRarities the rarity tier ids this affix may roll on
 */
public record AffixDto(
    String id,
    String label,
    Map<String, Integer> stats,
    List<String> allowedRarities
) {
}
