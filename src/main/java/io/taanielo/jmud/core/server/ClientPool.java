package io.taanielo.jmud.core.server;

import java.util.List;

/**
 * Registry of connected clients with two membership views: every accepted connection, and the
 * subset whose player has completed world entry (issue #514).
 *
 * <p>In-world membership replaces the old per-call {@code Client.isInWorld()} filtering: consumers
 * choose the view that matches their intent, so a connection still at the login prompt or the
 * race/class creation prompts can never leak into world-facing enumeration by omission.
 */
public interface ClientPool {

    /** Registers a newly accepted connection and starts its reader thread. */
    void add(Client client);

    /** Removes a connection from the pool (and from the in-world view, if present). */
    void remove(Client client);

    /**
     * Marks a pooled client as having entered the game world — eligible for broadcasts, WHO,
     * targeted delivery, and world enumeration. Called exactly when the player's world presence is
     * wired up: normal login, character-creation completion, or linkdead reattach. A no-op when the
     * client is not (or no longer) in the pool, so a promotion racing a disconnect cannot leak a
     * closed client into the world view.
     */
    void promoteToWorld(Client client);

    int getNextId();

    /**
     * Immutable snapshot of every accepted connection: login prompt, mid character-creation,
     * in-world, and linkdead alike. For transport-level concerns — shutdown notices, connection
     * counts, session reattach lookups.
     */
    List<Client> allConnections();

    /**
     * Immutable snapshot of only the clients whose player is in the game world (includes linkdead
     * sessions — they still occupy their room). For game-facing enumeration: broadcasts, WHO,
     * targeted message delivery, arena drafting.
     */
    List<Client> inWorld();
}
