package io.taanielo.jmud.core.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PlayerReputationTest {

    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final FactionId GUARDS = FactionId.of("guards");

    @Test
    void untrackedFaction_isNeutralZero() {
        assertEquals(0, PlayerReputation.empty().standing(BANDITS));
    }

    @Test
    void adjust_accumulatesStanding() {
        PlayerReputation rep = PlayerReputation.empty()
            .adjust(BANDITS, -10)
            .adjust(BANDITS, -5);

        assertEquals(-15, rep.standing(BANDITS));
        assertEquals(0, rep.standing(GUARDS));
    }

    @Test
    void adjust_byZero_returnsSameInstance() {
        PlayerReputation rep = PlayerReputation.empty().adjust(BANDITS, 3);
        assertSame(rep, rep.adjust(BANDITS, 0));
    }

    @Test
    void adjust_doesNotMutateOriginal() {
        PlayerReputation original = PlayerReputation.empty().adjust(BANDITS, 5);
        PlayerReputation changed = original.adjust(BANDITS, -2);

        assertEquals(5, original.standing(BANDITS));
        assertEquals(3, changed.standing(BANDITS));
    }

    @Test
    void stringMap_roundTripsThroughPersistence() {
        PlayerReputation rep = PlayerReputation.empty()
            .adjust(BANDITS, -7)
            .adjust(GUARDS, 4);

        Map<String, Integer> raw = rep.toStringMap();
        assertEquals(Integer.valueOf(-7), raw.get("bandits"));
        assertEquals(Integer.valueOf(4), raw.get("guards"));

        PlayerReputation restored = PlayerReputation.fromStringMap(raw);
        assertEquals(-7, restored.standing(BANDITS));
        assertEquals(4, restored.standing(GUARDS));
    }

    @Test
    void fromStringMap_nullOrEmpty_isEmpty() {
        assertTrue(PlayerReputation.fromStringMap(null).isEmpty());
        assertTrue(PlayerReputation.fromStringMap(Map.of()).isEmpty());
    }
}
