package io.taanielo.jmud.bootstrap;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

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
import io.taanielo.jmud.core.auction.AuctionExpiryTicker;
import io.taanielo.jmud.core.auction.AuctionHouseRepository;
import io.taanielo.jmud.core.auction.AuctionRepository;
import io.taanielo.jmud.core.auction.AuctionRepositoryException;
import io.taanielo.jmud.core.auction.AuctionService;
import io.taanielo.jmud.core.auction.repository.json.JsonAuctionHouseRepository;
import io.taanielo.jmud.core.auction.repository.json.JsonAuctionRepository;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.JsonUserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistryException;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bank.BankRepository;
import io.taanielo.jmud.core.bank.BankRepositoryException;
import io.taanielo.jmud.core.bank.BankService;
import io.taanielo.jmud.core.bank.repository.json.JsonBankRepository;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.character.ClassLevelGainsResolver;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.combat.ClassArmorBonusResolver;
import io.taanielo.jmud.core.combat.CombatAttributeBonusResolver;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.OffhandAttackResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.combat.RaceAttackBonusResolver;
import io.taanielo.jmud.core.combat.SeededCombatRandom;
import io.taanielo.jmud.core.combat.SeededCombatRandomProvider;
import io.taanielo.jmud.core.combat.ShieldBlockResolver;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.flavor.CombatFlavor;
import io.taanielo.jmud.core.combat.flavor.DamageVerbTable;
import io.taanielo.jmud.core.combat.flavor.repository.json.JsonCombatFlavorRepository;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.content.ContentCompletenessChecker;
import io.taanielo.jmud.core.craft.CrafterProfile;
import io.taanielo.jmud.core.craft.CraftingService;
import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepository;
import io.taanielo.jmud.core.craft.RecipeRepositoryException;
import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.creation.NewbieKit;
import io.taanielo.jmud.core.creation.NewbieKitException;
import io.taanielo.jmud.core.creation.NewbieKitService;
import io.taanielo.jmud.core.creation.json.JsonNewbieKitRepository;
import io.taanielo.jmud.core.dialogue.DialogueRepositoryException;
import io.taanielo.jmud.core.dialogue.DialogueService;
import io.taanielo.jmud.core.dialogue.repository.json.JsonDialogueRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.enchant.EnchantRecipe;
import io.taanielo.jmud.core.enchant.EnchantRecipeRepositoryException;
import io.taanielo.jmud.core.enchant.EnchantingService;
import io.taanielo.jmud.core.enchant.repository.json.JsonEnchantRecipeRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.faction.repository.json.JsonFactionRepository;
import io.taanielo.jmud.core.gathering.ResourceGatheringService;
import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.ResourceNodeRespawnTicker;
import io.taanielo.jmud.core.gathering.repository.json.JsonResourceNodeRepository;
import io.taanielo.jmud.core.guild.GuildRepositoryException;
import io.taanielo.jmud.core.guild.GuildService;
import io.taanielo.jmud.core.guild.repository.json.JsonGuildRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.messaging.GossipHistory;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.messaging.TellService;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.WorldBossAnnouncer;
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
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.OnlinePlayersSupplier;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.CompositeQuestRepository;
import io.taanielo.jmud.core.quest.DailyQuestPool;
import io.taanielo.jmud.core.quest.DailyQuestRotationTicker;
import io.taanielo.jmud.core.quest.DailyQuestService;
import io.taanielo.jmud.core.quest.QuestItemRewardService;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestReputationRewardService;
import io.taanielo.jmud.core.quest.repository.json.JsonDailyQuestPoolRepository;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;
import io.taanielo.jmud.core.reload.ContentReloadService;
import io.taanielo.jmud.core.reload.ItemContentReloader;
import io.taanielo.jmud.core.reload.RoomContentReloader;
import io.taanielo.jmud.core.salvage.SalvageService;
import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.salvage.SalvageTierRepositoryException;
import io.taanielo.jmud.core.salvage.repository.json.JsonSalvageTierRepository;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.socket.LinkdeadTimeoutTicker;
import io.taanielo.jmud.core.server.socket.PlayerSession;
import io.taanielo.jmud.core.server.socket.PlayerSessionRegistry;
import io.taanielo.jmud.core.server.socket.ShutdownHandle;
import io.taanielo.jmud.core.server.socket.SocketCommandRegistry;
import io.taanielo.jmud.core.server.socket.WizardPolicy;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.ShopRepositoryException;
import io.taanielo.jmud.core.shop.ShopService;
import io.taanielo.jmud.core.shop.repository.json.JsonShopRepository;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickClock;
import io.taanielo.jmud.core.tick.TickMetricsService;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickSettings;
import io.taanielo.jmud.core.tick.TickThreadDispatcher;
import io.taanielo.jmud.core.trade.TradeParticipantStatus;
import io.taanielo.jmud.core.trade.TradeService;
import io.taanielo.jmud.core.transport.BoatEngine;
import io.taanielo.jmud.core.transport.FerryRepository;
import io.taanielo.jmud.core.transport.repository.json.JsonFerryRepository;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.weather.WeatherRoomView;
import io.taanielo.jmud.core.weather.WeatherSettings;
import io.taanielo.jmud.core.world.AmbientMessageEngine;
import io.taanielo.jmud.core.world.AmbientMessageSettings;
import io.taanielo.jmud.core.world.CorpseDecayTicker;
import io.taanielo.jmud.core.world.ItemAffixService;
import io.taanielo.jmud.core.world.ItemDurabilityService;
import io.taanielo.jmud.core.world.MapService;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomItemService;
import io.taanielo.jmud.core.world.RoomRenderer;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.WorldClock;
import io.taanielo.jmud.core.world.WorldClockSettings;
import io.taanielo.jmud.core.world.area.AreaConsistencyChecker;
import io.taanielo.jmud.core.world.area.AreaMapService;
import io.taanielo.jmud.core.world.area.AreaRepository;
import io.taanielo.jmud.core.world.area.repository.json.JsonAreaRepository;
import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.ItemCatalog;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomCatalog;
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
    MapService mapService,
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
    ClassLevelGainsResolver classLevelGainsResolver,
    CharacterAttributesResolver characterAttributesResolver,
    CharacterCreationService characterCreationService,
    NewbieKitService newbieKitService,
    ShopService shopService,
    QuestRepository questRepository,
    DailyQuestService dailyQuestService,
    QuestItemRewardService questItemRewardService,
    QuestReputationRewardService questReputationRewardService,
    PartyService partyService,
    TradeService tradeService,
    BankService bankService,
    AuctionService auctionService,
    GuildService guildService,
    MessageBroadcaster messageBroadcaster,
    GossipHistory gossipHistory,
    WorldClock worldClock,
    WeatherEngine weatherEngine,
    ItemDurabilityService itemDurabilityService,
    ItemAffixService itemAffixService,
    CraftingService craftingService,
    CraftingService alchemyService,
    CraftingService cookingService,
    SalvageService salvageService,
    EnchantingService enchantingService,
    ResourceGatheringService resourceGatheringService,
    DialogueService dialogueService,
    ItemRepository itemRepository,
    AttackRepository attackRepository,
    AchievementService achievementService,
    DuelService duelService,
    NotesService notesService,
    PlayerSessionRegistry playerSessionRegistry,
    AreaMapService areaMapService,
    AreaConsistencyChecker areaConsistencyChecker,
    ContentCompletenessChecker contentCompletenessChecker,
    ShutdownHandle shutdownHandle
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
        MapService mapService = new MapService(roomRepository);
        AreaRepository areaRepository = createAreaRepository();
        AreaMapService areaMapService = new AreaMapService(areaRepository);
        AreaConsistencyChecker areaConsistencyChecker =
            createAreaConsistencyChecker(areaRepository, roomRepository, itemRepository);
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
        // Tick-health metrics are recorded and queried entirely on the tick thread (STATS runs via
        // the player command queue), so no synchronisation is needed (AGENTS.md §5). Attach before
        // start() so the tick thread only reads an already-set reference.
        TickMetricsService tickMetricsService = new TickMetricsService(TickSettings.metricsRetention());
        tickScheduler.setMetricsService(tickMetricsService);

        AbilityRegistry abilityRegistry = loadAbilities();
        AuditService auditService = AuditService.create(tickClock::currentTick);
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, auditService, gameMetrics.registry());

        EffectRepository effectRepository = createEffectRepository();
        EffectEngine effectEngine = new EffectEngine(effectRepository);

        EquipmentArmorResolver equipmentArmorResolver = new EquipmentArmorResolver(itemRepository);
        ShieldBlockResolver shieldBlockResolver = new ShieldBlockResolver(itemRepository);
        RaceArmorBonusResolver raceArmorBonusResolver = new RaceArmorBonusResolver(raceRepository);
        ClassArmorBonusResolver classArmorBonusResolver = new ClassArmorBonusResolver(classRepository);
        // Snapshot per-class level-up gains once from the (cache-warmed) class repository so
        // level-ups on the tick thread resolve HP/mana/move gains from memory, never disk
        // (AGENTS.md §5). One shared LevelUpService carries these gains into every XP-award path.
        ClassLevelGainsResolver classLevelGainsResolver = createClassLevelGainsResolver(classRepository);
        LevelUpService levelUpService = new LevelUpService(classLevelGainsResolver);
        RaceAttackBonusResolver raceAttackBonusResolver = new RaceAttackBonusResolver(raceRepository);
        // Snapshot race/class attribute data once so combat, abilities and SCORE resolve a
        // character's derived attributes from memory on the tick thread, never disk (AGENTS.md §5).
        CharacterAttributesResolver characterAttributesResolver =
            createCharacterAttributesResolver(raceRepository, classRepository);
        CombatAttributeBonusResolver combatAttributeBonusResolver =
            new CombatAttributeBonusResolver(characterAttributesResolver);
        // Decide the RNG mode once so combat and world rolls (mob wander/AI, gold, loot, flee)
        // share the same seed and are reproducible together (AGENTS.md §5). The world seed is
        // logged by SeededCombatRandomProvider; set jmud.world.seed to replay a specific session.
        boolean seededRng = !"threadlocal".equalsIgnoreCase(config.getString("jmud.combat.rng", "seeded"));
        long worldSeed = seededRng ? config.getLong("jmud.world.seed", ThreadLocalRandom.current().nextLong()) : 0L;
        CombatRandom worldRandom = seededRng ? new SeededCombatRandom(worldSeed) : new ThreadLocalCombatRandom();
        // Worded-damage flavor tables (issue #525), shared by player-vs-player duels (CombatEngine)
        // and player/mob combat (MobRegistry) so both surface the same classic-MUD damage tiers and
        // condition wording.
        CombatFlavor combatFlavor = createCombatFlavor();
        DamageVerbTable damageVerbTable = combatFlavor.damageVerbs();
        CombatEngine combatEngine = createCombatEngine(
                effectRepository, raceArmorBonusResolver, raceAttackBonusResolver, classArmorBonusResolver,
                equipmentArmorResolver, shieldBlockResolver, combatAttributeBonusResolver, attackRepository,
                tickClock::currentTick, effectEngine, seededRng, worldSeed, damageVerbTable);

        // Dynamic weather evolves on the tick thread and shares the world RNG so its transitions are
        // deterministic and replayable alongside combat/mob rolls (AGENTS.md §5). Only outdoor rooms
        // (room schema v7 `is_outdoor`) observe it.
        WeatherEngine weatherEngine = new WeatherEngine(
            worldRandom, roomRepository,
            WeatherSettings.transitionIntervalTicks(), WeatherSettings.intensityStep());
        tickRegistry.register(weatherEngine);
        roomService.setWeatherView(new WeatherRoomView(weatherEngine));

        // Ambient flavour lines (dripping water, distant howls) are emitted on the tick thread to
        // occupied rooms that declare an ambient message pool (room schema v8). It shares the world
        // RNG so its scheduling and message choices are deterministic and replayable (AGENTS.md §5).
        AmbientMessageEngine ambientMessageEngine = new AmbientMessageEngine(
            worldRandom, roomRepository, playerLocationService, messageBroadcaster,
            AmbientMessageSettings.minIntervalTicks(), AmbientMessageSettings.maxIntervalTicks());
        tickRegistry.register(ambientMessageEngine);

        // Scheduled ferries sail between docks on a tick timetable, carrying any players standing on
        // their deck room to the next dock (AGENTS.md §5). State is tick-thread-confined and driven
        // by tick counts, so arrivals are deterministic; flavour lines share the world RNG.
        FerryRepository ferryRepository = createFerryRepository();
        BoatEngine boatEngine = new BoatEngine(
            worldRandom, playerLocationService, messageBroadcaster, ferryRepository.findAll());
        tickRegistry.register(boatEngine);

        EncumbranceService encumbranceService = new EncumbranceService(raceRepository, classRepository);

        HealingEngine healingEngine = new HealingEngine(effectRepository);
        HealingBaseResolver healingBaseResolver = new HealingBaseResolver(raceRepository, classRepository);

        AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
        AbilityTargetResolver abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);

        // Wizard privileges are config-driven for now (jmud.wizards = comma-separated usernames)
        // until the full role system lands (issue #44).
        Set<Username> wizardUsernames = Arrays.stream(config.getString("jmud.wizards", "").split(",", -1))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .map(Username::of)
            .collect(Collectors.toUnmodifiableSet());
        WizardPolicy wizardPolicy = new WizardPolicy(wizardUsernames);

        // The shutdown coordinator is built later (in Main, once the servers exist), so the wizard
        // SHUTDOWN command holds this late-bound handle and Main installs the sequence into it.
        ShutdownHandle shutdownHandle = new ShutdownHandle();

        ItemDurabilityService itemDurabilityService =
            new ItemDurabilityService(config.getInt("jmud.combat.durability_loss_per_hit", 1));

        AffixRepository affixRepository = new JsonAffixRepository();
        ItemAffixService itemAffixService = new ItemAffixService(affixRepository);

        CraftingService craftingService = createCraftingService(itemRepository);
        CraftingService alchemyService = createAlchemyService(itemRepository);
        CraftingService cookingService = createCookingService(itemRepository);
        SalvageService salvageService = createSalvageService(itemRepository);
        EnchantingService enchantingService =
            createEnchantingService(itemRepository, affixRepository, itemAffixService);

        // Resource gathering: node definitions load once at startup and their depletion/respawn
        // state is tracked in memory on the tick thread (AGENTS.md §5), the same transient tradeoff
        // mob respawns make. The respawn ticker drives node availability off tick counts, not the
        // wall clock.
        ResourceGatheringService resourceGatheringService =
            createResourceGatheringService(itemRepository);
        tickRegistry.register(new ResourceNodeRespawnTicker(resourceGatheringService));

        ReputationService reputationService = createReputationService();
        AchievementService achievementService = createAchievementService();

        // Party and guild services are created before the mob registry initialises so the world-boss
        // announcer can resolve a killer's affiliation, and so the initial world-load spawn
        // announcement fires through a fully-wired registry (AGENTS.md §5 — all on the tick thread).
        PartyService partyService = new PartyService();
        GuildService guildService = createGuildService();
        // Ephemeral tracker of the last private-message sender per player, backing REPLY (issue #462).
        TellService tellService = new TellService();

        PlayerEventBus playerEventBus = new PlayerEventBus();
        MobRegistry mobRegistry = createMobRegistry(
                playerEventBus, roomService, playerRepository, persistenceQueue, itemRepository, attackRepository, worldRandom);
        if (mobRegistry != null) {
            mobRegistry.setLevelUpService(levelUpService);
            mobRegistry.setEffectEngine(effectEngine);
            mobRegistry.setWorldClock(worldClock);
            mobRegistry.setItemDurabilityService(itemDurabilityService);
            mobRegistry.setReputationService(reputationService);
            mobRegistry.setAchievementService(achievementService);
            mobRegistry.setPartyService(partyService);
            mobRegistry.setEncumbranceService(encumbranceService);
            mobRegistry.setCombatAttributeBonusResolver(combatAttributeBonusResolver);
            mobRegistry.setDamageVerbTable(damageVerbTable);
            mobRegistry.setTargetConditionTable(combatFlavor.conditions());
            mobRegistry.setWorldBossAnnouncer(
                new WorldBossAnnouncer(messageBroadcaster, roomService, guildService, partyService));
            mobRegistry.init();
            tickRegistry.register(mobRegistry);
        }

        // Hot-reload of JSON content (issue #349): the JSON item/room repositories and the mob
        // registry read+validate their files off the tick thread; the dispatcher applies the atomic
        // cache swap back on the tick thread (AGENTS.md §5). Room item references resolve against the
        // freshly prepared items first, falling back to the live item repository.
        ContentReloadService contentReloadService = new ContentReloadService(
            (ItemContentReloader) itemRepository,
            (RoomContentReloader) roomRepository,
            mobRegistry,
            itemRepository::findById);
        TickThreadDispatcher tickThreadDispatcher = new TickThreadDispatcher(tickRegistry);

        // Built after mobRegistry so the wizard SPAWN/PURGE commands can be wired to it.
        SocketCommandRegistry commandRegistry = SocketCommandRegistry.createDefault(
            equipmentArmorResolver, raceArmorBonusResolver, classArmorBonusResolver, characterAttributesResolver,
            classRepository, abilityRegistry,
            playerRepository, roomService, tellService, messageBroadcaster, reputationService, weatherEngine,
            tickMetricsService, wizardPolicy, playerLocationService, mobRegistry, shutdownHandle,
            contentReloadService, tickThreadDispatcher);

        CharacterCreationService characterCreationService =
            new CharacterCreationService(raceRepository, classRepository, abilityRegistry);
        NewbieKitService newbieKitService = new NewbieKitService(createNewbieKit(), itemRepository);
        ShopService shopService = createShopService(itemRepository, reputationService);
        BankService bankService = createBankService();
        QuestRepository baseQuestRepository = createQuestRepository();
        QuestItemRewardService questItemRewardService =
            new QuestItemRewardService(itemRepository, encumbranceService);
        QuestReputationRewardService questReputationRewardService =
            new QuestReputationRewardService(reputationService);
        DailyQuestService dailyQuestService =
            createDailyQuestService(questItemRewardService, questReputationRewardService);
        dailyQuestService.setLevelUpService(levelUpService);
        // Daily quests reuse the single active-quest slot and the normal kill-progress path, so
        // expose them through a composite that resolves accepted daily variants by id while keeping
        // them out of the Guild Clerk's QUEST LIST (AGENTS.md §3.3 — reuse the canonical quest flow).
        QuestRepository questRepository =
            new CompositeQuestRepository(baseQuestRepository, dailyQuestService);
        if (mobRegistry != null) {
            QuestKillService questKillService = new QuestKillService(questRepository);
            questKillService.setLevelUpService(levelUpService);
            mobRegistry.setQuestKillService(questKillService);
        }
        tickRegistry.register(
            new DailyQuestRotationTicker(worldClock, dailyQuestService, messageBroadcaster));

        DuelService duelService = new DuelService();
        tickRegistry.register(duelService);

        // The Arena drafts random online players into announced consensual duels on a fixed tick
        // interval. It reads the live client snapshot only to enumerate online usernames; all state
        // mutation (challenge issue, spectator notices) runs on the tick thread (AGENTS.md §5).
        OnlinePlayersSupplier onlinePlayers = () -> clientPool.inWorld().stream()
            .flatMap(client -> client.currentPlayer().stream())
            .map(player -> player.getUsername())
            .toList();
        tickRegistry.register(
            new ArenaEventTicker(duelService, messageBroadcaster, onlinePlayers, worldRandom));

        // Secure two-way player trading (issue #387). The service auto-cancels open sessions each
        // tick when a participant leaves the shared room, goes offline, dies, or enters combat; the
        // status snapshot is read from the live client pool and world services (AGENTS.md §5).
        final MobRegistry tradeMobRegistry = mobRegistry;
        TradeService tradeService = new TradeService(
            username -> tradeStatusOf(username, clientPool, roomService, tradeMobRegistry, duelService),
            (username, message) -> messageBroadcaster.sendToPlayer(username, new PlainTextMessage(message)));
        tickRegistry.register(tradeService);

        GossipHistory gossipHistory = new GossipHistory();

        DialogueService dialogueService = createDialogueService();

        NotesService notesService = new NotesService(createNotesRepository(), Clock.systemUTC());

        // Tracks live sessions by username (including linkdead ones) and ages out sessions whose
        // connection dropped and were not reclaimed within the grace period (issue #343).
        PlayerSessionRegistry playerSessionRegistry = new PlayerSessionRegistry();
        tickRegistry.register(new LinkdeadTimeoutTicker(playerSessionRegistry));

        // Auction House: returns expired listings to their sellers each tick, crediting/mailing the
        // seller wherever they are (live session or on disk) via the same cross-player update path as
        // MAIL. All state mutation runs on the tick thread (AGENTS.md §5).
        AuctionService auctionService = createAuctionService();
        tickRegistry.register(new AuctionExpiryTicker(
            auctionService,
            tickClock::currentTick,
            seller -> resolveAuctionSeller(seller, playerSessionRegistry, playerRepository),
            updated -> persistAuctionSeller(updated, playerSessionRegistry, persistenceQueue)));

        gameMetrics.bindGlobalGauges(tickRegistry, clientPool);

        ContentCompletenessChecker contentCompletenessChecker =
            createContentCompletenessChecker(roomRepository, itemRepository, classRepository, attackRepository);

        return new GameContext(
            userRegistry,
            authenticationPolicy,
            authenticationLimiter,
            playerRepository,
            persistenceQueue,
            roomRepository,
            roomService,
            mapService,
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
            classLevelGainsResolver,
            characterAttributesResolver,
            characterCreationService,
            newbieKitService,
            shopService,
            questRepository,
            dailyQuestService,
            questItemRewardService,
            questReputationRewardService,
            partyService,
            tradeService,
            bankService,
            auctionService,
            guildService,
            messageBroadcaster,
            gossipHistory,
            worldClock,
            weatherEngine,
            itemDurabilityService,
            itemAffixService,
            craftingService,
            alchemyService,
            cookingService,
            salvageService,
            enchantingService,
            resourceGatheringService,
            dialogueService,
            itemRepository,
            attackRepository,
            achievementService,
            duelService,
            notesService,
            playerSessionRegistry,
            areaMapService,
            areaConsistencyChecker,
            contentCompletenessChecker,
            shutdownHandle
        );
    }

    /**
     * Resolves a live trade-participant status snapshot from the connected client pool and world
     * services, for the {@link TradeService} auto-cancel guard.
     *
     * @param username    the participant to inspect
     * @param clientPool  the connected client pool
     * @param roomService resolves player locations
     * @param mobRegistry combat tracker (may be {@code null} in minimal boots)
     * @param duelService duel tracker
     * @return the participant's current status, or {@link TradeParticipantStatus#OFFLINE}
     */
    private static TradeParticipantStatus tradeStatusOf(
        Username username,
        ClientPool clientPool,
        RoomService roomService,
        @Nullable MobRegistry mobRegistry,
        DuelService duelService
    ) {
        @Nullable Player player = clientPool.inWorld().stream()
            .flatMap(client -> client.currentPlayer().stream())
            .filter(candidate -> candidate.getUsername().equals(username))
            .findFirst()
            .orElse(null);
        if (player == null) {
            return TradeParticipantStatus.OFFLINE;
        }
        @Nullable RoomId room = roomService.findPlayerLocation(username).orElse(null);
        boolean inCombat = (mobRegistry != null && mobRegistry.isInCombat(username))
            || duelService.isDueling(username);
        return new TradeParticipantStatus(true, room, player.isDead(), inCombat);
    }

    private static NotesRepository createNotesRepository() {
        return new JsonNotesRepository();
    }

    private static FerryRepository createFerryRepository() {
        return new JsonFerryRepository();
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

    private static AreaRepository createAreaRepository() {
        try {
            AreaRepository areaRepository = new JsonAreaRepository();
            // Warm the lazily-loaded area/atlas cache at bootstrap so the first tick-thread READ of a
            // map item resolves from memory and never triggers a directory scan or JSON parse inline
            // (AGENTS.md §5 — no blocking I/O reachable from tick()).
            areaRepository.findAll();
            areaRepository.findAtlas();
            return areaRepository;
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize area repository: " + e.getMessage(), e);
        }
    }

    private static AreaConsistencyChecker createAreaConsistencyChecker(
        AreaRepository areaRepository, RoomRepository roomRepository, ItemRepository itemRepository) {
        try {
            return new AreaConsistencyChecker(
                areaRepository,
                (RoomCatalog) roomRepository,
                new JsonShopRepository(),
                new JsonMobTemplateRepository(),
                itemRepository);
        } catch (ShopRepositoryException | RepositoryException e) {
            throw new IllegalStateException(
                "Failed to initialize area consistency checker: " + e.getMessage(), e);
        }
    }

    private static ContentCompletenessChecker createContentCompletenessChecker(
        RoomRepository roomRepository,
        ItemRepository itemRepository,
        ClassRepository classRepository,
        AttackRepository attackRepository) {
        try {
            return new ContentCompletenessChecker(
                new JsonMobTemplateRepository(),
                (RoomCatalog) roomRepository,
                (ItemCatalog) itemRepository,
                attackRepository,
                new JsonAbilityRepository(),
                classRepository,
                new JsonShopRepository(),
                new JsonQuestRepository(),
                List.<RecipeRepository>of(
                    new JsonRecipeRepository(),
                    new JsonRecipeRepository(Path.of("data"), "recipes/alchemy"),
                    new JsonRecipeRepository(Path.of("data"), "recipes/cooking")),
                new JsonResourceNodeRepository(),
                new JsonNewbieKitRepository(),
                new JsonSalvageTierRepository(),
                new JsonFactionRepository());
        } catch (RepositoryException | ShopRepositoryException | RecipeRepositoryException
            | FactionRepositoryException | AbilityRepositoryException | QuestRepositoryException e) {
            throw new IllegalStateException(
                "Failed to initialize content completeness checker: " + e.getMessage(), e);
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
        ShieldBlockResolver shieldBlockResolver,
        CombatAttributeBonusResolver attributeBonusResolver,
        JsonAttackRepository attackRepository,
        LongSupplier tickSupplier,
        EffectEngine effectEngine,
        boolean seeded,
        long worldSeed,
        DamageVerbTable verbTable
    ) {
        CombatModifierResolver resolver = new CombatModifierResolver(effectRepository);
        OffhandAttackResolver offhandAttackResolver = new OffhandAttackResolver();
        if (!seeded) {
            CombatRandom threadLocalRandom = new ThreadLocalCombatRandom();
            return new CombatEngine(
                attackRepository, resolver, armorBonusResolver, attackBonusResolver, classArmorBonusResolver,
                equipmentArmorResolver, shieldBlockResolver, offhandAttackResolver, attributeBonusResolver,
                (tick, actorId) -> threadLocalRandom, () -> 0L, effectEngine, verbTable);
        }
        // Seeded mode: the provider derives an independent per-encounter stream from the shared
        // world seed and logs the effective seed at INFO so any session can be reconstructed.
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(worldSeed);
        return new CombatEngine(
            attackRepository, resolver, armorBonusResolver, attackBonusResolver, classArmorBonusResolver,
            equipmentArmorResolver, shieldBlockResolver, offhandAttackResolver, attributeBonusResolver,
            provider, tickSupplier, effectEngine, verbTable);
    }

    private static CombatFlavor createCombatFlavor() {
        try {
            return new JsonCombatFlavorRepository().load();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to load combat flavor tables: " + e.getMessage(), e);
        }
    }

    private static ItemRepository createItemRepository() {
        try {
            return new JsonItemRepository();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize item repository: " + e.getMessage(), e);
        }
    }

    private static NewbieKit createNewbieKit() {
        try {
            return new JsonNewbieKitRepository().load();
        } catch (NewbieKitException e) {
            throw new IllegalStateException("Failed to initialize newbie kit: " + e.getMessage(), e);
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

    private static ClassLevelGainsResolver createClassLevelGainsResolver(JsonClassRepository classRepository) {
        try {
            return ClassLevelGainsResolver.fromDefinitions(classRepository.findAll());
        } catch (ClassRepositoryException e) {
            throw new IllegalStateException("Failed to load class level gains: " + e.getMessage(), e);
        }
    }

    private static CharacterAttributesResolver createCharacterAttributesResolver(
        JsonRaceRepository raceRepository,
        JsonClassRepository classRepository
    ) {
        try {
            return CharacterAttributesResolver.fromDefinitions(
                raceRepository.findAll(), classRepository.findAll());
        } catch (RaceRepositoryException | ClassRepositoryException e) {
            throw new IllegalStateException("Failed to load character attributes: " + e.getMessage(), e);
        }
    }

    private static CraftingService createCraftingService(ItemRepository itemRepository) {
        try {
            List<Recipe> recipes = new JsonRecipeRepository().findAll();
            return new CraftingService(recipes, itemRepository);
        } catch (RecipeRepositoryException e) {
            throw new IllegalStateException("Failed to initialize crafting service: " + e.getMessage(), e);
        }
    }

    private static CraftingService createAlchemyService(ItemRepository itemRepository) {
        try {
            List<Recipe> recipes = new JsonRecipeRepository(Path.of("data"), "recipes/alchemy").findAll();
            return new CraftingService(recipes, itemRepository, CrafterProfile.alchemist());
        } catch (RecipeRepositoryException e) {
            throw new IllegalStateException("Failed to initialize alchemy service: " + e.getMessage(), e);
        }
    }

    private static CraftingService createCookingService(ItemRepository itemRepository) {
        try {
            List<Recipe> recipes = new JsonRecipeRepository(Path.of("data"), "recipes/cooking").findAll();
            return new CraftingService(recipes, itemRepository, CrafterProfile.cook());
        } catch (RecipeRepositoryException e) {
            throw new IllegalStateException("Failed to initialize cooking service: " + e.getMessage(), e);
        }
    }

    private static SalvageService createSalvageService(ItemRepository itemRepository) {
        try {
            List<SalvageTier> tiers = new JsonSalvageTierRepository().findAll();
            return new SalvageService(tiers, itemRepository);
        } catch (SalvageTierRepositoryException e) {
            throw new IllegalStateException("Failed to initialize salvage service: " + e.getMessage(), e);
        }
    }

    private static EnchantingService createEnchantingService(
        ItemRepository itemRepository,
        AffixRepository affixRepository,
        ItemAffixService itemAffixService
    ) {
        try {
            List<EnchantRecipe> recipes = new JsonEnchantRecipeRepository().findAll();
            return new EnchantingService(recipes, itemRepository, affixRepository, itemAffixService);
        } catch (EnchantRecipeRepositoryException e) {
            throw new IllegalStateException("Failed to initialize enchanting service: " + e.getMessage(), e);
        }
    }

    private static ResourceGatheringService createResourceGatheringService(ItemRepository itemRepository) {
        try {
            List<ResourceNode> nodes = new JsonResourceNodeRepository().findAll();
            return new ResourceGatheringService(nodes, itemRepository);
        } catch (RepositoryException e) {
            throw new IllegalStateException(
                "Failed to initialize resource gathering service: " + e.getMessage(), e);
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

    private static AuctionService createAuctionService() {
        try {
            AuctionHouseRepository houseRepository = new JsonAuctionHouseRepository();
            AuctionRepository listingRepository = new JsonAuctionRepository();
            return new AuctionService(houseRepository, listingRepository);
        } catch (AuctionRepositoryException e) {
            throw new IllegalStateException("Failed to initialize auction service: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a seller's current {@link Player} state for the Auction House expiry ticker, preferring
     * a live in-session player and falling back to the persisted player file for offline sellers.
     */
    private static Optional<Player> resolveAuctionSeller(
        Username seller, PlayerSessionRegistry sessions, PlayerRepository playerRepository) {
        PlayerSession session = sessions.lookup(seller).orElse(null);
        if (session != null && session.getPlayer() != null) {
            return Optional.of(session.getPlayer());
        }
        return playerRepository.loadPlayer(seller);
    }

    /**
     * Persists an updated seller after an Auction House expiry return, replacing the live session when
     * the seller is online and otherwise enqueuing a write-behind save for the offline player.
     */
    private static void persistAuctionSeller(
        Player updated, PlayerSessionRegistry sessions, PersistenceQueue persistenceQueue) {
        PlayerSession session = sessions.lookup(updated.getUsername()).orElse(null);
        if (session != null) {
            session.replacePlayer(updated);
        } else {
            persistenceQueue.enqueueSave(updated);
        }
    }

    private static GuildService createGuildService() {
        try {
            return new GuildService(new JsonGuildRepository());
        } catch (GuildRepositoryException e) {
            throw new IllegalStateException("Failed to initialize guild service: " + e.getMessage(), e);
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

    private static DailyQuestService createDailyQuestService(
            QuestItemRewardService itemRewardService,
            QuestReputationRewardService reputationRewardService) {
        try {
            List<DailyQuestPool> pools = new JsonDailyQuestPoolRepository().findAll();
            return new DailyQuestService(pools, itemRewardService, reputationRewardService);
        } catch (QuestRepositoryException e) {
            throw new IllegalStateException("Failed to initialize daily quest service: " + e.getMessage(), e);
        }
    }
}
