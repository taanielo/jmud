package io.taanielo.jmud.core.effects;

public interface EffectMessageSink {
    void sendToTarget(String message);

    default void sendToRoom(String message) {
    }
}
