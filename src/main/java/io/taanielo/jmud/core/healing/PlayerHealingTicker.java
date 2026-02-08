package io.taanielo.jmud.core.healing;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;

public class PlayerHealingTicker implements Tickable {
    private final Supplier<Player> playerSupplier;
    private final Consumer<Player> playerUpdater;
    private final HealingEngine engine;
    private final HealingBaseResolver baseResolver;
    private final EffectRepository effectRepository;
    private final EffectMessageSink effectSink;
    private final MessageRenderer renderer = new MessageRenderer();

    public PlayerHealingTicker(
        Supplier<Player> playerSupplier,
        Consumer<Player> playerUpdater,
        HealingEngine engine,
        HealingBaseResolver baseResolver,
        EffectRepository effectRepository,
        EffectMessageSink effectSink
    ) {
        this.playerSupplier = Objects.requireNonNull(playerSupplier, "Player supplier is required");
        this.playerUpdater = Objects.requireNonNull(playerUpdater, "Player updater is required");
        this.engine = Objects.requireNonNull(engine, "Healing engine is required");
        this.baseResolver = Objects.requireNonNull(baseResolver, "Base resolver is required");
        this.effectRepository = Objects.requireNonNull(effectRepository, "Effect repository is required");
        this.effectSink = Objects.requireNonNull(effectSink, "Effect sink is required");
    }

    @Override
    public void tick() {
        Player player = playerSupplier.get();
        if (player == null) {
            return;
        }
        if (player.isDead()) {
            return;
        }
        try {
            int baseHealPerTick = baseResolver.baseHpPerTick(player);
            Player updated = engine.apply(player, baseHealPerTick);
            if (updated != player) {
                playerUpdater.accept(updated);
            }
            emitTickMessages(player, updated);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to apply healing: " + e.getMessage(), e);
        }
    }

    private void emitTickMessages(Player before, Player after) throws EffectRepositoryException {
        int beforeHp = before.getVitals().hp();
        int afterHp = after.getVitals().hp();
        boolean healed = afterHp > beforeHp;
        boolean damaged = afterHp < beforeHp;
        if (!healed && !damaged) {
            return;
        }
        List<EffectInstance> effects = after.effects();
        if (effects.isEmpty()) {
            return;
        }
        for (EffectInstance instance : effects) {
            EffectDefinition definition = effectRepository.findById(instance.id())
                .orElseThrow(() -> new EffectRepositoryException("Unknown effect id " + instance.id().getValue()));
            if (!shouldEmitTick(definition, instance, healed, damaged)) {
                continue;
            }
            MessageContext context = new MessageContext(
                after.getUsername(),
                after.getUsername(),
                after.getUsername().getValue(),
                after.getUsername().getValue(),
                null,
                definition.name(),
                null,
                null
            );
            for (MessageSpec spec : definition.messages()) {
                if (spec.phase() != MessagePhase.TICK) {
                    continue;
                }
                String rendered = renderer.render(spec, context);
                if (rendered == null || rendered.isBlank()) {
                    continue;
                }
                if (spec.channel() == MessageChannel.ROOM) {
                    effectSink.sendToRoom(rendered);
                } else {
                    effectSink.sendToTarget(rendered);
                }
            }
        }
    }

    private boolean shouldEmitTick(
        EffectDefinition definition,
        EffectInstance instance,
        boolean healed,
        boolean damaged
    ) {
        if (definition.messages().isEmpty()) {
            return false;
        }
        if (definition.tickInterval() > 1 && instance.remainingTicks() % definition.tickInterval() != 0) {
            return false;
        }
        boolean hasHeal = definition.modifiers().stream()
            .anyMatch(mod -> mod.stat().equalsIgnoreCase(HealingModifierKeys.HEAL_PER_TICK));
        boolean hasDamage = definition.modifiers().stream()
            .anyMatch(mod -> mod.stat().equalsIgnoreCase(HealingModifierKeys.DAMAGE_PER_TICK));
        return (healed && hasHeal) || (damaged && hasDamage);
    }
}
