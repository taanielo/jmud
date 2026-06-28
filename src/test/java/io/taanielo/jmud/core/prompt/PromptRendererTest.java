package io.taanielo.jmud.core.prompt;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRendererTest {

    private final PromptRenderer renderer = new PromptRenderer();

    private Player testPlayer() {
        User user = new User(Username.of("Alice"), Password.of("secret"));
        return Player.of(user, "{hp}/{maxHp} HP");
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
}
