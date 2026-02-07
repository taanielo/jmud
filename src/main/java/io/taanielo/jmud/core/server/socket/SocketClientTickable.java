package io.taanielo.jmud.core.server.socket;

import java.util.Objects;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Tick adapter that enqueues per-player tick work on the client session thread.
 */
public class SocketClientTickable implements Tickable {
    private final SocketClient client;

    /**
     * Creates a tickable for the provided socket client.
     *
     * @param client the client to tick
     */
    public SocketClientTickable(SocketClient client) {
        this.client = Objects.requireNonNull(client, "Client is required");
    }

    @Override
    public void tick() {
        client.enqueueTick();
    }
}
