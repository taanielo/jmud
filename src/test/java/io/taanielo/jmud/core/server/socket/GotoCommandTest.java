package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingBroadcaster;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingContext;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.RoomWorld;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link GotoCommand}: token matching, wizard gating, room validation, teleport, and
 * room notifications.
 */
class GotoCommandTest {

    private static final RoomId START = RoomId.of("start");
    private static final RoomId DEST = RoomId.of("dest");

    private RoomWorld world() {
        return WizardCommandSupport.world(START,
            WizardCommandSupport.room(START, "Start Room"),
            WizardCommandSupport.room(DEST, "Destination Room"));
    }

    private GotoCommand command(RoomWorld world, CapturingBroadcaster broadcaster, String... wizards) {
        return new GotoCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy(wizards),
            world.location(), world.roomService(), broadcaster);
    }

    @Test
    void matchesGotoToken() {
        GotoCommand cmd = command(world(), new CapturingBroadcaster(), "Al");
        assertTrue(cmd.match("GOTO dest").isPresent());
        assertTrue(cmd.match("goto dest").isPresent());
        assertFalse(cmd.match("GO north").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        RoomWorld world = world();
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player bob = WizardCommandSupport.player("Bob");
        world.roomService().ensurePlayerLocation(bob.getUsername());
        CapturingContext context = new CapturingContext(bob);

        command(world, broadcaster, "Alice").match("GOTO dest").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("denied"));
        assertEquals(START, world.roomService().findPlayerLocation(bob.getUsername()).orElseThrow(),
            "denied admin must not be moved");
        assertTrue(broadcaster.roomDeliveries.isEmpty());
    }

    @Test
    void unknownRoomIsRejected() {
        RoomWorld world = world();
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, broadcaster, "Alice").match("GOTO nowhere").get().execute(context);

        assertTrue(context.promptMessage.contains("nowhere"));
        assertEquals(START, world.roomService().findPlayerLocation(alice.getUsername()).orElseThrow());
        assertTrue(broadcaster.roomDeliveries.isEmpty());
    }

    @Test
    void blankArgumentShowsUsage() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        CapturingContext context = new CapturingContext(WizardCommandSupport.player("Alice"));

        command(world(), broadcaster, "Alice").match("GOTO").get().execute(context);

        assertTrue(context.promptMessage.startsWith("Usage:"));
    }

    @Test
    void wizardTeleportsAndBroadcastsArrivalAndDeparture() {
        RoomWorld world = world();
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        Username admin = alice.getUsername();
        world.roomService().ensurePlayerLocation(admin);
        CapturingContext context = new CapturingContext(alice);

        command(world, broadcaster, "Alice").match("GOTO dest").get().execute(context);

        assertEquals(DEST, world.roomService().findPlayerLocation(admin).orElseThrow(),
            "wizard should be relocated to the destination room");
        assertTrue(context.lookSent, "the new room should be shown after teleport");
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("Destination Room")));

        Optional<CapturingBroadcaster.RoomDelivery> departure = broadcaster.roomDeliveries.stream()
            .filter(d -> d.room().equals(START)).findFirst();
        Optional<CapturingBroadcaster.RoomDelivery> arrival = broadcaster.roomDeliveries.stream()
            .filter(d -> d.room().equals(DEST)).findFirst();
        assertTrue(departure.isPresent(), "departure notice should go to the old room");
        assertTrue(arrival.isPresent(), "arrival notice should go to the new room");
        assertTrue(WizardCommandSupport.text(departure.get().message()).contains("vanishes"));
        assertTrue(WizardCommandSupport.text(arrival.get().message()).contains("arrives"));
        assertTrue(departure.get().exclude().contains(admin), "admin excluded from own notices");
        assertTrue(arrival.get().exclude().contains(admin));
    }
}
