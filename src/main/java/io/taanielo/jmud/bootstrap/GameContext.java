package io.taanielo.jmud.bootstrap;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.achievement.AchievementRepositoryException;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.achievement.repository.json.JsonAchievementRepository;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.JsonUserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistryException;
import io.taanielo.jmud.core.bank.BankRepository;
import io.taanielo.jmud.core.bank.BankRepositoryException;
import io.taanielo.jmud.core.bank.BankService;
import io.taanielo.jmud.core.bank.repository.json.JsonBankRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.combat.ClassArmorBonusResolver;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.combat.RaceAttackBonusResolver;
import io.taanielo.jmud.core.combat.SeededCombatRandom;
import io.taanielo.jmud.core.combat.SeededCombatRandomProvider;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.dialogue.DialogueRepositoryException;
import io.taanielo.jmud.core.dialogue.DialogueService;
import io.taanielo.jmud.core.dialogue.repository.json.JsonDialogueRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.faction.repository.json.JsonFactionRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.messaging.GossipHistory;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.notes.NotesRepository;
import io.taanielo.jmud.core.notes.NotesService;
import io.taanielo.jmud.core.notes.repository.json.JsonNotesRepository;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.ArenaEventTicker;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.DuelService;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.JsonPlayerRepository;
import io.taanielo.jmud.core.player.OnlinePlayersSupplier;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.CompositeQuestRepository;
import io.taanielo.jmud.core.quest.DailyQuestPool;
import io.taanielo.jmud.core.quest.DailyQuestRotationTicker;
import io.taanielo.jmud.core.quest.DailyQuestService;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.repository.json.JsonDailyQuestPoolRepository;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.socket.SocketCommandRegistry;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.ShopRepositoryException;
import io.taanielo.jmud.core.shop.ShopService;
import io.taanielo.jmud.core.shop.repository.json.JsonShopRepository;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickClock;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.weather.WeatherRoomView;
import io.taanielo.jmud.core.weather.WeatherSettings;
import io.taanielo.jmud.core.world.CorpseDecayTicker;
import io.taanielo.jmud.core.world.ItemAffixService;
import io.taanielo.jmud.core.world.ItemDurabilityService;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomItemService;
import io.taanielo.jmud.core.world.RoomRenderer;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.WorldClock;
import io.taanielo.jmud.core.world.WorldClockSettings;
import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;
import io.taanielo.jmud.core.world.repository.json.JsonAffixRepository;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

/**
 * Composition root. The only place allowed to construct {@code Json*} repository
 * implementations and infrastructure schedulers.
 */
