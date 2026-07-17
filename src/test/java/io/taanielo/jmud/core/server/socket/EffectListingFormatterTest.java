package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;

class EffectListingFormatterTest {

    private static EffectDefinition definition(String id, String name, List<EffectModifier> modifiers) {
        return new EffectDefinition(
            EffectId.of(id),
            name,
            10,
            1,
            EffectStacking.REFRESH,
            modifiers,
            List.of()
        );
    }

    private static EffectDefinition beneficial(String id, String name) {
        return definition(id, name, List.of(new EffectModifier("defense", ModifierOperation.ADD, 2)));
    }

    private static EffectDefinition harmful(String id, String name) {
        return definition(id, name, List.of(new EffectModifier("damage_per_tick", ModifierOperation.ADD, 3)));
    }

    @Test
    void emptyWhenNoActiveEffects() {
        List<String> lines = EffectListingFormatter.format(List.of(), new MapRepository(Map.of()));

        assertTrue(lines.isEmpty());
    }

    @Test
    void listsSingleBeneficialEffectWithDuration() {
        EffectDefinition bless = beneficial("bless", "Bless");
        List<String> lines = EffectListingFormatter.format(
            List.of(new EffectInstance(EffectId.of("bless"), 8, 1)),
            new MapRepository(Map.of(EffectId.of("bless"), bless)));

        assertEquals("Active effects:", lines.get(0));
        assertEquals("  Beneficial:", lines.get(1));
        assertTrue(lines.get(2).contains("Bless"));
        assertTrue(lines.get(2).contains("8 ticks"));
        assertEquals(3, lines.size());
    }

    @Test
    void separatesBeneficialAndHarmfulAndShowsStacks() {
        EffectDefinition bless = beneficial("bless", "Bless");
        EffectDefinition rend = harmful("rend", "Rend");
        MapRepository repository = new MapRepository(Map.of(
            EffectId.of("bless"), bless,
            EffectId.of("rend"), rend));

        List<String> lines = EffectListingFormatter.format(
            List.of(
                new EffectInstance(EffectId.of("bless"), 5, 1),
                new EffectInstance(EffectId.of("rend"), 6, 3)),
            repository);

        assertEquals("Active effects:", lines.get(0));
        assertEquals("  Beneficial:", lines.get(1));
        assertTrue(lines.get(2).contains("Bless"));
        assertEquals("  Harmful:", lines.get(3));
        assertTrue(lines.get(4).contains("Rend"));
        assertTrue(lines.get(4).contains("6 ticks"));
        assertTrue(lines.get(4).contains("x3"));
    }

    @Test
    void singularTickWording() {
        EffectDefinition bless = beneficial("bless", "Bless");
        List<String> lines = EffectListingFormatter.format(
            List.of(new EffectInstance(EffectId.of("bless"), 1, 1)),
            new MapRepository(Map.of(EffectId.of("bless"), bless)));

        assertTrue(lines.get(2).contains("1 tick)"));
    }

    @Test
    void skipsUnknownEffects() {
        List<String> lines = EffectListingFormatter.format(
            List.of(new EffectInstance(EffectId.of("ghost"), 5, 1)),
            new MapRepository(Map.of()));

        assertTrue(lines.isEmpty());
    }

    private record MapRepository(Map<EffectId, EffectDefinition> definitions) implements EffectRepository {
        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }
}
