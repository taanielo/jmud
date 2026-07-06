package io.taanielo.jmud.bootstrap;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
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
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.combat.SeededCombatRandomProvider;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.JsonPlayerRepository;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
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
import io.taanielo.jmud.core.world.CorpseDecayTicker;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomItemService;
import io.taanielo.jmud.core.world.RoomRenderer;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;
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
    PartyService partyService,
    BankService bankService,
    MessageBroadcaster messageBroadcaster
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

        TickRegistry tickRegistry = new TickRegistry();
        TickClock tickClock = new TickClock();
        tickRegistry.register(tickClock);
        tickRegistry.register(new CorpseDecayTicker(
            roomItemService,
            java.time.Duration.ofSeconds(DeathSettings.corpseDecaySeconds())
        ));
        FixedRateTickScheduler tickScheduler = new FixedRateTickScheduler(tickRegistry, gameMetrics.registry());

        AbilityRegistry abilityRegistry = loadAbilities();
        AuditService auditService = AuditService.create(tickClock::currentTick);
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, auditService, gameMetrics.registry());

        EffectRepository effectRepository = createEffectRepository();
        EffectEngine effectEngine = new EffectEngine(effectRepository);

        EquipmentArmorResolver equipmentArmorResolver = new EquipmentArmorResolver(itemRepository);
        RaceArmorBonusResolver raceArmorBonusResolver = new RaceArmorBonusResolver(raceRepository);
        CombatEngine combatEngine =
                createCombatEngine(config, effectRepository, raceArmorBonusResolver, equipmentArmorResolver, attackRepository, tickClock::currentTick, effectEngine);

        EncumbranceService encumbranceService = new EncumbranceService(raceRepository, classRepository);

        HealingEngine healingEngine = new HealingEngine(effectRepository);
        HealingBaseResolver healingBaseResolver = new HealingBaseResolver(raceRepository, classRepository);

        AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
        AbilityTargetResolver abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);

        SocketCommandRegistry commandRegistry =
            SocketCommandRegistry.createDefault(equipmentArmorResolver, raceArmorBonusResolver, playerRepository);

        PlayerEventBus playerEventBus = new PlayerEventBus();
        MobRegistry mobRegistry = createMobRegistry(
                playerEventBus, roomService, playerRepository, persistenceQueue, itemRepository, attackRepository);
        if (mobRegistry != null) {
            mobRegistry.setEffectEngine(effectEngine);
            mobRegistry.init();
            tickRegistry.register(mobRegistry);
        }

        CharacterCreationService characterCreationService = new CharacterCreationService(raceRepository, classRepository);
        ShopService shopService = createShopService(itemRepository);
        BankService bankService = createBankService();
        QuestRepository questRepository = createQuestRepository();
        if (mobRegistry != null && questRepository != null) {
            mobRegistry.setQuestKillService(new QuestKillService(questRepository));
        }

        PartyService partyService = new PartyService();
        if (mobRegistry != null) {
            mobRegistry.setPartyService(partyService);
        }

        MessageBroadcaster messageBroadcaster = new MessageBroadcasterImpl(clientPool, roomService);

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
            partyService,
            bankService,
            messageBroadcaster
        );
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
        JsonAttackRepository attackRepository
    ) {
        try {
            JsonMobTemplateRepository templateRepo = new JsonMobTemplateRepository();
            return new MobRegistry(
                templateRepo, itemRepository, attackRepository, roomService, playerRepository, persistenceQueue, playerEventBus);
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
        GameConfig config,
        EffectRepository effectRepository,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        JsonAttackRepository attackRepository,
        LongSupplier tickSupplier,
        EffectEngine effectEngine
    ) {
        CombatModifierResolver resolver = new CombatModifierResolver(effectRepository);
        String rngMode = config.getString("jmud.combat.rng", "seeded");
        if ("threadlocal".equalsIgnoreCase(rngMode)) {
            return new CombatEngine(
                attackRepository, resolver, armorBonusResolver, equipmentArmorResolver, new ThreadLocalCombatRandom(), effectEngine);
        }
        // Default: seeded mode — derive world seed from config, or generate a random one.
        // The SeededCombatRandomProvider constructor logs the effective seed at INFO so
        // any session can be reconstructed; set jmud.world.seed to replay a specific session.
        long worldSeed = config.getLong("jmud.world.seed", ThreadLocalRandom.current().nextLong());
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(worldSeed);
        return new CombatEngine(
            attackRepository, resolver, armorBonusResolver, equipmentArmorResolver, provider, tickSupplier, effectEngine);
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

    private static ShopService createShopService(ItemRepository itemRepository) {
        try {
            ShopRepository shopRepository = new JsonShopRepository();
            return new ShopService(shopRepository, itemRepository);
        } catch (ShopRepositoryException e) {
            throw new IllegalStateException("Failed to initialize shop service: " + e.getMessage(), e);
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

    private static QuestRepository createQuestRepository() {
        try {
            return new JsonQuestRepository();
        } catch (QuestRepositoryException e) {
            throw new IllegalStateException("Failed to initialize quest repository: " + e.getMessage(), e);
        }
    }
}