public record GameContext(
    UserRegistry userRegistry,
    AuthenticationPolicy authenticationPolicy,
    AuthenticationLimiter authenticationLimiter,
    PlayerRepository playerRepository,
    PersistenceQueue persistenceQueue,
    RoomRepository roomRepository,
    RoomService roomService,
    TickRegistry tickRegistry,
    TickClock tickClock,
    FixedRateTickScheduler tickScheduler,
    AbilityRegistry abilityRegistry,
    AuditService auditService,
    EffectRepository effectRepository,
    EffectEngine effectEngine,
    CombatEngine combatEngine,
    CombatRandom worldRandom,
    EncumbranceService encumbranceService,
    HealingEngine healingEngine,
    HealingBaseResolver healingBaseResolver,
    AbilityCostResolver abilityCostResolver,
    AbilityTargetResolver abilityTargetResolver,
    SocketCommandRegistry commandRegistry,
    PlayerEventBus playerEventBus,
    MobRegistry mobRegistry,
    CharacterCreationService characterCreationService,
    ShopService shopService,
    QuestRepository questRepository,
    DailyQuestService dailyQuestService,
    PartyService partyService,
    BankService bankService,
    MessageBroadcaster messageBroadcaster,
    GossipHistory gossipHistory,
    WorldClock worldClock,
    WeatherEngine weatherEngine,
    ItemDurabilityService itemDurabilityService,
    ItemAffixService itemAffixService,
    DialogueService dialogueService,
    ItemRepository itemRepository,
    AchievementService achievementService,
    DuelService duelService,
    NotesService notesService
) {

    /**
     * Builds a fully wired context for the socket server.
     *
     * @param clientPool the pool of connected clients, used to wire {@link MessageBroadcaster}
     *                    without transport adapters ever constructing it themselves
     */
    public static GameContext create(ClientPool clientPool) {
        GameConfig config = GameConfig.load();
        GameMetrics gameMetrics = GameMetrics.create(config);

        UserRegistry userRegistry = createUserRegistry();
        AuthenticationPolicy authenticationPolicy = AuthenticationPolicy.fromConfig(config);
        AuthenticationLimiter authenticationLimiter =
            new AuthenticationLimiter(authenticationPolicy, Clock.systemUTC(), gameMetrics.registry());
        PlayerRepository playerRepository = new JsonPlayerRepository();

        // Shared repository instances: each Json*Repository is constructed exactly once here
        // and passed to every consumer (AGENTS.md §3.3), instead of every consumer building
        // its own copy.
        ItemRepository itemRepository = createItemRepository();
        JsonAttackRepository attackRepository = createAttackRepository();
        JsonRaceRepository raceRepository = createRaceRepository();
        JsonClassRepository classRepository = createClassRepository();

        RoomRepository roomRepository = createRoomRepository(itemRepository);
        RoomItemService roomItemService = new RoomItemService();
        PlayerLocationService playerLocationService =
            new PlayerLocationService(roomRepository, RoomId.of("training-yard"));
        RoomService roomService = new RoomService(
            playerLocationService, roomItemService, new RoomRenderer(), roomRepository);
        MessageBroadcaster messageBroadcaster = new MessageBroadcasterImpl(clientPool, roomService);

        TickRegistry tickRegistry = new TickRegistry();
        TickClock tickClock = new TickClock();
        tickRegistry.register(tickClock);
        tickRegistry.register(new CorpseDecayTicker(
            roomItemService,
            java.time.Duration.ofSeconds(DeathSettings.corpseDecaySeconds())
        ));
        WorldClock worldClock = new WorldClock(WorldClockSettings.ticksPerPhase());
        tickRegistry.register(worldClock);
        roomService.setWorldClock(worldClock);
        FixedRateTickScheduler tickScheduler = new FixedRateTickScheduler(tickRegistry, gameMetrics.registry());

        AbilityRegistry abilityRegistry = loadAbilities();
        AuditService auditService = AuditService.create(tickClock::currentTick);
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, auditService, gameMetrics.registry());

        EffectRepository effectRepository = createEffectRepository();
        EffectEngine effectEngine = new EffectEngine(effectRepository);

        EquipmentArmorResolver equipmentArmorResolver = new EquipmentArmorResolver(itemRepository);
        RaceArmorBonusResolver raceArmorBonusResolver = new RaceArmorBonusResolver(raceRepository);
        ClassArmorBonusResolver classArmorBonusResolver = new ClassArmorBonusResolver(classRepository);
        RaceAttackBonusResolver raceAttackBonusResolver = new RaceAttackBonusResolver(raceRepository);
        // Decide the RNG mode once so combat and world rolls (mob wander/AI, gold, loot, flee)
        // share the same seed and are reproducible together (AGENTS.md §5). The world seed is
        // logged by SeededCombatRandomProvider; set jmud.world.seed to replay a specific session.
        boolean seededRng = !"threadlocal".equalsIgnoreCase(config.getString("jmud.combat.rng", "seeded"));
        long worldSeed = seededRng ? config.getLong("jmud.world.seed", ThreadLocalRandom.current().nextLong()) : 0L;
        CombatRandom worldRandom = seededRng ? new SeededCombatRandom(worldSeed) : new ThreadLocalCombatRandom();
        CombatEngine combatEngine = createCombatEngine(
                effectRepository, raceArmorBonusResolver, raceAttackBonusResolver, classArmorBonusResolver,
                equipmentArmorResolver, attackRepository,
                tickClock::currentTick, effectEngine, seededRng, worldSeed);

        // Dynamic weather evolves on the tick thread and shares the world RNG so its transitions are
        // deterministic and replayable alongside combat/mob rolls (AGENTS.md §5). Only outdoor rooms
        // (room schema v7 `is_outdoor`) observe it.
        WeatherEngine weatherEngine = new WeatherEngine(
            worldRandom, roomRepository,
            WeatherSettings.transitionIntervalTicks(), WeatherSettings.intensityStep());
        tickRegistry.register(weatherEngine);
        roomService.setWeatherView(new WeatherRoomView(weatherEngine));

        EncumbranceService encumbranceService = new EncumbranceService(raceRepository, classRepository);

        HealingEngine healingEngine = new HealingEngine(effectRepository);
        HealingBaseResolver healingBaseResolver = new HealingBaseResolver(raceRepository, classRepository);

        AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
        AbilityTargetResolver abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);

        SocketCommandRegistry commandRegistry = SocketCommandRegistry.createDefault(
            equipmentArmorResolver, raceArmorBonusResolver, classArmorBonusResolver,
            playerRepository, roomService, messageBroadcaster, weatherEngine);

        ItemDurabilityService itemDurabilityService =
            new ItemDurabilityService(config.getInt("jmud.combat.durability_loss_per_hit", 1));

        AffixRepository affixRepository = new JsonAffixRepository();
        ItemAffixService itemAffixService = new ItemAffixService(affixRepository);

        ReputationService reputationService = createReputationService();
        AchievementService achievementService = createAchievementService();

        PlayerEventBus playerEventBus = new PlayerEventBus();
        MobRegistry mobRegistry = createMobRegistry(
                playerEventBus, roomService, playerRepository, persistenceQueue, itemRepository, attackRepository, worldRandom);
        if (mobRegistry != null) {
            mobRegistry.setEffectEngine(effectEngine);
            mobRegistry.setWorldClock(worldClock);
            mobRegistry.setItemDurabilityService(itemDurabilityService);
            mobRegistry.setReputationService(reputationService);
            mobRegistry.setAchievementService(achievementService);
            mobRegistry.init();
            tickRegistry.register(mobRegistry);
        }

        CharacterCreationService characterCreationService = new CharacterCreationService(raceRepository, classRepository);
        ShopService shopService = createShopService(itemRepository, reputationService);
        BankService bankService = createBankService();
        QuestRepository baseQuestRepository = createQuestRepository();
        DailyQuestService dailyQuestService = createDailyQuestService();
        // Daily quests reuse the single active-quest slot and the normal kill-progress path, so
        // expose them through a composite that resolves accepted daily variants by id while keeping
        // them out of the Guild Clerk's QUEST LIST (AGENTS.md §3.3 — reuse the canonical quest flow).
        QuestRepository questRepository =
            new CompositeQuestRepository(baseQuestRepository, dailyQuestService);
        if (mobRegistry != null) {
            mobRegistry.setQuestKillService(new QuestKillService(questRepository));
        }
        tickRegistry.register(
            new DailyQuestRotationTicker(worldClock, dailyQuestService, messageBroadcaster));

        PartyService partyService = new PartyService();
        if (mobRegistry != null) {
            mobRegistry.setPartyService(partyService);
        }

        DuelService duelService = new DuelService();
        tickRegistry.register(duelService);

        // The Arena drafts random online players into announced consensual duels on a fixed tick
        // interval. It reads the live client snapshot only to enumerate online usernames; all state
        // mutation (challenge issue, spectator notices) runs on the tick thread (AGENTS.md §5).
        OnlinePlayersSupplier onlinePlayers = () -> clientPool.clients().stream()
            .filter(client -> client.isInWorld())
            .flatMap(client -> client.currentPlayer().stream())
            .map(player -> player.getUsername())
            .toList();
        tickRegistry.register(
            new ArenaEventTicker(duelService, messageBroadcaster, onlinePlayers, worldRandom));

        GossipHistory gossipHistory = new GossipHistory();

        DialogueService dialogueService = createDialogueService();

        NotesService notesService = new NotesService(createNotesRepository(), Clock.systemUTC());

        gameMetrics.bindGlobalGauges(tickRegistry, clientPool);

        return new GameContext(
            userRegistry,
            authenticationPolicy,
            authenticationLimiter,
            playerRepository,
            persistenceQueue,
            roomRepository,
            roomService,
            tickRegistry,
            tickClock,
            tickScheduler,
            abilityRegistry,
            auditService,
            effectRepository,
            effectEngine,
            combatEngine,
            worldRandom,
            encumbranceService,
            healingEngine,
            healingBaseResolver,
            abilityCostResolver,
            abilityTargetResolver,
            commandRegistry,
            playerEventBus,
            mobRegistry,
            characterCreationService,
            shopService,
            questRepository,
            dailyQuestService,
            partyService,
            bankService,
            messageBroadcaster,
            gossipHistory,
            worldClock,
            weatherEngine,
            itemDurabilityService,
            itemAffixService,
            dialogueService,
            itemRepository,
            achievementService,
            duelService,
            notesService
        );
    }

    private static NotesRepository createNotesRepository() {
        return new JsonNotesRepository();
    }

    private static UserRegistry createUserRegistry() {
        try {
            return new JsonUserRegistry();
        } catch (UserRegistryException e) {
            throw new IllegalStateException("Failed to initialize user registry: " + e.getMessage(), e);
        }
    }

    private static RoomRepository createRoomRepository(ItemRepository itemRepository) {
        try {
            return new JsonRoomRepository(itemRepository);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize room repository: " + e.getMessage(), e);
        }
    }

    private static MobRegistry createMobRegistry(
        PlayerEventBus playerEventBus,
        RoomService roomService,
        PlayerRepository playerRepository,
        PersistenceQueue persistenceQueue,
        ItemRepository itemRepository,
        JsonAttackRepository attackRepository,
        CombatRandom worldRandom
    ) {
        try {
            JsonMobTemplateRepository templateRepo = new JsonMobTemplateRepository();
            return new MobRegistry(
                templateRepo, itemRepository, attackRepository, roomService, playerRepository,
                persistenceQueue, playerEventBus, worldRandom);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize mob registry: " + e.getMessage(), e);
        }
    }

    private static AbilityRegistry loadAbilities() {
        try {
            return new AbilityRegistry(new JsonAbilityRepository().findAll());
        } catch (AbilityRepositoryException e) {
            throw new IllegalStateException("Failed to load abilities: " + e.getMessage(), e);
        }
    }

    private static EffectRepository createEffectRepository() {
        try {
            return new JsonEffectRepository();
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to initialize effects: " + e.getMessage(), e);
        }
    }

    private static CombatEngine createCombatEngine(
        EffectRepository effectRepository,
        RaceArmorBonusResolver armorBonusResolver,
        RaceAttackBonusResolver attackBonusResolver,
        ClassArmorBonusResolver classArmorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        JsonAttackRepository attackRepository,
        LongSupplier tickSupplier,
        EffectEngine effectEngine,
        boolean seeded,
        long worldSeed
    ) {
        CombatModifierResolver resolver = new CombatModifierResolver(effectRepository);
        if (!seeded) {
            CombatRandom threadLocalRandom = new ThreadLocalCombatRandom();
            return new CombatEngine(
                attackRepository, resolver, armorBonusResolver, attackBonusResolver, classArmorBonusResolver,
                equipmentArmorResolver, (tick, actorId) -> threadLocalRandom, () -> 0L, effectEngine);
        }
        // Seeded mode: the provider derives an independent per-encounter stream from the shared
        // world seed and logs the effective seed at INFO so any session can be reconstructed.
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(worldSeed);
        return new CombatEngine(
            attackRepository, resolver, armorBonusResolver, attackBonusResolver, classArmorBonusResolver,
            equipmentArmorResolver, provider, tickSupplier, effectEngine);
    }

    private static ItemRepository createItemRepository() {
        try {
            return new JsonItemRepository();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize item repository: " + e.getMessage(), e);
        }
    }

    private static JsonAttackRepository createAttackRepository() {
        try {
            return new JsonAttackRepository();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize attack repository: " + e.getMessage(), e);
        }
    }

    private static JsonRaceRepository createRaceRepository() {
        try {
            return new JsonRaceRepository();
        } catch (RaceRepositoryException e) {
            throw new IllegalStateException("Failed to initialize race repository: " + e.getMessage(), e);
        }
    }

    private static JsonClassRepository createClassRepository() {
        try {
            return new JsonClassRepository();
        } catch (ClassRepositoryException e) {
            throw new IllegalStateException("Failed to initialize class repository: " + e.getMessage(), e);
        }
    }

    private static ShopService createShopService(ItemRepository itemRepository, ReputationService reputationService) {
        try {
            ShopRepository shopRepository = new JsonShopRepository();
            return new ShopService(shopRepository, itemRepository, reputationService);
        } catch (ShopRepositoryException e) {
            throw new IllegalStateException("Failed to initialize shop service: " + e.getMessage(), e);
        }
    }

    private static ReputationService createReputationService() {
        try {
            return new ReputationService(new JsonFactionRepository());
        } catch (FactionRepositoryException e) {
            throw new IllegalStateException("Failed to initialize reputation service: " + e.getMessage(), e);
        }
    }

    private static AchievementService createAchievementService() {
        try {
            return new AchievementService(new JsonAchievementRepository());
        } catch (AchievementRepositoryException e) {
            throw new IllegalStateException("Failed to initialize achievement service: " + e.getMessage(), e);
        }
    }

    private static BankService createBankService() {
        try {
            BankRepository bankRepository = new JsonBankRepository();
            return new BankService(bankRepository);
        } catch (BankRepositoryException e) {
            throw new IllegalStateException("Failed to initialize bank service: " + e.getMessage(), e);
        }
    }

    private static DialogueService createDialogueService() {
        try {
            return new DialogueService(new JsonDialogueRepository());
        } catch (DialogueRepositoryException e) {
            throw new IllegalStateException("Failed to initialize dialogue service: " + e.getMessage(), e);
        }
    }

    private static QuestRepository createQuestRepository() {
        try {
            return new JsonQuestRepository();
        } catch (QuestRepositoryException e) {
            throw new IllegalStateException("Failed to initialize quest repository: " + e.getMessage(), e);
        }
    }

    private static DailyQuestService createDailyQuestService() {
        try {
            List<DailyQuestPool> pools = new JsonDailyQuestPoolRepository().findAll();
            return new DailyQuestService(pools);
        } catch (QuestRepositoryException e) {
            throw new IllegalStateException("Failed to initialize daily quest service: " + e.getMessage(), e);
        }
    }
}
