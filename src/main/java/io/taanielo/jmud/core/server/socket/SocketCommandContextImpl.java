package io.taanielo.jmud.core.server.socket;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.bootstrap.GameContext;
import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityMatch;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.achievement.AchievementService.AchievementStatus;
import io.taanielo.jmud.core.action.FleeResult;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameActionService;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bank.BankTransactionResult;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.dialogue.DialogueNode;
import io.taanielo.jmud.core.dialogue.DialogueResponse;
import io.taanielo.jmud.core.dialogue.DialogueService;
import io.taanielo.jmud.core.dialogue.DialogueTree;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.notes.NoteDeletionResult;
import io.taanielo.jmud.core.notes.NotesService;
import io.taanielo.jmud.core.notes.PlayerNote;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.party.Party;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.AliasResult;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.LightingService;
import io.taanielo.jmud.core.player.MailResult;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerAliasService;
import io.taanielo.jmud.core.player.PlayerMailService;
import io.taanielo.jmud.core.player.RestSettings;
import io.taanielo.jmud.core.player.RestingTicker;
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.DailyQuestCompletionResult;
import io.taanielo.jmud.core.quest.DailyQuestService;
import io.taanielo.jmud.core.quest.DeliveryQuestResult;
import io.taanielo.jmud.core.quest.ExplorationQuestService;
import io.taanielo.jmud.core.quest.QuestDeliveryService;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestNpcDeliveryService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.shop.ShopTransactionResult;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.DoorActionResult;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemDurabilityService;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Executes gameplay commands dispatched by {@link SocketCommandDispatcher} on behalf of a single
 * connected {@link SocketClient}.
 *
 * <p>This class holds essentially all of jmud's player-facing game logic that used to live
 * directly inside {@code SocketClient} (movement, combat, inventory, shops, banking, quests,
 * training, parties, resting, and message/prompt delivery). {@code SocketClient} itself is kept
 * to transport read/write and session wiring (AGENTS.md §3.3); this class — constructed once per
 * connection and reused across every dispatched command — is where that logic now lives so it
 * stays unit-testable without sockets (AGENTS.md §10).
 */
@Slf4j
class SocketCommandContextImpl implements SocketCommandContext {

    /** Format used to render achievement unlock timestamps in the local time zone. */
    private static final DateTimeFormatter ACHIEVEMENT_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Format used to render bulletin-board note timestamps in the local time zone. */
    private static final DateTimeFormatter NOTE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SocketClient client;
    private final ClientConnection connection;
    private final PlayerSession session;
    private final GameContext context;
    private final ClientPool clientPool;
    private final GameActionService gameActionService;
    private final AbilityRegistry abilityRegistry;
    private final AbilityTargetResolver abilityTargetResolver;
    private final EncumbranceService encumbranceService;
    private final RoomService roomService;
    private final AuditService auditService;
    private final PersistenceQueue persistenceQueue;
    private final PromptRenderer promptRenderer;
    private final SocketCommandDispatcher dispatcher;
    private final PlayerAliasService playerAliasService;
    private final PlayerMailService playerMailService;
    private final LightingService lightingService;
    private final @Nullable NotesService notesService;

    /**
     * Creates a command context by extracting all game-logic dependencies from the provided
     * {@link GameContext}, so that {@link SocketClient} does not need to hold or import them.
     *
     * @param client     the owning transport adapter (used only for lifecycle callbacks)
     * @param connection the underlying transport connection for writing to the player
     * @param session    the player's in-memory session state
     * @param context    the composition root supplying all game services
     * @param clientPool the pool of connected clients, for multi-player fan-out
     * @param dispatcher the command dispatcher that sets the audit correlation ID
     */
    SocketCommandContextImpl(
        SocketClient client,
        ClientConnection connection,
        PlayerSession session,
        GameContext context,
        ClientPool clientPool,
        SocketCommandDispatcher dispatcher
    ) {
        this.client = Objects.requireNonNull(client, "Client is required");
        this.connection = Objects.requireNonNull(connection, "Connection is required");
        this.session = Objects.requireNonNull(session, "Session is required");
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.dispatcher = Objects.requireNonNull(dispatcher, "Command dispatcher is required");
        this.abilityRegistry = Objects.requireNonNull(context.abilityRegistry(), "Ability registry is required");
        this.abilityTargetResolver = Objects.requireNonNull(context.abilityTargetResolver(), "Ability target resolver is required");
        this.encumbranceService = Objects.requireNonNull(context.encumbranceService(), "Encumbrance service is required");
        this.roomService = Objects.requireNonNull(context.roomService(), "Room service is required");
        this.auditService = Objects.requireNonNull(context.auditService(), "Audit service is required");
        this.persistenceQueue = Objects.requireNonNull(context.persistenceQueue(), "Persistence queue is required");
        this.promptRenderer = new PromptRenderer();
        this.playerAliasService = new PlayerAliasService();
        this.playerMailService = new PlayerMailService();
        this.lightingService = new LightingService();
        this.notesService = context.notesService();
        this.gameActionService = new GameActionService(
            abilityRegistry,
            context.abilityCostResolver(),
            context.effectEngine(),
            context.combatEngine(),
            roomService,
            abilityTargetResolver,
            session.getCooldownTracker(),
            encumbranceService,
            p -> context.mobRegistry() != null && context.mobRegistry().isInCombat(p.getUsername()),
            context.worldRandom(),
            p -> {
                if (context.mobRegistry() != null) {
                    context.mobRegistry().fleeCombat(p.getUsername());
                }
            },
            (roomId, nameInput) -> context.mobRegistry() != null
                ? context.mobRegistry().findStealTarget(roomId, nameInput)
                : java.util.Optional.empty(),
            roomId -> context.mobRegistry() == null
                ? java.util.List.of()
                : context.mobRegistry().getMobsInRoom(roomId).stream()
                    .filter(io.taanielo.jmud.core.mob.MobInstance::isAlive)
                    .map(mob -> mob.template().name())
                    .toList()
        );
        if (context.duelService() != null) {
            this.gameActionService.setDuelService(context.duelService());
        }
        if (context.weatherEngine() != null) {
            this.gameActionService.setWeatherEngine(context.weatherEngine());
        }
    }

    // ── Client interface ───────────────────────────────────────────────

    @Override
    public boolean isAuthenticated() {
        return session.isAuthenticated();
    }

    @Override
    public Player getPlayer() {
        return session.getPlayer();
    }

    @Override
    public List<Client> clients() {
        return clientPool.clients();
    }

    @Override
    public List<Username> onlinePlayerNames() {
        return clientPool.clients().stream()
            .filter(SocketClient.class::isInstance)
            .map(SocketClient.class::cast)
            .map(SocketClient::authenticatedUsername)
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public void sendMessage(Message message) {
        client.sendMessage(message);
    }

    @Override
    public void close() {
        session.setQuitRequested(true);
        client.close();
    }

    @Override
    public void run() {
        client.run();
    }

    // ── Tick / event callbacks (invoked outside command dispatch) ──────

    /** Applies a healing/damage tick update, delivering any resulting death messages. */
    void applyHealingUpdate(Player updated) {
        Player current = session.getPlayer();
        if (current != null) {
            int beforeHp = current.getVitals().hp();
            int afterHp = updated.getVitals().hp();
            int delta = afterHp - beforeHp;
            if (delta != 0) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("amount", Math.abs(delta));
                metadata.put("hpBefore", beforeHp);
                metadata.put("hpAfter", afterHp);
                String eventType = delta > 0 ? "healing.tick" : "damage.tick";
                emitAudit(
                    eventType,
                    AuditSubject.player(current.getUsername()),
                    null,
                    resolveRoomId(current),
                    "success",
                    metadata
                );
            }
        }
        if (updated.getVitals().hp() <= 0 && (current == null || !current.isDead())) {
            GameActionResult deathResult = gameActionService.resolveDeathIfNeeded(updated, null);
            deliverMessages(deathResult.messages());
            auditDeath(deathResult.updatedTarget(), null);
            session.replacePlayer(deathResult.updatedTarget());
            return;
        }
        session.setPlayer(updated);
        saveOrWarn(updated);
    }

    /** Applies a sustenance (hunger/thirst) decay tick update. */
    void applySustenanceUpdate(Player updated) {
        session.setPlayer(updated);
        saveOrWarn(updated);
    }

    /** Delivers a hunger/thirst warning line to the player, followed by a fresh prompt. */
    void deliverSustenanceWarning(String message) {
        connection.writeLine(message);
        sendPrompt();
    }

    /** Applies a respawn tick update, sending the post-respawn room description. */
    void applyRespawnUpdate(Player updated) {
        session.replacePlayer(updated);
        connection.writeLine("You awaken in the starting room.");
        Player player = session.getPlayer();
        RoomService.LookResult result = roomService.look(player.getUsername(), session.getTextStyler());
        emitAudit(
            "player.respawn",
            AuditSubject.player(player.getUsername()),
            null,
            result.room() == null ? null : result.room().getId().getValue(),
            "success",
            Map.of()
        );
        connection.writeLines(result.lines());
        writeRoomOccupantLines(result.room());
        sendPrompt();
    }

    // ── Game action result delivery ────────────────────────────────────

    void deliverMessages(List<GameMessage> messages) {
        for (GameMessage msg : messages) {
            switch (msg.type()) {
                case SOURCE -> connection.writeLine(msg.text());
                case PLAYER -> sendToUsername(msg.targetExclude(), msg.text());
                case ROOM -> deliverRoomMessage(msg.sourceExclude(), msg.targetExclude(), msg.text());
                case ROOM_AT -> {
                    Set<Username> excludeSet = msg.targetExclude() == null ? Set.of() : Set.of(msg.targetExclude());
                    context.messageBroadcaster().broadcastToRoom(
                        msg.roomId(), new PlainTextMessage(msg.text()), excludeSet);
                }
            }
        }
    }

    void deliverResult(GameActionResult result) {
        deliverMessages(result.messages());
        if (result.updatedSource() != null) {
            session.replacePlayer(result.updatedSource());
        }
        if (result.updatedTarget() != null) {
            Player current = session.getPlayer();
            if (current != null && !result.updatedTarget().getUsername().equals(current.getUsername())) {
                updateTarget(result.updatedTarget());
            }
        }
    }

    private void updateTarget(Player updatedTarget) {
        saveOrWarn(updatedTarget);
        for (Client c : clientPool.clients()) {
            if (c instanceof SocketClient socketClient) {
                if (socketClient.isAuthenticatedUser(updatedTarget.getUsername())) {
                    socketClient.applyExternalPlayerUpdate(updatedTarget);
                    return;
                }
            }
        }
    }

    // ── ANSI command ───────────────────────────────────────────────────

    private void handleAnsiCommand(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to change ANSI settings.");
            return;
        }
        String normalized = args == null ? "" : args.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("ANSI is " + (player.isAnsiEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON" -> setAnsiEnabled(true);
            case "OFF" -> setAnsiEnabled(false);
            case "TOGGLE" -> setAnsiEnabled(!player.isAnsiEnabled());
            default -> writeLineWithPrompt("Usage: ANSI [on|off|toggle|status]");
        }
    }

