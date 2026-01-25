package io.taanielo.jmud.core.healing;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;

public class PlayerHealingTicker implements Tickable {
    private final Supplier<Player> playerSupplier;
    private final Consumer<Player> playerUpdater;
    private final HealingEngine engine;
    private final HealingBaseResolver baseResolver;

    public PlayerHealingTicker(
        Supplier<Player> playerSupplier,
        Consumer<Player> playerUpdater,
        HealingEngine engine,
        HealingBaseResolver baseResolver
    ) {
        this.playerSupplier = Objects.requireNonNull(playerSupplier, "Player supplier is required");
        this.playerUpdater = Objects.requireNonNull(playerUpdater, "Player updater is required");
        this.engine = Objects.requireNonNull(engine, "Healing engine is required");
        this.baseResolver = Objects.requireNonNull(baseResolver, "Base resolver is required");
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
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to apply healing: " + e.getMessage(), e);
        }
    }
}
