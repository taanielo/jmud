package io.taanielo.jmud.core.player;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.RoomService;

public class PlayerRespawnTicker implements Tickable {
    private final Supplier<Player> playerSupplier;
    private final Consumer<Player> playerUpdater;
    private final RoomService roomService;
    private final int respawnTicks;
    private volatile boolean scheduled;
    private volatile int remainingTicks;

    public PlayerRespawnTicker(
        Supplier<Player> playerSupplier,
        Consumer<Player> playerUpdater,
        RoomService roomService,
        int respawnTicks
    ) {
        this.playerSupplier = Objects.requireNonNull(playerSupplier, "Player supplier is required");
        this.playerUpdater = Objects.requireNonNull(playerUpdater, "Player updater is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        if (respawnTicks < 0) {
            throw new IllegalArgumentException("Respawn ticks must be non-negative");
        }
        this.respawnTicks = respawnTicks;
    }

    public void schedule() {
        if (scheduled) {
            return;
        }
        if (respawnTicks == 0) {
            Player player = playerSupplier.get();
            if (player != null && player.isDead()) {
                respawn(player);
            }
            return;
        }
        remainingTicks = respawnTicks;
        scheduled = true;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    @Override
    public void tick() {
        if (!scheduled) {
            return;
        }
        Player player = playerSupplier.get();
        if (player == null) {
            return;
        }
        if (!player.isDead()) {
            scheduled = false;
            return;
        }
        remainingTicks -= 1;
        if (remainingTicks <= 0) {
            scheduled = false;
            respawn(player);
        }
    }

    private void respawn(Player player) {
        Player respawned = player.respawn();
        roomService.respawnPlayer(respawned.getUsername());
        playerUpdater.accept(respawned);
    }
}
