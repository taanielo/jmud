package io.taanielo.jmud.core.combat;

import java.util.List;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Represents an entity that can participate in combat.
 */
public interface Combatant {
    Username getUsername();

    PlayerVitals getVitals();

    List<EffectInstance> effects();
}
