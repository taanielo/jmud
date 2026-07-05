package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Bounded per-player command queue drained by the tick thread.
 *
 * <p>Reader threads enqueue parsed commands via {@link #enqueue(Runnable)}; the tick
 * thread executes them in FIFO order in {@link #tick()} (AGENTS.md §5). The queue is
 * bounded so a flooding client cannot grow memory without limit: once
 * {@code capacity} commands are pending, {@link #enqueue(Runnable)} rejects further
 * commands without blocking, and the caller is expected to inform the player.
 *
 * <p>Each tick drains at most {@code maxCommandsPerTick} commands so a full queue
 * cannot monopolize a tick; the remainder carries over to the next tick.
 */
@Slf4j
public class PlayerCommandQueue implements Tickable {

    /** Default maximum number of pending commands (enough for legitimate fast typing/aliases). */
    public static final int DEFAULT_CAPACITY = 20;
    /** Default maximum number of commands executed per tick. */
    public static final int DEFAULT_MAX_COMMANDS_PER_TICK = 5;

    private final LinkedBlockingQueue<Runnable> queue;
    private final int maxCommandsPerTick;

    /**
     * Creates a queue with {@link #DEFAULT_CAPACITY} and {@link #DEFAULT_MAX_COMMANDS_PER_TICK}.
     */
    public PlayerCommandQueue() {
        this(DEFAULT_CAPACITY, DEFAULT_MAX_COMMANDS_PER_TICK);
    }

    /**
     * Creates a queue with the given bounds.
     *
     * @param capacity maximum number of pending commands; must be positive
     * @param maxCommandsPerTick maximum commands executed per tick; must be positive
     */
    public PlayerCommandQueue(int capacity, int maxCommandsPerTick) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (maxCommandsPerTick <= 0) {
            throw new IllegalArgumentException("Max commands per tick must be positive");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxCommandsPerTick = maxCommandsPerTick;
    }

    /**
     * Attempts to enqueue a command without blocking. Safe to call from reader threads.
     *
     * @param command the command to run on the tick thread
     * @return {@code true} if the command was accepted; {@code false} if the queue is
     *     full and the command was dropped
     */
    public boolean enqueue(Runnable command) {
        boolean accepted = queue.offer(Objects.requireNonNull(command, "Command is required"));
        if (!accepted) {
            log.debug("Player command queue full ({} pending); dropping command", queue.size());
        }
        return accepted;
    }

    @Override
    public void tick() {
        int executed = 0;
        Runnable task;
        while (executed < maxCommandsPerTick && (task = queue.poll()) != null) {
            executed++;
            try {
                task.run();
            } catch (Exception e) {
                log.error("Player command failed", e);
            }
        }
    }

    /**
     * Returns the number of commands currently waiting in the queue.
     *
     * @return current queue depth; always &ge; 0
     */
    public int size() {
        return queue.size();
    }
}
