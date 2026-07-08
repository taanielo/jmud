package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.repository.AffixRepository;

/**
 * Unit tests for {@link ItemAffixService} covering affix resolution and effective-stat computation.
 */
class ItemAffixServiceTest {

    private static final ItemAffix BEAR = new ItemAffix(
        AffixId.of("of-the-bear"), "of the Bear", Map.of("strength", 2), Set.of(Rarity.UNCOMMON, Rarity.RARE));
    private static final ItemAffix VITALITY = new ItemAffix(
        AffixId.of("of-vitality"), "of Vitality", Map.of("hp", 10, "strength", 1), Set.of(Rarity.RARE));

    private static AffixRepository repositoryWith(ItemAffix... affixes) {
        List<ItemAffix> defs = List.of(affixes);
        return new AffixRepository() {
            @Override
            public Optional<ItemAffix> findById(AffixId id) {
                return defs.stream().filter(a -> a.id().equals(id)).findFirst();
            }

            @Override
            public List<ItemAffix> findAll() {
                return defs;
            }
        };
    }

    private static Item itemWith(Map<String, Integer> baseStats, List<AffixId> affixes) {
        return new Item(
            ItemId.of("blade"), "a blade", "A fine blade.",
            new ItemAttributes(baseStats), List.of(), List.of(), EquipmentSlot.WEAPON, 5, 100, null, null, null,
            List.of(), null, null, null, Rarity.RARE, affixes);
    }

    @Test
    void resolveSkipsUnknownAffixIds() throws Exception {
        ItemAffixService service = new ItemAffixService(repositoryWith(BEAR));
        Item item = itemWith(Map.of(), List.of(AffixId.of("of-the-bear"), AffixId.of("ghost")));

        List<ItemAffix> resolved = service.resolve(item);

        assertEquals(List.of(BEAR), resolved);
    }

    @Test
    void bonusStatsSumsAcrossAffixes() throws Exception {
        ItemAffixService service = new ItemAffixService(repositoryWith(BEAR, VITALITY));
        Item item = itemWith(Map.of(), List.of(AffixId.of("of-the-bear"), AffixId.of("of-vitality")));

        Map<String, Integer> bonuses = service.bonusStats(item);

        assertEquals(Integer.valueOf(3), bonuses.get("strength"));
        assertEquals(Integer.valueOf(10), bonuses.get("hp"));
    }

    @Test
    void effectiveStatsCombineBaseAndAffixBonuses() throws Exception {
        ItemAffixService service = new ItemAffixService(repositoryWith(BEAR, VITALITY));
        Item item = itemWith(Map.of("strength", 1, "dexterity", 4),
            List.of(AffixId.of("of-the-bear"), AffixId.of("of-vitality")));

        Map<String, Integer> effective = service.effectiveStats(item);

        assertEquals(Integer.valueOf(4), effective.get("strength"));
        assertEquals(Integer.valueOf(4), effective.get("dexterity"));
        assertEquals(Integer.valueOf(10), effective.get("hp"));
    }

    @Test
    void itemWithoutAffixesYieldsNoBonuses() throws Exception {
        ItemAffixService service = new ItemAffixService(repositoryWith(BEAR));
        Item item = itemWith(Map.of("strength", 2), List.of());

        assertTrue(service.bonusStats(item).isEmpty());
        assertEquals(Map.of("strength", 2), service.effectiveStats(item));
    }

    @Test
    void baseStatsAreNotMutated() throws Exception {
        ItemAffixService service = new ItemAffixService(repositoryWith(BEAR));
        Item item = itemWith(Map.of("strength", 1), List.of(AffixId.of("of-the-bear")));

        service.effectiveStats(item);

        assertEquals(Map.of("strength", 1), item.getAttributes().getStats());
    }
}