    private void setAnsiEnabled(boolean enabled) {
        Player player = session.getPlayer();
        if (player.isAnsiEnabled() == enabled) {
            writeLineWithPrompt("ANSI is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        session.replacePlayer(player.withAnsiEnabled(enabled));
        session.setTextStyler(TextStylers.forEnabled(session.getPlayer().isAnsiEnabled()));
        writeLineWithPrompt("ANSI is now " + (enabled ? "ON" : "OFF"));
    }

    // ── I/O helpers ────────────────────────────────────────────────────

    /**
     * Silently cancels resting if the current player is resting.
     * The caller is responsible for sending any relevant message afterwards.
     */
    private void cancelRestIfActive() {
        Player player = session.getPlayer();
        if (player != null && player.isResting()) {
            session.setPlayer(player.withResting(false));
            session.clearRestingTicker();
            connection.writeLine("Your rest is interrupted.");
        }
    }

    /**
     * Reveals the player if they are currently hidden in stealth, emitting the break messages to
     * the player and their room. Used by attack paths (e.g. striking a mob) where breaking stealth
     * is a side effect rather than the action's own result.
     */
    private void breakStealthIfActive() {
        Player player = session.getPlayer();
        if (player != null && player.isStealthActive()) {
            deliverResult(new GameActionResult(
                player.withStealth(false),
                null,
                List.of(
                    GameMessage.toSource("You emerge from the shadows."),
                    GameMessage.toRoom(
                        player.getUsername(), null,
                        player.getUsername().getValue() + " emerges from the shadows!"))));
        }
    }

    @Override
    public void writeLineWithPrompt(String message) {
        connection.writeLine(message);
        sendPrompt();
    }

    @Override
    public void sendPrompt() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            return;
        }
        String format = player.getPromptFormat();
        if (format == null || format.isBlank()) {
            format = PromptSettings.defaultFormat();
        }
        String partyHp = format.contains("{partyHp}") ? buildPartyHpString(player) : "";
        String promptLine = promptRenderer.render(format, player, partyHp);
        connection.write(promptLine + "> ");
    }

    /**
     * Builds the {@code {partyHp}} token value for the given player.
     *
     * <p>Iterates the party members, looks up current HP from connected sessions
     * (falling back to the repository), and returns a space-separated
     * {@code Name:hp/maxHp} string. Returns an empty string when the player
     * is not in a party.
     *
     * @param player the current player
     * @return formatted party HP string, or {@code ""} when not in a party
     */
    private String buildPartyHpString(Player player) {
        PartyService partyService = context.partyService();
        if (partyService == null) {
            return "";
        }
        return partyService.findParty(player.getUsername())
            .map(party -> {
                StringBuilder sb = new StringBuilder();
                for (Username memberId : party.memberIds()) {
                    if (memberId.equals(player.getUsername())) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(memberId.getValue()).append(':').append(findMemberCurrentHp(memberId));
                }
                return sb.toString();
            })
            .orElse("");
    }

    /**
     * Returns the current HP string for a party member by consulting connected sessions
     * first, then falling back to the player repository.
     *
     * @param username the member to look up
     * @return {@code "hp/maxHp"} string
     */
    private String findMemberCurrentHp(Username username) {
        for (Client c : clientPool.clients()) {
            if (c instanceof SocketClient sc && sc.isAuthenticatedUser(username)) {
                Player p = sc.session().getPlayer();
                if (p != null) {
                    return p.getVitals().hp() + "/" + p.getVitals().maxHp();
                }
            }
        }
        return context.playerRepository().loadPlayer(username)
            .map(p -> p.getVitals().hp() + "/" + p.getVitals().maxHp())
            .orElse("?/?");
    }

    @Override
    public void sendToUsername(Username username, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        context.messageBroadcaster().sendToPlayer(username, new PlainTextMessage(message));
    }

    @Override
    public void sendToRoom(Player source, Player target, String message) {
        deliverRoomMessage(source.getUsername(), target.getUsername(), message);
    }

    @Override
    public void sendToRoom(Player source, String message) {
        if (source == null) {
            return;
        }
        deliverRoomMessage(source.getUsername(), null, message);
    }

    private void sendToRoom(Room room, Username exclude, String message) {
        if (message == null || message.isBlank() || room == null) {
            return;
        }
        Set<Username> excludeSet = exclude == null ? Set.of() : Set.of(exclude);
        context.messageBroadcaster().broadcastToRoom(room.getId(), new PlainTextMessage(message), excludeSet);
    }

    void deliverRoomMessage(Username sourceExclude, Username targetExclude, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Username lookupUser = sourceExclude != null
            ? sourceExclude
            : (session.getPlayer() != null ? session.getPlayer().getUsername() : null);
        if (lookupUser == null) {
            return;
        }
        Optional<RoomId> roomIdOpt = roomService.findPlayerLocation(lookupUser);
        if (roomIdOpt.isEmpty()) {
            return;
        }
        Set<Username> excludeSet = new HashSet<>();
        if (sourceExclude != null) {
            excludeSet.add(sourceExclude);
        }
        if (targetExclude != null) {
            excludeSet.add(targetExclude);
        }
        context.messageBroadcaster().broadcastToRoom(roomIdOpt.get(), new PlainTextMessage(message), excludeSet);
    }

