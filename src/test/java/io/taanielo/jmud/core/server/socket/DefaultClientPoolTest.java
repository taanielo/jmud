package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.server.Client;

/**
 * Unit tests for {@link DefaultClientPool}'s two membership views (issue #514): every accepted
 * connection versus only the clients whose player has entered the game world.
 */
class DefaultClientPoolTest {

    @Test
    void addedClientIsConnectedButNotInWorld() {
        DefaultClientPool pool = new DefaultClientPool();
        Client client = new IdleClient();

        pool.add(client);

        assertEquals(List.of(client), pool.allConnections());
        assertTrue(pool.inWorld().isEmpty(),
            "A freshly accepted connection must not be in the world view");
    }

    @Test
    void promotionAddsClientToWorldViewAndKeepsItConnected() {
        DefaultClientPool pool = new DefaultClientPool();
        Client client = new IdleClient();
        pool.add(client);

        pool.promoteToWorld(client);

        assertEquals(List.of(client), pool.allConnections());
        assertEquals(List.of(client), pool.inWorld());
    }

    @Test
    void promotionIsIdempotent() {
        DefaultClientPool pool = new DefaultClientPool();
        Client client = new IdleClient();
        pool.add(client);

        pool.promoteToWorld(client);
        pool.promoteToWorld(client);

        assertEquals(List.of(client), pool.inWorld(), "Double promotion must not duplicate the client");
    }

    @Test
    void promotingAnUnpooledClientIsANoOp() {
        DefaultClientPool pool = new DefaultClientPool();
        Client stranger = new IdleClient();

        pool.promoteToWorld(stranger);

        assertTrue(pool.inWorld().isEmpty(),
            "A client that is not (or no longer) pooled must never enter the world view");
        assertTrue(pool.allConnections().isEmpty());
    }

    @Test
    void removeDemotesAndDisconnects() {
        DefaultClientPool pool = new DefaultClientPool();
        Client client = new IdleClient();
        pool.add(client);
        pool.promoteToWorld(client);

        pool.remove(client);

        assertTrue(pool.allConnections().isEmpty());
        assertTrue(pool.inWorld().isEmpty(), "Removal must also demote the client from the world view");
    }

    @Test
    void promotionOrderIsPreserved() {
        DefaultClientPool pool = new DefaultClientPool();
        Client first = new IdleClient();
        Client second = new IdleClient();
        pool.add(second);
        pool.add(first);

        pool.promoteToWorld(first);
        pool.promoteToWorld(second);

        assertEquals(List.of(first, second), pool.inWorld(),
            "The world view lists clients in promotion order, not connection order");
    }

    @Test
    void viewsAreImmutableSnapshots() {
        DefaultClientPool pool = new DefaultClientPool();
        Client client = new IdleClient();
        pool.add(client);
        pool.promoteToWorld(client);

        List<Client> connectionsSnapshot = pool.allConnections();
        List<Client> worldSnapshot = pool.inWorld();
        pool.remove(client);

        assertEquals(List.of(client), connectionsSnapshot, "Snapshots must not see later mutations");
        assertEquals(List.of(client), worldSnapshot, "Snapshots must not see later mutations");
        assertThrows(UnsupportedOperationException.class, () -> connectionsSnapshot.add(client));
        assertThrows(UnsupportedOperationException.class, () -> worldSnapshot.add(client));
        assertFalse(pool.allConnections().contains(client));
    }

    /** Minimal client whose reader thread (started by {@code add}) exits immediately. */
    private static final class IdleClient implements Client {
        @Override
        public void sendMessage(Message message) {
        }

        @Override
        public void close() {
        }

        @Override
        public void run() {
        }
    }
}
