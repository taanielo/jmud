package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PlayerCommandQueueTest {

    @Test
    void executesCommandsInOrderAndContinuesAfterFailure() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        List<String> executed = new ArrayList<>();

        queue.enqueue(() -> executed.add("first"));
        queue.enqueue(() -> {
            executed.add("boom");
            throw new IllegalStateException("fail");
        });
        queue.enqueue(() -> executed.add("third"));

        queue.tick();

        assertEquals(List.of("first", "boom", "third"), executed);
    }
}
