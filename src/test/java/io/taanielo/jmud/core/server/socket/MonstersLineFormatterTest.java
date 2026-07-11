package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.MobInstance;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link MonsterLineFormatter#format(List)}.
 */
class MonstersLineFormatterTest {

    private static MobInstance mob(String name) {
        MobTemplate template = new MobTemplate(
            io.taanielo.jmud.core.mob.MobId.of(name.toLowerCase(Locale.ROOT).replace(' ', '-')),
            name,
            10,
            null,
            null,
            false,
            List.of(),
            RoomId.of("test-room"),
            1,
            0,
            0,
            null,
            null,
            false
        );
        return new MobInstance(template);
    }

    @Test
    void emptyListReturnsNone() {
        assertEquals("Monsters: none", MonsterLineFormatter.format(List.of()));
    }

    @Test
    void nullListReturnsNone() {
        assertEquals("Monsters: none", MonsterLineFormatter.format(null));
    }

    @Test
    void singleMobShowsOneEntry() {
        assertEquals("Monsters: 1x Goblin", MonsterLineFormatter.format(List.of(mob("Goblin"))));
    }

    @Test
    void multipleDifferentMobsListedSeparately() {
        String result = MonsterLineFormatter.format(List.of(mob("Goblin"), mob("Troll")));
        assertEquals("Monsters: 1x Goblin, 1x Troll", result);
    }

    @Test
    void sameTypeMobsAreGroupedAndCounted() {
        String result = MonsterLineFormatter.format(
            List.of(mob("Goblin"), mob("Goblin"), mob("Goblin")));
        assertEquals("Monsters: 3x Goblin", result);
    }

    @Test
    void mixedMobsGroupedByName() {
        String result = MonsterLineFormatter.format(
            List.of(mob("Goblin"), mob("Troll"), mob("Goblin")));
        assertEquals("Monsters: 2x Goblin, 1x Troll", result);
    }

    @Test
    void encounterOrderPreservedForFirstOccurrence() {
        // Troll appears first, then Goblin — output must match insertion order.
        String result = MonsterLineFormatter.format(
            List.of(mob("Troll"), mob("Goblin"), mob("Troll")));
        assertEquals("Monsters: 2x Troll, 1x Goblin", result);
    }
}
