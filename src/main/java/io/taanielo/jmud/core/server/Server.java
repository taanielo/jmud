package io.taanielo.jmud.core.server;

public interface Server extends Runnable {

    /**
     * Stops accepting new connections and releases listening resources.
     *
     * <p>Safe to call from a thread other than the one running {@link #run()};
     * implementations must cause any blocking accept loop in {@link #run()} to
     * return promptly. Idempotent.
     */
    void stop();
}
