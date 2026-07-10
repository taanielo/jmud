package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.reload.ContentReloadService;
import io.taanielo.jmud.core.reload.PreparedItemReload;
import io.taanielo.jmud.core.reload.PreparedReload;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingBroadcaster;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingContext;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickThreadDispatcher;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Unit tests for {@link ReloadCommand}: token matching, wizard gating, off-thread prepare, the
 * tick-thread commit, and success/failure messaging. Uses a synchronous executor so the prepare
 * runs inline, and drives the {@link TickRegistry} manually to run the deferred commit.
 */
class ReloadCommandTest {

    private static final Executor INLINE = Runnable::run;

    private static PreparedReload prepared(String type, int count) {
        return new PreparedReload() {
            @Override public String contentType() { return type; }
            @Override public int count() { return count; }
            @Override public void commit() { }
        };
    }

    private static PreparedItemReload preparedItems(int count) {
        return new PreparedItemReload() {
            @Override public Optional<Item> find(ItemId id) { return Optional.empty(); }
            @Override public String contentType() { return "items"; }
            @Override public int count() { return count; }
            @Override public void commit() { }
        };
    }

    private static ContentReloadService succeedingService() {
        return new ContentReloadService(
            () -> preparedItems(156),
            lookup -> prepared("rooms", 42),
            () -> prepared("mobs", 8),
            id -> Optional.empty());
    }

    private static ContentReloadService failingService(String message) {
        return new ContentReloadService(
            () -> {
                throw new RepositoryException(message);
            },
            lookup -> prepared("rooms", 0),
            null,
            id -> Optional.empty());
    }

    private ReloadCommand command(ContentReloadService service, CapturingBroadcaster broadcaster,
                                  TickRegistry registry, String... wizards) {
        return new ReloadCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy(wizards),
            service, broadcaster, new TickThreadDispatcher(registry), INLINE);
    }

    private static void tick(TickRegistry registry) {
        registry.snapshot().forEach(Tickable::tick);
    }

    @Test
    void matchesReloadToken() {
        ReloadCommand cmd = command(succeedingService(), new CapturingBroadcaster(), new TickRegistry(), "Al");
        assertTrue(cmd.match("RELOAD").isPresent());
        assertTrue(cmd.match("reload").isPresent());
        assertFalse(cmd.match("REST").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        TickRegistry registry = new TickRegistry();
        CapturingContext context = new CapturingContext(WizardCommandSupport.player("Bob"));

        command(succeedingService(), broadcaster, registry, "Alice").match("RELOAD").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("denied"));
        assertTrue(broadcaster.playerDeliveries.isEmpty());
        assertTrue(registry.snapshot().isEmpty(), "a denied reload must not schedule a commit");
    }

    @Test
    void wizardReloadReportsCountsOnNextTick() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        TickRegistry registry = new TickRegistry();
        Player alice = WizardCommandSupport.player("Alice");
        CapturingContext context = new CapturingContext(alice);

        command(succeedingService(), broadcaster, registry, "Alice").match("RELOAD").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("reloading"));
        // The commit (and its confirmation) is deferred to the tick thread.
        assertTrue(broadcaster.playerDeliveries.isEmpty(), "confirmation must wait for the tick-thread commit");

        tick(registry);

        assertEquals(1, broadcaster.playerDeliveries.size());
        assertEquals("Reloaded 42 rooms, 156 items, 8 mobs.",
            WizardCommandSupport.text(broadcaster.playerDeliveries.getFirst()));
    }

    @Test
    void parseErrorReportsFailureAndSchedulesNoCommit() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        TickRegistry registry = new TickRegistry();
        CapturingContext context = new CapturingContext(WizardCommandSupport.player("Alice"));

        command(failingService("bad room json"), broadcaster, registry, "Alice")
            .match("RELOAD").get().execute(context);

        assertTrue(registry.snapshot().isEmpty(), "a failed reload must not schedule a commit");
        assertEquals(1, broadcaster.playerDeliveries.size());
        String message = WizardCommandSupport.text(broadcaster.playerDeliveries.getFirst());
        assertTrue(message.contains("bad room json"));
        assertTrue(message.toLowerCase().contains("no changes applied"));
    }
}
