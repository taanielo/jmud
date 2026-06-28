package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Renders the player prompt string by substituting named tokens.
 *
 * <p>Supported tokens:
 * <ul>
 *   <li>{@code {hp}} / {@code {maxHp}} — current and maximum hit points</li>
 *   <li>{@code {mana}} / {@code {maxMana}} — current and maximum mana</li>
 *   <li>{@code {move}} / {@code {maxMove}} — current and maximum movement points</li>
 *   <li>{@code {exp}} — accumulated experience points</li>
 *   <li>{@code {partyHp}} — space-separated {@code Name:hp/maxHp} entries for party members
 *       (empty string when not in a party)</li>
 * </ul>
 */
public class PromptRenderer {

    /**
     * Renders the prompt format for the given player.
     *
     * <p>The {@code {partyHp}} token is replaced with an empty string.
     *
     * @param format the prompt format string
     * @param player the current player
     * @return the rendered prompt
     */
    public String render(String format, Player player) {
        return render(format, player, "");
    }

    /**
     * Renders the prompt format for the given player, substituting the {@code {partyHp}} token
     * with the provided pre-computed party HP string.
     *
     * @param format    the prompt format string
     * @param player    the current player
     * @param partyHp   pre-computed party HP string (empty string when not in a party)
     * @return the rendered prompt
     */
    public String render(String format, Player player, String partyHp) {
        PlayerVitals vitals = player.getVitals();
        String rendered = format;
        rendered = rendered.replace("{hp}", String.valueOf(vitals.hp()));
        rendered = rendered.replace("{maxHp}", String.valueOf(vitals.maxHp()));
        rendered = rendered.replace("{mana}", String.valueOf(vitals.mana()));
        rendered = rendered.replace("{maxMana}", String.valueOf(vitals.maxMana()));
        rendered = rendered.replace("{move}", String.valueOf(vitals.move()));
        rendered = rendered.replace("{maxMove}", String.valueOf(vitals.maxMove()));
        rendered = rendered.replace("{exp}", String.valueOf(player.getExperience()));
        rendered = rendered.replace("{partyHp}", partyHp != null ? partyHp : "");
        if (player.isResting()) {
            rendered = "[REST] " + rendered;
        }
        return rendered;
    }
}
