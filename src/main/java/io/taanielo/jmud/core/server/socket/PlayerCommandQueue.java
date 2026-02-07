package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.tick.Tickable;

@Slf4j
public class PlayerCommandQueue implements Tickable {
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(Runnable command) {
        queue.add(Objects.requireNonNull(command, "Command is required"));
    }

    @Override
    public void tick() {
        Runnable task = queue.poll();
        while (task != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Player command failed", e);
            }
            task = queue.poll();
        }
    }
}
