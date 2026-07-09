package io.taanielo.jmud.core.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class PromptRendererTest {

    /** ANSI escape (0x1B), used to detect colorized output without embedding a control char. */
    private static final char ESC = (char) 0x1B;

    private final PromptRenderer renderer = new PromptRenderer();

    private Player testPlayer() {
        User user = new User(Username.of("Alice"), Password.of("secret"));
        return Player.of(user, "{hp}/{maxHp} HP");
    }

    private Player statsPlayer() {
        User user = new User(Username.of("Alice"), Password.of("secret"));
        PlayerVitals vitals = new PlayerVitals(30, 100, 12, 40, 55, 60);
        return new Player(user, 7, 1234, vitals, List.of(), "prompt", false, List.of(), null, null);
    }

    @Test
    void render_substitutesHpTokens() {
        Player player = testPlayer();
        String result = renderer.render("{hp}/{maxHp}", player);

        String expected = player.getVitals().hp() + "/" + player.getVitals().maxHp();
        assertEquals(expected, result);
    }

    @Test
    void render_partyHpToken_emptyWhenNoPartyHpProvided() {
        Player player = testPlayer();
        String result = renderer.render("{partyHp}", player);

        assertEquals("", result);
    }

    @Test
    void render_partyHpToken_substitutesProvidedValue() {
        Player player = testPlayer();
        String result = renderer.render("{hp} [{partyHp}]", player, "Bob:80/100 Carol:50/100");

        assertTrue(result.contains("Bob:80/100 Carol:50/100"));
        assertTrue(result.contains(String.valueOf(player.getVitals().hp())));
    }

    @Test
    void render_partyHpToken_nullTreatedAsEmpty() {
        Player player = testPlayer();
        String result = renderer.render("[{partyHp}]", player, null);

        assertEquals("[]", result);
    }

    @Test
    void render_withoutPartyHp_backwardsCompatible() {
        Player player = testPlayer();
        String result = renderer.render("{hp}/{maxHp}", player);

        assertEquals(player.getVitals().hp() + "/" + player.getVitals().maxHp(), result);
    }

    @Test
    void render_substitutesAllPercentTokens() {
        Player player = statsPlayer();
        String result = renderer.render("%h/%H %m/%M %v %x %l", player);

        assertEquals("30/100 12/40 55 1234 7", result);
    }

    @Test
    void render_percentPercentIsLiteralPercent() {
        Player player = statsPlayer();
        String result = renderer.render("%h%% done", player);

        assertEquals("30% done", result);
    }

    @Test
    void render_unknownPercentTokenLeftAsIs() {
        Player player = statsPlayer();
        String result = renderer.render("%z %h", player);

        assertEquals("%z 30", result);
    }

    @Test
    void render_trailingPercentLeftAsIs() {
        Player player = statsPlayer();
        String result = renderer.render("hp %", player);

        assertEquals("hp %", result);
    }

    @Test
    void render_percentTokensCombineWithBraceTokens() {
        Player player = statsPlayer();
        String result = renderer.render("[%h/{maxHp}]", player);

        assertEquals("[30/100]", result);
    }

    @Test
    void render_colorizeWrapsValuesInAnsiCodesWithoutChangingDigits() {
        Player player = statsPlayer();
        String plain = renderer.render("%h/%H", player, "", false);
        String colored = renderer.render("%h/%H", player, "", true);

        assertEquals("30/100", plain);
        assertNotEquals(plain, colored);
        assertTrue(colored.indexOf(ESC) >= 0, "colorized prompt should contain an ANSI escape");
        assertTrue(colored.contains("30"));
        assertTrue(colored.contains("100"));
    }

    @Test
    void render_colorizeFalseEmitsNoAnsiCodes() {
        Player player = statsPlayer();
        String colored = renderer.render("[%h/%Hhp {mana}/{maxMana}mn]", player, "", false);

        assertFalse(colored.indexOf(ESC) >= 0, "uncolored prompt should contain no ANSI escapes");
        assertEquals("[30/100hp 12/40mn]", colored);
    }
}
