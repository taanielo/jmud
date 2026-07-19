package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link AutoWalkState}: the session-transient, tick-thread-only state machine backing
 * {@code AUTOWALK}. The step action passed to {@link AutoWalkState#begin} mirrors the real
 * {@code SocketCommandContextImpl#advanceAutoWalk} contract (pop one direction, "move", cancel on
 * arrival or block) so the behaviour is exercised without any networking (AGENTS.md §10).
 */
class AutoWalkStateTest {

    @Test
    void multiStepWalkCompletesOneDirectionPerTick() {
        AutoWalkState walk = new AutoWalkState();
        List<Direction> walked = new ArrayList<>();
        walk.begin("Frozen Peaks", List.of(Direction.NORTH, Direction.UP, Direction.EAST), () -> {
            walked.add(walk.nextStep());
            if (!walk.hasNextStep()) {
                walk.cancel();
            }
        });

        assertTrue(walk.isWalking());
        assertEquals("Frozen Peaks", walk.destinationName());
        assertEquals(3, walk.remainingSteps());

        walk.tick();
        walk.tick();
        assertTrue(walk.isWalking());
        assertEquals(List.of(Direction.NORTH, Direction.UP), walked);

        walk.tick();
        assertEquals(List.of(Direction.NORTH, Direction.UP, Direction.EAST), walked);
        assertFalse(walk.isWalking(), "walk clears itself when the final step lands");

        walk.tick();
        assertEquals(3, walked.size(), "a cancelled walk never steps again");
    }

    @Test
    void cancelMidRouteStopsFurtherSteps() {
        AutoWalkState walk = new AutoWalkState();
        List<Direction> walked = new ArrayList<>();
        walk.begin("Frozen Peaks", List.of(Direction.NORTH, Direction.UP, Direction.EAST), () -> {
            walked.add(walk.nextStep());
            if (!walk.hasNextStep()) {
                walk.cancel();
            }
        });

        walk.tick();
        assertEquals(List.of(Direction.NORTH), walked);

        // Simulate an interruption (manual input / combat): the walk is cancelled externally.
        walk.cancel();
        assertFalse(walk.isWalking());

        walk.tick();
        assertEquals(List.of(Direction.NORTH), walked, "no step runs after an interruption");
    }

    @Test
    void beginReplacesAnActiveWalk() {
        AutoWalkState walk = new AutoWalkState();
        walk.begin("Old Town", List.of(Direction.NORTH, Direction.NORTH), () -> { });
        assertEquals("Old Town", walk.destinationName());
        assertEquals(2, walk.remainingSteps());

        walk.begin("New Peaks", List.of(Direction.EAST), () -> { });
        assertEquals("New Peaks", walk.destinationName());
        assertEquals(1, walk.remainingSteps());
    }

    @Test
    void beginRejectsEmptyRoute() {
        AutoWalkState walk = new AutoWalkState();
        assertThrows(IllegalArgumentException.class,
            () -> walk.begin("Nowhere", List.of(), () -> { }));
    }

    @Test
    void nextStepThrowsWhenNoneQueued() {
        AutoWalkState walk = new AutoWalkState();
        assertThrows(IllegalStateException.class, walk::nextStep);
    }

    @Test
    void tickIsNoOpWhenNotWalking() {
        AutoWalkState walk = new AutoWalkState();
        walk.tick();
        assertFalse(walk.isWalking());
        assertEquals(0, walk.remainingSteps());
    }
}
