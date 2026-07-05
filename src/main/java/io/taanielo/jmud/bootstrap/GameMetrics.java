package io.taanielo.jmud.bootstrap;

import java.util.Objects;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.socket.PlayerTicker;
import io.taanielo.jmud.core.tick.TickRegistry;

/**
 * Holds the application-wide Micrometer {@link MeterRegistry} and registers
 * infrastructure-level gauges.
 *
 * <p>When {@code jmud.metrics.enabled=true} (the default), a
 * {@link JmxMeterRegistry} is created so all metrics are visible over JMX
 * without any extra configuration. When the flag is {@code false}, an empty
 * {@link CompositeMeterRegistry} (no-op) is used so every metric call is a
 * cheap no-op and no JMX MBeans are registered.
 *
 * <p>Callers receive the registry via {@link #registry()} and must never
 * null-check it; the no-op path still returns a valid registry.
 *
 * <p>Metrics code must never be placed in domain packages
 * ({@code core.combat}, {@code core.effects}, etc.). All instrumentation
 * belongs in infrastructure or adapter classes (AGENTS.md §3.2).
 */
public final class GameMetrics {

    private final MeterRegistry registry;

    private GameMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Registry is required");
    }

    /**
     * Creates a {@link GameMetrics} instance from the given config.
     *
     * <p>Reads {@code jmud.metrics.enabled} (default {@code true}). When
     * enabled, starts a JMX registry; otherwise returns a no-op instance.
     *
     * @param config the game configuration to read the flag from
     * @return a fully constructed {@code GameMetrics}; never {@code null}
     */
    public static GameMetrics create(GameConfig config) {
        Objects.requireNonNull(config, "Config is required");
        boolean enabled = config.getBoolean("jmud.metrics.enabled", true);
        if (enabled) {
            return new GameMetrics(new JmxMeterRegistry(JmxConfig.DEFAULT, io.micrometer.core.instrument.Clock.SYSTEM));
        }
        return new GameMetrics(new CompositeMeterRegistry());
    }

    /**
     * Creates a no-op {@link GameMetrics} instance backed by an empty
     * {@link CompositeMeterRegistry}. Useful in tests that do not care
     * about metrics.
     *
     * @return a no-op {@code GameMetrics}; never {@code null}
     */
    public static GameMetrics noOp() {
        return new GameMetrics(new CompositeMeterRegistry());
    }

    /**
     * Returns the underlying meter registry. Never {@code null}; when
     * metrics are disabled the registry is a no-op.
     *
     * @return the meter registry
     */
    public MeterRegistry registry() {
        return registry;
    }

    /**
     * Registers aggregate gauges that require references to the tick registry
     * and the client pool. Must be called once after the composition root has
     * finished wiring all components.
     *
     * <p>Gauges registered here:
     * <ul>
     *   <li>{@code jmud.players.online} — number of currently connected clients</li>
     *   <li>{@code jmud.tick.tickables} — total registered tickables</li>
     *   <li>{@code jmud.command.queue.size.total} — total pending player commands
     *       across all connected players (sum of all {@link PlayerTicker} queue depths)</li>
     * </ul>
     *
     * @param tickRegistry the tick registry used to enumerate tickables and sum queue depths
     * @param clientPool   the pool of connected clients used for the online-player gauge
     */
    public void bindGlobalGauges(TickRegistry tickRegistry, ClientPool clientPool) {
        Objects.requireNonNull(tickRegistry, "Tick registry is required");
        Objects.requireNonNull(clientPool, "Client pool is required");

        Gauge.builder("jmud.players.online", clientPool, pool -> pool.clients().size())
            .description("Number of currently connected players")
            .register(registry);

        Gauge.builder("jmud.tick.tickables", tickRegistry, reg -> reg.snapshot().size())
            .description("Number of registered tickables in the tick registry")
            .register(registry);

        Gauge.builder(
                "jmud.command.queue.size.total",
                tickRegistry,
                reg -> reg.snapshot().stream()
                        .filter(t -> t instanceof PlayerTicker)
                        .mapToInt(t -> ((PlayerTicker) t).totalQueuedCommands())
                        .sum())
            .description("Total pending player commands across all connected players")
            .register(registry);
    }
}
