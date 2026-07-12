package io.taanielo.jmud.core.combat.flavor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class DamageVerbTableTest {

    private static DamageVerbTable table() {
        return new DamageVerbTable(List.of(
            new DamageVerbTier(1, 4, verb("scratches")),
            new DamageVerbTier(5, 9, verb("grazes")),
            new DamageVerbTier(10, 14, verb("hits")),
            new DamageVerbTier(15, 19, verb("injures")),
            new DamageVerbTier(20, 24, verb("wounds")),
            new DamageVerbTier(25, 34, verb("mauls")),
            new DamageVerbTier(35, 44, verb("DECIMATES")),
            new DamageVerbTier(45, 54, verb("DEVASTATES")),
            new DamageVerbTier(55, 69, verb("MUTILATES")),
            new DamageVerbTier(70, 84, verb("MASSACRES")),
            new DamageVerbTier(85, 99, verb("ANNIHILATES")),
            new DamageVerbTier(100, null, verb("UNSPEAKABLE"))));
    }

    private static DamageVerb verb(String word) {
        return new DamageVerb(word, word.toLowerCase(Locale.ROOT));
    }

    /** With a 100-HP target, damage equals the percentage, so every boundary can be probed directly. */
    private static String verbAt(int percent) {
        return table().verbFor(percent, 100).thirdPerson();
    }

    @Test
    void resolvesEveryTierBoundaryInclusively() {
        assertEquals("scratches", verbAt(1));
        assertEquals("scratches", verbAt(4));
        assertEquals("grazes", verbAt(5));
        assertEquals("grazes", verbAt(9));
        assertEquals("hits", verbAt(10));
        assertEquals("hits", verbAt(14));
        assertEquals("injures", verbAt(15));
        assertEquals("injures", verbAt(19));
        assertEquals("wounds", verbAt(20));
        assertEquals("wounds", verbAt(24));
        assertEquals("mauls", verbAt(25));
        assertEquals("mauls", verbAt(34));
        assertEquals("DECIMATES", verbAt(35));
        assertEquals("DECIMATES", verbAt(44));
        assertEquals("DEVASTATES", verbAt(45));
        assertEquals("DEVASTATES", verbAt(54));
        assertEquals("MUTILATES", verbAt(55));
        assertEquals("MUTILATES", verbAt(69));
        assertEquals("MASSACRES", verbAt(70));
        assertEquals("MASSACRES", verbAt(84));
        assertEquals("ANNIHILATES", verbAt(85));
        assertEquals("ANNIHILATES", verbAt(99));
        assertEquals("UNSPEAKABLE", verbAt(100));
    }

    @Test
    void openEndedTopTierMatchesOverkill() {
        assertEquals("UNSPEAKABLE", table().verbFor(250, 100).thirdPerson());
        // 300 damage against a 100-HP target is 300% — still the top tier.
        assertEquals("UNSPEAKABLE", table().verbFor(300, 100).thirdPerson());
    }

    @Test
    void subOnePercentHitFallsBackToGentlestTier() {
        // 1 damage against a 900-HP target floors to 0% but a landed hit still reads as a scratch.
        assertEquals("scratches", table().verbFor(1, 900).thirdPerson());
    }

    @Test
    void tierBasisIsPercentOfTargetMaxHp() {
        // The issue's worked example: 6 damage is a heavy blow to a 15-HP goblin (40%) ...
        assertEquals("DECIMATES", table().verbFor(6, 15).thirdPerson());
        // ... but barely a scratch to a 900-HP wyrm (0%).
        assertEquals("scratches", table().verbFor(6, 900).thirdPerson());
    }

    @Test
    void zeroOrNegativeDamageIsGentlest() {
        assertEquals("scratches", table().verbFor(0, 100).thirdPerson());
        assertEquals("scratches", table().verbFor(-5, 100).thirdPerson());
    }

    @Test
    void nonPositiveMaxHpIsTopTier() {
        assertEquals("UNSPEAKABLE", table().verbFor(1, 0).thirdPerson());
    }

    @Test
    void percentOfMaxHpFloors() {
        DamageVerbTable table = table();
        assertEquals(0, table.percentOfMaxHp(1, 900));
        assertEquals(40, table.percentOfMaxHp(6, 15));
        assertEquals(99, table.percentOfMaxHp(99, 100));
        assertEquals(100, table.percentOfMaxHp(100, 100));
    }

    @Test
    void emptyTableRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DamageVerbTable(List.of()));
    }
}
