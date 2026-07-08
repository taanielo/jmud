package io.taanielo.jmud.core.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Rarity;

/**
 * Unit tests for rarity-aware item-name coloring in {@link AnsiTextStyler} and {@link PlainTextStyler}.
 */
class TextStylerRarityTest {

    private static final String ESC = String.valueOf((char) 27);
    private static final String RESET = ESC + "[0m";

    private final AnsiTextStyler ansi = new AnsiTextStyler();
    private final PlainTextStyler plain = new PlainTextStyler();

    @Test
    void plainStylerReturnsUnchangedNameForEveryTier() {
        for (Rarity rarity : Rarity.values()) {
            assertEquals("Sword", plain.rarity("Sword", rarity),
                "Plain styler must not decorate " + rarity);
        }
    }

    @Test
    void ansiCommonIsUnstyled() {
        assertEquals("Sword", ansi.rarity("Sword", Rarity.COMMON));
    }

    @Test
    void ansiUncommonIsGreenWrapped() {
        assertEquals(ESC + "[32mSword" + RESET, ansi.rarity("Sword", Rarity.UNCOMMON));
    }

    @Test
    void ansiRareIsCyanWrapped() {
        assertEquals(ESC + "[36mSword" + RESET, ansi.rarity("Sword", Rarity.RARE));
    }

    @Test
    void ansiTiersUseDistinctColors() {
        String common = ansi.rarity("Sword", Rarity.COMMON);
        String uncommon = ansi.rarity("Sword", Rarity.UNCOMMON);
        String rare = ansi.rarity("Sword", Rarity.RARE);
        assertNotEquals(common, uncommon);
        assertNotEquals(uncommon, rare);
        assertNotEquals(common, rare);
    }

    @Test
    void ansiComposesWithDamagedAnnotation() {
        String styled = ansi.rarity("Sword (damaged)", Rarity.RARE);
        assertTrue(styled.contains("Sword (damaged)"),
            "Rarity coloring must preserve the (damaged) annotation");
    }
}
