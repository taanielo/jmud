package io.taanielo.jmud.core.effects;

import io.taanielo.jmud.core.player.Player;

public class HungerEffect extends PlayerEffect {

    private EffectState state;
    private EffectSeverity lastSeverity = EffectSeverity.NORMAL;

    public HungerEffect(Player player, MessageSink messageSink, EffectState state) {
        super(player, messageSink);
        this.state = state;
    }

    public static HungerEffect defaultEffect(Player player, MessageSink messageSink) {
        int warning = EffectSettings.warningThreshold();
        int severe = EffectSettings.severeThreshold();
        EffectState state = EffectState.of(
            EffectSettings.maxHunger(),
            EffectSettings.maxHunger(),
            EffectSettings.hungerDecay(),
            warning,
            severe
        );
        return new HungerEffect(player, messageSink, state);
    }

    @Override
    public EffectId id() {
        return EffectId.HUNGER;
    }

    @Override
    public void tick() {
        state = state.decay();
        EffectSeverity severity = state.severity();
        if (severity != lastSeverity) {
            if (severity == EffectSeverity.WARNING) {
                send("You feel hungry.");
            } else if (severity == EffectSeverity.SEVERE) {
                send("You are starving.");
            }
            lastSeverity = severity;
        }
    }
}
