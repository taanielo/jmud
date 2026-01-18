package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class PromptRenderer {

    public String render(String format, Player player) {
        PlayerVitals vitals = player.getVitals();
        String rendered = format;
        rendered = rendered.replace("{hp}", String.valueOf(vitals.hp()));
        rendered = rendered.replace("{maxHp}", String.valueOf(vitals.maxHp()));
        rendered = rendered.replace("{mana}", String.valueOf(vitals.mana()));
        rendered = rendered.replace("{maxMana}", String.valueOf(vitals.maxMana()));
        rendered = rendered.replace("{move}", String.valueOf(vitals.move()));
        rendered = rendered.replace("{maxMove}", String.valueOf(vitals.maxMove()));
        rendered = rendered.replace("{exp}", String.valueOf(player.getExperience()));
        return rendered;
    }
}
