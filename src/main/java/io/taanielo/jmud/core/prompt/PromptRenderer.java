package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Renders the player prompt string by substituting named tokens.
 *
 * <p>Two token syntaxes are supported and may be freely mixed in a single format string:
 *
 * <p>Brace tokens (the historic default format uses these):
 * <ul>
 *   <li>{@code {hp}} / {@code {maxHp}} — current and maximum hit points</li>
 *   <li>{@code {mana}} / {@code {maxMana}} — current and maximum mana</li>
 *   <li>{@code {move}} / {@code {maxMove}} — current and maximum movement points</li>
 *   <li>{@code {exp}} — accumulated experience points</li>
 *   <li>{@code {partyHp}} — space-separated {@code Name:hp/maxHp} entries for party members
 *       (empty string when not in a party)</li>
 * </ul>
 *
 * <p>Percent tokens (used by the {@code PROMPT} command):
 * <ul>
 *   <li>{@code %h} / {@code %H} — current and maximum hit points</li>
 *   <li>{@code %m} / {@code %M} — current and maximum mana</li>
 *   <li>{@code %v} — current movement points</li>
 *   <li>{@code %x} — accumulated experience points</li>
 *   <li>{@code %l} — current level</li>
 *   <li>{@code %%} — a literal percent sign</li>
 * </ul>
 *
 * <p>When colorization is requested the substituted numeric values are wrapped in ANSI color codes,
 * with a distinct color per prompt section (health, mana, movement, experience, level). Literal
 * text in the format is left uncolored.
 */
public class PromptRenderer {

    private static final String RESET = "[0m";
    private static final String HEALTH_COLOR = "[31m";
    private static final String MANA_COLOR = "[36m";
    private static final String MOVE_COLOR = "[32m";
    private static final String EXP_COLOR = "[33m";
    private static final String LEVEL_COLOR = "[35m";

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
        return render(format, player, partyHp, false);
    }

    /**
     * Renders the prompt format for the given player, optionally colorizing the substituted values.
     *
     * @param format    the prompt format string
     * @param player    the current player
     * @param partyHp   pre-computed party HP string (empty string when not in a party)
     * @param colorize  when {@code true}, wrap each substituted section value in ANSI color codes
     * @return the rendered prompt
     */
    public String render(String format, Player player, String partyHp, boolean colorize) {
        PlayerVitals vitals = player.getVitals();
        String rendered = format;
        rendered = rendered.replace("{hp}", value(vitals.hp(), HEALTH_COLOR, colorize));
        rendered = rendered.replace("{maxHp}", value(vitals.maxHp(), HEALTH_COLOR, colorize));
        rendered = rendered.replace("{mana}", value(vitals.mana(), MANA_COLOR, colorize));
        rendered = rendered.replace("{maxMana}", value(vitals.maxMana(), MANA_COLOR, colorize));
        rendered = rendered.replace("{move}", value(vitals.move(), MOVE_COLOR, colorize));
        rendered = rendered.replace("{maxMove}", value(vitals.maxMove(), MOVE_COLOR, colorize));
        rendered = rendered.replace("{exp}", value(player.getExperience(), EXP_COLOR, colorize));
        rendered = rendered.replace("{partyHp}", partyHp != null ? partyHp : "");
        rendered = applyPercentTokens(rendered, player, vitals, colorize);
        if (player.isResting()) {
            rendered = "[REST] " + rendered;
        }
        return rendered;
    }

    private String applyPercentTokens(String input, Player player, PlayerVitals vitals, boolean colorize) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '%' && i + 1 < input.length()) {
                char token = input.charAt(i + 1);
                String replacement = switch (token) {
                    case 'h' -> value(vitals.hp(), HEALTH_COLOR, colorize);
                    case 'H' -> value(vitals.maxHp(), HEALTH_COLOR, colorize);
                    case 'm' -> value(vitals.mana(), MANA_COLOR, colorize);
                    case 'M' -> value(vitals.maxMana(), MANA_COLOR, colorize);
                    case 'v' -> value(vitals.move(), MOVE_COLOR, colorize);
                    case 'x' -> value(player.getExperience(), EXP_COLOR, colorize);
                    case 'l' -> value(player.getLevel(), LEVEL_COLOR, colorize);
                    case '%' -> "%";
                    default -> null;
                };
                if (replacement != null) {
                    result.append(replacement);
                    i++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private String value(long amount, String color, boolean colorize) {
        String text = String.valueOf(amount);
        return colorize ? color + text + RESET : text;
    }
}
