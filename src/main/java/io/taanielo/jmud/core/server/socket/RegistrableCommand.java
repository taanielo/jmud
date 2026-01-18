package io.taanielo.jmud.core.server.socket;

import java.util.Objects;

/**
 * Base command that registers itself with the provided registry.
 */
public abstract class RegistrableCommand implements SocketCommandHandler {
    protected RegistrableCommand(SocketCommandRegistry registry) {
        Objects.requireNonNull(registry, "Command registry is required").register(this);
    }
}
