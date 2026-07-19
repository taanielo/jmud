package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for the persisted marriage bond on {@link Player} (see the MARRY command).
 *
 * <p>Covers the state half of the happy path — bonding both partners and divorce clearing both
 * sides — plus the unmarried default and level-up preservation of the bond.
 */
class PlayerMarriageTest {

    private Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("pw", 1000)), "%hp> ");
    }

    @Test
    void freshPlayerIsUnmarried() {
        Player p = player("Lonely");
        assertFalse(p.isMarried());
        assertNull(p.spouse());
    }

    @Test
    void bondingSetsSpouseOnBothRecords() {
        Player alice = player("Alice").withSpouse("Bob");
        Player bob = player("Bob").withSpouse("Alice");

        assertTrue(alice.isMarried());
        assertTrue(bob.isMarried());
        assertEquals("Bob", alice.spouse());
        assertEquals("Alice", bob.spouse());
    }

    @Test
    void divorceClearsBothSides() {
        Player alice = player("Alice").withSpouse("Bob");
        Player bob = player("Bob").withSpouse("Alice");

        Player singleAlice = alice.withSpouse(null);
        Player singleBob = bob.withSpouse(null);

        assertFalse(singleAlice.isMarried());
        assertFalse(singleBob.isMarried());
        assertNull(singleAlice.spouse());
        assertNull(singleBob.spouse());
    }

    @Test
    void blankSpouseNormalisesToUnmarried() {
        Player p = player("Alice").withSpouse("   ");
        assertFalse(p.isMarried());
        assertNull(p.spouse());
    }

    @Test
    void spouseSurvivesLevelUp() {
        Player married = player("Alice").withSpouse("Bob");

        Player levelled = married.withIdentity(married.identity().withLevel(10));

        assertEquals(10, levelled.getLevel());
        assertTrue(levelled.isMarried());
        assertEquals("Bob", levelled.spouse());
    }
}
