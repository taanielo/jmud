package io.taanielo.jmud.core.effects;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

public abstract class PlayerEffect implements Effect<Player> {

    private final Player player;
    private final MessageSink messageSink;

    protected PlayerEffect(Player player, MessageSink messageSink) {
        this.player = Objects.requireNonNull(player, "Player is required");
        this.messageSink = Objects.requireNonNull(messageSink, "Message sink is required");
    }

    @Override
    public Player target() {
        return player;
    }

    protected void send(String message) {
        messageSink.send(message);
    }
}
