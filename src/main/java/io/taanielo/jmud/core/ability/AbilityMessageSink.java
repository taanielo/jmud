package io.taanielo.jmud.core.ability;

import io.taanielo.jmud.core.player.Player;

public interface AbilityMessageSink {
    void sendToSource(Player source, String message);

    void sendToTarget(Player target, String message);

    void sendToRoom(Player source, Player target, String message);
}
