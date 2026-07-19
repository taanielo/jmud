package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.area.WayfindService.AutoWalkStep;

/**
 * Unit tests for {@link AutoWalkState}: the session-transient, tick-thread-only state machine backing
 * {@code AUTOWALK}. The step action passed to {@link AutoWalkState#begin} mirrors the real
 * {@code SocketCommandContextImpl#advanceAutoWalk} contract (peek one step, "perform" it, pop it,
 * cancel on arrival or block) so the behaviour is exercised without any networking (AGENTS.md §10).
 */
class AutoWalkStateTest {

    private static List<AutoWalkStep> walkSteps(Direction... directions) {
        List<AutoWalkStep> steps = new ArrayList<>();
        for (Direction direction : directions) {
            steps.add(new AutoWalkStep.Walk(direction));
        }
        return steps;
    }

    @Test
    void multiStepWalkCompletesOneDirectionPerTick() {
        AutoWalkState walk = new AutoWalkState();
        List<Direction> walked = new ArrayList<>();
        walk.begin("Frozen Peaks", walkSteps(Direction.NORTH, Direction.UP, Direction.EAST), () -> {
            walked.add(((AutoWalkStep.Walk) walk.peekNextStep()).direction());
            walk.advanceStep();
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
    void ferryStepIsHeldUntilItReportsComplete() {
        AutoWalkState walk = new AutoWalkState();
        List<AutoWalkStep> steps = new ArrayList<>();
        steps.add(new AutoWalkStep.Ferry("Coastal Ferry", RoomId.of("deck"), RoomId.of("south-dock")));
        steps.add(new AutoWalkStep.Walk(Direction.EAST));
        List<String> log = new ArrayList<>();
        // Simulate the ferry taking three ticks (board, wait, arrive) before the leg completes.
        int[] ferryTicks = {0};
        walk.begin("Shrouded Isle", steps, () -> {
            switch (walk.peekNextStep()) {
                case AutoWalkStep.Ferry ferry -> {
                    ferryTicks[0]++;
                    log.add("ferry-" + ferryTicks[0]);
                    if (ferryTicks[0] >= 3) {
                        walk.advanceStep();
                    }
                }
                case AutoWalkStep.Walk step -> {
                    log.add("walk-" + step.direction());
                    walk.advanceStep();
                    if (!walk.hasNextStep()) {
                        walk.cancel();
                    }
                }
            }
        });

        walk.tick();
        walk.tick();
        assertEquals(2, walk.remainingSteps(), "the ferry step stays queued while it is in progress");
        walk.tick();
        assertEquals(1, walk.remainingSteps(), "the ferry step pops once complete");
        walk.tick();
        assertFalse(walk.isWalking());
        assertEquals(List.of("ferry-1", "ferry-2", "ferry-3", "walk-EAST"), log);
    }

    @Test
    void cancelMidRouteStopsFurtherSteps() {
        AutoWalkState walk = new AutoWalkState();
        List<Direction> walked = new ArrayList<>();
        walk.begin("Frozen Peaks", walkSteps(Direction.NORTH, Direction.UP, Direction.EAST), () -> {
            walked.add(((AutoWalkStep.Walk) walk.peekNextStep()).direction());
            walk.advanceStep();
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
        walk.begin("Old Town", walkSteps(Direction.NORTH, Direction.NORTH), () -> { });
        assertEquals("Old Town", walk.destinationName());
        assertEquals(2, walk.remainingSteps());

        walk.begin("New Peaks", walkSteps(Direction.EAST), () -> { });
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
    void peekAndAdvanceThrowWhenNoneQueued() {
        AutoWalkState walk = new AutoWalkState();
        assertThrows(IllegalStateException.class, walk::peekNextStep);
        assertThrows(IllegalStateException.class, walk::advanceStep);
    }

    @Test
    void tickIsNoOpWhenNotWalking() {
        AutoWalkState walk = new AutoWalkState();
        walk.tick();
        assertFalse(walk.isWalking());
        assertEquals(0, walk.remainingSteps());
    }
}
