package io.taanielo.jmud.core.server.socket;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.SystemNoticeMessage;
import io.taanielo.jmud.core.player.Player;

/**
 * Handles the wizard-only {@code SHUTDOWN} command, which initiates the server's orderly shutdown.
 *
 * <p>The command broadcasts a warning to every connected client, then triggers the shutdown
 * sequence via a late-bound {@link ShutdownHandle}. The sequence must not run on the tick thread —
 * {@link ShutdownCoordinator#shutdown()} stops the tick scheduler and joins it, which would deadlock
 * if invoked from a tick — so it is dispatched to a background {@link Executor} (a virtual thread in
 * production) after the configured warning delay. Access is gated by {@link WizardPolicy}.
 */
public class ShutdownCommand extends RegistrableCommand {

    private static final Duration DEFAULT_WARNING = Duration.ofSeconds(10);

    private final WizardPolicy wizardPolicy;
    private final ShutdownHandle shutdownHandle;
    private final MessageBroadcaster messageBroadcaster;
    private final Executor shutdownExecutor;
    private final Duration warning;

    /**
     * Creates the SHUTDOWN command with the production default warning delay and a
     * virtual-thread-per-task executor.
     *
     * @param registry           the command registry to register with
     * @param wizardPolicy       policy deciding which players may shut the server down
     * @param shutdownHandle     late-bound handle to the shutdown sequence, installed by the boot code
     * @param messageBroadcaster scoped delivery service used to warn every connected client
     */
    public ShutdownCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        ShutdownHandle shutdownHandle,
        MessageBroadcaster messageBroadcaster
    ) {
        this(registry, wizardPolicy, shutdownHandle, messageBroadcaster,
            command -> Thread.ofVirtual().name("wizard-shutdown").start(command),
            DEFAULT_WARNING);
    }

    /**
     * Creates the SHUTDOWN command with an explicit executor and warning delay. Package-private so
     * tests can supply a synchronous executor and a zero delay for deterministic assertions.
     *
     * @param registry           the command registry to register with
     * @param wizardPolicy       policy deciding which players may shut the server down
     * @param shutdownHandle     late-bound handle to the shutdown sequence
     * @param messageBroadcaster scoped delivery service used to warn every connected client
     * @param shutdownExecutor   executor on which the shutdown sequence runs (never the tick thread)
     * @param warning            how long to wait after the warning before triggering shutdown
     */
    ShutdownCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        ShutdownHandle shutdownHandle,
        MessageBroadcaster messageBroadcaster,
        Executor shutdownExecutor,
        Duration warning
    ) {
        super(registry);
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        this.shutdownHandle = Objects.requireNonNull(shutdownHandle, "Shutdown handle is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        this.shutdownExecutor = Objects.requireNonNull(shutdownExecutor, "Shutdown executor is required");
        this.warning = Objects.requireNonNull(warning, "Warning duration is required");
    }

    @Override
    public String name() {
        return "shutdown";
    }

    @Override
    public String shortDescription() {
        return "Gracefully shut the server down (wizard only).";
    }

    @Override
    public String longDescription() {
        return "Usage: SHUTDOWN\n"
             + "  Warns every connected player, then saves all players and stops the server after a\n"
             + "  short delay. Restricted to wizards.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"SHUTDOWN".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleShutdown));
    }

    private void handleShutdown(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to use SHUTDOWN.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The SHUTDOWN command is restricted to wizards.");
            return;
        }
        if (!shutdownHandle.isInstalled()) {
            context.writeLineWithPrompt("Shutdown is not available right now.");
            return;
        }
        long seconds = Math.max(0, warning.toSeconds());
        messageBroadcaster.broadcastGlobal(
            new SystemNoticeMessage("The server will shut down in " + seconds + " second"
                + (seconds == 1 ? "" : "s") + "."),
            Set.of());
        context.writeLineWithPrompt("Shutdown initiated. Warning broadcast to all players.");

        // Run the shutdown sequence off the tick thread (it stops and joins the tick scheduler).
        shutdownExecutor.execute(() -> {
            sleep(warning);
            shutdownHandle.run();
        });
    }

    private static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
