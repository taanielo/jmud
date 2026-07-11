package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingBroadcaster;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingContext;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.RecordingPlayerRepository;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.RoomWorld;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link PurgeCommand}: token matching, wizard gating, mob removal, offline-player
 * deletion, online-player refusal, and room notifications.
 */
class PurgeCommandTest {

    private static final RoomId START = RoomId.of("start");

    private MobTemplate goblin() {
        return new MobTemplate(
            MobId.of("goblin"), "Goblin", 20, null, null, false, List.of(),
            START, 1, 10, 5, null, List.of(), false);
    }

    private RoomWorld world() {
        return WizardCommandSupport.world(START, WizardCommandSupport.room(START, "Start Room"));
    }

    private PurgeCommand command(RoomWorld world, MobRegistry registry, RecordingPlayerRepository repo,
                                 CapturingBroadcaster broadcaster, String... wizards) {
        return new PurgeCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy(wizards),
            registry, world.roomService(), repo, broadcaster);
    }

    @Test
    void matchesPurgeToken() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        PurgeCommand cmd = command(world, registry, new RecordingPlayerRepository(),
            new CapturingBroadcaster(), "Al");
        assertTrue(cmd.match("PURGE goblin").isPresent());
        assertFalse(cmd.match("PUT sword bag").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player bob = WizardCommandSupport.player("Bob");
        world.roomService().ensurePlayerLocation(bob.getUsername());
        CapturingContext context = new CapturingContext(bob);

        command(world, registry, new RecordingPlayerRepository(), broadcaster, "Alice")
            .match("PURGE goblin").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase(Locale.ROOT).contains("denied"));
        assertFalse(registry.getMobsInRoom(START).isEmpty(), "mob must remain for a non-wizard");
    }

    @Test
    void wizardPurgesMobAndBroadcasts() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, registry, new RecordingPlayerRepository(), broadcaster, "Alice")
            .match("PURGE goblin").get().execute(context);

        assertTrue(registry.getMobsInRoom(START).isEmpty(), "the goblin should be removed from the room");
        assertTrue(context.promptMessage.contains("Goblin"));
        assertTrue(broadcaster.roomDeliveries.stream().anyMatch(d -> d.room().equals(START)
            && WizardCommandSupport.text(d.message()).contains("Goblin")));
    }

    @Test
    void wizardDeletesOfflinePlayer() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        RecordingPlayerRepository repo = new RecordingPlayerRepository();
        repo.put(WizardCommandSupport.player("Ghost"));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, registry, repo, broadcaster, "Alice")
            .match("PURGE Ghost").get().execute(context);

        assertTrue(repo.deleted.contains(Username.of("Ghost")), "offline player record should be deleted");
        assertTrue(context.promptMessage.contains("Ghost"));
    }

    @Test
    void onlinePlayerCannotBePurged() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        RecordingPlayerRepository repo = new RecordingPlayerRepository();
        repo.put(WizardCommandSupport.player("Ghost"));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice, List.of(Username.of("Ghost")));

        command(world, registry, repo, broadcaster, "Alice")
            .match("PURGE Ghost").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase(Locale.ROOT).contains("online"));
        assertTrue(repo.deleted.isEmpty(), "an online player must not be deleted");
    }

    @Test
    void unknownTargetReportsNotFound() {
        RoomWorld world = world();
        MobRegistry registry = WizardCommandSupport.mobRegistry(world.roomService(), List.of(goblin()));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        world.roomService().ensurePlayerLocation(alice.getUsername());
        CapturingContext context = new CapturingContext(alice);

        command(world, registry, new RecordingPlayerRepository(), broadcaster, "Alice")
            .match("PURGE nobody").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase(Locale.ROOT).contains("no mob or offline player"));
    }
}
