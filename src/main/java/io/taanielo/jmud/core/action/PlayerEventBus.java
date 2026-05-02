package io.taanielo.jmud.core.action;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Routes {@link GameActionResult}s produced by server-side actors (e.g. mob AI)
 * to the appropriate connected player session.
 *
 * <p>Each player session registers a handler on login and unregisters on logout.
 * Publishers (e.g. {@code MobRegistry}) call {@link #publish} without knowing
 * anything about transport or session internals.
 */
public class PlayerEventBus {

    private final ConcurrentHashMap<Username, Consumer<GameActionResult>> handlers = new ConcurrentHashMap<>();

    public void register(Username username, Consumer<GameActionResult> handler) {
        handlers.put(username, handler);
    }

    public void unregister(Username username) {
        handlers.remove(username);
    }

    public void publish(Username username, GameActionResult result) {
        Consumer<GameActionResult> handler = handlers.get(username);
        if (handler != null) {
            handler.accept(result);
        }
    }
}
