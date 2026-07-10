package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.SystemNoticeMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.reload.ContentReloadService;
import io.taanielo.jmud.core.reload.PreparedContentReload;
import io.taanielo.jmud.core.reload.ReloadReport;
import io.taanielo.jmud.core.tick.TickThreadDispatcher;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Handles the wizard-only {@code RELOAD} command, which hot-reloads game content (rooms, items and
 * mob templates) from the JSON data files without stopping the server or disconnecting players.
 *
 * <p><strong>Threading (AGENTS.md §5).</strong> Reading and validating the JSON files is blocking
 * file I/O and must never run on the tick thread, so this command dispatches the read to a
 * background {@link Executor} (a virtual thread in production) via
 * {@link ContentReloadService#prepare()}. That call touches no live game state — it only builds an
 * in-memory snapshot — and throws if any file fails to parse or validate. Only once a fully valid
 * snapshot exists is the atomic cache swap applied to game state, and that swap is hopped back onto
 * the tick thread through {@link TickThreadDispatcher#runOnNextTick(Runnable)}. The wizard is then
 * told, via {@link MessageBroadcaster}, how many entries of each type were reloaded.
 *
 * <p>Because the commit happens only after a successful parse, a broken JSON file aborts the reload
 * with an explanatory message and leaves the running world completely unchanged (transactional
 * safety). Players in reloaded rooms observe the updated data on their next view/examine, since
 * those lookups read straight from the swapped repository cache. Access is gated by
 * {@link WizardPolicy}.
 */
public class ReloadCommand extends RegistrableCommand {

    private final WizardPolicy wizardPolicy;
    private final ContentReloadService reloadService;
    private final MessageBroadcaster messageBroadcaster;
    private final TickThreadDispatcher tickThreadDispatcher;
    private final Executor reloadExecutor;

    /**
     * Creates the RELOAD command with a virtual-thread-per-task executor for the file I/O.
     *
     * @param registry             the command registry to register with
     * @param wizardPolicy         policy deciding which players may reload content
     * @param reloadService        service that reads/validates content off-thread and commits it
     * @param messageBroadcaster   scoped delivery service used to report success/failure
     * @param tickThreadDispatcher bridge used to apply the reload on the tick thread
     */
    public ReloadCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        ContentReloadService reloadService,
        MessageBroadcaster messageBroadcaster,
        TickThreadDispatcher tickThreadDispatcher
    ) {
        this(registry, wizardPolicy, reloadService, messageBroadcaster, tickThreadDispatcher,
            command -> Thread.ofVirtual().name("wizard-reload").start(command));
    }

    /**
     * Creates the RELOAD command with an explicit executor. Package-private so tests can supply a
     * synchronous executor for deterministic assertions.
     *
     * @param registry             the command registry to register with
     * @param wizardPolicy         policy deciding which players may reload content
     * @param reloadService        service that reads/validates content off-thread and commits it
     * @param messageBroadcaster   scoped delivery service used to report success/failure
     * @param tickThreadDispatcher bridge used to apply the reload on the tick thread
     * @param reloadExecutor       executor on which the file I/O runs (never the tick thread)
     */
    ReloadCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        ContentReloadService reloadService,
        MessageBroadcaster messageBroadcaster,
        TickThreadDispatcher tickThreadDispatcher,
        Executor reloadExecutor
    ) {
        super(registry);
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        this.reloadService = Objects.requireNonNull(reloadService, "Reload service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        this.tickThreadDispatcher = Objects.requireNonNull(tickThreadDispatcher, "Tick thread dispatcher is required");
        this.reloadExecutor = Objects.requireNonNull(reloadExecutor, "Reload executor is required");
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String shortDescription() {
        return "Hot-reload rooms/items/mobs from JSON (wizard only).";
    }

    @Override
    public String longDescription() {
        return "Usage: RELOAD\n"
             + "  Re-reads room, item and mob definitions from the JSON data files and applies them\n"
             + "  to the running world without disconnecting anyone. Reads happen off the tick thread\n"
             + "  and changes take effect atomically; a parse error aborts the reload with no partial\n"
             + "  updates. Restricted to wizards.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"RELOAD".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleReload));
    }

    private void handleReload(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to use RELOAD.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The RELOAD command is restricted to wizards.");
            return;
        }
        Username admin = player.getUsername();
        context.writeLineWithPrompt("Reloading game content from disk...");

        // Read and validate the JSON off the tick thread; one slow or broken file must not stall the
        // world (AGENTS.md §5). The atomic cache swap is hopped back onto the tick thread.
        reloadExecutor.execute(() -> runReload(admin));
    }

    private void runReload(Username admin) {
        PreparedContentReload prepared;
        try {
            prepared = reloadService.prepare();
        } catch (RepositoryException e) {
            messageBroadcaster.sendToPlayer(
                admin,
                new SystemNoticeMessage("Reload failed: " + e.getMessage() + " (no changes applied)."));
            return;
        }
        tickThreadDispatcher.runOnNextTick(() -> {
            ReloadReport report = prepared.commit();
            messageBroadcaster.sendToPlayer(admin, new SystemNoticeMessage(report.summary()));
        });
    }
}
