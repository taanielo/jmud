package io.taanielo.jmud.core.effects;

import io.taanielo.jmud.core.tick.Tickable;

public interface Effect<T> extends Tickable {
    EffectId id();
    T target();
}
