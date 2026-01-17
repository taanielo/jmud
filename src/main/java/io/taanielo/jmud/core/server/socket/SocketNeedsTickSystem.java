package io.taanielo.jmud.core.server.socket;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.tick.Tickable;

public class SocketNeedsTickSystem implements Tickable {

    private final ClientPool clientPool;

    public SocketNeedsTickSystem(ClientPool clientPool) {
        this.clientPool = clientPool;
    }

    @Override
    public void tick() {
        for (Client client : clientPool.clients()) {
            if (client instanceof SocketClient socketClient) {
                socketClient.tickNeeds();
            }
        }
    }
}
