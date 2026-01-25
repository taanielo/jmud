package io.taanielo.jmud.core.ability;

import java.util.Objects;

public interface AbilityEffectListener {
    void onApplied(AbilityEffect effect, AbilityContext context);

    static AbilityEffectListener noop() {
        return (effect, context) -> {
        };
    }

    static AbilityEffectListener require(AbilityEffectListener listener) {
        return Objects.requireNonNullElse(listener, noop());
    }
}
