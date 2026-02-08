package io.taanielo.jmud.core.effects;

import java.util.List;

import io.taanielo.jmud.core.authentication.Username;

public interface EffectTarget {
    List<EffectInstance> effects();

    default Username username() {
        return null;
    }

    default String displayName() {
        Username username = username();
        return username == null ? null : username.getValue();
    }
}
