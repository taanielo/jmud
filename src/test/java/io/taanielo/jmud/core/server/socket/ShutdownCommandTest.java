package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingBroadcaster;
import io.taanielo.jmud.core.server.socket.WizardCommandSupport.CapturingContext;

/**
 * Unit tests for {@link ShutdownCommand}: token matching, wizard gating, warning broadcast, and
 * shutdown triggering. Uses a synchronous executor and zero warning so the sequence runs inline.
 */
class ShutdownCommandTest {

    private static final Executor INLINE = Runnable::run;

    private ShutdownCommand command(ShutdownHandle handle, CapturingBroadcaster broadcaster, String... wizards) {
        return new ShutdownCommand(new SocketCommandRegistry(), WizardCommandSupport.wizardPolicy(wizards),
            handle, broadcaster, INLINE, Duration.ZERO);
    }

    @Test
    void matchesShutdownToken() {
        ShutdownCommand cmd = command(new ShutdownHandle(), new CapturingBroadcaster(), "Al");
        assertTrue(cmd.match("SHUTDOWN").isPresent());
        assertTrue(cmd.match("shutdown").isPresent());
        assertFalse(cmd.match("SHOUT hi").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ShutdownHandle handle = new ShutdownHandle();
        handle.install(() -> ran.set(true));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        CapturingContext context = new CapturingContext(WizardCommandSupport.player("Bob"));

        command(handle, broadcaster, "Alice").match("SHUTDOWN").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("denied"));
        assertFalse(ran.get(), "shutdown must not run for a non-wizard");
        assertTrue(broadcaster.globalDeliveries.isEmpty());
    }

    @Test
    void notInstalledHandleReportsUnavailable() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        CapturingContext context = new CapturingContext(WizardCommandSupport.player("Alice"));

        command(new ShutdownHandle(), broadcaster, "Alice").match("SHUTDOWN").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("not available"));
        assertTrue(broadcaster.globalDeliveries.isEmpty());
    }

    @Test
    void wizardBroadcastsWarningAndTriggersShutdown() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ShutdownHandle handle = new ShutdownHandle();
        handle.install(() -> ran.set(true));
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player alice = WizardCommandSupport.player("Alice");
        CapturingContext context = new CapturingContext(alice);

        command(handle, broadcaster, "Alice").match("SHUTDOWN").get().execute(context);

        assertTrue(ran.get(), "shutdown sequence should have been triggered");
        assertFalse(broadcaster.globalDeliveries.isEmpty(), "a warning should be broadcast to all clients");
        assertTrue(WizardCommandSupport.text(broadcaster.globalDeliveries.get(0).message())
            .toLowerCase().contains("shut down"));
        assertTrue(context.promptMessage.toLowerCase().contains("shutdown initiated"));
    }
}
