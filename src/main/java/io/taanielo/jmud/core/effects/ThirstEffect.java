package io.taanielo.jmud.core.effects;

import io.taanielo.jmud.core.player.Player;

public class ThirstEffect extends PlayerEffect {

    private EffectState state;
    private EffectSeverity lastSeverity = EffectSeverity.NORMAL;

    public ThirstEffect(Player player, MessageSink messageSink, EffectState state) {
        super(player, messageSink);
        this.state = state;
    }

    public static ThirstEffect defaultEffect(Player player, MessageSink messageSink) {
        int warning = EffectSettings.warningThreshold();
        int severe = EffectSettings.severeThreshold();
        EffectState state = EffectState.of(
            EffectSettings.maxThirst(),
            EffectSettings.maxThirst(),
            EffectSettings.thirstDecay(),
            warning,
            severe
        );
        return new ThirstEffect(player, messageSink, state);
    }

    @Override
    public EffectId id() {
        return EffectId.THIRST;
    }

    @Override
    public void tick() {
        state = state.decay();
        EffectSeverity severity = state.severity();
        if (severity != lastSeverity) {
            if (severity == EffectSeverity.WARNING) {
                send("You feel thirsty.");
            } else if (severity == EffectSeverity.SEVERE) {
                send("You are dehydrated.");
            }
            lastSeverity = severity;
        }
    }
}
