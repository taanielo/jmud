package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingBroadcaster;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingContext;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.RoomWorld;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link SpawnCommand}: token matching, wizard gating, template validation, target
 * room resolution, and room notifications.
 */
class SpawnCommandTest {

    private static final RoomId START = RoomId.of("start");
    private static final RoomId DEST = RoomId.of("dest");

    private MobTemplate goblin() {
        return new MobTemplate(
            MobId.of("goblin"), "Goblin", 20, null, null, false, List.of(),
            START, 1, 10, 5, null, List.of(), false);
    }

    private RoomWorld world() {
        return WizardCommandSupport.world(START,
            WizardCommandSupport.room(START, "Start Room"),
            WizardCommandSupport.room(DEST, "Destination Room"));
    }

    private SpawnCommand command(RoomWorld world, MobRegistry registry,
                                 CapturingBroadcaster broadcaster, String... wizards) {
        return new SpawnCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy(wizards),
            registry, world.roomService(), broadcaster);
    }

    @Test
    void matchesSpawnToken() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        SpawnCommand cmd = command(world, registry, new CapturingBroadcaster(), "Al");
        assertTrue(cmd.match("SPAWN goblin").isPresent());
        assertFalse(cmd.match("SPELL goblin").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player bob = WizardCommandSupport.player("Bob");
        world.roomService().ensurePlayerLocation(bob.getUsername());
        CapturingContext context = new CapturingContext(bob);

        command(world, registry, broadcaster, "Alice").match("SPAWN goblin dest").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase(Locale.ROOT).contains("denied"));
        assertTrue(registry.getMobsInRoom(DEST).isEmpty(), "no mob should be spawned for a non-wizard");
    }

    @Test
    void unknownTemplateIsRejected() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, registry, broadcaster, "Alice").match("SPAWN dragon dest").get().execute(context);

        assertTrue(context.promptMessage.contains("dragon"));
        assertTrue(registry.getMobsInRoom(DEST).isEmpty());
    }

    @Test
    void wizardSpawnsIntoNamedRoomAndBroadcasts() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, registry, broadcaster, "Alice").match("SPAWN goblin dest").get().execute(context);

        assertEquals(1, registry.getMobsInRoom(DEST).size(), "a goblin should be spawned into the target room");
        assertTrue(context.promptMessage.contains("Goblin"));
        assertTrue(broadcaster.roomDeliveries.stream().anyMatch(d -> d.room().equals(DEST)
            && WizardCommandSupport.text(d.message()).contains("Goblin")));
    }

    @Test
    void wizardSpawnsIntoCurrentRoomWhenNoRoomGiven() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        int before = registry.getMobsInRoom(START).size();
        command(world, registry, broadcaster, "Alice").match("SPAWN goblin").get().execute(context);

        assertEquals(before + 1, registry.getMobsInRoom(START).size(),
            "with no room given, the mob spawns in the admin's current room");
    }

    @Test
    void unavailableRegistryReportsGracefully() {
        RoomWorld world = world();
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        new SpawnCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy("Alice"),
            null, world.roomService(), broadcaster)
            .match("SPAWN goblin").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase(Locale.ROOT).contains("not available"));
    }
}
