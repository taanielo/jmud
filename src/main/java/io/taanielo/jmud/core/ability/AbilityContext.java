package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

public class AbilityContext {
    private Player source;
    private Player target;

    public AbilityContext(Player source, Player target) {
        this.source = Objects.requireNonNull(source, "Source player is required");
        this.target = Objects.requireNonNull(target, "Target player is required");
    }

    public Player source() {
        return source;
    }

    public Player target() {
        return target;
    }

    public void updateSource(Player updated) {
        this.source = Objects.requireNonNull(updated, "Updated source is required");
    }

    public void updateTarget(Player updated) {
        this.target = Objects.requireNonNull(updated, "Updated target is required");
    }
}
