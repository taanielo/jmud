package io.taanielo.jmud.core.server.socket;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
import io.taanielo.jmud.core.ability.training.AbilityTrainingService;
import io.taanielo.jmud.core.ability.training.TrainableAbilityStatus;
import io.taanielo.jmud.core.ability.training.TrainingAttempt;
import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.achievement.AchievementService.AchievementStatus;
import io.taanielo.jmud.core.action.FleeResult;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameActionService;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.auction.AuctionFilter;
import io.taanielo.jmud.core.auction.AuctionListing;
import io.taanielo.jmud.core.auction.AuctionService;
import io.taanielo.jmud.core.auction.AuctionService.NumberedListing;
import io.taanielo.jmud.core.auction.AuctionSettings;
import io.taanielo.jmud.core.auction.AuctionTransactionResult;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bank.BankTransactionResult;
import io.taanielo.jmud.core.bank.VaultUpgradeTier;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.craft.CraftOutcome;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.dialogue.DialogueNode;
import io.taanielo.jmud.core.dialogue.DialogueResponse;
import io.taanielo.jmud.core.dialogue.DialogueService;
import io.taanielo.jmud.core.dialogue.DialogueTree;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.enchant.EnchantOutcome;
import io.taanielo.jmud.core.gathering.GatherOutcome;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildLevel;
import io.taanielo.jmud.core.guild.GuildMember;
import io.taanielo.jmud.core.guild.GuildResult;
import io.taanielo.jmud.core.guild.GuildService;
import io.taanielo.jmud.core.guild.GuildVaultResult;
import io.taanielo.jmud.core.guild.VaultedItem;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.notes.NoteDeletionResult;
import io.taanielo.jmud.core.notes.NotesService;
import io.taanielo.jmud.core.notes.PlayerNote;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.party.LootMode;
import io.taanielo.jmud.core.party.Party;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.AliasResult;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.LightingService;
import io.taanielo.jmud.core.player.MailResult;
import io.taanielo.jmud.core.player.MovementCostService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerAliasService;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerFriendList;
import io.taanielo.jmud.core.player.PlayerGuildMembership;
import io.taanielo.jmud.core.player.PlayerIdentity;
import io.taanielo.jmud.core.player.PlayerIgnoreList;
import io.taanielo.jmud.core.player.PlayerMailService;
import io.taanielo.jmud.core.player.PlayerMount;
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
import io.taanielo.jmud.core.quest.QuestItemRewardService;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestNpcDeliveryService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestReputationRewardService;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.salvage.SalvageOutcome;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.shop.ShopTransactionResult;
import io.taanielo.jmud.core.social.MarriageService;
import io.taanielo.jmud.core.trade.TradeExecutionService;
import io.taanielo.jmud.core.trade.TradeService;
import io.taanielo.jmud.core.trade.TradeSession;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.DoorActionResult;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.ItemDurabilityService;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomRenderer;
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
    private final AbilityTrainingService abilityTrainingService;
    private final AbilityTargetResolver abilityTargetResolver;
    private final EncumbranceService encumbranceService;
    private final MovementCostService movementCostService;
    private final RoomService roomService;
    private final AuditService auditService;
    private final PersistenceQueue persistenceQueue;
    private final PromptRenderer promptRenderer;
    private final SocketCommandDispatcher dispatcher;
    private final PlayerAliasService playerAliasService;
    private final PlayerMailService playerMailService;
    private final LightingService lightingService;
    private final @Nullable NotesService notesService;
    private final TradeExecutionService tradeExecutionService = new TradeExecutionService();

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
        this.abilityTrainingService = new AbilityTrainingService(this.abilityRegistry);
        this.abilityTargetResolver = Objects.requireNonNull(context.abilityTargetResolver(), "Ability target resolver is required");
        this.encumbranceService = Objects.requireNonNull(context.encumbranceService(), "Encumbrance service is required");
        this.movementCostService = new MovementCostService(encumbranceService);
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
                    .map(mob -> new io.taanielo.jmud.core.action.MobLocatorPort.TrackableMob(
                        mob.template().id().getValue(), mob.template().name()))
                    .toList()
        );
        if (context.duelService() != null) {
            this.gameActionService.setDuelService(context.duelService());
        }
        if (context.weatherEngine() != null) {
            this.gameActionService.setWeatherEngine(context.weatherEngine());
        }
        if (context.partyService() != null) {
            this.gameActionService.setPartyService(context.partyService());
        }
        if (context.characterAttributesResolver() != null) {
            this.gameActionService.setCharacterAttributesResolver(context.characterAttributesResolver());
        }
        if (context.areaMapService() != null) {
            this.gameActionService.setAreaMapService(context.areaMapService());
        }
        this.gameActionService.setOnlinePlayerLookup(
            username -> java.util.Optional.ofNullable(findOnlinePlayer(username)));
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
        return clientPool.allConnections();
    }

    @Override
    public List<Username> onlinePlayerNames() {
        // The in-world pool view already excludes connections at the login or race/class prompts
        // (issues #512/#514), so WHO/TELL/WHISPER/GIVE can never target a mid-creation player.
        return clientPool.inWorld().stream()
            .filter(SocketClient.class::isInstance)
            .map(SocketClient.class::cast)
            // Linkdead players are still in the world (attackable, occupying their room) but are
            // unresponsive, so they are omitted from the WHO roster (issue #343).
            .filter(sc -> !sc.session().isLinkdead())
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
        for (Client c : clientPool.allConnections()) {
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

    private void handleAutoLootCommand(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to change autoloot settings.");
            return;
        }
        String normalized = args == null ? "" : args.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("AUTOLOOT is " + (player.isAutoLootEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON" -> setAutoLootEnabled(true);
            case "OFF" -> setAutoLootEnabled(false);
            case "TOGGLE" -> setAutoLootEnabled(!player.isAutoLootEnabled());
            default -> writeLineWithPrompt("Usage: AUTOLOOT [on|off|toggle|status]");
        }
    }

    private void setAutoLootEnabled(boolean enabled) {
        Player player = session.getPlayer();
        if (player.isAutoLootEnabled() == enabled) {
            writeLineWithPrompt("AUTOLOOT is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        session.replacePlayer(player.withAutoLootEnabled(enabled));
        writeLineWithPrompt("AUTOLOOT is now " + (enabled ? "ON" : "OFF"));
    }

    private void handleBriefCommand(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to change brief mode.");
            return;
        }
        String normalized = args == null ? "" : args.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("BRIEF is " + (player.isBriefModeEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON" -> setBriefModeEnabled(true);
            case "OFF" -> setBriefModeEnabled(false);
            case "TOGGLE" -> setBriefModeEnabled(!player.isBriefModeEnabled());
            default -> writeLineWithPrompt("Usage: BRIEF [on|off|toggle|status]");
        }
    }

    private void setBriefModeEnabled(boolean enabled) {
        Player player = session.getPlayer();
        if (player.isBriefModeEnabled() == enabled) {
            writeLineWithPrompt("BRIEF is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        session.replacePlayer(player.withBriefModeEnabled(enabled));
        writeLineWithPrompt("BRIEF is now " + (enabled ? "ON" : "OFF"));
    }

    private static RoomRenderer.DescriptionMode descriptionModeFor(Player player) {
        return player.isBriefModeEnabled()
            ? RoomRenderer.DescriptionMode.BRIEF
            : RoomRenderer.DescriptionMode.FULL;
    }

    // ── AFK / away status (issue #464) ──────────────────────────────────

    @Override
    public void toggleAfk(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to go AFK.");
            return;
        }
        AfkStatus.ToggleResult result = AfkStatus.toggle(session.isAway(), args);
        if (result.away()) {
            session.setAway(result.message());
        } else {
            session.clearAway();
        }
        writeLineWithPrompt(result.confirmation());
    }

    @Override
    public void clearAwayIfActive() {
        if (session.isAway()) {
            session.clearAway();
            connection.writeLine("Welcome back. You are no longer AFK.");
        }
    }

    @Override
    public boolean isPlayerAway(Username username) {
        PlayerSession target = findSession(username);
        return target != null && target.isAway();
    }

    @Override
    public Optional<String> awayNotice(Username username) {
        PlayerSession target = findSession(username);
        if (target == null || !target.isAway()) {
            return Optional.empty();
        }
        return Optional.of(AfkStatus.recipientNotice(username, target.awayMessage()));
    }

    // ── LFG / looking-for-group status (issue #510) ─────────────────────

    @Override
    public void toggleLfg(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to look for a group.");
            return;
        }
        if (LfgStatus.isStatusQuery(args)) {
            writeLineWithPrompt(LfgStatus.status(session.isLfg(), session.lfgMessage()));
            return;
        }
        LfgStatus.ToggleResult result = LfgStatus.toggle(session.isLfg(), args);
        if (result.lfg()) {
            session.setLfg(result.message());
        } else {
            session.clearLfg();
        }
        writeLineWithPrompt(result.confirmation());
    }

    @Override
    public boolean isPlayerLfg(Username username) {
        PlayerSession target = findSession(username);
        return target != null && target.isLfg();
    }

    @Override
    public String lfgTag(Username username) {
        PlayerSession target = findSession(username);
        if (target == null || !target.isLfg()) {
            return "";
        }
        return LfgStatus.rosterTag(true, target.lfgMessage());
    }

    @Override
    public String levelClassTag(Username username) {
        Player player = findOnlinePlayer(username);
        if (player == null) {
            return "";
        }
        return " [" + player.getLevel() + " " + resolveClassDisplayName(player) + "]";
    }

    /**
     * Resolves the display name of the given player's class from the class registry (the same source
     * the trainer and character sheet use), falling back to the raw class id when the definition
     * cannot be loaded or the player has no class yet.
     *
     * @param player the player whose class display name to resolve
     * @return the class display name, never blank
     */
    private String resolveClassDisplayName(Player player) {
        if (player.getClassId() == null) {
            return "Adventurer";
        }
        String classIdValue = player.getClassId().getValue();
        CharacterCreationService ccs = context.characterCreationService();
        if (ccs == null) {
            return classIdValue;
        }
        try {
            return ccs.resolveClassDefinition(classIdValue)
                .map(ClassDefinition::name)
                .orElse(classIdValue);
        } catch (CharacterCreationException e) {
            log.warn("Failed to resolve class display name for WHO: {}", e.getMessage());
            return classIdValue;
        }
    }

    /**
     * Resolves the live {@link PlayerSession} for the given online username, or {@code null} when no
     * such client is currently connected. Used to inspect another player's transient AFK state.
     */
    private @Nullable PlayerSession findSession(Username username) {
        SocketClient socketClient = findSocketClient(username);
        return socketClient == null ? null : socketClient.session();
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

    /**
     * Throws the player off any mount they are riding when they enter combat, emitting the spook
     * messages to the player and their room. Used by combat-initiating paths (e.g. ASSIST) where
     * dismounting is a same-tick side effect rather than the action's own result, mirroring how
     * {@code GameActionService} breaks the mount on the ATTACK path.
     */
    private void breakMountIfActive() {
        Player player = session.getPlayer();
        if (player != null && player.isMounted()) {
            String name = player.mount().mountName();
            deliverResult(new GameActionResult(
                player.withMount(PlayerMount.dismounted()),
                null,
                List.of(
                    GameMessage.toSource(
                        "The clash of combat spooks " + name + " and you drop down to fight on foot!"),
                    GameMessage.toRoom(
                        player.getUsername(), null,
                        player.getUsername().getValue() + " leaps down from " + name
                            + " as battle is joined."))));
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
        String promptLine = promptRenderer.render(format, player, partyHp, player.isAnsiEnabled());
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
        for (Client c : clientPool.allConnections()) {
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
    public @Nullable Player getOnlinePlayer(Username username) {
        return findOnlinePlayer(username);
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

    @Override
    public void sendRoomSay(Player source, String message) {
        if (source == null || message == null || message.isBlank()) {
            return;
        }
        Username sourceUser = source.getUsername();
        Optional<RoomId> roomIdOpt = roomService.findPlayerLocation(sourceUser);
        if (roomIdOpt.isEmpty()) {
            return;
        }
        RoomId roomId = roomIdOpt.get();
        Set<Username> exclude = new HashSet<>();
        exclude.add(sourceUser);
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            Player occupantPlayer = findOnlinePlayer(occupant);
            if (occupantPlayer != null && occupantPlayer.ignoreList().has(sourceUser.getValue())) {
                exclude.add(occupant);
            }
        }
        context.messageBroadcaster().broadcastToRoom(roomId, new PlainTextMessage(message), exclude);
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

    private List<String> formatLookDescription(Player target) {
        String name = target.getUsername().getValue();
        String race = target.getRace().getValue();
        String classId = target.getClassId().getValue();
        String generated = name + " the " + race + " " + classId + " (level " + target.getLevel() + ").";
        String custom = target.description();
        if (custom.isEmpty()) {
            return List.of(generated);
        }
        return List.of(custom, generated);
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
        writeResourceNodeLines(result.room());
        writeRoomOccupantLines(result.room());
        sendPrompt();
    }

    /** Writes the look-description line for any available resource node in the given room. */
    private void writeResourceNodeLines(@Nullable Room room) {
        if (room == null || context.resourceGatheringService() == null) {
            return;
        }
        for (String line : context.resourceGatheringService().describeAvailableNodes(room.getId())) {
            connection.writeLine(line);
        }
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
            // Not a player: fall back to looking at a creature in the room (e.g. a tamed companion),
            // which renders its custom description (see DESCRIBE) when one is set.
            if (context.mobRegistry() != null) {
                Optional<RoomId> roomIdOpt = roomService.findPlayerLocation(source.getUsername());
                if (roomIdOpt.isPresent()) {
                    Optional<List<String>> mobLook =
                        context.mobRegistry().describeMobOnLook(roomIdOpt.get(), targetInput);
                    if (mobLook.isPresent()) {
                        connection.writeLines(mobLook.get());
                        sendPrompt();
                        return;
                    }
                }
            }
            writeLineWithPrompt("You don't see that here.");
            return;
        }
        Player target = resolved.get();
        connection.writeLines(formatLookDescription(target));
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
        if (movementCostService.isExhausted(player)) {
            writeLineWithPrompt(MovementCostService.EXHAUSTED_MESSAGE);
            return;
        }
        RoomService.LookResult currentLook = roomService.look(player.getUsername(), session.getTextStyler());
        Room oldRoom = currentLook.room();
        String fromRoom = resolveRoomId(player);
        RoomService.MoveResult result =
            roomService.move(player.getUsername(), direction, session.getTextStyler(), descriptionModeFor(player));
        emitMoveAudit(player, direction, fromRoom, result);
        if (result.moved()) {
            player = spendMovePoints(player);
            player = dismountIfNotOutdoors(player, result.room());
        }
        renderMoveOutcome(player, direction, oldRoom, result, null);
        if (result.moved()) {
            triggerAutoFollow(player.getUsername(), fromRoom, direction);
        }
    }

    /**
     * Deducts the move-point cost of one step from the given (already-moved) player, replaces the
     * session snapshot, and persists it through the write-behind queue. Runs on the tick thread as
     * part of command execution (AGENTS.md §5).
     *
     * @param player the player who just completed a room transition
     * @return the updated player snapshot with move points spent
     */
    private Player spendMovePoints(Player player) {
        Player spent = movementCostService.spend(player);
        session.replacePlayer(spent);
        saveOrWarn(spent);
        return spent;
    }

    /**
     * Auto-dismounts a rider who has just entered an indoor or underground room, since mounts may
     * only be ridden outdoors. Replaces the session snapshot and notifies the player. Runs on the
     * tick thread as part of command execution (AGENTS.md §5).
     *
     * @param player the player who just entered {@code room}
     * @param room   the room the player moved into (may be {@code null} when unresolved)
     * @return the updated (possibly dismounted) player snapshot
     */
    private Player dismountIfNotOutdoors(Player player, @Nullable Room room) {
        if (!player.isMounted() || room == null || room.isOutdoor()) {
            return player;
        }
        String mountName = player.mount().mountName();
        Player dismounted = player.withMount(PlayerMount.dismounted());
        session.replacePlayer(dismounted);
        connection.writeLine("You cannot ride " + mountName + " here and swing down from the saddle.");
        return dismounted;
    }

    /** Emits the {@code player.move} audit event for a move attempt (successful or blocked). */
    private void emitMoveAudit(Player player, Direction direction, String fromRoom,
                               RoomService.MoveResult result) {
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
    }

    /**
     * Renders the outcome of a completed {@link RoomService#move} to this player's connection:
     * departure/arrival room broadcasts, the destination description (or darkness), danger warnings,
     * exploration tracking, and a trailing prompt.
     *
     * <p>Shared by manually-typed moves ({@link #sendMove}) and auto-follow moves
     * ({@link #performFollowMove}). When {@code selfMovePrefix} is non-null it is printed to the mover
     * before the room text — auto-follow passes a {@code "You follow X east."} line so the follower
     * understands why they moved.
     *
     * @param player         the moving player
     * @param direction      the direction moved
     * @param oldRoom        the room the player left (may be {@code null})
     * @param result         the resolved move result
     * @param selfMovePrefix an optional line shown to the mover before the room text
     */
    private void renderMoveOutcome(Player player, Direction direction, @Nullable Room oldRoom,
                                   RoomService.MoveResult result, @Nullable String selfMovePrefix) {
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
            connection.writeLine(selfMovePrefix != null
                ? selfMovePrefix
                : "You move " + direction.label() + ".");
            connection.writeLines(lightingService.darknessLines());
            warnIfRoomTooDangerous(player, destination);
            markRoomExplored(destination.getId());
            recordExplorationVisit(destination);
            sendPrompt();
            return;
        }
        if (selfMovePrefix != null) {
            connection.writeLine(selfMovePrefix);
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
     * After this player successfully moved, walks along every party member auto-following them who is
     * still in the room the leader just left, reusing the same {@link RoomService#move} path. Followers
     * who have gone offline/linkdead or left the party have their relationship silently cleared here so
     * no dangling follow can accumulate.
     *
     * <p>Runs on the tick thread as part of the leader's move command, so each follower's step lands in
     * the same tick (AGENTS.md &sect;5).
     *
     * @param leader      the player who just moved
     * @param fromRoomId  the room id the leader departed
     * @param direction   the direction the leader travelled
     */
    private void triggerAutoFollow(Username leader, @Nullable String fromRoomId, Direction direction) {
        PartyService partyService = context.partyService();
        if (partyService == null || fromRoomId == null) {
            return;
        }
        List<Username> followers = partyService.followersOf(leader);
        if (followers.isEmpty()) {
            return;
        }
        RoomId fromRoom = RoomId.of(fromRoomId);
        for (Username followerName : followers) {
            SocketClient followerClient = findSocketClient(followerName);
            if (followerClient == null || followerClient.session().isLinkdead()) {
                // Offline or linkdead: drop the stale relationship (cannot notify an absent follower).
                partyService.clearFollowsInvolving(followerName);
                continue;
            }
            if (!partyService.inSameParty(followerName, leader)) {
                partyService.unfollow(followerName);
                sendToUsername(followerName,
                    "You are no longer in a party with " + leader.getValue() + "; you stop following.");
                continue;
            }
            Optional<RoomId> followerRoom = roomService.findPlayerLocation(followerName);
            if (followerRoom.isEmpty() || !followerRoom.get().equals(fromRoom)) {
                // The follower is not in the room the leader left — nothing to do for this step.
                continue;
            }
            followerClient.autoFollow(direction, leader);
        }
    }

    /**
     * Performs an auto-follow step for this player behind {@code leaderName}, invoked on the follower's
     * own command context so all room description, prompt, and exploration side effects land on the
     * follower's connection. Cancels the follow (with a one-line notice) when the follower is in combat,
     * overburdened, or cannot take the same exit — never leaving a silent stuck state.
     *
     * @param direction  the direction to follow
     * @param leaderName the leader being followed
     */
    void performFollowMove(Direction direction, Username leaderName) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            return;
        }
        PartyService partyService = context.partyService();
        if (partyService == null) {
            return;
        }
        // Combat lockout mirrors the RECALL restriction (context.mobRegistry().isInCombat(...)).
        if (context.mobRegistry() != null && context.mobRegistry().isInCombat(player.getUsername())) {
            cancelFollow(partyService, player.getUsername(),
                "You are locked in combat and stop following " + leaderName.getValue() + ".");
            return;
        }
        if (movementCostService.isExhausted(player)) {
            cancelFollow(partyService, player.getUsername(),
                "You are too exhausted to keep following " + leaderName.getValue() + ". REST to recover.");
            return;
        }
        cancelRestIfActive();
        session.clearDialogue();
        RoomService.LookResult currentLook = roomService.look(player.getUsername(), session.getTextStyler());
        Room oldRoom = currentLook.room();
        String fromRoom = resolveRoomId(player);
        RoomService.MoveResult result =
            roomService.move(player.getUsername(), direction, session.getTextStyler(), descriptionModeFor(player));
        emitMoveAudit(player, direction, fromRoom, result);
        if (!result.moved()) {
            cancelFollow(partyService, player.getUsername(),
                "You cannot follow " + leaderName.getValue() + " " + direction.label() + " from here.");
            return;
        }
        player = spendMovePoints(player);
        renderMoveOutcome(player, direction, oldRoom, result,
            "You follow " + leaderName.getValue() + " " + direction.label() + ".");
    }

    /** Clears this follower's auto-follow relationship and notifies them with the given reason. */
    private void cancelFollow(PartyService partyService, Username follower, String reason) {
        partyService.unfollow(follower);
        writeLineWithPrompt(reason);
    }

    /**
     * Looks up the connected {@link SocketClient} authenticated as {@code username}, or {@code null}
     * when no such client is connected.
     */
    private @Nullable SocketClient findSocketClient(Username username) {
        for (Client c : clientPool.allConnections()) {
            if (c instanceof SocketClient sc && sc.isAuthenticatedUser(username)) {
                return sc;
            }
        }
        return null;
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
    // Identity comparison is intentional: exploreRoom returns the same Player instance (this) when the
    // room was already explored, so reference identity is the no-op sentinel that skips the resave.
    @SuppressWarnings("ReferenceEquality")
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
        ExplorationQuestService explorationSvc = new ExplorationQuestService(
            questRepo, context.questItemRewardService(), context.questReputationRewardService());
        explorationSvc.setLevelUpService(new LevelUpService(context.classLevelGainsResolver()));
        explorationSvc.recordRoomVisit(player, destination.getId()).ifPresent(result -> {
            session.replacePlayer(result.player());
            dropQuestRewardOverflow(result.player(), result.droppedItems());
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
    public void updateAutoLoot(String args) {
        handleAutoLootCommand(args);
    }

    @Override
    public void updateBrief(String args) {
        handleBriefCommand(args);
    }

    @Override
    public void updatePrompt(String args) {
        handlePromptCommand(args);
    }

    private void handlePromptCommand(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to change your prompt.");
            return;
        }
        String raw = args == null ? "" : args.trim();
        if (raw.isEmpty()) {
            showPromptFormat(player);
            return;
        }
        String[] parts = raw.split("\\s+", 2);
        String sub = parts[0].toUpperCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "SET" -> setPromptFormat(rest);
            case "COLOR", "COLOUR" -> handlePromptColor(rest);
            default -> writeLineWithPrompt(
                "Usage: PROMPT | PROMPT SET <format> | PROMPT COLOR [on|off|toggle|status]");
        }
    }

    private void showPromptFormat(Player player) {
        String format = player.getPromptFormat();
        if (format == null || format.isBlank()) {
            format = PromptSettings.defaultFormat();
        }
        connection.writeLine("Your prompt format: " + format);
        connection.writeLine(
            "Tokens: %h/%H hp, %m/%M mana, %v moves, %x exp, %l level, %% for a literal percent.");
        connection.writeLine(
            "Also supported: {hp} {maxHp} {mana} {maxMana} {move} {maxMove} {exp} {partyHp}.");
        connection.writeLine("Prompt color is " + (player.isAnsiEnabled() ? "ON" : "OFF")
            + " (change with PROMPT COLOR on|off).");
        connection.writeLine("Set a new format with: PROMPT SET <format>");
        sendPrompt();
    }

    private void setPromptFormat(String format) {
        if (format == null || format.isBlank()) {
            writeLineWithPrompt("Usage: PROMPT SET <format>");
            return;
        }
        Player updated = session.getPlayer().withPromptFormat(format);
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("Prompt format updated.");
    }

    private void handlePromptColor(String arg) {
        Player player = session.getPlayer();
        String normalized = arg == null ? "" : arg.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("Prompt color is " + (player.isAnsiEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON" -> setPromptColorEnabled(true);
            case "OFF" -> setPromptColorEnabled(false);
            case "TOGGLE" -> setPromptColorEnabled(!player.isAnsiEnabled());
            default -> writeLineWithPrompt("Usage: PROMPT COLOR [on|off|toggle|status]");
        }
    }

    private void setPromptColorEnabled(boolean enabled) {
        Player player = session.getPlayer();
        if (player.isAnsiEnabled() == enabled) {
            writeLineWithPrompt("Prompt color is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        session.replacePlayer(player.withAnsiEnabled(enabled));
        session.setTextStyler(TextStylers.forEnabled(session.getPlayer().isAnsiEnabled()));
        saveOrWarn(session.getPlayer());
        writeLineWithPrompt("Prompt color is now " + (enabled ? "ON" : "OFF"));
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

    // ── Marriage (issue #649) ───────────────────────────────────────────

    @Override
    public void executeMarry(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to marry.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("STATUS")) {
            showMarriageStatus(player);
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String sub = parts[0].toUpperCase(Locale.ROOT);
        String rest = parts.length < 2 ? "" : parts[1];
        switch (sub) {
            case "ACCEPT" -> acceptMarriageProposal(player);
            case "DECLINE" -> declineMarriageProposal(player);
            case "DIVORCE" -> divorce(player);
            case "TELL" -> spouseTell(rest);
            default -> proposeMarriage(player, trimmed);
        }
    }

    private MarriageService marriageService() {
        return context.marriageService();
    }

    private void showMarriageStatus(Player player) {
        MarriageService marriageService = marriageService();
        if (player.isMarried()) {
            // Self-heal a dangling bond: a spouse who was purged (or whose file no longer exists) and
            // is not online effectively divorces this player (issue #649). Reflect single status.
            String spouse = player.spouse();
            if (spouse != null && findOnlinePlayer(Username.of(spouse)) == null
                && context.playerRepository().loadPlayer(Username.of(spouse)).isEmpty()) {
                session.replacePlayer(player.withSpouse(null));
                writeLineWithPrompt("Your former spouse " + spouse
                    + " is no longer among us; you are single once more.");
                return;
            }
            writeLineWithPrompt("You are married to " + spouse + ".");
            return;
        }
        Optional<Username> proposer = marriageService.pendingProposer(player.getUsername());
        if (proposer.isPresent()) {
            writeLineWithPrompt(proposer.get().getValue()
                + " has proposed to you. Type MARRY ACCEPT or MARRY DECLINE.");
            return;
        }
        writeLineWithPrompt("You are not married and have no pending proposals.");
    }

    private void proposeMarriage(Player player, String targetName) {
        if (player.isMarried()) {
            writeLineWithPrompt("You are already married to " + player.spouse()
                + ". You must DIVORCE before proposing to another.");
            return;
        }
        Optional<Player> match = abilityTargetResolver.resolve(player, targetName);
        if (match.isEmpty()) {
            writeLineWithPrompt("There is no one here by that name to marry.");
            return;
        }
        Player target = match.get();
        if (target.getUsername().equals(player.getUsername())) {
            writeLineWithPrompt("You cannot marry yourself.");
            return;
        }
        if (target.isMarried()) {
            writeLineWithPrompt(target.getUsername().getValue() + " is already married.");
            return;
        }
        if (marriageService().hasPendingProposal(target.getUsername())) {
            writeLineWithPrompt(target.getUsername().getValue()
                + " already has a pending proposal. Let them answer it first.");
            return;
        }
        marriageService().propose(player.getUsername(), target.getUsername());
        sendToUsername(target.getUsername(), player.getUsername().getValue()
            + " proposes marriage to you! Type MARRY ACCEPT to say yes, or MARRY DECLINE."
            + " The proposal lapses in 60 seconds.");
        writeLineWithPrompt("You propose marriage to " + target.getUsername().getValue()
            + ". They have 60 seconds to answer.");
    }

    private void acceptMarriageProposal(Player player) {
        Optional<Username> proposerMatch = marriageService().pendingProposer(player.getUsername());
        if (proposerMatch.isEmpty()) {
            writeLineWithPrompt("You have no pending marriage proposal.");
            return;
        }
        Username proposerName = proposerMatch.get();
        if (player.isMarried()) {
            marriageService().resolve(player.getUsername());
            writeLineWithPrompt("You are already married to " + player.spouse() + ".");
            return;
        }
        Player proposer = findOnlinePlayer(proposerName);
        if (proposer == null) {
            marriageService().resolve(player.getUsername());
            writeLineWithPrompt(proposerName.getValue() + " is no longer online; the proposal is void.");
            return;
        }
        if (proposer.isMarried()) {
            marriageService().resolve(player.getUsername());
            writeLineWithPrompt(proposerName.getValue() + " has married someone else in the meantime.");
            return;
        }
        marriageService().resolve(player.getUsername());
        Player updatedAccepter = player.withSpouse(proposerName.getValue());
        Player updatedProposer = proposer.withSpouse(player.getUsername().getValue());
        session.replacePlayer(updatedAccepter);
        updateTarget(updatedProposer);
        sendToUsername(proposerName, player.getUsername().getValue()
            + " accepts your proposal. You are now married!");
        // Server-wide wedding announcement (flavor only, no mechanical reward), excluding the couple
        // who already receive their own confirmations (AGENTS.md §3.3 — via MessageBroadcaster).
        context.messageBroadcaster().broadcastGlobal(
            new PlainTextMessage("Wedding bells ring out! " + proposerName.getValue() + " and "
                + player.getUsername().getValue() + " are now married. Congratulations!"),
            Set.of(proposerName, player.getUsername()));
        writeLineWithPrompt("You accept " + proposerName.getValue() + "'s proposal. You are now married!");
    }

    private void declineMarriageProposal(Player player) {
        Optional<Username> proposerMatch = marriageService().resolve(player.getUsername());
        if (proposerMatch.isEmpty()) {
            writeLineWithPrompt("You have no pending marriage proposal.");
            return;
        }
        Username proposerName = proposerMatch.get();
        sendToUsername(proposerName, player.getUsername().getValue() + " declined your marriage proposal.");
        writeLineWithPrompt("You decline " + proposerName.getValue() + "'s marriage proposal.");
    }

    private void divorce(Player player) {
        if (!player.isMarried()) {
            writeLineWithPrompt("You are not married.");
            return;
        }
        String spouseName = player.spouse();
        Username spouseUsername = Username.of(spouseName);
        Username self = player.getUsername();
        session.replacePlayer(player.withSpouse(null));
        Player onlineSpouse = findOnlinePlayer(spouseUsername);
        if (onlineSpouse != null) {
            if (bondPointsAt(onlineSpouse, self)) {
                updateTarget(onlineSpouse.withSpouse(null));
            }
            sendToUsername(spouseUsername, self.getValue()
                + " has divorced you. You are single once more.");
        } else {
            notifyOfflineSpouseOfDivorce(spouseUsername, self);
        }
        writeLineWithPrompt("You divorce " + spouseName + ". You are single once more.");
    }

    /** Returns whether {@code spouse}'s recorded bond points back at {@code partner}. */
    private static boolean bondPointsAt(Player spouse, Username partner) {
        String bonded = spouse.spouse();
        return bonded != null && Username.of(bonded).equals(partner);
    }

    /**
     * Clears an offline spouse's persisted bond and leaves them a mail so they learn of the divorce on
     * next login (issue #649). Best-effort: if their record no longer exists, or no longer points back
     * at the divorcing player, there is nothing to clear.
     */
    private void notifyOfflineSpouseOfDivorce(Username spouseUsername, Username initiator) {
        Player offlineSpouse = context.playerRepository().loadPlayer(spouseUsername).orElse(null);
        if (offlineSpouse == null || !bondPointsAt(offlineSpouse, initiator)) {
            return;
        }
        String initiatorName = initiator.getValue();
        Player cleared = offlineSpouse.withSpouse(null);
        long currentTick = context.tickClock().currentTick();
        MailResult mail = playerMailService.send(cleared, initiatorName, currentTick,
            initiatorName + " has divorced you. You are single once more.");
        saveOrWarn(mail.success() && mail.updatedPlayer() != null ? mail.updatedPlayer() : cleared);
    }

    @Override
    public void spouseTell(String message) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use SPOUSETELL.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        if (!player.isMarried()) {
            writeLineWithPrompt("You are not married. SPOUSETELL needs a spouse (see HELP MARRY).");
            return;
        }
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) {
            writeLineWithPrompt("Usage: SPOUSETELL <message>");
            return;
        }
        String spouseName = player.spouse();
        Username spouseUsername = Username.of(spouseName);
        Player onlineSpouse = findOnlinePlayer(spouseUsername);
        if (onlineSpouse == null) {
            writeLineWithPrompt("Your spouse " + spouseName + " is not online right now.");
            return;
        }
        // Spouse messages bypass IGNORE by design (issue #649): the escape hatch is DIVORCE.
        sendToUsername(spouseUsername, player.getUsername().getValue() + " tells you (spouse): " + trimmed);
        writeLineSafe("You tell " + spouseUsername.getValue() + " (spouse): " + trimmed);
        awayNotice(spouseUsername).ifPresent(this::writeLineSafe);
        sendPrompt();
    }

    @Override
    public String marriedTag(Username username) {
        Player online = findOnlinePlayer(username);
        if (online != null && online.isMarried()) {
            return " (Married to " + online.spouse() + ")";
        }
        return "";
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
        if (result.updatedSource() != null) {
            checkDeliveryPickupProgress(playerBeforeGet);
        }
        revealRoomIfNewlyLit(playerBeforeGet);
        sendPrompt();
    }

    /**
     * After one or more pickups, fires delivery-quest pickup-progress notifications for every item id
     * whose carried count increased between {@code playerBeforeGet} and the current session player.
     *
     * <p>Shared by single {@code GET} and bulk {@code GET ALL}/{@code GET ALL FROM} so a bulk pickup
     * still advances a delivery quest once per newly-gathered item, matching the single-item path.
     *
     * @param playerBeforeGet the player's state before the pickup(s) mutated their inventory
     */
    private void checkDeliveryPickupProgress(Player playerBeforeGet) {
        if (context.questRepository() == null) {
            return;
        }
        Player updatedPlayer = session.getPlayer();
        if (updatedPlayer == null) {
            return;
        }
        Map<ItemId, Long> oldCounts =
            playerBeforeGet.getInventory().stream()
                .collect(Collectors.groupingBy(Item::getId, Collectors.counting()));
        Map<ItemId, Long> newCounts =
            updatedPlayer.getInventory().stream()
                .collect(Collectors.groupingBy(Item::getId, Collectors.counting()));
        QuestDeliveryService deliverySvc = new QuestDeliveryService(context.questRepository());
        for (Map.Entry<ItemId, Long> entry : newCounts.entrySet()) {
            long before = oldCounts.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() > before) {
                deliverySvc.checkPickupProgress(updatedPlayer, entry.getKey())
                    .ifPresent(connection::writeLine);
            }
        }
    }

    @Override
    public void getAllItems() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to get items.");
            return;
        }
        cancelRestIfActive();
        Player playerBeforeGet = session.getPlayer();
        GameActionResult result = gameActionService.getAllItems(playerBeforeGet);
        deliverResult(result);
        if (result.updatedSource() != null) {
            checkDeliveryPickupProgress(playerBeforeGet);
        }
        revealRoomIfNewlyLit(playerBeforeGet);
        sendPrompt();
    }

    @Override
    public void getAllFromContainer(String containerInput) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to get items from containers.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.getAllFromContainer(session.getPlayer(), containerInput);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void dropAllItems() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to drop items.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.dropAllItems(session.getPlayer());
        deliverResult(result);
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
        for (Client c : clientPool.allConnections()) {
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
    public void searchRoom() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to search.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.searchForHiddenExits(session.getPlayer());
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void mount(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to mount.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.mount(session.getPlayer(), args);
        deliverResult(result);
        sendPrompt();
    }

    @Override
    public void dismount(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to dismount.");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = gameActionService.dismount(session.getPlayer());
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
        npcDeliverySvc.setLevelUpService(new LevelUpService(context.classLevelGainsResolver()));
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
    public void nameCompanion(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to name a companion.");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("You have no companions to name.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        String[] parts = normalized.split("\\s+", 2);
        if (normalized.isEmpty() || parts.length < 2 || parts[1].isBlank()) {
            writeLineWithPrompt("Usage: NAME <companion> <new name>");
            return;
        }
        cancelRestIfActive();
        GameActionResult result = context.mobRegistry()
            .nameCompanion(session.getPlayer(), parts[0], parts[1]);
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
    public void executeAssist(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to assist.");
            return;
        }
        cancelRestIfActive();
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There is no one to assist here.");
            return;
        }
        breakStealthIfActive();
        breakMountIfActive();
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        GameActionResult result = context.mobRegistry()
            .processPlayerAssist(player, args, roomIdOpt.get());
        deliverResult(result);
        sendPrompt();
    }

    /** Ability id of the Warrior TAUNT skill (data/skills/skill.taunt.json). */
    private static final AbilityId TAUNT_ABILITY_ID = AbilityId.of("skill.taunt");

    @Override
    public void executeTaunt(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to taunt.");
            return;
        }
        cancelRestIfActive();
        Player player = session.getPlayer();
        if (!player.getLearnedAbilities().contains(TAUNT_ABILITY_ID)) {
            writeLineWithPrompt("You do not know how to taunt.");
            return;
        }
        Ability taunt = abilityRegistry.findById(TAUNT_ABILITY_ID).orElse(null);
        if (taunt == null) {
            writeLineWithPrompt("You cannot taunt right now.");
            return;
        }
        // Level gate for a save-edited/legacy character that holds the skill below its level (#522).
        if (taunt.level() > player.getLevel()) {
            writeLineWithPrompt("You are not yet skilled enough to taunt (requires level "
                + taunt.level() + ").");
            return;
        }
        var cooldowns = session.getCooldownTracker();
        if (cooldowns.isOnCooldown(taunt.id())) {
            writeLineWithPrompt("You are still catching your breath ("
                + cooldowns.remainingTicks(taunt.id()) + " ticks remaining).");
            return;
        }
        if (context.mobRegistry() == null) {
            writeLineWithPrompt("There is nothing here to taunt.");
            return;
        }
        breakStealthIfActive();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        // The mount break is folded into processPlayerTaunt's returned source so it only sticks on a
        // successful taunt (and a failed taunt never spuriously dismounts) — mirroring the ATTACK path.
        GameActionResult result = context.mobRegistry()
            .processPlayerTaunt(player, args, taunt, roomIdOpt.get());
        if (result.updatedSource() != null && taunt.cooldown().ticks() > 0) {
            cooldowns.startCooldown(taunt.id(), taunt.cooldown().ticks());
        }
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
        if (movementCostService.isExhausted(player)) {
            writeLineWithPrompt(MovementCostService.EXHAUSTED_MESSAGE);
            return;
        }
        GameActionResult result = gameActionService.recall(player);
        deliverResult(result);
        if (result.metadata().containsKey("recalled")) {
            spendMovePoints(player);
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
        if (found.isTwoHanded()) {
            connection.writeLine("Two-handed: requires both hands; cannot be used with an off-hand item.");
        }
        if (found.getAttributes() != null && !found.getAttributes().getStats().isEmpty()) {
            StringBuilder sb = new StringBuilder("Stats:");
            found.getAttributes().getStats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(" ").append(e.getKey()).append(" +").append(e.getValue()));
            connection.writeLine(sb.toString());
        }
        String damageRange = weaponDamageRange(found);
        if (damageRange != null) {
            connection.writeLine("Damage: " + damageRange);
        }
        writeAffixLines(found);
        sendPrompt();
    }

    /**
     * Writes the enchantment lines for an identified item: the label of each attached affix and the
     * item's effective stats (base attributes plus affix bonuses). Silent when the item bears no
     * affixes or the affix data cannot be read.
     */
    private void writeAffixLines(Item item) {
        if (context.itemAffixService() == null || item.getAffixes().isEmpty()) {
            return;
        }
        try {
            List<ItemAffix> affixes = context.itemAffixService().resolve(item);
            if (!affixes.isEmpty()) {
                StringBuilder labels = new StringBuilder("Enchantments:");
                for (ItemAffix affix : affixes) {
                    labels.append(' ').append(affix.label());
                }
                connection.writeLine(labels.toString());
            }
            Map<String, Integer> effective = context.itemAffixService().effectiveStats(item);
            if (!effective.isEmpty()) {
                StringBuilder sb = new StringBuilder("Effective stats:");
                effective.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(' ').append(e.getKey()).append(" +").append(e.getValue()));
                connection.writeLine(sb.toString());
            }
        } catch (RepositoryException e) {
            // Affix data unavailable; base stats already shown, so skip the enchantment lines.
        }
    }

    @Override
    public void compareItem(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to compare items.");
            return;
        }
        if (args == null || args.isBlank()) {
            writeLineWithPrompt("Compare what?");
            return;
        }
        // Search inventory first, then room items — mirrors examineItem's resolution order.
        Item candidate = matchItemByName(player.getInventory(), args);
        if (candidate == null) {
            RoomService.LookResult look = roomService.look(player.getUsername(), session.getTextStyler());
            if (look.room() != null) {
                candidate = matchItemByName(look.room().getItems(), args);
            }
        }
        if (candidate == null) {
            writeLineWithPrompt("You don't see '" + args.trim() + "' here.");
            return;
        }
        if (!candidate.isIdentified()) {
            connection.writeLine(session.getTextStyler().rarity(
                candidate.presentationName(), candidate.presentationRarity()));
            connection.writeLine("You cannot make out its true nature. Identify it to reveal its properties.");
            sendPrompt();
            return;
        }
        EquipmentSlot slot = candidate.getEquipSlot();
        if (slot == null) {
            writeLineWithPrompt(candidate.getName() + " cannot be equipped, so there is nothing to compare.");
            return;
        }
        ItemId equippedId = player.getEquipment().equipped(slot);
        Optional<Item> equipped = equippedId == null
            ? Optional.empty()
            : player.getInventory().stream()
                .filter(i -> i.getId().equals(equippedId))
                .findFirst();
        for (String line : ItemComparison.format(
                candidate, equipped, this::resolveEffectiveStats, session.getTextStyler())) {
            connection.writeLine(line);
        }
        // Weapon damage lives on the item's attack definition, not its stat map, so surface it
        // explicitly for weapons — including dual-wield off-hand daggers — so players can judge a swap.
        String candidateDamage = weaponDamageRange(candidate);
        if (candidateDamage != null) {
            String equippedDamage = equipped.map(this::weaponDamageRange).orElse(null);
            connection.writeLine(equippedDamage != null
                ? "Damage: " + equippedDamage + " -> " + candidateDamage
                : "Damage: " + candidateDamage);
        }
        sendPrompt();
    }

    /**
     * Resolves the {@code min-max} base damage of a weapon item's attack, or {@code null} when the
     * item is not a weapon (no {@code attackRef}) or its attack definition cannot be loaded. Used by
     * EXAMINE and COMPARE so players can evaluate weapons — including dual-wield off-hand weapons —
     * before equipping them.
     *
     * @param item the item whose weapon damage to resolve
     * @return the {@code "min-max"} damage string, or {@code null} when the item is not a weapon
     */
    private String weaponDamageRange(Item item) {
        if (item == null || item.getAttackRef() == null) {
            return null;
        }
        try {
            return context.attackRepository().findById(item.getAttackRef())
                .map(attack -> attack.minDamage() + "-" + attack.maxDamage())
                .orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * Resolves an item's effective stats (base attributes plus affix bonuses) through
     * {@link io.taanielo.jmud.core.world.ItemAffixService}, falling back to the item's plain base
     * attributes when no affix service is configured or the affix data cannot be read. Never throws,
     * so the pure {@link ItemComparison} formatter stays free of checked-exception handling.
     *
     * @param item the item whose effective stats to resolve
     * @return the effective stats keyed by stat name, never null
     */
    private Map<String, Integer> resolveEffectiveStats(Item item) {
        if (context.itemAffixService() != null) {
            try {
                return context.itemAffixService().effectiveStats(item);
            } catch (RepositoryException e) {
                // Affix data unavailable; fall back to base attributes below.
            }
        }
        return item.getAttributes().getStats();
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

    @Override
    public void repairAllItems() {
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
            context.itemDurabilityService().repairAll(player);
        if (outcome.success()) {
            session.replacePlayer(outcome.updatedPlayer());
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void craft(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to craft items.");
            return;
        }
        if (context.craftingService() == null) {
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
            writeLineWithPrompt("There is no blacksmith here to craft with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.craftingService().formatRecipes(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        CraftOutcome outcome = context.craftingService().craft(player, args);
        Player crafted = outcome.updatedPlayer();
        if (outcome.success() && crafted != null) {
            session.replacePlayer(crafted);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void salvage(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to salvage items.");
            return;
        }
        if (context.salvageService() == null) {
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
            writeLineWithPrompt("There is no blacksmith here to salvage with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.salvageService().preview(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        SalvageOutcome outcome = context.salvageService().salvage(player, args);
        Player salvaged = outcome.updatedPlayer();
        if (outcome.success() && salvaged != null) {
            session.replacePlayer(salvaged);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void brew(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to brew potions.");
            return;
        }
        if (context.alchemyService() == null) {
            writeLineWithPrompt("There is no alchemist here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!isAlchemistPresent(roomIdOpt.get())) {
            writeLineWithPrompt("There is no alchemist here to brew with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.alchemyService().formatRecipes(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        CraftOutcome outcome = context.alchemyService().craft(player, args);
        Player brewed = outcome.updatedPlayer();
        if (outcome.success() && brewed != null) {
            session.replacePlayer(brewed);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void cook(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to cook meals.");
            return;
        }
        if (context.cookingService() == null) {
            writeLineWithPrompt("There is no cook here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!isCookPresent(roomIdOpt.get())) {
            writeLineWithPrompt("There is no cook here to cook with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.cookingService().formatRecipes(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        CraftOutcome outcome = context.cookingService().craft(player, args);
        Player cooked = outcome.updatedPlayer();
        if (outcome.success() && cooked != null) {
            session.replacePlayer(cooked);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void enchant(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to enchant items.");
            return;
        }
        if (context.enchantingService() == null) {
            writeLineWithPrompt("There is no enchanter here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!isEnchanterPresent(roomIdOpt.get())) {
            writeLineWithPrompt("There is no enchanter here to enchant with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.enchantingService().formatRecipes(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        EnchantOutcome outcome = context.enchantingService().enchant(player, args);
        Player enchanted = outcome.updatedPlayer();
        if (outcome.success() && enchanted != null) {
            session.replacePlayer(enchanted);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void tan(String args) {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to tan leather.");
            return;
        }
        if (context.leatherworkingService() == null) {
            writeLineWithPrompt("There is no leatherworker here.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        if (!isLeatherworkerPresent(roomIdOpt.get())) {
            writeLineWithPrompt("There is no leatherworker here to tan with.");
            return;
        }
        if (args == null || args.isBlank()) {
            for (String line : context.leatherworkingService().formatRecipes(player)) {
                connection.writeLine(line);
            }
            sendPrompt();
            return;
        }
        CraftOutcome outcome = context.leatherworkingService().craft(player, args);
        Player tanned = outcome.updatedPlayer();
        if (outcome.success() && tanned != null) {
            session.replacePlayer(tanned);
        }
        writeLineWithPrompt(outcome.message());
    }

    @Override
    public void gather() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to gather resources.");
            return;
        }
        if (context.resourceGatheringService() == null) {
            writeLineWithPrompt("There is nothing here to gather.");
            return;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return;
        }
        GatherOutcome outcome = context.resourceGatheringService().gather(player, roomIdOpt.get());
        Player updated = outcome.updatedPlayer();
        if (outcome.success() && updated != null) {
            session.replacePlayer(updated);
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

    /** Returns whether an alchemist NPC (tagged {@code alchemist}) is alive in the given room. */
    private boolean isAlchemistPresent(RoomId roomId) {
        if (context.mobRegistry() == null) {
            return false;
        }
        return context.mobRegistry().getMobsInRoom(roomId).stream()
            .anyMatch(mob -> mob.template().hasTag("alchemist"));
    }

    /** Returns whether a cook NPC (tagged {@code cook}) is alive in the given room. */
    private boolean isCookPresent(RoomId roomId) {
        if (context.mobRegistry() == null) {
            return false;
        }
        return context.mobRegistry().getMobsInRoom(roomId).stream()
            .anyMatch(mob -> mob.template().hasTag("cook"));
    }

    /** Returns whether an enchanter NPC (tagged {@code enchanter}) is alive in the given room. */
    private boolean isEnchanterPresent(RoomId roomId) {
        if (context.mobRegistry() == null) {
            return false;
        }
        return context.mobRegistry().getMobsInRoom(roomId).stream()
            .anyMatch(mob -> mob.template().hasTag("enchanter"));
    }

    /** Returns whether a leatherworker NPC (tagged {@code leatherworker}) is alive in the given room. */
    private boolean isLeatherworkerPresent(RoomId roomId) {
        if (context.mobRegistry() == null) {
            return false;
        }
        return context.mobRegistry().getMobsInRoom(roomId).stream()
            .anyMatch(mob -> mob.template().hasTag("leatherworker"));
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
            case "LOG" -> handleQuestLog(questRepo);
            case "ACCEPT" -> handleQuestAccept(questRepo, subArgs);
            case "STATUS" -> handleQuestStatus(questRepo);
            case "TRACK" -> handleQuestTrack(questRepo);
            case "COMPLETE" -> handleQuestComplete(questRepo);
            case "DELIVER" -> handleQuestDeliver(questRepo);
            case "ABANDON" -> handleQuestAbandon();
            default -> writeLineWithPrompt(
                "Usage: QUEST [LIST|LOG|ACCEPT <id>|STATUS|TRACK|COMPLETE|DELIVER|ABANDON]");
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
            case "ABANDON" -> handleDailyQuestAbandon();
            default -> writeLineWithPrompt(
                "Usage: DAILY_QUEST [LIST|ACCEPT <pool>|STATUS|COMPLETE|ABANDON]");
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
                "  %-14s %-24s %s",
                t.dailyPoolId(), t.name(), formatQuestReward(t)));
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
        if (player.getActiveDailyQuest() != null) {
            writeLineWithPrompt("You already hold a daily quest. DAILY_QUEST ABANDON it first.");
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
        session.replacePlayer(player.withActiveDailyQuest(activeQuest));
        writeLineWithPrompt(
            "Daily quest accepted: " + template.name() + ". "
                + "Kill " + template.requiredKills() + " " + template.targetMobId()
                + "(s), then use DAILY_QUEST COMPLETE to claim your reward. Good luck.");
    }

    private void handleDailyQuestStatus(DailyQuestService dailyQuestService) {
        Player player = session.getPlayer();
        ActiveQuest active = player.getActiveDailyQuest();
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
        ActiveQuest active = player.getActiveDailyQuest();
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
            dropQuestRewardOverflow(rewarded, result.droppedItems());
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

    private void handleDailyQuestAbandon() {
        Player player = session.getPlayer();
        if (player.getActiveDailyQuest() == null) {
            writeLineWithPrompt("You have no active daily quest to abandon.");
            return;
        }
        session.replacePlayer(player.withActiveDailyQuest(null));
        writeLineWithPrompt("Daily quest abandoned. No reward will be granted.");
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
    public void executeTrade(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to trade.");
            return;
        }
        TradeService tradeService = context.tradeService();
        if (tradeService == null) {
            writeLineWithPrompt("The trade system is not available.");
            return;
        }
        String[] parts = SocketCommandParsing.splitInput(args);
        String sub = parts[0];
        String subArgs = parts[1];
        switch (sub) {
            case "ACCEPT" -> handleTradeAccept(player, tradeService);
            case "DECLINE" -> handleTradeDecline(player, tradeService);
            case "ADD" -> handleTradeAdd(player, tradeService, subArgs);
            case "REMOVE" -> handleTradeRemove(player, tradeService, subArgs);
            case "CONFIRM" -> handleTradeConfirm(player, tradeService);
            case "CANCEL" -> handleTradeCancel(player, tradeService);
            case "STATUS", "" -> handleTradeStatus(player, tradeService);
            default -> {
                String rawName = (args == null ? "" : args.trim()).split("\\s+", 2)[0];
                handleTradePropose(player, tradeService, rawName);
            }
        }
    }

    private void handleTradePropose(Player player, TradeService tradeService, String targetName) {
        if (targetName.isBlank()) {
            writeLineWithPrompt("Trade with whom? Usage: TRADE <player>");
            return;
        }
        Username target = onlinePlayerNames().stream()
            .filter(u -> u.equals(Username.of(targetName)))
            .findFirst()
            .orElse(Username.of(targetName));
        TradeService.TradeResult result = tradeService.propose(player.getUsername(), target);
        writeLineWithPrompt(result.message());
        if (result.success()) {
            sendToUsername(target, player.getUsername().getValue()
                + " wants to trade with you. Type TRADE ACCEPT or TRADE DECLINE.");
        }
    }

    private void handleTradeAccept(Player player, TradeService tradeService) {
        TradeService.TradeResult result = tradeService.accept(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            tradeService.session(player.getUsername()).ifPresent(sessionState ->
                sendToUsername(sessionState.other(player.getUsername()),
                    player.getUsername().getValue()
                        + " accepted your trade. Stage your offer with TRADE ADD <item> and TRADE CONFIRM."));
        }
    }

    private void handleTradeDecline(Player player, TradeService tradeService) {
        Username other = tradeService.session(player.getUsername())
            .map(sessionState -> sessionState.other(player.getUsername()))
            .orElse(null);
        TradeService.TradeResult result = tradeService.decline(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success() && other != null) {
            sendToUsername(other, player.getUsername().getValue() + " declined the trade.");
        }
    }

    private void handleTradeCancel(Player player, TradeService tradeService) {
        Username other = tradeService.session(player.getUsername())
            .map(sessionState -> sessionState.other(player.getUsername()))
            .orElse(null);
        TradeService.TradeResult result = tradeService.cancel(player.getUsername());
        writeLineWithPrompt(result.message());
        if (result.success() && other != null) {
            sendToUsername(other, player.getUsername().getValue() + " cancelled the trade.");
        }
    }

    private void handleTradeAdd(Player player, TradeService tradeService, String subArgs) {
        String[] addParts = SocketCommandParsing.splitInput(subArgs);
        if ("GOLD".equals(addParts[0])) {
            handleTradeAddGold(player, tradeService, addParts[1]);
            return;
        }
        if (subArgs.isBlank()) {
            writeLineWithPrompt("Add what? Usage: TRADE ADD <item> | TRADE ADD GOLD <amount>");
            return;
        }
        TradeSession sessionState = tradeService.session(player.getUsername()).orElse(null);
        if (sessionState == null || !sessionState.isAccepted()) {
            writeLineWithPrompt("You are not in an active trade.");
            return;
        }
        Item item = matchItemByName(player.getInventory(), subArgs);
        if (item == null) {
            writeLineWithPrompt("You aren't carrying that.");
            return;
        }
        boolean hadConfirm = anyConfirmed(sessionState);
        TradeService.TradeResult result = tradeService.addItem(player.getUsername(), item);
        reportOfferChange(player, sessionState.other(player.getUsername()), result,
            "adds " + item.getName() + " to", hadConfirm);
    }

    private void handleTradeAddGold(Player player, TradeService tradeService, String amountInput) {
        TradeSession sessionState = tradeService.session(player.getUsername()).orElse(null);
        if (sessionState == null || !sessionState.isAccepted()) {
            writeLineWithPrompt("You are not in an active trade.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(amountInput.trim());
        } catch (NumberFormatException e) {
            writeLineWithPrompt("Add how much gold? Usage: TRADE ADD GOLD <amount>");
            return;
        }
        if (amount <= 0) {
            writeLineWithPrompt("Add how much gold? Usage: TRADE ADD GOLD <amount>");
            return;
        }
        int alreadyOffered = sessionState.goldOf(player.getUsername());
        if (alreadyOffered + amount > player.getGold()) {
            writeLineWithPrompt("You do not have that much gold to offer.");
            return;
        }
        boolean hadConfirm = anyConfirmed(sessionState);
        TradeService.TradeResult result = tradeService.addGold(player.getUsername(), amount);
        reportOfferChange(player, sessionState.other(player.getUsername()), result,
            "adds " + amount + " gold to", hadConfirm);
    }

    private void handleTradeRemove(Player player, TradeService tradeService, String subArgs) {
        TradeSession sessionState = tradeService.session(player.getUsername()).orElse(null);
        if (sessionState == null || !sessionState.isAccepted()) {
            writeLineWithPrompt("You are not in an active trade.");
            return;
        }
        boolean hadConfirm = anyConfirmed(sessionState);
        TradeService.TradeResult result = tradeService.removeItem(player.getUsername(), subArgs);
        reportOfferChange(player, sessionState.other(player.getUsername()), result,
            "changes their side of", hadConfirm);
    }

    private void handleTradeConfirm(Player player, TradeService tradeService) {
        TradeSession sessionState = tradeService.session(player.getUsername()).orElse(null);
        if (sessionState == null || !sessionState.isAccepted()) {
            writeLineWithPrompt("You are not in an active trade.");
            return;
        }
        TradeService.TradeResult result = tradeService.confirm(player.getUsername());
        if (!result.success()) {
            writeLineWithPrompt(result.message());
            return;
        }
        connection.writeLine(result.message());
        Username otherUser = sessionState.other(player.getUsername());
        sendToUsername(otherUser, player.getUsername().getValue() + " has confirmed the trade.");
        if (!sessionState.bothConfirmed()) {
            sendPrompt();
            return;
        }
        executeConfirmedTrade(player, tradeService, sessionState, otherUser);
    }

    private void executeConfirmedTrade(
        Player player,
        TradeService tradeService,
        TradeSession sessionState,
        Username otherUser
    ) {
        Player proposerPlayer = findOnlinePlayer(sessionState.proposer());
        Player targetPlayer = findOnlinePlayer(sessionState.target());
        if (proposerPlayer == null || targetPlayer == null) {
            tradeService.cancel(player.getUsername());
            writeLineWithPrompt("The trade was cancelled; the other party is no longer available.");
            sendToUsername(otherUser, "The trade was cancelled; the other party is no longer available.");
            return;
        }
        TradeExecutionService.TradeExecutionResult exec = tradeExecutionService.execute(
            proposerPlayer, targetPlayer, sessionState, encumbranceService::isOverburdened);
        if (!exec.success()) {
            tradeService.resetConfirms(player.getUsername());
            String message = exec.error() + " Both confirmations have been reset.";
            writeLineWithPrompt(message);
            sendToUsername(otherUser, message);
            return;
        }
        boolean selfIsProposer = player.getUsername().equals(sessionState.proposer());
        Player updatedSelf = selfIsProposer ? exec.updatedProposer() : exec.updatedTarget();
        Player updatedOther = selfIsProposer ? exec.updatedTarget() : exec.updatedProposer();
        String selfSummary = selfIsProposer ? exec.proposerSummary() : exec.targetSummary();
        String otherSummary = selfIsProposer ? exec.targetSummary() : exec.proposerSummary();
        tradeService.complete(player.getUsername());
        deliverResult(new GameActionResult(updatedSelf, updatedOther, List.of(
            GameMessage.toSource(selfSummary),
            GameMessage.toPlayer(otherUser, otherSummary))));
        sendPrompt();
    }

    private void handleTradeStatus(Player player, TradeService tradeService) {
        TradeSession sessionState = tradeService.session(player.getUsername()).orElse(null);
        if (sessionState == null) {
            writeLineWithPrompt("You are not in a trade.");
            return;
        }
        Username self = player.getUsername();
        Username other = sessionState.other(self);
        if (!sessionState.isAccepted()) {
            writeLineWithPrompt(sessionState.proposer().equals(self)
                ? "Awaiting " + other.getValue() + "'s response to your trade proposal."
                : other.getValue() + " has proposed a trade. Type TRADE ACCEPT or TRADE DECLINE.");
            return;
        }
        connection.writeLine("Trade with " + other.getValue() + ":");
        connection.writeLine("  Your offer:   " + describeOffer(sessionState, self)
            + (sessionState.hasConfirmed(self) ? "  [confirmed]" : ""));
        connection.writeLine("  Their offer:  " + describeOffer(sessionState, other)
            + (sessionState.hasConfirmed(other) ? "  [confirmed]" : ""));
        sendPrompt();
    }

    private String describeOffer(TradeSession sessionState, Username who) {
        List<String> parts = new ArrayList<>();
        for (Item item : sessionState.itemsOf(who)) {
            parts.add(item.getName());
        }
        int gold = sessionState.goldOf(who);
        if (gold > 0) {
            parts.add(gold + " gold");
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    private void reportOfferChange(
        Player player,
        Username other,
        TradeService.TradeResult result,
        String verbPhrase,
        boolean hadConfirm
    ) {
        if (!result.success()) {
            writeLineWithPrompt(result.message());
            return;
        }
        String selfMessage = hadConfirm
            ? result.message() + " Both confirmations have been reset."
            : result.message();
        writeLineWithPrompt(selfMessage);
        sendToUsername(other, player.getUsername().getValue() + " " + verbPhrase + " the trade.");
        if (hadConfirm) {
            sendToUsername(other, "The offer changed; both confirmations have been reset.");
        }
    }

    private static boolean anyConfirmed(TradeSession sessionState) {
        return sessionState.hasConfirmed(sessionState.proposer())
            || sessionState.hasConfirmed(sessionState.target());
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
            case "LOOT" -> handlePartyLoot(player, partyService, subArgs);
            case "" -> handlePartyStatus(player, partyService);
            default -> writeLineWithPrompt(
                "Usage: PARTY [FORM|INVITE <player>|ACCEPT|DECLINE|LEAVE|DISBAND|LOOT <free|round-robin|roll>]");
        }
    }

    @Override
    public void executeFollow(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to follow.");
            return;
        }
        PartyService partyService = context.partyService();
        if (partyService == null) {
            writeLineWithPrompt("The party system is not available.");
            return;
        }
        String target = args == null ? "" : args.trim();
        if (target.isEmpty()) {
            Optional<Username> leader = partyService.leaderOf(player.getUsername());
            if (leader.isPresent()) {
                writeLineWithPrompt("You are following " + leader.get().getValue()
                    + ". Type FOLLOW OFF to stop.");
            } else {
                writeLineWithPrompt("You are not following anyone. Usage: FOLLOW <player> | FOLLOW OFF");
            }
            return;
        }
        if (target.equalsIgnoreCase("OFF") || target.equalsIgnoreCase("STOP")
            || target.equalsIgnoreCase("NONE")) {
            PartyService.PartyResult result = partyService.unfollow(player.getUsername());
            writeLineWithPrompt(result.message());
            return;
        }
        Username leaderName = Username.of(target);
        if (leaderName.equals(player.getUsername())) {
            writeLineWithPrompt("You cannot follow yourself.");
            return;
        }
        boolean leaderOnline = findOnlinePlayer(leaderName) != null;
        PartyService.PartyResult result = partyService.follow(
            player.getUsername(), leaderName, leaderOnline);
        writeLineWithPrompt(result.message());
        if (result.success()) {
            sendToUsername(leaderName, player.getUsername().getValue() + " starts following you.");
        }
    }

    @Override
    public void executeGuild(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to use guild commands.");
            return;
        }
        GuildService guildService = context.guildService();
        if (guildService == null) {
            writeLineWithPrompt("The guild system is not available.");
            return;
        }
        reconcileGuildMembership(player, guildService);
        String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
        String sub = parts[0];
        String subArgs = parts[1];
        switch (sub) {
            case "CREATE" -> handleGuildCreate(guildService, subArgs);
            case "INVITE" -> handleGuildInvite(guildService, subArgs);
            case "ACCEPT" -> handleGuildAccept(guildService);
            case "DECLINE" -> handleGuildDecline(guildService);
            case "LEAVE" -> handleGuildLeave(guildService);
            case "KICK" -> handleGuildKick(guildService, subArgs);
            case "PROMOTE" -> handleGuildPromote(guildService, subArgs);
            case "DEMOTE" -> handleGuildDemote(guildService, subArgs);
            case "DISBAND" -> handleGuildDisband(guildService, subArgs);
            case "WHO" -> handleGuildWho(guildService);
            case "BANK" -> handleGuildBank(guildService);
            case "DEPOSIT" -> handleGuildDeposit(guildService, subArgs);
            case "WITHDRAW" -> handleGuildWithdraw(guildService, subArgs);
            case "VAULT" -> handleGuildVault(guildService);
            case "STORE" -> handleGuildStore(guildService, subArgs);
            case "CLAIM" -> handleGuildClaim(guildService, subArgs);
            case "" -> handleGuildStatus(guildService);
            default -> guildChat(args);
        }
    }

    @Override
    public void guildChat(String message) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to use guild chat.");
            return;
        }
        GuildService guildService = context.guildService();
        if (guildService == null) {
            writeLineWithPrompt("The guild system is not available.");
            return;
        }
        reconcileGuildMembership(player, guildService);
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        if (message == null || message.isBlank()) {
            writeLineWithPrompt("Say what to your guild?");
            return;
        }
        String trimmed = message.trim();
        Username sender = player.getUsername();
        List<Username> online = onlinePlayerNames();
        String line = "[Guild] " + sender.getValue() + ": " + trimmed;
        for (GuildMember member : guild.members()) {
            if (!member.username().equals(sender) && online.contains(member.username())) {
                sendToUsername(member.username(), line);
            }
        }
        connection.writeLine("[Guild] You: " + trimmed);
        sendPrompt();
    }

    @Override
    public String guildTag(Username username) {
        GuildService guildService = context.guildService();
        if (guildService == null || username == null) {
            return "";
        }
        return guildService.guildTag(username).map(name -> " [" + name + "]").orElse("");
    }

    @Override
    public String activeTitle(Username username) {
        if (username == null) {
            return "";
        }
        Player player = findOnlinePlayer(username);
        if (player == null) {
            return "";
        }
        String active = player.titles().active();
        return active == null ? "" : " the " + active;
    }

    private void handleGuildCreate(GuildService guildService, String name) {
        Player player = session.getPlayer();
        if (name == null || name.isBlank()) {
            writeLineWithPrompt("Found a guild named what? Usage: GUILD CREATE <name>");
            return;
        }
        if (player.getGold() < GuildService.CREATION_COST_GOLD) {
            writeLineWithPrompt("Founding a guild costs " + GuildService.CREATION_COST_GOLD
                + " gold. You only have " + player.getGold() + ".");
            return;
        }
        GuildResult result = guildService.create(player.getUsername(), name);
        if (result.success() && result.guild() != null) {
            Player updated = player
                .addGold(-GuildService.CREATION_COST_GOLD)
                .withGuildMembership(PlayerGuildMembership.of(result.guild().id()));
            session.replacePlayer(updated);
            saveOrWarn(updated);
            writeLineWithPrompt(result.message() + " (" + GuildService.CREATION_COST_GOLD + " gold spent.)");
        } else {
            writeLineWithPrompt(result.message());
        }
    }

    private void handleGuildInvite(GuildService guildService, String targetName) {
        Player player = session.getPlayer();
        if (targetName == null || targetName.isBlank()) {
            writeLineWithPrompt("Invite whom? Usage: GUILD INVITE <player>");
            return;
        }
        Username invitee = Username.of(targetName.trim());
        boolean inviteeOnline = onlinePlayerNames().contains(invitee);
        GuildResult result = guildService.invite(player.getUsername(), invitee, inviteeOnline);
        writeLineWithPrompt(result.message());
        if (result.success() && result.guild() != null) {
            sendToUsername(invitee, player.getUsername().getValue()
                + " invites you to join " + result.guild().name()
                + ". Type GUILD ACCEPT or GUILD DECLINE.");
        }
    }

    private void handleGuildAccept(GuildService guildService) {
        Player player = session.getPlayer();
        GuildResult result = guildService.accept(player.getUsername());
        if (result.success() && result.guild() != null) {
            setGuildMembership(player.getUsername(), PlayerGuildMembership.of(result.guild().id()));
            Username joiner = player.getUsername();
            List<Username> online = onlinePlayerNames();
            for (GuildMember member : result.guild().members()) {
                if (!member.username().equals(joiner) && online.contains(member.username())) {
                    sendToUsername(member.username(), joiner.getValue() + " has joined the guild.");
                }
            }
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildDecline(GuildService guildService) {
        Player player = session.getPlayer();
        GuildResult result = guildService.decline(player.getUsername());
        writeLineWithPrompt(result.message());
    }

    private void handleGuildLeave(GuildService guildService) {
        Player player = session.getPlayer();
        Username leaver = player.getUsername();
        GuildResult result = guildService.leave(leaver);
        if (result.success()) {
            setGuildMembership(leaver, PlayerGuildMembership.none());
            if (result.guild() != null) {
                if (result.guild().memberCount() == 0) {
                    // Departure emptied the guild, so it was auto-disbanded. Mirror GUILD DISBAND and
                    // hand the shared vault and pooled treasury back to the leaver so nothing is lost.
                    String treasurySummary = refundTreasuryToLeaver(result.guild());
                    if (treasurySummary != null) {
                        connection.writeLine(treasurySummary);
                    }
                    String vaultSummary = returnVaultToLeader(result.guild());
                    if (vaultSummary != null) {
                        connection.writeLine(vaultSummary);
                    }
                } else {
                    List<Username> online = onlinePlayerNames();
                    for (GuildMember member : result.guild().members()) {
                        if (online.contains(member.username())) {
                            sendToUsername(member.username(), leaver.getValue() + " has left the guild.");
                        }
                    }
                }
            }
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildKick(GuildService guildService, String targetName) {
        Player player = session.getPlayer();
        if (targetName == null || targetName.isBlank()) {
            writeLineWithPrompt("Kick whom? Usage: GUILD KICK <player>");
            return;
        }
        Username target = Username.of(targetName.trim());
        GuildResult result = guildService.kick(player.getUsername(), target);
        if (result.success()) {
            setGuildMembership(target, PlayerGuildMembership.none());
            sendToUsername(target, "You have been removed from the guild.");
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildPromote(GuildService guildService, String targetName) {
        Player player = session.getPlayer();
        if (targetName == null || targetName.isBlank()) {
            writeLineWithPrompt("Promote whom? Usage: GUILD PROMOTE <player>");
            return;
        }
        Username target = Username.of(targetName.trim());
        GuildResult result = guildService.promote(player.getUsername(), target);
        if (result.success()) {
            sendToUsername(target, "You have been promoted to officer.");
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildDemote(GuildService guildService, String targetName) {
        Player player = session.getPlayer();
        if (targetName == null || targetName.isBlank()) {
            writeLineWithPrompt("Demote whom? Usage: GUILD DEMOTE <player>");
            return;
        }
        Username target = Username.of(targetName.trim());
        GuildResult result = guildService.demote(player.getUsername(), target);
        if (result.success()) {
            sendToUsername(target, "You have been demoted to member.");
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildDisband(GuildService guildService, String subArgs) {
        Player player = session.getPlayer();
        if (!"CONFIRM".equals(subArgs == null ? "" : subArgs.trim().toUpperCase(Locale.ROOT))) {
            Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
            if (guild == null) {
                writeLineWithPrompt("You are not in a guild.");
                return;
            }
            if (!guild.isLeader(player.getUsername())) {
                writeLineWithPrompt("Only the guild leader can disband the guild.");
                return;
            }
            writeLineWithPrompt("This permanently disbands " + guild.name()
                + ". Type GUILD DISBAND CONFIRM to proceed.");
            return;
        }
        GuildResult result = guildService.disband(player.getUsername());
        if (result.success() && result.guild() != null) {
            List<Username> online = onlinePlayerNames();
            for (GuildMember member : result.guild().members()) {
                setGuildMembership(member.username(), PlayerGuildMembership.none());
                if (!member.username().equals(player.getUsername()) && online.contains(member.username())) {
                    sendToUsername(member.username(),
                        result.guild().name() + " has been disbanded by its leader.");
                }
            }
            String vaultSummary = returnVaultToLeader(result.guild());
            if (vaultSummary != null) {
                connection.writeLine(vaultSummary);
            }
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildWho(GuildService guildService) {
        Player player = session.getPlayer();
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        List<Username> online = onlinePlayerNames();
        connection.writeLine(guild.name() + " (" + guild.memberCount() + " member"
            + (guild.memberCount() == 1 ? "" : "s") + "):");
        guild.members().stream()
            .sorted((a, b) -> Integer.compare(a.joinOrder(), b.joinOrder()))
            .forEach(member -> {
                String status = online.contains(member.username()) ? "online" : "offline";
                String rankTag = " [" + member.rank().displayName() + "]";
                connection.writeLine("  " + member.username().getValue() + rankTag + " - " + status);
            });
        connection.writeLine("Treasury: " + guild.treasuryGold() + " gold.");
        writeGuildLevelLines(guild);
        sendPrompt();
    }

    /**
     * Writes the guild's level and lifetime-deposit progress lines (used by the roster and vault views).
     * Announces the guild's current level, its level-scaled vault capacity, its lifetime deposited total
     * and either the gold needed for the next level or that it has reached the max level.
     */
    private void writeGuildLevelLines(Guild guild) {
        GuildLevel level = guild.level();
        connection.writeLine("Guild level: " + level.rank() + "/" + GuildLevel.FIVE.rank()
            + " (vault capacity " + GuildService.vaultCapacity(guild) + " slots).");
        OptionalInt nextThreshold = guild.nextLevelThreshold();
        if (nextThreshold.isPresent()) {
            int remaining = nextThreshold.getAsInt() - guild.lifetimeDepositedGold();
            connection.writeLine("Lifetime deposited: " + guild.lifetimeDepositedGold()
                + " gold. Next level at " + nextThreshold.getAsInt() + " gold ("
                + remaining + " more to go).");
        } else {
            connection.writeLine("Lifetime deposited: " + guild.lifetimeDepositedGold()
                + " gold. Max level reached.");
        }
    }

    private void handleGuildBank(GuildService guildService) {
        Player player = session.getPlayer();
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        writeLineWithPrompt(guild.name() + " treasury: " + guild.treasuryGold() + " gold.");
    }

    private void handleGuildDeposit(GuildService guildService, String args) {
        Player player = session.getPlayer();
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        Integer amount = parseGuildAmount(args, "deposit");
        if (amount == null) {
            return;
        }
        if (player.getGold() < amount) {
            writeLineWithPrompt("You only have " + player.getGold() + " gold to deposit.");
            return;
        }
        GuildLevel levelBefore = guild.level();
        GuildResult result = guildService.deposit(player.getUsername(), amount);
        if (result.success() && result.guild() != null) {
            Player updated = player.addGold(-amount);
            session.replacePlayer(updated);
            saveOrWarn(updated);
            broadcastToGuild(result.guild(), player.getUsername(),
                player.getUsername().getValue() + " deposited " + amount + " gold to the guild bank.");
            GuildLevel levelAfter = result.guild().level();
            if (levelAfter.rank() > levelBefore.rank()) {
                connection.writeLine(result.message());
                writeLineWithPrompt("Your deposit raises " + result.guild().name() + " to guild level "
                    + levelAfter.rank() + "! Shared vault capacity is now "
                    + GuildService.vaultCapacity(result.guild()) + " slots.");
                broadcastToGuild(result.guild(), player.getUsername(),
                    result.guild().name() + " has reached guild level " + levelAfter.rank()
                        + "! The shared vault now holds up to "
                        + GuildService.vaultCapacity(result.guild()) + " items.");
                return;
            }
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildWithdraw(GuildService guildService, String args) {
        Player player = session.getPlayer();
        Integer amount = parseGuildAmount(args, "withdraw");
        if (amount == null) {
            return;
        }
        GuildResult result = guildService.withdraw(player.getUsername(), amount);
        if (result.success() && result.guild() != null) {
            Player updated = player.addGold(amount);
            session.replacePlayer(updated);
            saveOrWarn(updated);
            broadcastToGuild(result.guild(), player.getUsername(),
                player.getUsername().getValue() + " withdrew " + amount + " gold from the guild bank.");
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildVault(GuildService guildService) {
        Player player = session.getPlayer();
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        List<VaultedItem> items = guild.vaultedItems();
        if (items.isEmpty()) {
            connection.writeLine("The " + guild.name() + " vault is empty.");
            writeGuildLevelLines(guild);
            sendPrompt();
            return;
        }
        connection.writeLine(guild.name() + " vault (" + items.size() + "/"
            + GuildService.vaultCapacity(guild) + "):");
        for (VaultedItem vaulted : items) {
            connection.writeLine("  " + vaulted.item().getName()
                + " (deposited by " + vaulted.depositor().getValue() + ")");
        }
        writeGuildLevelLines(guild);
        sendPrompt();
    }

    private void handleGuildStore(GuildService guildService, String itemName) {
        Player player = session.getPlayer();
        if (itemName == null || itemName.isBlank()) {
            writeLineWithPrompt("Store what? Usage: GUILD STORE <item name>");
            return;
        }
        Guild guild = guildService.guildOf(player.getUsername()).orElse(null);
        if (guild == null) {
            writeLineWithPrompt("You are not in a guild.");
            return;
        }
        Item item = matchItemByName(player.getInventory(), itemName);
        if (item == null) {
            writeLineWithPrompt("You aren't carrying '" + itemName.trim() + "'.");
            return;
        }
        GuildResult result = guildService.storeItem(player.getUsername(), item);
        if (result.success() && result.guild() != null) {
            PlayerEquipment equipment = player.getEquipment();
            if (equipment.isEquipped(item.getId())) {
                EquipmentSlot slot = equipment.equippedSlot(item.getId());
                if (slot != null) {
                    equipment = equipment.unequip(slot);
                }
            }
            Player updated = player.removeItem(item).withEquipment(equipment);
            session.replacePlayer(updated);
            saveOrWarn(updated);
            broadcastToGuild(result.guild(), player.getUsername(),
                player.getUsername().getValue() + " stored " + item.getName() + " in the guild vault.");
        }
        writeLineWithPrompt(result.message());
    }

    private void handleGuildClaim(GuildService guildService, String itemName) {
        Player player = session.getPlayer();
        if (itemName == null || itemName.isBlank()) {
            writeLineWithPrompt("Claim what? Usage: GUILD CLAIM <item name>");
            return;
        }
        int maxCarry = encumbranceService.maxCarry(player);
        int carried = 0;
        for (Item carriedItem : player.getInventory()) {
            carried += carriedItem.getWeight();
        }
        GuildVaultResult result = guildService.claimItem(player.getUsername(), itemName, carried, maxCarry);
        if (result.success() && result.guild() != null && result.item() != null) {
            Player updated = player.addItem(result.item());
            session.replacePlayer(updated);
            saveOrWarn(updated);
            broadcastToGuild(result.guild(), player.getUsername(),
                player.getUsername().getValue() + " claimed " + result.item().getName()
                    + " from the guild vault.");
        }
        writeLineWithPrompt(result.message());
    }

    /**
     * Returns every item still in the disbanded guild's vault to the leader (the disbanding caller):
     * as much as they can carry goes into inventory, the rest is dropped at their feet. Mutates and
     * persists the leader; returns a summary line for the player, or {@code null} when the vault was
     * empty. Runs on the tick thread; the room drop is an in-memory mutation (AGENTS.md §5).
     */
    @Nullable
    // Refunds a disbanded guild's pooled treasury gold to the current player (the leaver/leader),
    // returning a player-facing summary or null when the treasury was empty.
    private String refundTreasuryToLeaver(Guild disbanded) {
        int gold = disbanded.treasuryGold();
        if (gold <= 0) {
            return null;
        }
        Player updated = session.getPlayer().addGold(gold);
        session.replacePlayer(updated);
        saveOrWarn(updated);
        return gold + " gold from the guild treasury is returned to you.";
    }

    private String returnVaultToLeader(Guild disbanded) {
        List<VaultedItem> items = disbanded.vaultedItems();
        if (items.isEmpty()) {
            return null;
        }
        Player leader = session.getPlayer();
        int maxCarry = encumbranceService.maxCarry(leader);
        int carried = 0;
        for (Item carriedItem : leader.getInventory()) {
            carried += carriedItem.getWeight();
        }
        Player updated = leader;
        List<Item> overflow = new ArrayList<>();
        for (VaultedItem vaulted : items) {
            Item item = vaulted.item();
            if (carried + item.getWeight() <= maxCarry) {
                updated = updated.addItem(item);
                carried += item.getWeight();
            } else {
                overflow.add(item);
            }
        }
        session.replacePlayer(updated);
        saveOrWarn(updated);
        if (!overflow.isEmpty()) {
            roomService.findPlayerLocation(leader.getUsername()).ifPresent(roomId -> {
                for (Item item : overflow) {
                    roomService.addItem(roomId, item);
                }
            });
        }
        String summary = items.size() + " item" + (items.size() == 1 ? "" : "s")
            + " from the guild vault "
            + (items.size() == 1 ? "is" : "are") + " returned to you.";
        if (!overflow.isEmpty()) {
            summary += " " + overflow.size() + " could not be carried and "
                + (overflow.size() == 1 ? "is" : "are") + " dropped at your feet.";
        }
        return summary;
    }

    /**
     * Parses a positive gold amount for a guild treasury operation, writing a usage or validation
     * message to the player and returning {@code null} when the input is missing or invalid.
     */
    @Nullable
    private Integer parseGuildAmount(String args, String verb) {
        if (args == null || args.isBlank()) {
            writeLineWithPrompt(capitalize(verb) + " how much gold? Usage: GUILD "
                + verb.toUpperCase(Locale.ROOT) + " <amount>");
            return null;
        }
        int amount;
        try {
            amount = Integer.parseInt(args.trim());
        } catch (NumberFormatException e) {
            writeLineWithPrompt("'" + args.trim() + "' is not a valid amount.");
            return null;
        }
        if (amount <= 0) {
            writeLineWithPrompt("You must " + verb + " a positive amount of gold.");
            return null;
        }
        return amount;
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /** Sends a line to every online guild member except {@code except}. */
    private void broadcastToGuild(Guild guild, Username except, String line) {
        List<Username> online = onlinePlayerNames();
        for (GuildMember member : guild.members()) {
            if (!member.username().equals(except) && online.contains(member.username())) {
                sendToUsername(member.username(), line);
            }
        }
    }

    private void handleGuildStatus(GuildService guildService) {
        Player player = session.getPlayer();
        if (guildService.guildOf(player.getUsername()).isEmpty()) {
            writeLineWithPrompt("You are not in a guild. Use GUILD CREATE <name> to found one.");
            return;
        }
        handleGuildWho(guildService);
    }

    /**
     * Updates the persisted guild-membership component for the given user, whether they are the
     * command's caller or another online player. Offline players need no update here: guild
     * membership is resolved from the authoritative {@link GuildService} roster, so their stale
     * persisted pointer is never trusted and self-heals via {@link #reconcileGuildMembership}.
     */
    private void setGuildMembership(Username username, PlayerGuildMembership membership) {
        Player self = session.getPlayer();
        if (self != null && self.getUsername().equals(username)) {
            Player updated = self.withGuildMembership(membership);
            session.replacePlayer(updated);
            saveOrWarn(updated);
            return;
        }
        Player online = findOnlinePlayer(username);
        if (online != null) {
            updateTarget(online.withGuildMembership(membership));
        }
    }

    /**
     * Reconciles the caller's persisted guild pointer against the authoritative roster: if the
     * player still records a guild that no longer contains them (e.g. it was disbanded or they were
     * kicked while offline), clears the stale pointer so their save file reflects reality.
     */
    private void reconcileGuildMembership(Player player, GuildService guildService) {
        if (player.guildMembership().hasGuild()
            && guildService.guildOf(player.getUsername()).isEmpty()) {
            Player updated = player.withGuildMembership(PlayerGuildMembership.none());
            session.replacePlayer(updated);
            saveOrWarn(updated);
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
    public void storeItemInBank(String args) {
        if (!bankReady()) {
            return;
        }
        Player player = session.getPlayer();
        BankTransactionResult result = context.bankService().storeItem(player, args);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void claimItemFromBank(String args) {
        if (!bankReady()) {
            return;
        }
        Player player = session.getPlayer();
        int maxCarry = encumbranceService.maxCarry(player);
        BankTransactionResult result = context.bankService().claimItem(player, args, maxCarry);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    @Override
    public void sendVault() {
        if (!bankReady()) {
            return;
        }
        Player player = session.getPlayer();
        int capacity = context.bankService().effectiveVaultCapacity(player);
        String upgradeHint = vaultUpgradeHint(player);
        for (String line : VaultListing.format(
                player.getBankedItems(), capacity, session.getTextStyler(), upgradeHint)) {
            connection.writeLine(line);
        }
        sendPrompt();
    }

    @Override
    public void upgradeVault() {
        if (!bankReady()) {
            return;
        }
        Player player = session.getPlayer();
        BankTransactionResult result = context.bankService().upgradeVault(player);
        if (result.success()) {
            session.replacePlayer(result.updatedPlayer());
        }
        writeLineWithPrompt(result.message());
    }

    /**
     * Builds the "next vault upgrade" hint line for the {@code VAULT} listing, or {@code null} when
     * the player is already at the top tier.
     */
    private String vaultUpgradeHint(Player player) {
        VaultUpgradeTier current = VaultUpgradeTier.forRank(player.vault().capacityTier());
        return current.next()
            .map(next -> String.format(
                "Next upgrade: %d slots for %d gold (VAULT UPGRADE).",
                context.bankService().vaultCapacity() + next.slotBonus(),
                next.upgradeCost()))
            .orElse(null);
    }

    /**
     * Validates that the player is logged in and standing in a bank room, emitting the appropriate
     * message when not. Returns {@code true} when a vault operation may proceed.
     */
    private boolean bankReady() {
        if (!session.isAuthenticated() || session.getPlayer() == null) {
            writeLineWithPrompt("You must be logged in to use the bank.");
            return false;
        }
        if (context.bankService() == null) {
            writeLineWithPrompt("There is no bank here.");
            return false;
        }
        Player player = session.getPlayer();
        var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
        if (roomIdOpt.isEmpty()) {
            writeLineWithPrompt("You are nowhere.");
            return false;
        }
        if (context.bankService().findBankInRoom(roomIdOpt.get()).isEmpty()) {
            writeLineWithPrompt("There is no bank here.");
            return false;
        }
        return true;
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
    public void manageTitle(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to manage titles.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            listTitles(player);
            return;
        }
        if (normalized.equalsIgnoreCase("NONE") || normalized.equalsIgnoreCase("CLEAR")) {
            Player updated = player.clearActiveTitle();
            session.replacePlayer(updated);
            saveOrWarn(updated);
            writeLineWithPrompt("You are no longer displaying a title.");
            return;
        }
        Optional<String> matched = player.titles().matchEarned(normalized);
        if (matched.isEmpty()) {
            writeLineWithPrompt("You have not earned the title \"" + normalized + "\".");
            return;
        }
        String canonical = matched.get();
        Player updated = player.withActiveTitle(canonical);
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("You are now displaying the title \"" + canonical + "\".");
    }

    private void listTitles(Player player) {
        List<String> earned = player.titles().earned();
        if (earned.isEmpty()) {
            writeLineWithPrompt("You haven't earned any titles yet.");
            return;
        }
        String active = player.titles().active();
        connection.writeLine("Your titles:");
        for (String title : earned) {
            boolean isActive = title.equals(active);
            connection.writeLine("  " + title + (isActive ? " (active)" : ""));
        }
        if (active == null) {
            connection.writeLine("Active title: none. Use TITLE <name> to display one.");
        }
        sendPrompt();
    }

    @Override
    public void manageDescription(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to set a description.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            String current = player.description();
            if (current.isEmpty()) {
                writeLineWithPrompt("You have no custom description set. Use DESCRIBE <text> to set one.");
            } else {
                connection.writeLine("Your description:");
                connection.writeLine("  " + current);
                sendPrompt();
            }
            return;
        }
        // A leading token naming one of the player's own tamed companions targets that companion's
        // roleplay description (DESCRIBE <pet> [text|CLEAR|NONE]); otherwise DESCRIBE manages the
        // player's own description. Companion resolution runs on the tick thread (AGENTS.md §5).
        if (context.mobRegistry() != null) {
            String[] petParts = normalized.split("\\s+", 2);
            String companionToken = petParts[0];
            if (context.mobRegistry().ownsCompanionMatching(player, companionToken)) {
                String descriptionArg = petParts.length > 1 ? petParts[1] : "";
                if (!descriptionArg.isBlank()) {
                    cancelRestIfActive();
                }
                GameActionResult result = context.mobRegistry()
                    .describeCompanion(session.getPlayer(), companionToken, descriptionArg);
                deliverResult(result);
                sendPrompt();
                return;
            }
        }
        if (normalized.equalsIgnoreCase("CLEAR") || normalized.equalsIgnoreCase("NONE")) {
            if (player.description().isEmpty()) {
                writeLineWithPrompt("You have no custom description to clear.");
                return;
            }
            Player updated = player.withDescription("");
            session.replacePlayer(updated);
            saveOrWarn(updated);
            writeLineWithPrompt("Your description has been cleared.");
            return;
        }
        if (normalized.length() > PlayerIdentity.MAX_DESCRIPTION_LENGTH) {
            writeLineWithPrompt("Your description is too long ("
                + normalized.length() + " characters); the limit is "
                + PlayerIdentity.MAX_DESCRIPTION_LENGTH + ".");
            return;
        }
        Player updated = player.withDescription(normalized);
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("Your description has been set.");
    }

    @Override
    public void manageIgnore(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to manage your ignore list.");
            return;
        }
        String normalizedArgs = args == null ? "" : args.trim();
        if (normalizedArgs.isEmpty() || normalizedArgs.equalsIgnoreCase("LIST")) {
            listIgnored(player);
            return;
        }
        String[] parts = normalizedArgs.split("\\s+", 2);
        String subcommand = parts[0].toUpperCase(Locale.ROOT);
        String target = parts.length > 1 ? parts[1].trim() : "";
        switch (subcommand) {
            case "ADD" -> ignoreAdd(player, target);
            case "REMOVE", "DEL", "DELETE" -> ignoreRemove(player, target);
            case "CLEAR" -> ignoreClear(player);
            default -> writeLineWithPrompt(
                "Usage: IGNORE  |  IGNORE ADD <name>  |  IGNORE REMOVE <name>  |  IGNORE CLEAR");
        }
    }

    private void listIgnored(Player player) {
        var ignored = player.getIgnoredPlayers();
        if (ignored.isEmpty()) {
            writeLineWithPrompt("You are not ignoring anyone.");
            return;
        }
        connection.writeLine("You are ignoring:");
        int index = 1;
        for (String name : ignored) {
            connection.writeLine("  " + index++ + ". " + name);
        }
        sendPrompt();
    }

    private void ignoreAdd(Player player, String target) {
        if (target.isBlank()) {
            writeLineWithPrompt("Usage: IGNORE ADD <name>");
            return;
        }
        if (Username.of(target).equals(player.getUsername())) {
            writeLineWithPrompt("You cannot ignore yourself.");
            return;
        }
        if (player.ignoreList().has(target)) {
            writeLineWithPrompt("You are already ignoring " + target + ".");
            return;
        }
        Player updated = player.withIgnoreList(player.ignoreList().with(target));
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("You are now ignoring " + target + ".");
    }

    private void ignoreRemove(Player player, String target) {
        if (target.isBlank()) {
            writeLineWithPrompt("Usage: IGNORE REMOVE <name>");
            return;
        }
        if (!player.ignoreList().has(target)) {
            writeLineWithPrompt(target + " is not in your ignore list.");
            return;
        }
        Player updated = player.withIgnoreList(player.ignoreList().without(target));
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("You are no longer ignoring " + target + ".");
    }

    private void ignoreClear(Player player) {
        if (player.ignoreList().isEmpty()) {
            writeLineWithPrompt("Your ignore list is already empty.");
            return;
        }
        Player updated = player.withIgnoreList(PlayerIgnoreList.empty());
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("Your ignore list has been cleared.");
    }

    @Override
    public boolean isFriend(Username username) {
        Player player = session.getPlayer();
        if (player == null || username == null) {
            return false;
        }
        return player.friendList().has(username.getValue());
    }

    @Override
    public void manageFriends(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to manage your friends list.");
            return;
        }
        String normalizedArgs = args == null ? "" : args.trim();
        if (normalizedArgs.isEmpty() || normalizedArgs.equalsIgnoreCase("LIST")) {
            listFriends(player);
            return;
        }
        String[] parts = normalizedArgs.split("\\s+", 2);
        String subcommand = parts[0].toUpperCase(Locale.ROOT);
        String target = parts.length > 1 ? parts[1].trim() : "";
        switch (subcommand) {
            case "ADD" -> friendAdd(player, target);
            case "REMOVE", "DEL", "DELETE" -> friendRemove(player, target);
            case "CLEAR" -> friendClear(player);
            default -> writeLineWithPrompt(
                "Usage: FRIEND  |  FRIEND ADD <name>  |  FRIEND REMOVE <name>  |  FRIEND CLEAR");
        }
    }

    private void listFriends(Player player) {
        var friends = player.getFriends();
        if (friends.isEmpty()) {
            writeLineWithPrompt("You have no friends on your list.");
            return;
        }
        connection.writeLine("Your friends:");
        int index = 1;
        for (String name : friends) {
            boolean online = findOnlinePlayer(Username.of(name)) != null;
            connection.writeLine("  " + index++ + ". " + name + (online ? " (online)" : " (offline)"));
        }
        sendPrompt();
    }

    private void friendAdd(Player player, String target) {
        if (target.isBlank()) {
            writeLineWithPrompt("Usage: FRIEND ADD <name>");
            return;
        }
        if (Username.of(target).equals(player.getUsername())) {
            writeLineWithPrompt("You cannot add yourself as a friend.");
            return;
        }
        if (player.friendList().has(target)) {
            writeLineWithPrompt(target + " is already on your friends list.");
            return;
        }
        if (resolvePlayerByUsername(Username.of(target)) == null) {
            writeLineWithPrompt("There is no player named " + target + ".");
            return;
        }
        Player updated = player.withFriendList(player.friendList().with(target));
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt(target + " has been added to your friends list.");
    }

    private void friendRemove(Player player, String target) {
        if (target.isBlank()) {
            writeLineWithPrompt("Usage: FRIEND REMOVE <name>");
            return;
        }
        if (!player.friendList().has(target)) {
            writeLineWithPrompt(target + " is not on your friends list.");
            return;
        }
        Player updated = player.withFriendList(player.friendList().without(target));
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt(target + " has been removed from your friends list.");
    }

    private void friendClear(Player player) {
        if (player.friendList().isEmpty()) {
            writeLineWithPrompt("Your friends list is already empty.");
            return;
        }
        Player updated = player.withFriendList(PlayerFriendList.empty());
        session.replacePlayer(updated);
        saveOrWarn(updated);
        writeLineWithPrompt("Your friends list has been cleared.");
    }

    @Override
    public void notifyFriendsOfLogin(Player player) {
        notifyFriends(player, FriendNotifier.loginMessage(player));
    }

    @Override
    public void notifyFriendsOfLogout(Player player) {
        notifyFriends(player, FriendNotifier.logoutMessage(player));
    }

    /**
     * Delivers {@code message} to every currently-online player (other than {@code player} itself)
     * whose {@code FRIEND} list contains {@code player}, via the canonical
     * {@link io.taanielo.jmud.core.messaging.MessageBroadcaster#sendToPlayer} delivery path
     * (AGENTS.md &sect;3.3). The recipient decision lives in {@link FriendNotifier#recipients} so it
     * stays unit-testable without a running {@code GameContext}; the relationship is one-directional,
     * so only players who friended {@code player} are notified, and offline friends have no session
     * and are skipped naturally.
     *
     * @param player  the player whose login/logout is being announced
     * @param message the fully-rendered notice line to deliver to each notified friend
     */
    private void notifyFriends(Player player, String message) {
        if (player == null) {
            return;
        }
        List<FriendNotifier.OnlinePlayer> online = new ArrayList<>();
        for (Username username : onlinePlayerNames()) {
            Player onlinePlayer = findOnlinePlayer(username);
            if (onlinePlayer != null) {
                online.add(new FriendNotifier.OnlinePlayer(username, onlinePlayer.friendList()));
            }
        }
        for (Username recipient : FriendNotifier.recipients(player.getUsername(), online)) {
            context.messageBroadcaster().sendToPlayer(recipient, new PlainTextMessage(message));
        }
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
                ? playerMailService.read(player, index, encumbranceService)
                : playerMailService.delete(player, index, encumbranceService);
            applyOwnMailResult(result, null);
            return;
        }

        if ("GOLD".equals(firstToken)) {
            handleSendGoldMail(player, parts.length < 2 ? "" : parts[1], currentTick);
            return;
        }

        if ("ITEM".equals(firstToken)) {
            handleSendItemMail(player, parts.length < 2 ? "" : parts[1], currentTick);
            return;
        }

        handleSendMail(player, parts, currentTick);
    }

    private void handleSendGoldMail(Player sender, String rest, long currentTick) {
        String[] goldParts = rest.trim().split("\\s+", 3);
        if (goldParts.length < 3 || goldParts[0].isBlank() || goldParts[2].isBlank()) {
            writeLineWithPrompt("Usage: MAIL GOLD <playername> <amount> <message>");
            return;
        }
        String targetName = goldParts[0];
        Integer amount = parseMailIndex(goldParts[1]);
        if (amount == null) {
            writeLineWithPrompt("'" + goldParts[1] + "' is not a valid amount of gold.");
            return;
        }
        String message = goldParts[2].trim();
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
        MailResult result = playerMailService.sendGold(sender, recipient, currentTick, message, amount);
        if (result.success()) {
            if (result.updatedPlayer() != null) {
                updateTarget(result.updatedPlayer());
            }
            if (result.updatedSender() != null) {
                session.replacePlayer(result.updatedSender());
                saveOrWarn(result.updatedSender());
            }
        }
        writeLineWithPrompt(result.message());
    }

    private void handleSendItemMail(Player sender, String rest, long currentTick) {
        String[] itemParts = rest.trim().split("\\s+", 3);
        if (itemParts.length < 3 || itemParts[0].isBlank() || itemParts[1].isBlank() || itemParts[2].isBlank()) {
            writeLineWithPrompt("Usage: MAIL ITEM <playername> <itemname> <message>");
            return;
        }
        String targetName = itemParts[0];
        String itemName = itemParts[1];
        String message = itemParts[2].trim();
        Username targetUsername = Username.of(targetName);
        if (targetUsername.equals(sender.getUsername())) {
            writeLineWithPrompt("You cannot mail yourself.");
            return;
        }
        Item item = matchItemByName(sender.getInventory(), itemName);
        if (item == null) {
            writeLineWithPrompt("You aren't carrying that.");
            return;
        }
        Player recipient = resolvePlayerByUsername(targetUsername);
        if (recipient == null) {
            writeLineWithPrompt("No such player: " + targetName);
            return;
        }
        cancelRestIfActive();
        MailResult result = playerMailService.sendItem(sender, recipient, currentTick, message, item);
        if (result.success()) {
            if (result.updatedPlayer() != null) {
                updateTarget(result.updatedPlayer());
            }
            if (result.updatedSender() != null) {
                session.replacePlayer(result.updatedSender());
                saveOrWarn(result.updatedSender());
            }
        }
        writeLineWithPrompt(result.message());
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
        for (Client c : clientPool.allConnections()) {
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
    public void manageAuction(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to use the Auction House.");
            return;
        }
        AuctionService auctionService = context.auctionService();
        RoomId roomId = currentRoomId(player);
        if (auctionService == null || roomId == null
                || auctionService.findAuctionHouseInRoom(roomId).isEmpty()) {
            writeLineWithPrompt("There is no Auction House here.");
            return;
        }
        long currentTick = context.tickClock().currentTick();
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            listAuctions(auctionService, player, "", currentTick);
            return;
        }
        String[] parts = normalized.split("\\s+", 2);
        String sub = parts[0].toUpperCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "LIST" -> listAuctions(auctionService, player, rest, currentTick);
            case "SELL" -> auctionSell(auctionService, player, roomId, rest, currentTick);
            case "BUY" -> auctionBuy(auctionService, player, rest, currentTick);
            case "CANCEL" -> auctionCancel(auctionService, player, rest, currentTick);
            default -> writeLineWithPrompt(
                "Usage: AUCTION LIST [keyword|MINE] | AUCTION SELL <item> <price> "
                    + "| AUCTION BUY <#> | AUCTION CANCEL <#>");
        }
    }

    private void listAuctions(AuctionService auctionService, Player player, String filterArg, long currentTick) {
        AuctionFilter filter;
        String emptyMessage;
        if (filterArg.isEmpty()) {
            filter = AuctionFilter.all();
            emptyMessage = "There are no items up for auction.";
        } else if (filterArg.equalsIgnoreCase("MINE")) {
            filter = AuctionFilter.mine(player.getUsername());
            emptyMessage = "You have no active listings.";
        } else {
            filter = AuctionFilter.keyword(filterArg);
            emptyMessage = "No auction listings match '" + filterArg + "'.";
        }
        List<NumberedListing> listings = auctionService.activeListings(currentTick, filter);
        if (listings.isEmpty()) {
            writeLineWithPrompt(emptyMessage);
            return;
        }
        connection.writeLine("Items up for auction:");
        connection.writeLine(String.format("%-4s %-28s %-8s %-14s %s", "#", "Item", "Price", "Seller", "Ticks left"));
        var styler = session.getTextStyler();
        for (NumberedListing entry : listings) {
            AuctionListing listing = entry.listing();
            Item item = listing.item();
            String name = styler.rarity(item.presentationName(), item.presentationRarity());
            connection.writeLine(String.format("%-4d %-28s %-8d %-14s %d",
                entry.number(), name, listing.price(), listing.seller().getValue(),
                listing.ticksRemaining(currentTick)));
        }
        sendPrompt();
    }

    private void auctionSell(
        AuctionService auctionService, Player player, RoomId roomId, String rest, long currentTick) {
        int lastSpace = rest.lastIndexOf(' ');
        if (rest.isBlank() || lastSpace < 0) {
            writeLineWithPrompt("Usage: AUCTION SELL <item> <price>");
            return;
        }
        String itemInput = rest.substring(0, lastSpace).trim();
        String priceInput = rest.substring(lastSpace + 1).trim();
        int price;
        try {
            price = Integer.parseInt(priceInput);
        } catch (NumberFormatException e) {
            writeLineWithPrompt("'" + priceInput + "' is not a valid price.");
            return;
        }
        long expiryTick = currentTick + AuctionSettings.listingTicks();
        AuctionTransactionResult result =
            auctionService.sell(player, itemInput, price, roomId, currentTick, expiryTick);
        if (result.success() && result.updatedActor() != null) {
            session.replacePlayer(result.updatedActor());
        }
        writeLineWithPrompt(result.message());
    }

    private void auctionBuy(AuctionService auctionService, Player player, String rest, long currentTick) {
        Integer number = parseMailIndex(rest);
        if (number == null) {
            writeLineWithPrompt("Usage: AUCTION BUY <#>");
            return;
        }
        AuctionTransactionResult result = auctionService.buy(player, number, currentTick);
        if (result.success() && result.updatedActor() != null && result.listing() != null) {
            session.replacePlayer(result.updatedActor());
            creditAuctionSeller(auctionService, result.listing(), currentTick);
        }
        writeLineWithPrompt(result.message());
    }

    private void creditAuctionSeller(AuctionService auctionService, AuctionListing listing, long currentTick) {
        Player seller = resolvePlayerByUsername(listing.seller());
        if (seller == null) {
            log.warn("Auction sale of {} completed but seller {} could not be found to credit",
                listing.item().getName(), listing.seller().getValue());
            return;
        }
        Player credited = auctionService.applySaleCredit(seller, listing, currentTick);
        updateTarget(credited);
    }

    private void auctionCancel(AuctionService auctionService, Player player, String rest, long currentTick) {
        Integer number = parseMailIndex(rest);
        if (number == null) {
            writeLineWithPrompt("Usage: AUCTION CANCEL <#>");
            return;
        }
        AuctionTransactionResult result = auctionService.cancel(player, number, currentTick);
        if (result.success() && result.updatedActor() != null) {
            session.replacePlayer(result.updatedActor());
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
            String followTag = partyService.leaderOf(memberId)
                .map(followed -> " - following " + followed.getValue())
                .orElse("");
            connection.writeLine("  " + memberId.getValue() + leaderTag + "  HP: " + hp + followTag);
        }
        connection.writeLine("Loot mode: " + party.lootMode().label());
        sendPrompt();
    }

    private void handlePartyLoot(Player player, PartyService partyService, String modeArg) {
        String arg = modeArg == null ? "" : modeArg.trim();
        if (arg.isEmpty() || "STATUS".equalsIgnoreCase(arg)) {
            Optional<Party> partyOpt = partyService.findParty(player.getUsername());
            if (partyOpt.isEmpty()) {
                writeLineWithPrompt("You are not in a party. Use PARTY FORM to create one.");
                return;
            }
            writeLineWithPrompt("Party loot mode: " + partyOpt.get().lootMode().label() + ".");
            return;
        }
        Optional<LootMode> mode = LootMode.parse(arg);
        if (mode.isEmpty()) {
            writeLineWithPrompt("Usage: PARTY LOOT <free|round-robin|roll>");
            return;
        }
        PartyService.PartyResult result = partyService.setLootMode(player.getUsername(), mode.get());
        writeLineWithPrompt(result.message());
        if (result.success()) {
            for (Username other : partyService.getOtherMembers(player.getUsername())) {
                sendToUsername(other, player.getUsername().getValue()
                    + " sets the party loot mode to " + mode.get().label() + ".");
            }
        }
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
        if (classDef == null || classDef.trainableAbilityIds().isEmpty()) {
            writeLineWithPrompt("No trainable abilities found for your class.");
            return;
        }
        connection.writeLine("Master Trainer — Trainable Abilities (Practice Points: " + player.getPracticePoints() + "):");
        connection.writeLine(String.format("  %-24s %-4s %-8s %s", "Ability ID", "Lvl", "Cost", "Status"));
        connection.writeLine("  " + "-".repeat(56));
        for (TrainableAbilityStatus row : abilityTrainingService.listing(player, classDef.trainableAbilityIds())) {
            Ability ability = row.ability();
            String status = switch (row.status()) {
                case LEARNED -> "learned";
                case AVAILABLE -> player.getPracticePoints() > 0 ? "available" : "available (no points)";
                case REQUIRES_LEVEL -> "requires level " + ability.level();
            };
            connection.writeLine(String.format(
                "  %-24s %-4d %-8s %s", ability.id().getValue(), ability.level(), "1 prac", status));
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
        // Resolve the training outcome via the domain service (pool membership, already-learned,
        // level gate and practice-point checks), then apply and persist only on success. The spend
        // happens here on the tick thread so nothing mutates player state off-thread (AGENTS.md §5).
        TrainingAttempt attempt =
            abilityTrainingService.resolve(player, classDef.trainableAbilityIds(), abilityInput);
        switch (attempt) {
            case TrainingAttempt.NotTrainable notTrainable -> writeLineWithPrompt(
                "'" + notTrainable.input() + "' is not trainable by your class. Use TRAIN LIST to see options.");
            case TrainingAttempt.AlreadyLearned alreadyLearned -> writeLineWithPrompt(
                "You have already learned " + alreadyLearned.ability().name() + ".");
            case TrainingAttempt.LevelTooLow levelTooLow -> writeLineWithPrompt(
                "You must be level " + levelTooLow.requiredLevel() + " to train "
                    + levelTooLow.ability().name() + ". (You are level " + levelTooLow.playerLevel() + ".)");
            case TrainingAttempt.NoPracticePoints _ -> writeLineWithPrompt(
                "You have no practice points. Practice points are earned by levelling up.");
            case TrainingAttempt.Success success -> {
                Player updated = success.updatedPlayer();
                session.replacePlayer(updated);
                saveOrWarn(updated);
                writeLineWithPrompt("You have learned " + success.ability().name() + "! ("
                    + updated.getPracticePoints() + " practice point(s) remaining)");
            }
        }
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
        Player player = session.getPlayer();
        // Present contracts easiest-first: by recommended level ascending (quests without a
        // recommendation sort last), breaking ties on id for a stable, deterministic listing.
        List<QuestTemplate> ordered = templates.stream()
            .sorted(Comparator
                .comparingInt((QuestTemplate t) ->
                    t.hasRecommendedLevel() ? t.recommendedLevel() : Integer.MAX_VALUE)
                .thenComparing(t -> t.id().getValue()))
            .toList();
        connection.writeLine("Guild Clerk — Available Contracts (easiest first):");
        connection.writeLine(String.format("  %-20s %-4s %-30s %s", "ID", "Lvl", "Description", "Reward"));
        connection.writeLine("  " + "-".repeat(72));
        for (QuestTemplate t : ordered) {
            // NPC-delivery errands are handed out in conversation, not by the Guild Clerk.
            if (t.isNpcDeliveryQuest()) {
                continue;
            }
            // Hide quests whose prerequisite the player has not yet completed.
            if (t.hasPrerequisite()
                    && !player.completedQuests().hasCompleted(QuestId.of(t.prerequisiteQuestId()))) {
                continue;
            }
            String desc = t.description().length() > 29
                ? t.description().substring(0, 26) + "..."
                : t.description();
            String level = t.hasRecommendedLevel() ? String.valueOf(t.recommendedLevel()) : "-";
            connection.writeLine(String.format(
                "  %-20s %-4s %-30s %s",
                t.id().getValue(), level, desc, formatQuestReward(t)));
        }
        sendPrompt();
    }

    /**
     * Formats a quest's reward for the listing/status preview, e.g. {@code "100g / 350xp"} or
     * {@code "100g / 350xp / Troll Tooth Charm"} when an item reward is configured.
     */
    private String formatQuestReward(QuestTemplate template) {
        String reward = template.goldReward() + "g / " + template.xpReward() + "xp";
        QuestItemRewardService rewardService = context.questItemRewardService();
        if (rewardService != null) {
            String itemReward = rewardService.describeReward(template).orElse(null);
            if (itemReward != null) {
                reward += " / " + itemReward;
            }
        }
        QuestReputationRewardService reputationRewardService = context.questReputationRewardService();
        if (reputationRewardService != null) {
            String reputationReward = reputationRewardService.describeReward(template).orElse(null);
            if (reputationReward != null) {
                reward += " / " + reputationReward;
            }
        }
        return reward;
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
        if (!template.isRepeatable() && player.completedQuests().hasCompleted(template.id())) {
            writeLineWithPrompt("You have already completed this contract.");
            return;
        }
        if (template.hasPrerequisite()
                && !player.completedQuests().hasCompleted(QuestId.of(template.prerequisiteQuestId()))) {
            String prerequisiteName = questPrerequisiteName(questRepo, template.prerequisiteQuestId());
            writeLineWithPrompt(
                "You must first complete " + prerequisiteName + " before taking this contract.");
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

    private void handleQuestTrack(QuestRepository questRepo) {
        Player player = session.getPlayer();
        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            writeLineWithPrompt("You have no active contract to track.");
            return;
        }
        QuestTemplate template;
        try {
            template = questRepo.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template {}: {}", active.templateId(), e.getMessage());
            writeLineWithPrompt("Quest tracking unavailable.");
            return;
        }
        if (template == null) {
            writeLineWithPrompt("Unknown quest. Use QUEST ABANDON to clear it.");
            return;
        }
        // QUEST TRACK is read-only quest guidance (like QUEST STATUS/REPUTATION): it does not
        // consume moves/mana/cooldown and must not interrupt resting.
        GameActionResult result = gameActionService.trackQuestTarget(player, template.targetMobId());
        deliverResult(result);
        sendPrompt();
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
        QuestKillService questKillSvc = new QuestKillService(
            questRepo, context.questItemRewardService(), context.questReputationRewardService());
        questKillSvc.setAchievementService(context.achievementService());
        questKillSvc.setLevelUpService(new LevelUpService(context.classLevelGainsResolver()));
        QuestKillService.CompletionResult result = questKillSvc.grantCompletionReward(player, template);
        Player rewarded = result.player();
        session.replacePlayer(rewarded);
        dropQuestRewardOverflow(rewarded, result.droppedItems());
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
        QuestDeliveryService deliverySvc = new QuestDeliveryService(
            questRepo, context.questItemRewardService(), context.questReputationRewardService());
        deliverySvc.setAchievementService(context.achievementService());
        QuestDeliveryService.DeliverResult result = deliverySvc.deliver(player);
        if (result.success()) {
            session.replacePlayer(result.player());
            dropQuestRewardOverflow(result.player(), result.droppedItems());
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
        QuestNpcDeliveryService npcDeliverySvc = new QuestNpcDeliveryService(
            questRepo, context.questItemRewardService(), context.questReputationRewardService());
        npcDeliverySvc.setAchievementService(context.achievementService());
        npcDeliverySvc.setLevelUpService(new LevelUpService(context.classLevelGainsResolver()));
        DeliveryQuestResult result = npcDeliverySvc.deliver(player, roomId, receiverPresent);
        if (result.success()) {
            session.replacePlayer(result.player());
            dropQuestRewardOverflow(result.player(), result.droppedItems());
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

    /**
     * Lists the player's completed one-time contracts by display name, sorted for stable output.
     * Shows a friendly empty-state message when the player has completed none yet.
     */
    private void handleQuestLog(QuestRepository questRepo) {
        Player player = session.getPlayer();
        var completed = player.completedQuests().completed();
        if (completed.isEmpty()) {
            writeLineWithPrompt("You have not completed any one-time contracts yet.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (QuestId id : completed) {
            names.add(questDisplayName(questRepo, id));
        }
        names.sort(String::compareToIgnoreCase);
        connection.writeLine("Completed Contracts:");
        for (String name : names) {
            connection.writeLine("  " + name);
        }
        sendPrompt();
    }

    /**
     * Resolves a quest's display name from the repository, falling back to its raw id when the quest
     * cannot be loaded (e.g. a since-removed contract).
     */
    private String questDisplayName(QuestRepository questRepo, QuestId id) {
        try {
            return questRepo.findById(id).map(QuestTemplate::name).orElse(id.getValue());
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest {} for display name: {}", id.getValue(), e.getMessage());
            return id.getValue();
        }
    }

    /**
     * Resolves the display name of a prerequisite quest id for rejection messaging, falling back to
     * the raw id when the quest cannot be loaded.
     */
    private String questPrerequisiteName(QuestRepository questRepo, String prerequisiteQuestId) {
        return questDisplayName(questRepo, QuestId.of(prerequisiteQuestId));
    }

    /**
     * Drops quest item-reward copies that did not fit in the player's inventory at their feet in the
     * current room, mirroring the overweight-loot convention. No-op when nothing overflowed. Runs on
     * the tick thread; the room mutation is in-memory (AGENTS.md §5).
     */
    private void dropQuestRewardOverflow(Player player, List<Item> droppedItems) {
        if (droppedItems.isEmpty()) {
            return;
        }
        roomService.findPlayerLocation(player.getUsername()).ifPresent(roomId -> {
            for (Item item : droppedItems) {
                roomService.addItem(roomId, item);
            }
        });
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
     * Either starts character creation (for brand-new players, deferring all in-world wiring
     * to {@link #completeWorldEntry()} — issue #512) or wires world presence and dispatches an
     * immediate {@code look} command.
     *
     * <p>Must be called on the reader thread immediately after authentication completes, before
     * the main read loop continues.
     *
     * @param entersCreation {@code true} when the player is brand-new and about to answer the
     *                       race/class creation prompts instead of entering the world
     * @param isReattach     {@code true} when adopting a linkdead session (issue #343)
     */
    void registerPostLoginCallbacks(boolean entersCreation, boolean isReattach) {
        // A brand-new player answers the race/class prompts before entering the world: show the
        // gossip history banner, start creation, and defer every piece of in-world wiring to
        // completeWorldEntry(), invoked by SocketClient.finishCharacterCreation (issue #512).
        // Until then the player has no room location, receives no event-bus deliveries, and is
        // invisible to WHO/mob AI.
        if (entersCreation) {
            sendGossipHistory();
            client.beginCharacterCreation();
            return;
        }
        wireWorldPresence(isReattach);
        // Show recent gossip history exactly once, before any other post-login output.
        sendGossipHistory();
        // Notify of any unread mail waiting in the player's mailbox.
        sendMailNotice();
        String correlationId = auditService.newCorrelationId();
        session.enqueueCommand(() -> dispatcher.dispatch(this, "look", correlationId));
    }

    /**
     * Completes deferred world entry for a player who has just finished character creation
     * (issue #512): performs the in-world wiring that {@link #registerPostLoginCallbacks} skips
     * while the creation prompts are active. The caller ({@code SocketClient.finishCharacterCreation})
     * is responsible for room placement beforehand and for dispatching the initial {@code look}.
     */
    void completeWorldEntry() {
        wireWorldPresence(false);
    }

    /**
     * Registers everything that makes this connection a live participant in the game world:
     * promotion into the pool's in-world view (issue #514), the player-event-bus listener
     * (mob-initiated combat results), effect and healing/sustenance tick callbacks, the
     * death-state check, tamed-pet respawn, and the friend login notice.
     * Must only run once the player is actually in-world (issue #512).
     *
     * @param isReattach {@code true} when adopting a linkdead session — the player never left,
     *                   so friends are not re-notified of a login
     */
    private void wireWorldPresence(boolean isReattach) {
        // Promote into the pool's in-world view: from here on this client is eligible for
        // broadcasts, WHO, and targeted delivery (issue #514).
        clientPool.promoteToWorld(client);
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
        // On a genuine login (never a linkdead reattach — that player was already online), tell every
        // online player who has this player friended that they have entered the game.
        if (!isReattach && session.getPlayer() != null) {
            notifyFriendsOfLogin(session.getPlayer());
        }
        if (!isReattach && session.getPlayer() != null) {
            reconcileMarriageOnLogin(session.getPlayer());
        }
    }

    /**
     * Reconciles a possibly-stale marriage bond at login: if the player's spouse no longer exists (for
     * example the spouse was purged while this player was offline) and is not online, the bond is
     * cleared so no dangling reference remains, and the player is told they are single (issue #649).
     */
    private void reconcileMarriageOnLogin(Player player) {
        if (!player.isMarried()) {
            return;
        }
        String spouse = player.spouse();
        if (spouse == null) {
            return;
        }
        Username spouseUsername = Username.of(spouse);
        boolean spouseExists = findOnlinePlayer(spouseUsername) != null
            || context.playerRepository().loadPlayer(spouseUsername).isPresent();
        if (!spouseExists) {
            session.replacePlayer(player.withSpouse(null));
            connection.writeLine("Your former spouse " + spouse
                + " is no longer among us; you are single once more.");
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
