package io.taanielo.jmud.core.server.socket;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Late-bound handle to the server's orderly shutdown sequence.
 *
 * <p>The {@link ShutdownCoordinator} depends on the live {@code Server} instances, which are
 * constructed in {@code Main} after the composition root (and therefore after the
 * {@link SocketCommandRegistry}). The wizard {@code SHUTDOWN} command still needs a way to trigger
 * that sequence, so it holds this handle at construction time and {@code Main} installs the actual
 * shutdown action into it once the coordinator exists.
 *
 * <p>The stored action is {@code volatile} so the install (on the main thread) is visible to the
 * tick thread that later triggers the command.
 */
public final class ShutdownHandle {

    private volatile @Nullable Runnable action;

    /**
     * Installs the shutdown action to run when a wizard triggers {@code SHUTDOWN}.
     *
     * @param action the shutdown sequence to invoke (typically {@code shutdownCoordinator::shutdown})
     */
    public void install(Runnable action) {
        this.action = Objects.requireNonNull(action, "Shutdown action is required");
    }

    /**
     * Returns whether a shutdown action has been installed.
     *
     * @return {@code true} once {@link #install(Runnable)} has been called
     */
    public boolean isInstalled() {
        return action != null;
    }

    /**
     * Runs the installed shutdown action, or does nothing when none has been installed yet.
     */
    public void run() {
        Runnable current = action;
        if (current != null) {
            current.run();
        }
    }
}
