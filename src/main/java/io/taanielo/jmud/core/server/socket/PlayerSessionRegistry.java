package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Tracks the live {@link PlayerSession} for every player currently in the world, keyed by
 * {@link Username}, including sessions that have gone <em>linkdead</em> after a dropped connection
 * (issue #343).
 *
 * <p>The registry is the single lookup point used by the login flow to decide whether an
 * authenticating player should reattach to an existing (linkdead) session instead of loading a
 * fresh one from disk, and by {@link LinkdeadTimeoutTicker} to reap expired sessions.
 *
 * <p>Entries are written from reader threads (at login/reattach) and read/removed from the tick
 * thread (by the timeout ticker). A {@link ConcurrentHashMap} backs the store so these accesses are
 * safe without external locking (AGENTS.md §5); the map only ever holds transport-independent
 * session references, never mutable game state.
 */
public final class PlayerSessionRegistry {

    private final Map<Username, PlayerSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) the active session for the given username.
     *
     * @param username the authenticated player's username
     * @param session  the live session to track
     */
    public void register(Username username, PlayerSession session) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(session, "Session is required");
        sessions.put(username, session);
    }

    /**
     * Looks up the tracked session for a username.
     *
     * @param username the username to resolve
     * @return the session if one is tracked, otherwise empty
     */
    public Optional<PlayerSession> lookup(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return Optional.ofNullable(sessions.get(username));
    }

    /**
     * Removes the tracked session for a username, if present. Idempotent.
     *
     * @param username the username whose session should be forgotten
     */
    public void remove(Username username) {
        Objects.requireNonNull(username, "Username is required");
        sessions.remove(username);
    }

    /**
     * Removes the mapping for a username only when it currently points at the given session, so a
     * stale session cannot evict a newer one that has already reattached under the same name.
     *
     * @param username the username whose mapping should be removed
     * @param session  the session that must currently be mapped for removal to occur
     */
    public void removeIf(Username username, PlayerSession session) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(session, "Session is required");
        sessions.remove(username, session);
    }

    /**
     * Returns an immutable snapshot of the current username-to-session entries, safe to iterate
     * while the registry is concurrently mutated.
     *
     * @return a point-in-time copy of all tracked entries
     */
    public List<Map.Entry<Username, PlayerSession>> entries() {
        return List.copyOf(sessions.entrySet());
    }

    /**
     * Returns the number of tracked sessions.
     *
     * @return the current session count
     */
    public int size() {
        return sessions.size();
    }
}
