package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link GuildQuestPool} construction invariants and level-banded eligibility. */
class GuildQuestPoolTest {

    private static GuildQuestObjective objective(String id, int minLevel) {
        return new GuildQuestObjective(id, id, "mob-" + id, id + "s", 5, 100, minLevel);
    }

    @Test
    void rejectsEmptyPool() {
        assertThrows(IllegalArgumentException.class, () -> new GuildQuestPool(List.of()));
    }

    @Test
    void rejectsPoolWithNoLevelOneObjective() {
        assertThrows(IllegalArgumentException.class,
            () -> new GuildQuestPool(List.of(objective("a", 2), objective("b", 3))));
    }

    @Test
    void levelOneGuildSeesOnlyLevelOneObjectives() {
        GuildQuestPool pool = new GuildQuestPool(List.of(
            objective("a", 1), objective("b", 1), objective("c", 3), objective("d", 5)));

        List<GuildQuestObjective> eligible = pool.objectivesUpToLevel(1);

        assertEquals(2, eligible.size());
        assertTrue(eligible.stream().allMatch(o -> o.minGuildLevel() == 1));
    }

    @Test
    void higherLevelGuildUnlocksTougherObjectives() {
        GuildQuestPool pool = new GuildQuestPool(List.of(
            objective("a", 1), objective("c", 3), objective("d", 5)));

        assertEquals(2, pool.objectivesUpToLevel(3).size());
        assertEquals(3, pool.objectivesUpToLevel(5).size());
    }
}
