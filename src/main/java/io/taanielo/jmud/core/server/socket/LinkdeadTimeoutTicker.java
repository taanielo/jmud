package io.taanielo.jmud.core.server.socket;

import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * System-level {@link Tickable} that ages out linkdead sessions (issue #343).
 *
 * <p>Each tick it walks the {@link PlayerSessionRegistry} and, for every session that is currently
 * linkdead, decrements its countdown. When a session's countdown reaches zero it is removed from
 * the registry and asked to run its expiry hook (final save plus {@code player.linkdead_timeout}
 * audit and transport teardown), reclaiming the player from the world.
 *
 * <p>Runs entirely on the single tick thread, so all mutation of linkdead state happens there and
 * needs no locking (AGENTS.md §5). Lives in {@code core.server.socket} rather than {@code core.tick}
 * because it depends on the socket-layer session registry, which {@code core.tick} may not reference
 * under the transport-isolation architecture rule.
 */
@Slf4j
public final class LinkdeadTimeoutTicker implements Tickable {

    private final PlayerSessionRegistry registry;

    /**
     * Creates a ticker over the given session registry.
     *
     * @param registry the registry of active (and linkdead) sessions to age out
     */
    public LinkdeadTimeoutTicker(PlayerSessionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Session registry is required");
    }

    /**
     * Decrements the countdown of every linkdead session and reaps any that have expired this tick.
     */
    @Override
    public void tick() {
        for (Map.Entry<Username, PlayerSession> entry : registry.entries()) {
            Username username = entry.getKey();
            PlayerSession session = entry.getValue();
            if (!session.isLinkdead()) {
                continue;
            }
            if (session.tickLinkdead()) {
                log.info("Linkdead session for {} timed out; reaping.", username.getValue());
                registry.removeIf(username, session);
                session.expireLinkdead();
            }
        }
    }
}