    /** Writes the monster/shop/bank occupant lines for a room, if present. */
    private void writeRoomOccupantLines(Room room) {
        if (room == null) {
            return;
        }
        if (context.mobRegistry() != null) {
            var mobs = context.mobRegistry().getMobsInRoom(room.getId());
            connection.writeLine(MonsterLineFormatter.format(mobs));
        }
        String shopLine = formatShopNpcLine(room.getId());
        if (!shopLine.isEmpty()) {
            connection.writeLine(shopLine);
        }
        String bankLine = formatBankNpcLine(room.getId());
        if (!bankLine.isEmpty()) {
            connection.writeLine(bankLine);
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────

    /**
     * Hands the given player off to the write-behind persistence queue rather than
     * saving synchronously on this (tick) thread (AGENTS.md §5). Failures are logged
     * and audited by the queue itself after its retry; there is no more a way to warn
     * the player synchronously about a failed save from this call site.
     *
     * @param playerToSave the player to save
     */
    private void saveOrWarn(Player playerToSave) {
        persistenceQueue.enqueueSave(playerToSave);
    }

    // ── Auditing ───────────────────────────────────────────────────────

    void emitAudit(
        String eventType,
        AuditSubject actor,
        AuditSubject target,
        String roomId,
        String result,
        Map<String, Object> metadata
    ) {
        String correlationId = dispatcher.currentCorrelationId();
        AuditEvent event = new AuditEvent(
            eventType,
            actor,
            target,
            roomId,
            result,
            correlationId == null ? auditService.newCorrelationId() : correlationId,
            metadata
        );
        auditService.emit(event);
    }

    private void auditDeath(Player deadTarget, Player attacker) {
        if (deadTarget == null) {
            return;
        }
        AuditSubject actor = attacker == null
            ? AuditSubject.system("environment")
            : AuditSubject.player(attacker.getUsername());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("cause", attacker == null ? "effect" : "combat");
        emitAudit(
            "player.death",
            actor,
            AuditSubject.player(deadTarget.getUsername()),
            resolveRoomId(deadTarget),
            "success",
            metadata
        );
    }

    String resolveRoomId(Player player) {
        if (player == null) {
            return null;
        }
        return roomService.findPlayerLocation(player.getUsername())
            .map(RoomId::getValue)
            .orElse(null);
    }

    private void auditAbilityUse(AbilityMatch match, GameActionResult result, String input) {
        Player player = session.getPlayer();
        if (player == null || result == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        if (input != null && !input.isBlank()) {
            metadata.put("input", input.trim());
        }
        if (match != null) {
            metadata.put("abilityId", match.ability().id().getValue());
            metadata.put("abilityName", match.ability().name());
            String remainingTarget = match.remainingTarget();
            if (remainingTarget != null && !remainingTarget.isBlank()) {
                metadata.put("targetInput", remainingTarget);
            }
        }
        AuditSubject target = result.updatedTarget() == null
            ? null
            : AuditSubject.player(result.updatedTarget().getUsername());
        emitAudit(
            "ability.use",
            AuditSubject.player(player.getUsername()),
            target,
            resolveRoomId(player),
            "attempted",
            metadata
        );
    }

    private void auditAttack(GameActionResult result, String args) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        if (result.updatedTarget() == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetName", result.updatedTarget().getUsername().getValue());
        if (args != null && !args.isBlank()) {
            metadata.put("input", args.trim());
        }
        // Include the per-encounter RNG seed so any fight can be replayed from the
        // audit log: seed = mix(worldSeed, tick, actorId) — worldSeed is logged at boot.
        Object rngSeed = result.metadata().get("rngSeed");
        if (rngSeed != null) {
            metadata.put("rngSeed", rngSeed);
        }
        emitAudit(
            "combat.attack",
            AuditSubject.player(player.getUsername()),
            AuditSubject.player(result.updatedTarget().getUsername()),
            resolveRoomId(player),
            "attempted",
            metadata
        );
    }

    // ── Utility ────────────────────────────────────────────────────────

    private String formatShopNpcLine(RoomId roomId) {
        if (context.shopService() == null || roomId == null) {
            return "";
        }
        return context.shopService()
            .findShopInRoom(roomId)
            .map(shop -> "Merchant: " + shop.name())
            .orElse("");
    }

    private String formatBankNpcLine(RoomId roomId) {
        if (context.bankService() == null || roomId == null) {
            return "";
        }
        return context.bankService()
            .findBankInRoom(roomId)
            .map(bank -> "Banker: " + bank.name())
            .orElse("");
    }

    private String formatLookDescription(Player target) {
        String name = target.getUsername().getValue();
        String race = target.getRace().getValue();
        String classId = target.getClassId().getValue();
        return name + " the " + race + " " + classId + " (level " + target.getLevel() + ").";
    }

    /**
     * Returns the first item in the list whose name or id starts with (or equals)
     * the normalised input, or {@code null} when no match is found.
     */
    private static Item matchItemByName(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }

    // ── Command implementations ─────────────────────────────────────────

    @Override
    public void sendLook() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to look around.");
            return;
        }
        RoomService.LookResult result =
            roomService.look(session.getPlayer().getUsername(), session.getTextStyler());
        Room room = result.room();
        if (room != null && !lightingService.canSeeRoom(session.getPlayer(), room)) {
            connection.writeLines(lightingService.darknessLines());
            sendPrompt();
            return;
        }
        connection.writeLines(result.lines());
        writeRoomOccupantLines(result.room());
        sendPrompt();
    }

    @Override
    public void sendMap() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to view the map.");
            return;
        }
        Player player = session.getPlayer();
        String roomId = resolveRoomId(player);
        if (roomId == null) {
            writeLineWithPrompt("You are nowhere. The world feels unfinished.");
            return;
        }
        RoomId currentRoom = RoomId.of(roomId);
        markRoomExplored(currentRoom);
        player = session.getPlayer();
        String map = context.mapService().renderMap(player, currentRoom);
        context.messageBroadcaster().sendToPlayer(player.getUsername(), new PlainTextMessage(map));
        sendPrompt();
    }

    @Override
    public void sendLookAt(String targetInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to look around.");
            return;
        }
        if (targetInput == null || targetInput.isBlank()) {
            writeLineWithPrompt("Look at whom?");
            return;
        }
        Player source = session.getPlayer();
        Optional<Player> resolved = abilityTargetResolver.resolve(source, targetInput);
        if (resolved.isEmpty()) {
            writeLineWithPrompt("You don't see that here.");
            return;
        }
        Player target = resolved.get();
        String description = formatLookDescription(target);
        connection.writeLine(description);
        if (!target.getUsername().equals(source.getUsername())) {
            String sourceName = source.getUsername().getValue();
            sendToUsername(target.getUsername(), sourceName + " looks at you.");
            String roomMessage = sourceName + " looks at " + target.getUsername().getValue() + ".";
            deliverRoomMessage(source.getUsername(), target.getUsername(), roomMessage);
        }
        sendPrompt();
    }

    @Override
    public void sendMove(Direction direction) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to move.");
            return;
        }
        cancelRestIfActive();
        session.clearDialogue();
        Player player = session.getPlayer();
        if (encumbranceService.isOverburdened(player)) {
            writeLineWithPrompt("You are carrying too much to do that.");
            return;
        }
        RoomService.LookResult currentLook = roomService.look(player.getUsername(), session.getTextStyler());
        Room oldRoom = currentLook.room();
        String fromRoom = resolveRoomId(player);
        RoomService.MoveResult result =
            roomService.move(player.getUsername(), direction, session.getTextStyler());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("direction", direction.label());
        metadata.put("fromRoom", fromRoom);
        metadata.put("toRoom", result.room() == null ? null : result.room().getId().getValue());
        emitAudit(
            "player.move",
            AuditSubject.player(player.getUsername()),
            null,
            result.room() == null ? fromRoom : result.room().getId().getValue(),
            result.moved() ? "success" : "blocked",
            metadata
        );
        if (result.moved()) {
            if (context.duelService() != null) {
                // Leaving the room dissolves any pending or active duel involving this player.
                context.duelService().clearFor(player.getUsername());
            }
            if (oldRoom != null) {
                String leaveMessage = player.getUsername().getValue()
                    + " leaves " + direction.label() + ".";
                sendToRoom(oldRoom, player.getUsername(), leaveMessage);
            }
            String arriveMessage = player.getUsername().getValue() + " arrives.";
            deliverRoomMessage(player.getUsername(), null, arriveMessage);
        }
        Room destination = result.room();
        if (result.moved() && destination != null && !lightingService.canSeeRoom(player, destination)) {
            connection.writeLine("You move " + direction.label() + ".");
            connection.writeLines(lightingService.darknessLines());
            warnIfRoomTooDangerous(player, destination);
            markRoomExplored(destination.getId());
            recordExplorationVisit(destination);
            sendPrompt();
            return;
        }
        connection.writeLines(result.lines());
        if (result.moved()) {
            writeRoomOccupantLines(result.room());
            warnIfRoomTooDangerous(player, result.room());
            if (destination != null) {
                markRoomExplored(destination.getId());
            }
            recordExplorationVisit(destination);
        }
        sendPrompt();
    }

    /**
     * Records the given room in the current player's exploration set so it appears on their minimap
     * (MAP command), then persists the updated snapshot through the write-behind queue.
     *
     * <p>Runs on the tick thread as part of command execution (AGENTS.md &sect;5), so it safely
     * mutates and replaces the session player. A no-op when the room was already explored.
     *
     * @param roomId the room the player has entered
     */
    private void markRoomExplored(RoomId roomId) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        Player updated = player.exploreRoom(roomId);
        if (updated == player) {
            return;
        }
        session.replacePlayer(updated);
        saveOrWarn(updated);
    }

    /**
     * Records the player entering {@code destination} against any active exploration quest, emitting
     * progress or completion messages. Runs on the tick thread (as all command execution does), so it
     * safely mutates and persists the player snapshot through the session.
     */
    private void recordExplorationVisit(@Nullable Room destination) {
        if (destination == null) {
            return;
        }
        QuestRepository questRepo = context.questRepository();
        Player player = session.getPlayer();
        if (questRepo == null || player == null || player.getActiveQuest() == null) {
            return;
        }
        ExplorationQuestService explorationSvc = new ExplorationQuestService(questRepo);
        explorationSvc.recordRoomVisit(player, destination.getId()).ifPresent(result -> {
            session.replacePlayer(result.player());
            for (String message : result.messages()) {
                connection.writeLine(message);
            }
        });
    }

    /**
     * Sends an advisory warning if the destination room's recommended level exceeds the player's
     * current level. Movement itself is never blocked or delayed by this check.
     */
    private void warnIfRoomTooDangerous(Player player, @Nullable Room room) {
        if (room != null && room.exceedsLevel(player.getLevel())) {
            connection.writeLine("This area seems more dangerous than you are prepared for.");
        }
    }

    @Override
    public void useAbility(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use abilities.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        AbilityMatch match = abilityRegistry
            .findBestMatch(args, player.getLearnedAbilities()).orElse(null);
        if (handleAoeCastIfApplicable(match, args, player)) {
            return;
        }
        GameActionResult result = gameActionService.useAbility(player, args);
        auditAbilityUse(match, result, args);
        deliverResult(result);
        sendPrompt();
    }

    /**
     * Routes an area-of-effect spell (targeting {@link AbilityTargeting#AoE}) to the mob layer, which
     * strikes every hostile mob in the caster's room and scales the mana cost with the crowd. Ordinary
     * single-target and group abilities are left to {@link GameActionService#useAbility(Player, String)},
     * so this returns {@code false} for them and the caller proceeds normally.
     *
     * <p>Enforces the spell's cooldown through the same per-session tracker the ability engine uses,
     * starting it only when the cast actually resolved (a non-null updated source signals that mana
     * was spent, distinguishing it from a "no enemies here" / "not enough mana" rejection).
     *
     * @param match  the resolved ability match, or {@code null} when the input matched no ability
     * @param args   the raw command arguments, forwarded to the audit log
     * @param player the casting player
     * @return {@code true} when the input was an AoE spell and has been fully handled here
     */
    private boolean handleAoeCastIfApplicable(AbilityMatch match, String args, Player player) {
        if (match == null || match.ability().targeting() != AbilityTargeting.AoE) {
            return false;
        }
        Ability spell = match.ability();
        var cooldowns = session.getCooldownTracker();
        if (cooldowns.isOnCooldown(spell.id())) {
            writeLineWithPrompt("Ability is on cooldown ("
                + cooldowns.remainingTicks(spell.id()) + " ticks remaining).");
            return true;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There are no enemies here to strike.");
            return true;
        }
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return true;
        }
        GameActionResult result = context.mobRegistry()
            .processPlayerAoeSpell(player, spell, roomIdOpt.get());
        if (result.updatedSource() != null && spell.cooldown().ticks() > 0) {
            cooldowns.startCooldown(spell.id(), spell.cooldown().ticks());
        }
        auditAbilityUse(match, result, args);
        deliverResult(result);
        sendPrompt();
        return true;
    }

    @Override
    public void castSpell(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to cast spells.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        AbilityMatch match = abilityRegistry
            .findBestMatch(args, player.getLearnedAbilities()).orElse(null);
        if (match != null && match.ability().type() != io.taanielo.jmud.core.ability.AbilityType.SPELL) {
            writeLineWithPrompt("That is not a spell.");
            return;
        }
        if (handleAoeCastIfApplicable(match, args, player)) {
            return;
        }
        GameActionResult result = gameActionService.useAbility(player, args);
        auditAbilityUse(match, result, args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void updateAnsi(String args) {
        handleAnsiCommand(args);
    }

    @Override
    public void writeLineSafe(String message) {
        connection.writeLine(message);
    }

    @Override
    public Optional<Player> resolveTarget(Player source, String input) {
        return abilityTargetResolver.resolve(source, input);
    }

    @Override
    public void executeAttack(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to attack.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.attack(session.getPlayer(), args);
        auditAttack(result, args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void initiateDuel(String targetName) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to duel.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.initiatePlayerDuel(session.getPlayer(), targetName);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void acceptDuel() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to accept a duel.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.acceptPlayerDuel(session.getPlayer());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void getItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to get items.");
            return;
        }
        cancelRestIfActive();
        Player playerBeforeGet = session.getPlayer();
        GameActionResult result = gameActionService.getItem(playerBeforeGet, args);
        deliverResult(result);
        // After a successful pickup, check if this triggers delivery quest progress
        if (result.updatedSource() != null && context.questRepository() != null) {
            Player updatedPlayer = session.getPlayer();
            // Find the newly added item by comparing item-id counts before and after
            Map<ItemId, Long> oldCounts =
                playerBeforeGet.getInventory().stream()
                    .collect(Collectors.groupingBy(Item::getId, Collectors.counting()));
            QuestDeliveryService deliverySvc = new QuestDeliveryService(context.questRepository());
            for (Item item : updatedPlayer.getInventory()) {
                long before = oldCounts.getOrDefault(item.getId(), 0L);
                long after = updatedPlayer.getInventory().stream()
                    .filter(i -> i.getId().equals(item.getId())).count();
                if (after > before) {
                    deliverySvc.checkPickupProgress(updatedPlayer, item.getId())
                        .ifPresent(connection::writeLine);
                    break;
                }
            }
        }
        revealRoomIfNewlyLit(playerBeforeGet);
        sendPrompt();
    }

    /**
     * After a pickup, re-renders the current room when the picked-up item was a light source that
     * turned a previously-dark room visible, so the player immediately sees their surroundings.
     *
     * @param playerBeforePickup the player's state before the pickup mutated their inventory
     */
    private void revealRoomIfNewlyLit(Player playerBeforePickup) {
        Player playerAfterPickup = session.getPlayer();
        if (playerAfterPickup == null) {
            return;
        }
        RoomService.LookResult look =
            roomService.look(playerAfterPickup.getUsername(), session.getTextStyler());
        Room room = look.room();
        if (room == null || !room.requiresLight()) {
            return;
        }
        boolean couldSeeBefore = lightingService.canSeeRoom(playerBeforePickup, room);
        boolean canSeeNow = lightingService.canSeeRoom(playerAfterPickup, room);
        if (!couldSeeBefore && canSeeNow) {
            connection.writeLines(look.lines());
            writeRoomOccupantLines(room);
        }
    }

    @Override
    public void dropItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to drop items.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.dropItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void putIntoContainer(String itemInput, String containerInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to put items in containers.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.putItem(session.getPlayer(), itemInput, containerInput);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void getFromContainer(String itemInput, String containerInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to get items from containers.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.getFromContainer(session.getPlayer(), itemInput, containerInput);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void giveItem(Username targetUsername, String itemInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to give items.");
            return;
        }
        cancelRestIfActive();
        Player source = session.getPlayer();
        Player target = findOnlinePlayer(targetUsername);
        if (target == null) {
            writeLineWithPrompt(targetUsername.getValue() + " is not here.");
            return;
        }

        GameActionResult result = gameActionService.giveItem(source, target, itemInput);
        deliverResult(result);
        sendPrompt();
    }

    /**
     * Looks up the live in-session {@link Player} for a connected username, searching the
     * client pool rather than the repository so the caller sees the most current state.
     *
     * @param username the username to look up
     * @return the connected player, or {@code null} if no client is authenticated as that user
     */
    private @Nullable Player findOnlinePlayer(Username username) {
        for (Client c : clientPool.clients()) {
            if (c instanceof SocketClient sc && sc.isAuthenticatedUser(username)) {
                return sc.session().getPlayer();
            }
        }
        return null;
    }

    @Override
    public void quaffItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to quaff.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.quaffItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void eatItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to eat.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.eatItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void drinkItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to drink.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.drinkItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void readItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to read.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        GameActionResult result = gameActionService.readItem(player, args);
        deliverResult(result);
        if (result.metadata().containsKey("recalled")) {
            RoomService.LookResult look = roomService.look(player.getUsername(), session.getTextStyler());
            connection.writeLines(look.lines());
            writeRoomOccupantLines(look.room());
        }
        sendPrompt();
    }

    @Override
    public void identifyItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to identify items.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.identifyItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void pickLock(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to pick locks.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.pickLock(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void sneak(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to sneak.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.sneakToggle(session.getPlayer());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void steal(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to steal.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.steal(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void track(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to track.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.track(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    /** Ability id of the necromancer-style SUMMON spell (data/skills/spell.summon.json). */
    private static final AbilityId SUMMON_ABILITY_ID = AbilityId.of("spell.summon");

    @Override
    public void summon(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to summon.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        if (!player.getLearnedAbilities().contains(SUMMON_ABILITY_ID)) {
            writeLineWithPrompt("You have not learned how to summon.");
            return;
        }
        Ability spell = abilityRegistry.findById(SUMMON_ABILITY_ID).orElse(null);
        if (spell == null) {
            writeLineWithPrompt("You cannot summon right now.");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("Your summons echoes into nothing.");
            return;
        }
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.equalsIgnoreCase("dismiss")) {
            GameActionResult dismissResult = context.mobRegistry().dismissPet(player, roomIdOpt.get());
            deliverResult(dismissResult);
            sendPrompt();
            return;
        }
        var cooldowns = session.getCooldownTracker();
        if (cooldowns.isOnCooldown(spell.id())) {
            writeLineWithPrompt("Summon is on cooldown ("
                + cooldowns.remainingTicks(spell.id()) + " ticks remaining).");
            return;
        }
        GameActionResult result = context.mobRegistry().processSummon(player, spell, roomIdOpt.get());
        if (result.updatedSource() != null && spell.cooldown().ticks() > 0) {
            cooldowns.startCooldown(spell.id(), spell.cooldown().ticks());
        }
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void tame(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to tame.");
            return;
        }
        cancelRestIfActive();
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There is nothing here to tame.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        GameActionResult result = context.mobRegistry().processTame(player, args, roomIdOpt.get());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void talk(String npcName) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to talk.");
            return;
        }
        cancelRestIfActive();
        DialogueService dialogueService = context.dialogueService();
        if (dialogueService == null || context.mobRegistry() == null) {
            writeLineWithPrompt("There is no one here to talk to.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        RoomId roomId = roomIdOpt.get();
        String normalized = npcName.trim().toLowerCase(Locale.ROOT);
        var speaker = context.mobRegistry().getMobsInRoom(roomId).stream()
            .filter(mob -> mob.isAlive() && mob.template().dialogueId() != null)
            .filter(mob -> {
                String name = mob.template().name().toLowerCase(Locale.ROOT);
                return name.equals(normalized) || name.startsWith(normalized);
            })
            .findFirst()
            .orElse(null);
        if (speaker == null) {
            writeLineWithPrompt("There is no one here by that name to talk to.");
            return;
        }
        DialogueId dialogueId = speaker.template().dialogueId();
        DialogueTree tree = dialogueService.findTree(dialogueId).orElse(null);
        if (tree == null) {
            writeLineWithPrompt(speaker.template().name() + " has nothing to say.");
            return;
        }
        DialogueNode startNode = tree.startNode();
        session.startDialogue(tree, startNode.id(), roomId, speaker.template().name());
        if (startNode.isTerminal()) {
            session.clearDialogue();
        }
        writeLineWithPrompt(dialogueService.renderNode(speaker.template().name(), startNode));
    }

    @Override
    public void respond(String numberInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to respond.");
            return;
        }
        cancelRestIfActive();
        DialogueService dialogueService = context.dialogueService();
        DialogueTree tree = session.getDialogueTree();
        String currentNodeId = session.getDialogueNodeId();
        String speaker = session.getDialogueSpeaker();
        RoomId dialogueRoomId = session.getDialogueRoomId();
        if (dialogueService == null || tree == null || currentNodeId == null
                || speaker == null || dialogueRoomId == null) {
            writeLineWithPrompt("You are not talking to anyone. Use TALK <name> first.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty() || !roomIdOpt.get().equals(dialogueRoomId)) {
            session.clearDialogue();
            writeLineWithPrompt("Your conversation has ended.");
            return;
        }
        int number;
        try {
            number = Integer.parseInt(numberInput.trim());
        } catch (NumberFormatException e) {
            writeLineWithPrompt("Respond with a number. Usage: RESPOND <number>");
            return;
        }
        DialogueResponse chosen = dialogueService.selectResponse(tree, currentNodeId, number).orElse(null);
        DialogueNode next = dialogueService.respond(tree, currentNodeId, number).orElse(null);
        if (next == null || chosen == null) {
            writeLineWithPrompt("That is not one of your options.");
            return;
        }
        if (next.isTerminal()) {
            session.clearDialogue();
        } else {
            session.advanceDialogueNode(next.id());
        }
        if (chosen.grantQuestId() != null) {
            grantDialogueQuest(chosen.grantQuestId());
        }
        writeLineWithPrompt(dialogueService.renderNode(speaker, next));
    }

    /**
     * Grants an NPC-delivery quest offered by the chosen dialogue response, handing the player the
     * package item. Runs on the tick thread as part of {@code RESPOND}; failures (unknown quest,
     * already-held contract, missing package item) are reported to the player without ending the
     * conversation.
     */
    private void grantDialogueQuest(String questIdValue) {
        QuestRepository questRepo = context.questRepository();
        ItemRepository itemRepo = context.itemRepository();
        if (questRepo == null || itemRepo == null) {
            return;
        }
        QuestTemplate template;
        try {
            template = questRepo.findById(QuestId.of(questIdValue)).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load granted quest {}: {}", questIdValue, e.getMessage());
            return;
        }
        if (template == null || !template.isNpcDeliveryQuest()) {
            log.warn("Dialogue grant references quest '{}' which is not a valid NPC-delivery quest", questIdValue);
            return;
        }
        Player player = session.getPlayer();
        if (player.getActiveQuest() != null) {
            connection.writeLine("You already hold an active contract, so you decline the errand for now.");
            return;
        }
        Item packageItem;
        try {
            packageItem = itemRepo.findById(ItemId.of(template.packageItemId())).orElse(null);
        } catch (RepositoryException e) {
            log.warn("Failed to load package item {}: {}", template.packageItemId(), e.getMessage());
            return;
        }
        if (packageItem == null) {
            log.warn("NPC-delivery quest '{}' references unknown package item '{}'",
                questIdValue, template.packageItemId());
            return;
        }
        QuestNpcDeliveryService npcDeliverySvc = new QuestNpcDeliveryService(questRepo);
        DeliveryQuestResult result = npcDeliverySvc.grant(player, template, packageItem);
        if (result.success()) {
            session.replacePlayer(result.player());
        }
        for (String msg : result.messages()) {
            connection.writeLine(msg);
        }
    }

    @Override
    public void companions() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to see your companions.");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("You have no companions.");
            return;
        }
        GameActionResult result = context.mobRegistry().listCompanions(session.getPlayer());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void writeItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to write.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.writeItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void equipItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to equip items.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.equipItem(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void unequipItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to unequip items.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.unequipSlot(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void killMob(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to attack.");
            return;
        }
        cancelRestIfActive();
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There are no mobs here.");
            return;
        }
        breakStealthIfActive();
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        GameActionResult result = context.mobRegistry()
            .processPlayerAttack(player, args, roomIdOpt.get());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void executeRangedAttack(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to attack.");
            return;
        }
        cancelRestIfActive();
        String normalized = args == null ? "" : args.trim();
        int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace < 0) {
            writeLineWithPrompt("Usage: SHOOT <target> <direction>");
            return;
        }
        String targetName = normalized.substring(0, lastSpace).trim();
        String directionInput = normalized.substring(lastSpace + 1).trim();
        Optional<Direction> direction = Direction.fromInput(directionInput);
        if (targetName.isEmpty() || direction.isEmpty()) {
            writeLineWithPrompt("Usage: SHOOT <target> <direction>");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There are no mobs here.");
            return;
        }
        breakStealthIfActive();
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        GameActionResult result = context.mobRegistry()
            .processPlayerRangedAttack(player, targetName, direction.get(), roomIdOpt.get());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void fleeCombat() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to flee.");
            return;
        }
        Player player = session.getPlayer();
        RoomService.LookResult lookResult = roomService.look(player.getUsername(), session.getTextStyler());
        FleeResult result = gameActionService.flee(player, lookResult.room());
        if (!result.fled()) {
            writeLineWithPrompt(result.message());
            return;
        }
        connection.writeLine(result.message());
        sendMove(result.direction());
    }

    @Override
    public void recall() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to recall.");
            return;
        }
        Player player = session.getPlayer();
        GameActionResult result = gameActionService.recall(player);
        deliverResult(result);
        if (result.metadata().containsKey("recalled")) {
            RoomService.LookResult look = roomService.look(player.getUsername(), session.getTextStyler());
            connection.writeLines(look.lines());
            writeRoomOccupantLines(look.room());
        }
        sendPrompt();
    }

    @Override
    public void sendInventory() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to check your inventory.");
            return;
        }
        int carried = encumbranceService.carriedWeight(player);
        int maxCarry = encumbranceService.maxCarry(player);
        for (String line : InventoryListing.format(player.getInventory(), carried, maxCarry, session.getTextStyler())) {
            connection.writeLine(line);
        }
        sendPrompt();
    }

    @Override
    public void sendEquipment() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to check your equipment.");
            return;
        }
        Map<ItemId, Item> inventoryIndex = player.getInventory().stream()
            .collect(Collectors.toMap(Item::getId, i -> i, (a, b) -> a));
        for (String line : EquipmentListing.format(
                player.getEquipment().slots(), inventoryIndex::get, session.getTextStyler())) {
            connection.writeLine(line);
        }
        sendPrompt();
    }

    @Override
    public void gossip(String senderName, String message) {
        String line = senderName + " gossips: " + message;
        context.gossipHistory().record(line);
        // Skip the sender — they already see "You gossip: ..." from GossipCommand.
        context.messageBroadcaster().broadcastGlobal(
            new PlainTextMessage(line),
            Set.of(Username.of(senderName))
        );
    }

    @Override
    public void sendAbilities() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to view your abilities.");
            return;
        }
        List<AbilityId> learned = player.getLearnedAbilities();
        if (learned.isEmpty()) {
            writeLineWithPrompt("You have not learned any abilities yet.");
            return;
        }
        connection.writeLine(String.format("%-20s %-6s %-12s %s", "Ability", "Type", "Cost", "Cooldown"));
        connection.writeLine("-".repeat(52));
        for (AbilityId abilityId : learned) {
            Ability ability = abilityRegistry.findById(abilityId).orElse(null);
            if (ability == null) {
                continue;
            }
            AbilityCost cost = ability.cost();
            String costStr;
            if (cost.mana() > 0 && cost.move() > 0) {
                costStr = cost.mana() + " mana, " + cost.move() + " mv";
            } else if (cost.mana() > 0) {
                costStr = cost.mana() + " mana";
            } else if (cost.move() > 0) {
                costStr = cost.move() + " mv";
            } else {
                costStr = "none";
            }
            String cooldownStr = ability.cooldown().ticks() > 0
                ? ability.cooldown().ticks() + " ticks"
                : "none";
            connection.writeLine(String.format("%-20s %-6s %-12s %s",
                ability.name(), ability.type().name(), costStr, cooldownStr));
        }
        sendPrompt();
    }

    @Override
    public void examineItem(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to examine items.");
            return;
        }
        if (args == null || args.isBlank()) {
            writeLineWithPrompt("Examine what?");
            return;
        }
        // Search inventory first, then room items.
        Item found = matchItemByName(player.getInventory(), args);
        if (found == null) {
            RoomService.LookResult look = roomService.look(player.getUsername(), session.getTextStyler());
            if (look.room() != null) {
                found = matchItemByName(look.room().getItems(), args);
            }
        }
        if (found == null) {
            writeLineWithPrompt("You don't see '" + args.trim() + "' here.");
            return;
        }
        if (!found.isIdentified()) {
            connection.writeLine(session.getTextStyler().rarity(
                found.presentationName(), found.presentationRarity()));
            connection.writeLine("You cannot make out its true nature. Identify it to reveal its properties.");
            sendPrompt();
            return;
        }
        connection.writeLine(session.getTextStyler().rarity(found.getName(), found.getRarity()));
        connection.writeLine(found.getDescription());
        if (found.isContainer() && found.isLocked()) {
            connection.writeLine("It is locked.");
        }
        if (found.getEquipSlot() != null) {
            connection.writeLine("Slot: " + found.getEquipSlot().id());
        }
        if (found.getAttributes() != null && !found.getAttributes().getStats().isEmpty()) {
            StringBuilder sb = new StringBuilder("Stats:");
            found.getAttributes().getStats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(" ").append(e.getKey()).append(" +").append(e.getValue()));
            connection.writeLine(sb.toString());
        }
        sendPrompt();
    }

    @Override
    public void considerMob(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to consider a target.");
            return;
        }
        if (args == null || args.isBlank()) {
            writeLineWithPrompt("Consider whom?");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There is no " + args.trim() + " here to consider.");
            return;
        }
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        List<io.taanielo.jmud.core.mob.MobInstance> mobs =
            context.mobRegistry().getMobsInRoom(roomIdOpt.get());
        String normalized = args.trim().toLowerCase(Locale.ROOT);
        io.taanielo.jmud.core.mob.MobInstance found = null;
        for (io.taanielo.jmud.core.mob.MobInstance mob : mobs) {
            String name = mob.template().name().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                found = mob;
                break;
            }
        }
        if (found == null) {
            writeLineWithPrompt("There is no " + args.trim() + " here to consider.");
            return;
        }
        int mobMaxHp = found.template().maxHp();
        int playerMaxHp = player.getVitals().maxHp();
        double ratio = playerMaxHp > 0 ? (double) mobMaxHp / playerMaxHp : Double.MAX_VALUE;
        String tier;
        if (ratio <= 0.25) {
            tier = "poses no real threat";
        } else if (ratio <= 0.60) {
            tier = "looks like an easy opponent";
        } else if (ratio <= 1.00) {
            tier = "seems like a fair fight";
        } else if (ratio <= 1.50) {
            tier = "would be a serious challenge";
        } else {
            tier = "could kill you without effort";
        }
        writeLineWithPrompt("The " + found.template().name() + " " + tier + ".");
    }

    @Override
    public void startResting() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to rest.");
            return;
        }
        Player player = session.getPlayer();
        if (player.isDead()) {
            writeLineWithPrompt("You cannot rest while dead.");
            return;
        }
        if (player.isResting()) {
            writeLineWithPrompt("You are already resting.");
            return;
        }
        if (context.mobRegistry() != null
                && context.mobRegistry().isInCombat(player.getUsername())) {
            writeLineWithPrompt("You cannot rest while in combat.");
            return;
        }
        Player resting = player.withResting(true);
        session.setPlayer(resting);
        connection.writeLine("You sit down and begin to rest.");
        sendPrompt();
        RestingTicker ticker = new RestingTicker(
            session::getPlayer,
            this::applyRestUpdate,
            (msg, woken) -> {
                session.setPlayer(woken);
                session.clearRestingTicker();
                connection.writeLine(msg);
                sendPrompt();
            },
            RestSettings.regenHp(),
            RestSettings.regenMana(),
            RestSettings.regenMove()
        );
        session.registerRestingTicker(ticker);
    }

    @Override
    public void stopResting(String message) {
        Player player = session.getPlayer();
        if (player == null || !player.isResting()) {
            if (message != null && !message.isBlank()) {
                writeLineWithPrompt(message);
            } else {
                sendPrompt();
            }
            return;
        }
        Player woken = player.withResting(false);
        session.setPlayer(woken);
        session.clearRestingTicker();
        if (message != null && !message.isBlank()) {
            writeLineWithPrompt(message);
        } else {
            sendPrompt();
        }
    }

    @Override
    public void listShopInventory() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to browse a shop.");
            return;
        }
        if (context.shopService() == null) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
        if (shopOpt.isEmpty()) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        for (String line : context.shopService().formatListing(shopOpt.get(), player)) {
            connection.writeLine(line);
        }
        sendPrompt();
    }

    @Override
    public void buyFromShop(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to buy items.");
            return;
        }
        if (context.shopService() == null) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
        if (shopOpt.isEmpty()) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        ShopTransactionResult result = context.shopService().buy(player, shopOpt.get(), args);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void sellToShop(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to sell items.");
            return;
        }
        if (context.shopService() == null) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
        if (shopOpt.isEmpty()) {
            writeLineWithPrompt("There is no shop here.");
            return;
        }
        ShopTransactionResult result = context.shopService().sell(player, shopOpt.get(), args);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void repairItem(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to repair items.");
            return;
        }
        if (context.itemDurabilityService() == null) {
            writeLineWithPrompt("There is no blacksmith here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!isBlacksmithPresent(roomIdOpt.get())) {
            writeLineWithPrompt("There is no blacksmith here to repair your gear.");
            return;
        }
        ItemDurabilityService.RepairOutcome outcome =
            context.itemDurabilityService().repair(player, args);
        if (outcome.success()) {
            session.replacePlayer(outcome.updatedPlayer());
        }
        writeLineWithPrompt(outcome.message());
    }

    /** Returns whether a blacksmith NPC (tagged {@code blacksmith}) is alive in the given room. */
    private boolean isBlacksmithPresent(RoomId roomId) {
        if (context.mobRegistry() == null) {
            return false;
        }
        return context.mobRegistry().getMobsInRoom(roomId).stream()
            .anyMatch(mob -> mob.template().hasTag("blacksmith"));
    }

    @Override
    public void showAchievements() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to view achievements.");
            return;
        }
        AchievementService achievementService = context.achievementService();
        if (achievementService == null || achievementService.definitions().isEmpty()) {
            writeLineWithPrompt("There are no achievements to earn yet.");
            return;
        }
        Player player = session.getPlayer();
        List<AchievementStatus> statuses = achievementService.statuses(player);
        long unlockedCount = statuses.stream().filter(AchievementStatus::unlocked).count();
        connection.writeLine("Achievements (" + unlockedCount + "/" + statuses.size() + " unlocked):");
        for (AchievementStatus status : statuses) {
            connection.writeLine(formatAchievementLine(status));
        }
        sendPrompt();
    }

    private String formatAchievementLine(AchievementStatus status) {
        Achievement achievement = status.achievement();
        String marker = status.unlocked() ? "[X]" : "[ ]";
        String detail;
        var unlockedAt = status.unlockedAt();
        if (status.unlocked() && unlockedAt != null) {
            detail = "unlocked " + ACHIEVEMENT_TIME_FORMAT.format(unlockedAt);
        } else {
            detail = status.progress() + "/" + achievement.threshold() + " "
                + achievement.condition().progressUnit();
        }
        return String.format("  %s %-22s %s (%s)",
            marker, achievement.name(), achievement.description(), detail);
    }

    @Override
    public void executeQuest(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to manage quests.");
            return;
        }
        QuestRepository questRepo = context.questRepository();
        if (questRepo == null) {
            writeLineWithPrompt("Quests are not available.");
            return;
        }

        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0]; // already uppercased by splitInput
        String subArgs = parts[1];

        switch (sub) {
            case "LIST" -> handleQuestList(questRepo);
            case "ACCEPT" -> handleQuestAccept(questRepo, subArgs);
            case "STATUS" -> handleQuestStatus(questRepo);
            case "COMPLETE" -> handleQuestComplete(questRepo);
            case "DELIVER" -> handleQuestDeliver(questRepo);
            case "ABANDON" -> handleQuestAbandon();
            default -> writeLineWithPrompt(
                "Usage: QUEST [LIST|ACCEPT <id>|STATUS|COMPLETE|DELIVER|ABANDON]");
        }
    }

    @Override
    public void executeDailyQuest(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to manage daily quests.");
            return;
        }
        DailyQuestService dailyQuestService = context.dailyQuestService();
        if (dailyQuestService == null || dailyQuestService.poolIds().isEmpty()) {
            writeLineWithPrompt("Daily quests are not available.");
            return;
        }

        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0]; // already uppercased by splitInput
        String subArgs = parts[1];

        switch (sub) {
            case "", "LIST" -> handleDailyQuestList(dailyQuestService);
            case "ACCEPT" -> handleDailyQuestAccept(dailyQuestService, subArgs);
            case "STATUS" -> handleDailyQuestStatus(dailyQuestService);
            case "COMPLETE" -> handleDailyQuestComplete(dailyQuestService);
            default -> writeLineWithPrompt(
                "Usage: DAILY_QUEST [LIST|ACCEPT <pool>|STATUS|COMPLETE]");
        }
    }

    private void handleDailyQuestList(DailyQuestService dailyQuestService) {
        List<QuestTemplate> active = dailyQuestService.activeDailyQuests();
        if (active.isEmpty()) {
            writeLineWithPrompt("There are no daily quests today.");
            return;
        }
        connection.writeLine("Today's Daily Quests:");
        connection.writeLine(String.format("  %-14s %-24s %s", "POOL", "QUEST", "REWARD"));
        connection.writeLine("  " + "-".repeat(60));
        for (QuestTemplate t : active) {
            connection.writeLine(String.format(
                "  %-14s %-24s %dg / %dxp",
                t.dailyPoolId(), t.name(), t.goldReward(), t.xpReward()));
        }
        connection.writeLine("Accept one with DAILY_QUEST ACCEPT <pool>.");
        sendPrompt();
    }

    private void handleDailyQuestAccept(DailyQuestService dailyQuestService, String poolInput) {
        if (poolInput == null || poolInput.isBlank()) {
            writeLineWithPrompt("Accept which daily quest? Use DAILY_QUEST ACCEPT <pool>.");
            return;
        }
        Player player = session.getPlayer();
        if (player.getActiveQuest() != null) {
            writeLineWithPrompt("You already hold an active contract. QUEST ABANDON it first.");
            return;
        }
        String poolId = poolInput.trim().toLowerCase(Locale.ROOT);
        QuestTemplate template = dailyQuestService.getActiveDailyQuest(poolId).orElse(null);
        if (template == null) {
            writeLineWithPrompt("Unknown daily pool '" + poolInput.trim()
                + "'. Use DAILY_QUEST to see today's quests.");
            return;
        }
        ActiveQuest activeQuest = new ActiveQuest(template.id(), template.requiredKills());
        session.replacePlayer(player.withActiveQuest(activeQuest));
        writeLineWithPrompt(
            "Daily quest accepted: " + template.name() + ". "
                + "Kill " + template.requiredKills() + " " + template.targetMobId()
                + "(s), then use DAILY_QUEST COMPLETE to claim your reward. Good luck.");
    }

    private void handleDailyQuestStatus(DailyQuestService dailyQuestService) {
        Player player = session.getPlayer();
        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            writeLineWithPrompt("You have no active daily quest.");
            return;
        }
        QuestTemplate template = dailyQuestService.findQuestById(active.templateId()).orElse(null);
        if (template == null || !template.isDaily()) {
            writeLineWithPrompt("Your active contract is not a daily quest. Use QUEST STATUS instead.");
            return;
        }
        if (active.isComplete()) {
            writeLineWithPrompt(template.name() + ": complete — use DAILY_QUEST COMPLETE to claim your reward.");
        } else {
            int done = template.requiredKills() - active.killsRemaining();
            writeLineWithPrompt(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
        }
    }

    private void handleDailyQuestComplete(DailyQuestService dailyQuestService) {
        Player player = session.getPlayer();
        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            writeLineWithPrompt("You have no active daily quest to complete.");
            return;
        }
        QuestTemplate template = dailyQuestService.findQuestById(active.templateId()).orElse(null);
        if (template == null || !template.isDaily()) {
            writeLineWithPrompt("Your active contract is not a daily quest. Use QUEST COMPLETE instead.");
            return;
        }
        DailyQuestCompletionResult result = dailyQuestService.completeDailyQuest(player, active.templateId());
        if (result.success()) {
            Player rewarded = result.player();
            session.replacePlayer(rewarded);
            List<String> messages = result.messages();
            connection.writeLine(messages.get(0));
            connection.writeLine(messages.get(1));
            if (result.leveledUp()) {
                connection.writeLine("You have advanced to level " + rewarded.getLevel() + "!");
            }
            for (int i = 2; i < messages.size(); i++) {
                connection.writeLine(messages.get(i));
            }
            sendPrompt();
        } else {
            writeLineWithPrompt(result.messages().isEmpty()
                ? "You cannot complete that daily quest yet."
                : result.messages().get(0));
        }
    }

    @Override
    public void executeTrain(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to train.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty() || !"training-yard".equals(roomIdOpt.get().getValue())) {
            writeLineWithPrompt("The Master Trainer is not here. Find them in the Training Yard.");
            return;
        }
        // Verify trainer NPC is present in the room
        if (context.mobRegistry() != null) {
            var mobs = context.mobRegistry().getMobsInRoom(roomIdOpt.get());
            boolean trainerPresent = mobs.stream()
                .anyMatch(m -> "trainer".equals(m.template().id().getValue()));
            if (!trainerPresent) {
                writeLineWithPrompt("The Master Trainer is not here.");
                return;
            }
        }
        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0];
        String subArgs = parts[1];
        switch (sub) {
            case "LIST" -> handleTrainList(player);
            case "" -> writeLineWithPrompt("Usage: TRAIN LIST  or  TRAIN <ability-id>");
            default -> handleTrainAbility(player, sub + (subArgs.isBlank() ? "" : " " + subArgs));
        }
    }

    @Override
    public void executeParty(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use party commands.");
            return;
        }
        PartyService partyService = context.partyService();
        if (partyService == null) {
            writeLineWithPrompt("The party system is not available.");
            return;
        }
        Player player = session.getPlayer();
        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0];
        String subArgs = parts[1];

        switch (sub) {
            case "FORM" -> handlePartyForm(player, partyService);
            case "INVITE" -> handlePartyInvite(player, partyService, subArgs);
            case "ACCEPT" -> handlePartyAccept(player, partyService);
            case "DECLINE" -> handlePartyDecline(player, partyService);
            case "LEAVE" -> handlePartyLeave(player, partyService);
            case "DISBAND" -> handlePartyDisband(player, partyService);
            case "" -> handlePartyStatus(player, partyService);
            default -> writeLineWithPrompt(
                "Usage: PARTY [FORM|INVITE <player>|ACCEPT|DECLINE|LEAVE|DISBAND]");
        }
    }

    @Override
    public void lockExit(Direction direction) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to lock doors.");
            return;
        }
        Player player = session.getPlayer();
        DoorActionResult result = roomService.lock(
            player.getUsername(), direction, player.getInventory());
        if (result.roomMessage() != null) {
            deliverRoomMessage(player.getUsername(), null, result.roomMessage());
        }
        writeLineWithPrompt(result.playerMessage());
    }

    @Override
    public void unlockExit(Direction direction) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to unlock doors.");
            return;
        }
        Player player = session.getPlayer();
        DoorActionResult result = roomService.unlock(
            player.getUsername(), direction, player.getInventory());
        if (result.roomMessage() != null) {
            deliverRoomMessage(player.getUsername(), null, result.roomMessage());
        }
        writeLineWithPrompt(result.playerMessage());
    }

    @Override
    public void depositToBank(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use the bank.");
            return;
        }
        if (context.bankService() == null) {
            writeLineWithPrompt("There is no bank here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (context.bankService().findBankInRoom(roomIdOpt.get()).isEmpty()) {
            writeLineWithPrompt("There is no bank here.");
            return;
        }
        if (args == null || args.isBlank()) {
            writeLineWithPrompt("Deposit how much gold? Usage: DEPOSIT <amount>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args.trim());
        } catch (NumberFormatException e) {
            writeLineWithPrompt("'" + args.trim() + "' is not a valid amount.");
            return;
        }
        BankTransactionResult result = context.bankService().deposit(player, amount);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void withdrawFromBank(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use the bank.");
            return;
        }
        if (context.bankService() == null) {
            writeLineWithPrompt("There is no bank here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (context.bankService().findBankInRoom(roomIdOpt.get()).isEmpty()) {
            writeLineWithPrompt("There is no bank here.");
            return;
        }
        if (args == null || args.isBlank()) {
            writeLineWithPrompt("Withdraw how much gold? Usage: WITHDRAW <amount>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args.trim());
        } catch (NumberFormatException e) {
            writeLineWithPrompt("'" + args.trim() + "' is not a valid amount.");
            return;
        }
        BankTransactionResult result = context.bankService().withdraw(player, amount);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void manageAlias(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to manage aliases.");
            return;
        }
        String normalizedArgs = args == null ? "" : args.trim();
        AliasResult result;
        if (normalizedArgs.isEmpty()) {
            result = playerAliasService.list(player);
        } else if (normalizedArgs.equalsIgnoreCase("-d") || normalizedArgs.toLowerCase(Locale.ROOT).startsWith("-d ")) {
            String name = normalizedArgs.length() > 2 ? normalizedArgs.substring(2).trim() : "";
            result = playerAliasService.remove(player, name);
        } else {
            String[] parts = normalizedArgs.split("\\s+", 2);
            String name = parts[0];
            if (parts.length < 2 || parts[1].isBlank()) {
                writeLineWithPrompt("Usage: ALIAS  |  ALIAS <name> <expansion>  |  ALIAS -d <name>");
                return;
            }
            Set<String> builtinCommandNames = context.commandRegistry().commands().stream()
                .map(SocketCommandHandler::name)
                .collect(Collectors.toSet());
            result = playerAliasService.define(player, name, parts[1].trim(), builtinCommandNames);
        }
        applyAliasResult(result);
    }

    private void applyAliasResult(AliasResult result) {
        if (!result.lines().isEmpty()) {
            connection.writeLine("Your aliases:");
            for (String line : result.lines()) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        if (result.updatedPlayer() != null) {
            session.replacePlayer(result.updatedPlayer());
            saveOrWarn(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void manageMail(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to use mail.");
            return;
        }
        String normalizedArgs = args == null ? "" : args.trim();
        long currentTick = context.tickClock().currentTick();

        if (normalizedArgs.isEmpty()) {
            applyOwnMailResult(playerMailService.list(player, currentTick), "Your mail:");
            return;
        }

        String[] parts = normalizedArgs.split("\\s+", 2);
        String firstToken = parts[0].toUpperCase(Locale.ROOT);
        if ("READ".equals(firstToken) || "DELETE".equals(firstToken)) {
            if (parts.length < 2 || parts[1].isBlank()) {
                writeLineWithPrompt("Usage: MAIL " + firstToken + " <n>");
                return;
            }
            Integer index = parseMailIndex(parts[1].trim());
            if (index == null) {
                writeLineWithPrompt("'" + parts[1].trim() + "' is not a valid mail number.");
                return;
            }
            MailResult result = "READ".equals(firstToken)
                ? playerMailService.read(player, index)
                : playerMailService.delete(player, index);
            applyOwnMailResult(result, null);
            return;
        }

        handleSendMail(player, parts, currentTick);
    }

    private void handleSendMail(Player sender, String[] parts, long currentTick) {
        if (parts.length < 2 || parts[1].isBlank()) {
            writeLineWithPrompt("Usage: MAIL <playername> <message>");
            return;
        }
        String targetName = parts[0];
        String message = parts[1].trim();
        Username targetUsername = Username.of(targetName);
        if (targetUsername.equals(sender.getUsername())) {
            writeLineWithPrompt("You cannot mail yourself.");
            return;
        }
        Player recipient = resolvePlayerByUsername(targetUsername);
        if (recipient == null) {
            writeLineWithPrompt("No such player: " + targetName);
            return;
        }
        MailResult result = playerMailService.send(recipient, sender.getUsername().getValue(), currentTick, message);
        if (result.success() && result.updatedPlayer() != null) {
            updateTarget(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    /** Parses a one-based mail index, returning {@code null} if the text is not a positive integer. */
    private Integer parseMailIndex(String text) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves a player by username, preferring the live in-session state of a currently
     * connected client and falling back to the player repository for offline players. Returns
     * {@code null} when no such player exists, online or persisted.
     *
     * @param username the username to resolve
     */
    private @Nullable Player resolvePlayerByUsername(Username username) {
        for (Client c : clientPool.clients()) {
            if (c instanceof SocketClient sc && sc.isAuthenticatedUser(username)) {
                Player p = sc.session().getPlayer();
                if (p != null) {
                    return p;
                }
            }
        }
        return context.playerRepository().loadPlayer(username).orElse(null);
    }

    /** Applies a {@link MailResult} that affects only the invoking player's own mailbox. */
    private void applyOwnMailResult(MailResult result, @Nullable String header) {
        if (!result.lines().isEmpty()) {
            if (header != null) {
                connection.writeLine(header);
            }
            for (String line : result.lines()) {
                connection.writeLine(line);
            }
            if (result.updatedPlayer() != null) {
                session.replacePlayer(result.updatedPlayer());
                saveOrWarn(result.updatedPlayer());
            }
            sendPrompt();
            return;
        }
        if (result.updatedPlayer() != null) {
            session.replacePlayer(result.updatedPlayer());
            saveOrWarn(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void showBoard() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to read the board.");
            return;
        }
        if (notesService == null) {
            writeLineWithPrompt("The bulletin board is not available.");
            return;
        }
        RoomId roomId = currentRoomId(player);
        if (roomId == null) {
            writeLineWithPrompt("You are nowhere near a board.");
            return;
        }
        List<PlayerNote> notes = notesService.notesInRoom(roomId);
        if (notes.isEmpty()) {
            writeLineWithPrompt("The bulletin board here is empty. Post the first note with NOTE POST <message>.");
            return;
        }
        connection.writeLine("Bulletin board (" + notes.size() + " note" + (notes.size() == 1 ? "" : "s") + "):");
        int index = 1;
        for (PlayerNote note : notes) {
            connection.writeLine(String.format(
                "  [%d] %s (%s)",
                index++, note.author().getValue(), NOTE_TIME_FORMAT.format(note.timestamp())));
            connection.writeLine("      " + note.content());
        }
        connection.writeLine("Delete one of your notes with NOTE DELETE <number>.");
        sendPrompt();
    }

    @Override
    public void manageNote(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to use notes.");
            return;
        }
        if (notesService == null) {
            writeLineWithPrompt("The bulletin board is not available.");
            return;
        }
        RoomId roomId = currentRoomId(player);
        if (roomId == null) {
            writeLineWithPrompt("You are nowhere near a board.");
            return;
        }
        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0]; // already uppercased by splitInput
        String subArgs = parts[1];
        switch (sub) {
            case "POST" -> handleNotePost(player, roomId, subArgs);
            case "DELETE" -> handleNoteDelete(player, roomId, subArgs);
            default -> writeLineWithPrompt("Usage: NOTE POST <message>  |  NOTE DELETE <number>");
        }
    }

    private void handleNotePost(Player player, RoomId roomId, String message) {
        if (notesService == null) {
            return;
        }
        if (message == null || message.isBlank()) {
            writeLineWithPrompt("Post what? Usage: NOTE POST <message>");
            return;
        }
        try {
            notesService.postNote(roomId, player.getUsername(), message);
            writeLineWithPrompt("Your note has been posted to the board.");
        } catch (IllegalArgumentException e) {
            writeLineWithPrompt(e.getMessage());
        }
    }

    private void handleNoteDelete(Player player, RoomId roomId, String numberInput) {
        if (notesService == null) {
            return;
        }
        if (numberInput == null || numberInput.isBlank()) {
            writeLineWithPrompt("Delete which note? Usage: NOTE DELETE <number>");
            return;
        }
        Integer index = parseNoteIndex(numberInput.trim());
        if (index == null) {
            writeLineWithPrompt("'" + numberInput.trim() + "' is not a valid note number.");
            return;
        }
        NoteDeletionResult result = notesService.deleteNote(roomId, index, player.getUsername());
        String message = switch (result.outcome()) {
            case DELETED -> "Note " + index + " has been removed from the board.";
            case NO_SUCH_NOTE -> "There is no note numbered " + index + " on this board.";
            case NOT_AUTHORIZED -> "You can only delete notes you posted yourself.";
        };
        writeLineWithPrompt(message);
    }

    /** Parses a one-based note index, returning {@code null} if the text is not a positive integer. */
    private @Nullable Integer parseNoteIndex(String text) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Resolves the player's current room id, or {@code null} if their location is unknown. */
    private @Nullable RoomId currentRoomId(Player player) {
        return roomService.findPlayerLocation(player.getUsername()).orElse(null);
    }

    private void handlePartyForm(Player player, PartyService partyService) {
        PartyService.PartyResult result = partyService.form(player.getUsername());
        writeLineWithPrompt(result.message());
    }

    private void handlePartyInvite(Player player, PartyService partyService, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            writeLineWithPrompt("Invite whom? Usage: PARTY INVITE <player>");
            return;
        }
        Username invitee = Username.of(targetName.trim());
        List<Username> online = onlinePlayerNames();
        boolean inviteeOnline = online.contains(invitee);
        PartyService.PartyResult result = partyService.invite(
            player.getUsername(), invitee, inviteeOnline);
        writeLineWithPrompt(result.message());
        if (result.success()) {
            // Notify the invitee
            sendToUsername(invitee,
                player.getUsername().getValue()
                    + " invites you to join their party. Type PARTY ACCEPT or PARTY DECLINE.");
        }
    }

    private void handlePartyAccept(Player player, PartyService partyService) {
        // Notify the inviter before accepting (so we can look up party members)
        Optional<Username> inviterOpt = partyService.getPendingInviter(player.getUsername());
        PartyService.PartyResult result = partyService.accept(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            inviterOpt.ifPresent(inviter ->
                sendToUsername(inviter,
                    player.getUsername().getValue() + " has joined the party."));
        }
    }

    private void handlePartyDecline(Player player, PartyService partyService) {
        Optional<Username> inviterOpt = partyService.getPendingInviter(player.getUsername());
        PartyService.PartyResult result = partyService.decline(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            inviterOpt.ifPresent(inviter ->
                sendToUsername(inviter,
                    player.getUsername().getValue() + " declined the party invitation."));
        }
    }

    private void handlePartyLeave(Player player, PartyService partyService) {
        List<Username> others = partyService.getOtherMembers(player.getUsername());
        PartyService.PartyResult result = partyService.leave(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            for (Username other : others) {
                sendToUsername(other,
                    player.getUsername().getValue() + " has left the party.");
            }
        }
    }

    private void handlePartyDisband(Player player, PartyService partyService) {
        List<Username> others = partyService.getOtherMembers(player.getUsername());
        PartyService.PartyResult result = partyService.disband(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            for (Username other : others) {
                sendToUsername(other, "The party has been disbanded by the leader.");
            }
        }
    }

    private void handlePartyStatus(Player player, PartyService partyService) {
        Optional<Party> partyOpt = partyService.findParty(player.getUsername());
        if (partyOpt.isEmpty()) {
            writeLineWithPrompt("You are not in a party. Use PARTY FORM to create one.");
            return;
        }
        Party party = partyOpt.get();
        connection.writeLine("Party members:");
        for (Username memberId : party.memberIds()) {
            String hp = findMemberCurrentHp(memberId);
            String leaderTag = party.isLeader(memberId) ? " (leader)" : "";
            connection.writeLine("  " + memberId.getValue() + leaderTag + "  HP: " + hp);
        }
        sendPrompt();
    }

    private void handleTrainList(Player player) {
        if (player.getClassId() == null) {
            writeLineWithPrompt("You have no class. Complete character creation first.");
            return;
        }
        CharacterCreationService ccs = context.characterCreationService();
        if (ccs == null) {
            writeLineWithPrompt("Trainer is unavailable.");
            return;
        }
        ClassDefinition classDef;
        try {
            classDef = ccs.resolveClassDefinition(player.getClassId().getValue()).orElse(null);
        } catch (CharacterCreationException e) {
            log.warn("Failed to load class definition for train list: {}", e.getMessage());
            writeLineWithPrompt("Trainer is unavailable.");
            return;
        }
        if (classDef == null || classDef.startingAbilityIds().isEmpty()) {
            writeLineWithPrompt("No trainable abilities found for your class.");
            return;
        }
        connection.writeLine("Master Trainer — Trainable Abilities (Practice Points: " + player.getPracticePoints() + "):");
        connection.writeLine(String.format("  %-24s %-8s %s", "Ability ID", "Cost", "Status"));
        connection.writeLine("  " + "-".repeat(48));
        for (AbilityId abilityId : classDef.startingAbilityIds()) {
            Ability ability = abilityRegistry.findById(abilityId).orElse(null);
            String displayId = abilityId.getValue();
            String name = ability != null ? ability.name() : displayId;
            boolean learned = player.getLearnedAbilities().contains(abilityId);
            String status = learned ? "learned" : "unlearned";
            connection.writeLine(String.format("  %-24s %-8s %s", displayId, "1 prac", status));
        }
        sendPrompt();
    }

    private void handleTrainAbility(Player player, String abilityInput) {
        if (player.getClassId() == null) {
            writeLineWithPrompt("You have no class. Complete character creation first.");
            return;
        }
        CharacterCreationService ccs = context.characterCreationService();
        if (ccs == null) {
            writeLineWithPrompt("Trainer is unavailable.");
            return;
        }
        ClassDefinition classDef;
        try {
            classDef = ccs.resolveClassDefinition(player.getClassId().getValue()).orElse(null);
        } catch (CharacterCreationException e) {
            log.warn("Failed to load class definition for train: {}", e.getMessage());
            writeLineWithPrompt("Trainer is unavailable.");
            return;
        }
        if (classDef == null) {
            writeLineWithPrompt("Trainer is unavailable.");
            return;
        }
        // Find the ability by id (case-insensitive match within class abilities)
        String normalized = abilityInput.trim().toLowerCase(Locale.ROOT);
        AbilityId targetId = classDef.startingAbilityIds().stream()
            .filter(id -> id.getValue().equalsIgnoreCase(normalized))
            .findFirst()
            .orElse(null);
        if (targetId == null) {
            writeLineWithPrompt("'" + abilityInput.trim() + "' is not trainable by your class. Use TRAIN LIST to see options.");
            return;
        }
        if (player.getLearnedAbilities().contains(targetId)) {
            writeLineWithPrompt("You have already learned " + targetId.getValue() + ".");
            return;
        }
        if (player.getPracticePoints() <= 0) {
            writeLineWithPrompt("You have no practice points. Practice points are earned by levelling up.");
            return;
        }
        // Deduct one practice point, add the ability, and persist
        List<AbilityId> newAbilities = new ArrayList<>(player.getLearnedAbilities());
        newAbilities.add(targetId);
        Player updated = player
            .withPracticePoints(player.getPracticePoints() - 1)
            .withLearnedAbilities(newAbilities);
        session.replacePlayer(updated);
        saveOrWarn(updated);
        Ability ability = abilityRegistry.findById(targetId).orElse(null);
        String abilityName = ability != null ? ability.name() : targetId.getValue();
        writeLineWithPrompt("You have learned " + abilityName + "! (" + updated.getPracticePoints() + " practice point(s) remaining)");
    }

    private void handleQuestList(QuestRepository questRepo) {
        List<QuestTemplate> templates;
        try {
            templates = questRepo.findAll();
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest list: {}", e.getMessage());
            writeLineWithPrompt("Quest list unavailable.");
            return;
        }
        if (templates.isEmpty()) {
            writeLineWithPrompt("There are no contracts available.");
            return;
        }
        connection.writeLine("Guild Clerk — Available Contracts:");
        connection.writeLine(String.format("  %-20s %-36s %s", "ID", "Description", "Reward"));
        connection.writeLine("  " + "-".repeat(72));
        for (QuestTemplate t : templates) {
            // NPC-delivery errands are handed out in conversation, not by the Guild Clerk.
            if (t.isNpcDeliveryQuest()) {
                continue;
            }
            String desc = t.description().length() > 35
                ? t.description().substring(0, 32) + "..."
                : t.description();
            connection.writeLine(String.format(
                "  %-20s %-36s %dg / %dxp",
                t.id().getValue(), desc, t.goldReward(), t.xpReward()));
        }
        sendPrompt();
    }

    private void handleQuestAccept(QuestRepository questRepo, String questIdInput) {
        if (questIdInput == null || questIdInput.isBlank()) {
            writeLineWithPrompt("Accept which contract? Use QUEST ACCEPT <id>.");
            return;
        }
        Player player = session.getPlayer();
        if (player.getActiveQuest() != null) {
            writeLineWithPrompt("You already hold an active contract. QUEST ABANDON it first.");
            return;
        }
        QuestTemplate template;
        try {
            String normalized = questIdInput.trim().toLowerCase(Locale.ROOT);
            template = questRepo.findAll().stream()
                .filter(t -> t.id().getValue().equalsIgnoreCase(normalized)
                    || t.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to look up quest {}: {}", questIdInput, e.getMessage());
            writeLineWithPrompt("Quest lookup failed. Try again.");
            return;
        }
        if (template == null) {
            writeLineWithPrompt("Unknown contract '" + questIdInput.trim() + "'. Use QUEST LIST to see available contracts.");
            return;
        }
        if (template.isNpcDeliveryQuest()) {
            writeLineWithPrompt(
                "That errand is not handled by the Guild Clerk. Speak with "
                + template.giverNpcId() + " to receive the package.");
            return;
        }
        if (template.isExplorationQuest() && !explorationRoomsExist(template)) {
            writeLineWithPrompt("That expedition is not available right now.");
            return;
        }
        ActiveQuest active = new ActiveQuest(template.id(), template.requiredKills());
        Player updated = player.withActiveQuest(active);
        session.replacePlayer(updated);
        if (template.isExplorationQuest()) {
            writeLineWithPrompt(
                "Contract accepted: " + template.name() + ". "
                    + "Visit all " + template.requiredRoomIds().size()
                    + " rooms. Use QUEST STATUS to see where you still need to go. Good luck.");
        } else if (template.isDeliveryQuest()) {
            writeLineWithPrompt(
                "Contract accepted: " + template.name() + ". "
                    + "Collect " + template.requiredDropCount() + " "
                    + template.dropItemId() + "(s) and QUEST DELIVER them here. Good luck.");
        } else {
            writeLineWithPrompt(
                "Contract accepted: " + template.name() + ". "
                    + "Kill " + template.requiredKills() + " "
                    + template.targetMobId() + "(s). Good luck.");
        }
    }

    /**
     * Validates that every required room of an exploration quest exists in the room repository,
     * failing loudly (returning {@code false}) when any id is unknown so a misconfigured quest is
     * never granted.
     */
    private boolean explorationRoomsExist(QuestTemplate template) {
        RoomRepository roomRepository = context.roomRepository();
        if (roomRepository == null) {
            return false;
        }
        for (String roomId : template.requiredRoomIds()) {
            try {
                if (roomRepository.findById(RoomId.of(roomId)).isEmpty()) {
                    log.warn("Exploration quest {} references unknown room {}",
                        template.id().getValue(), roomId);
                    return false;
                }
            } catch (RepositoryException e) {
                log.warn("Failed to validate exploration quest room {}: {}", roomId, e.getMessage());
                return false;
            }
        }
        return true;
    }

    private void handleQuestStatus(QuestRepository questRepo) {
        Player player = session.getPlayer();
        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            writeLineWithPrompt("No active contract.");
            return;
        }
        QuestTemplate template;
        try {
            template = questRepo.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template {}: {}", active.templateId(), e.getMessage());
            writeLineWithPrompt("Quest status unavailable.");
            return;
        }
        if (template == null) {
            writeLineWithPrompt("Unknown quest. Use QUEST ABANDON to clear it.");
            return;
        }
        if (template.isDaily()) {
            writeLineWithPrompt("That is a daily quest. Use DAILY_QUEST STATUS instead.");
            return;
        }
        if (template.isExplorationQuest()) {
            List<String> remaining = new ArrayList<>();
            for (String roomId : template.requiredRoomIds()) {
                if (!active.hasVisited(roomId)) {
                    remaining.add(roomId);
                }
            }
            int visited = template.requiredRoomIds().size() - remaining.size();
            connection.writeLine(template.name() + ": explored " + visited + " of "
                + template.requiredRoomIds().size() + " rooms.");
            if (remaining.isEmpty()) {
                connection.writeLine("You have explored every required room.");
            } else {
                connection.writeLine("Still to visit: " + String.join(", ", remaining) + ".");
            }
            sendPrompt();
        } else if (template.isNpcDeliveryQuest()) {
            boolean holdsPackage = player.getInventory().stream()
                .anyMatch(it -> it.getId().getValue().equalsIgnoreCase(template.packageItemId()));
            if (holdsPackage) {
                writeLineWithPrompt(template.name() + ": carry the package to "
                    + template.receiverNpcId() + " and use QUEST DELIVER there.");
            } else {
                writeLineWithPrompt(template.name()
                    + ": you no longer carry the package. Use QUEST ABANDON to clear it.");
            }
        } else if (template.isDeliveryQuest()) {
            int held = 0;
            for (Item it : player.getInventory()) {
                if (it.getId().getValue().equalsIgnoreCase(template.dropItemId())) {
                    held++;
                }
            }
            if (held >= template.requiredDropCount()) {
                writeLineWithPrompt(template.name() + ": " + held + "/" + template.requiredDropCount()
                    + " collected — return to the Guild Clerk and use QUEST DELIVER to claim your reward.");
            } else {
                writeLineWithPrompt(template.name() + ": " + held + "/" + template.requiredDropCount() + " collected.");
            }
        } else if (active.isComplete()) {
            writeLineWithPrompt(template.name() + ": complete — return to the Guild Clerk to claim your reward.");
        } else {
            int done = template.requiredKills() - active.killsRemaining();
            writeLineWithPrompt(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
        }
    }

    private void handleQuestComplete(QuestRepository questRepo) {
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!"courtyard".equals(roomIdOpt.get().getValue())) {
            writeLineWithPrompt("The Guild Clerk is not here. Find them in the Courtyard.");
            return;
        }
        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            writeLineWithPrompt("You have no active contract to complete.");
            return;
        }
        // Delivery quests use QUEST DELIVER, not QUEST COMPLETE
        QuestTemplate templateCheck;
        try {
            templateCheck = questRepo.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            writeLineWithPrompt("Quest lookup failed.");
            return;
        }
        if (templateCheck != null && templateCheck.isNpcDeliveryQuest()) {
            writeLineWithPrompt(
                "This is a package errand. Carry the package to " + templateCheck.receiverNpcId()
                + " and use QUEST DELIVER there.");
            return;
        }
        if (templateCheck != null && templateCheck.isDeliveryQuest()) {
            writeLineWithPrompt("This contract requires item delivery. Use QUEST DELIVER instead.");
            return;
        }
        if (templateCheck != null && templateCheck.isDaily()) {
            writeLineWithPrompt("This is a daily quest. Use DAILY_QUEST COMPLETE to claim your reward.");
            return;
        }
        if (!active.isComplete()) {
            QuestTemplate template;
            try {
                template = questRepo.findById(active.templateId()).orElse(null);
            } catch (QuestRepositoryException e) {
                writeLineWithPrompt("Quest lookup failed.");
                return;
            }
            String name = template != null ? template.name() : active.templateId().getValue();
            writeLineWithPrompt("Contract not yet fulfilled: " + name + " (" + active.killsRemaining() + " kills remaining).");
            return;
        }
        QuestTemplate template;
        try {
            template = questRepo.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template on complete: {}", e.getMessage());
            writeLineWithPrompt("Quest reward lookup failed.");
            return;
        }
        if (template == null) {
            writeLineWithPrompt("Unknown quest template. Contract cleared.");
            session.replacePlayer(player.withActiveQuest(null));
            return;
        }
        QuestKillService questKillSvc = new QuestKillService(questRepo);
        QuestKillService.CompletionResult result = questKillSvc.grantCompletionReward(player, template);
        Player rewarded = result.player();
        session.replacePlayer(rewarded);
        List<String> messages = result.messages();
        // Reward/gold/xp messages first, then any level-up notice, then the title message (if any).
        connection.writeLine(messages.get(0));
        connection.writeLine(messages.get(1));
        if (result.leveledUp()) {
            connection.writeLine("You have advanced to level " + rewarded.getLevel() + "!");
        }
        for (int i = 2; i < messages.size(); i++) {
            connection.writeLine(messages.get(i));
        }
        sendPrompt();
    }

    private void handleQuestDeliver(QuestRepository questRepo) {
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        RoomId roomId = roomIdOpt.get();
        ActiveQuest active = player.getActiveQuest();
        if (active != null) {
            QuestTemplate template;
            try {
                template = questRepo.findById(active.templateId()).orElse(null);
            } catch (QuestRepositoryException e) {
                writeLineWithPrompt("Quest lookup failed.");
                return;
            }
            if (template != null && template.isNpcDeliveryQuest()) {
                handleNpcDelivery(questRepo, player, roomId, template);
                return;
            }
        }
        if (!"courtyard".equals(roomId.getValue())) {
            writeLineWithPrompt("The Guild Clerk is not here. Find them in the Courtyard.");
            return;
        }
        QuestDeliveryService deliverySvc = new QuestDeliveryService(questRepo);
        QuestDeliveryService.DeliverResult result = deliverySvc.deliver(player);
        if (result.success()) {
            session.replacePlayer(result.player());
        }
        for (String msg : result.messages()) {
            connection.writeLine(msg);
        }
        sendPrompt();
    }

    private void handleNpcDelivery(QuestRepository questRepo, Player player, RoomId roomId, QuestTemplate template) {
        boolean receiverPresent = context.mobRegistry() != null
            && context.mobRegistry().getMobsInRoom(roomId).stream()
                .anyMatch(m -> m.isAlive()
                    && m.template().id().getValue().equalsIgnoreCase(template.receiverNpcId()));
        QuestNpcDeliveryService npcDeliverySvc = new QuestNpcDeliveryService(questRepo);
        DeliveryQuestResult result = npcDeliverySvc.deliver(player, roomId, receiverPresent);
        if (result.success()) {
            session.replacePlayer(result.player());
        }
        for (String msg : result.messages()) {
            connection.writeLine(msg);
        }
        sendPrompt();
    }

    private void handleQuestAbandon() {
        Player player = session.getPlayer();
        if (player.getActiveQuest() == null) {
            writeLineWithPrompt("You have no active contract to abandon.");
            return;
        }
        session.replacePlayer(player.withActiveQuest(null));
        writeLineWithPrompt("Contract abandoned. No reward will be granted.");
    }

    /** Called by the resting ticker to apply regenerated vitals. */
    private void applyRestUpdate(Player updated) {
        session.setPlayer(updated);
        sendPrompt();
    }

    // ── Save-failure hook (wired into PlayerSession at login) ──────────

    /**
     * Called by {@link PlayerSession} when a queued player save fails to flush at
     * disconnect time. Warns the player and emits an audit event.
     *
     * @param player the player whose save failed
     */
    void handleSaveFailure(Player player) {
        log.error("Player save failed for {}; warning player", player.getUsername());
        connection.writeLine("Warning: your progress could not be saved.");
        emitAudit(
            "player.save.failed",
            AuditSubject.player(player.getUsername()),
            null,
            resolveRoomId(player),
            "failure",
            Map.of()
        );
    }

    // ── Post-login wiring (called once after a player authenticates) ───

    /**
     * Registers event-bus, effect, and healing callbacks on the session, enqueues the
     * initial death-state check, and either starts character creation (for new players) or
     * dispatches an immediate {@code look} command.
     *
     * <p>Must be called on the reader thread immediately after authentication completes, before
     * the main read loop continues.
     *
     * @param isNew {@code true} when this is the player's very first login (new character)
     */
    void registerPostLoginCallbacks(boolean isNew) {
        // Register player-event-bus listener for mob-initiated combat results
        if (context.playerEventBus() != null && session.getPlayer() != null) {
            context.playerEventBus().register(session.getPlayer().getUsername(), result ->
                session.enqueueCommand(() -> {
                    Player current = session.getPlayer();
                    if (result.updatedSource() != null && current != null && current.isResting()) {
                        session.setPlayer(current.withResting(false));
                        session.clearRestingTicker();
                        connection.writeLine("You are jolted awake by the attack!");
                    }
                    deliverResult(result);
                    sendPrompt();
                })
            );
        }
        // Register effect message sink
        session.registerEffects(new EffectMessageSink() {
            @Override
            public void sendToTarget(String message) {
                connection.writeLine(message);
                sendPrompt();
            }
            @Override
            public void sendToRoom(String message) {
                Player current = session.getPlayer();
                if (current != null) {
                    deliverRoomMessage(current.getUsername(), null, message);
                }
            }
        });
        // Register healing tick callback
        session.registerHealing(this::applyHealingUpdate);
        // Register hunger/thirst decay tick callback
        session.registerSustenance(this::applySustenanceUpdate, this::deliverSustenanceWarning);
        // Enqueue death-state check
        session.enqueueCommand(session::handleDeathState);
        // Re-spawn the player's persisted tamed companions into their room on the tick thread so
        // they rejoin their owner in the world (AGENTS.md §5).
        if (context.mobRegistry() != null) {
            session.enqueueCommand(() -> {
                Player current = session.getPlayer();
                if (current == null || current.isDead()) {
                    return;
                }
                roomService.findPlayerLocation(current.getUsername())
                    .ifPresent(room -> context.mobRegistry().spawnTamedPets(current, room));
            });
        }
        // Show recent gossip history exactly once, before any other post-login output.
        sendGossipHistory();
        // Notify of any unread mail waiting in the player's mailbox.
        sendMailNotice();
        // Start character creation for brand-new players; dispatch look for returning ones
        if (isNew && context.characterCreationService() != null) {
            client.beginCharacterCreation();
        } else {
            String correlationId = auditService.newCorrelationId();
            session.enqueueCommand(() -> dispatcher.dispatch(this, "look", correlationId));
        }
    }

    /**
     * Shows the server-wide {@link io.taanielo.jmud.core.messaging.GossipHistory} to the
     * connecting player exactly once, oldest-first, before any other post-login output.
     * Does nothing when the history is empty.
     */
    private void sendGossipHistory() {
        connection.writeLines(context.gossipHistory().renderForLogin());
    }

    /**
     * Shows a one-line unread-mail count notice to the connecting player when they have
     * unread mail waiting, mirroring {@link #sendGossipHistory()}. Does nothing when there
     * is no unread mail.
     */
    private void sendMailNotice() {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        long unread = player.mailbox().unreadCount();
        if (unread == 0) {
            return;
        }
        connection.writeLine("You have " + unread + " new message(s). Type MAIL to read them.");
    }
}
