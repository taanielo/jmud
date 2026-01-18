package io.taanielo.jmud.core.effects;

import java.util.Objects;

import io.taanielo.jmud.core.tick.Tickable;

public class PlayerEffectTicker implements Tickable {
    private final EffectTarget target;
    private final EffectEngine engine;
    private final EffectMessageSink sink;

    public PlayerEffectTicker(EffectTarget target, EffectEngine engine, EffectMessageSink sink) {
        this.target = Objects.requireNonNull(target, "Effect target is required");
        this.engine = Objects.requireNonNull(engine, "Effect engine is required");
        this.sink = Objects.requireNonNull(sink, "Effect message sink is required");
    }

    @Override
    public void tick() {
        try {
            engine.tick(target, sink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to tick effects: " + e.getMessage(), e);
        }
    }
}
