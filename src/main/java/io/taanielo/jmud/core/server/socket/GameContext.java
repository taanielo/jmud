package io.taanielo.jmud.core.server.socket;

import java.time.Clock;

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
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.MobRepositoryException;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;
import io.taanielo.jmud.core.bank.BankRepository;
import io.taanielo.jmud.core.bank.BankRepositoryException;
import io.taanielo.jmud.core.bank.BankService;
import io.taanielo.jmud.core.bank.repository.json.JsonBankRepository;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.ShopRepositoryException;
import io.taanielo.jmud.core.shop.ShopService;
import io.taanielo.jmud.core.shop.repository.json.JsonShopRepository;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.JsonPlayerRepository;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickClock;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.CorpseDecayTicker;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

/**
 * Shared dependency container for socket server components.
 */
public record GameContext(
    UserRegistry userRegistry,
    AuthenticationPolicy authenticationPolicy,
    AuthenticationLimiter authenticationLimiter,
    PlayerRepository playerRepository,
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
    BankService bankService
) {

    /**
     * Builds a fully wired context for the socket server.
     */
    public static GameContext create() {
        GameConfig config = GameConfig.load();
        UserRegistry userRegistry = createUserRegistry();
        AuthenticationPolicy authenticationPolicy = AuthenticationPolicy.fromConfig(config);
        AuthenticationLimiter authenticationLimiter = new AuthenticationLimiter(authenticationPolicy, Clock.systemUTC());
        PlayerRepository playerRepository = new JsonPlayerRepository();
        RoomRepository roomRepository = createRoomRepository();
        RoomService roomService = new RoomService(roomRepository, RoomId.of("training-yard"));

        TickRegistry tickRegistry = new TickRegistry();
        TickClock tickClock = new TickClock();
        tickRegistry.register(tickClock);
        tickRegistry.register(new CorpseDecayTicker(
            roomService,
            java.time.Duration.ofSeconds(DeathSettings.corpseDecaySeconds())
        ));
        FixedRateTickScheduler tickScheduler = new FixedRateTickScheduler(tickRegistry);

        AbilityRegistry abilityRegistry = loadAbilities();
        AuditService auditService = AuditService.create(tickClock::currentTick);

        EffectRepository effectRepository = createEffectRepository();
        EffectEngine effectEngine = new EffectEngine(effectRepository);

        CombatEngine combatEngine = createCombatEngine(effectRepository);

        EncumbranceService encumbranceService = createEncumbranceService();

        HealingEngine healingEngine = new HealingEngine(effectRepository);
        HealingBaseResolver healingBaseResolver = createHealingBaseResolver();

        AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
        AbilityTargetResolver abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);

        SocketCommandRegistry commandRegistry = SocketCommandRegistry.createDefault();

        PlayerEventBus playerEventBus = new PlayerEventBus();
        MobRegistry mobRegistry = createMobRegistry(playerEventBus, roomService, playerRepository);
        if (mobRegistry != null) {
            mobRegistry.init();
            tickRegistry.register(mobRegistry);
        }

        CharacterCreationService characterCreationService = createCharacterCreationService();
        ShopService shopService = createShopService();
        BankService bankService = createBankService();
        QuestRepository questRepository = createQuestRepository();
        if (mobRegistry != null && questRepository != null) {
            mobRegistry.setQuestKillService(new QuestKillService(questRepository));
        }

        PartyService partyService = new PartyService();
        if (mobRegistry != null) {
            mobRegistry.setPartyService(partyService);
        }

        return new GameContext(
            userRegistry,
            authenticationPolicy,
            authenticationLimiter,
            playerRepository,
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
            bankService
        );
    }

    private static UserRegistry createUserRegistry() {
        try {
            return new JsonUserRegistry();
        } catch (UserRegistryException e) {
            throw new IllegalStateException("Failed to initialize user registry: " + e.getMessage(), e);
        }
    }

    private static RoomRepository createRoomRepository() {
        try {
            ItemRepository itemRepository = new JsonItemRepository();
            return new JsonRoomRepository(itemRepository);
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to initialize room repository: " + e.getMessage(), e);
        }
    }

    private static MobRegistry createMobRegistry(
        PlayerEventBus playerEventBus,
        RoomService roomService,
        PlayerRepository playerRepository
    ) {
        try {
            JsonMobTemplateRepository templateRepo = new JsonMobTemplateRepository();
            ItemRepository itemRepo = new JsonItemRepository();
            JsonAttackRepository attackRepo = new JsonAttackRepository();
            return new MobRegistry(templateRepo, itemRepo, attackRepo, roomService, playerRepository, playerEventBus);
        } catch (MobRepositoryException
            | io.taanielo.jmud.core.world.repository.RepositoryException
            | AttackRepositoryException e) {
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

    private static CombatEngine createCombatEngine(EffectRepository effectRepository) {
        try {
            CombatModifierResolver resolver = new CombatModifierResolver(effectRepository);
            RaceArmorBonusResolver armorBonusResolver = new RaceArmorBonusResolver(new JsonRaceRepository());
            return new CombatEngine(new JsonAttackRepository(), resolver, armorBonusResolver, new ThreadLocalCombatRandom());
        } catch (AttackRepositoryException | RaceRepositoryException e) {
            throw new IllegalStateException("Failed to initialize combat: " + e.getMessage(), e);
        }
    }

    private static HealingBaseResolver createHealingBaseResolver() {
        try {
            return new HealingBaseResolver(new JsonRaceRepository(), new JsonClassRepository());
        } catch (RaceRepositoryException | ClassRepositoryException e) {
            throw new IllegalStateException("Failed to initialize healing base resolver: " + e.getMessage(), e);
        }
    }

    private static EncumbranceService createEncumbranceService() {
        try {
            return new EncumbranceService(new JsonRaceRepository(), new JsonClassRepository());
        } catch (RaceRepositoryException | ClassRepositoryException e) {
            throw new IllegalStateException("Failed to initialize encumbrance service: " + e.getMessage(), e);
        }
    }

    private static CharacterCreationService createCharacterCreationService() {
        try {
            return new CharacterCreationService(new JsonRaceRepository(), new JsonClassRepository());
        } catch (RaceRepositoryException | ClassRepositoryException e) {
            throw new IllegalStateException("Failed to initialize character creation service: " + e.getMessage(), e);
        }
    }

    private static ShopService createShopService() {
        try {
            ShopRepository shopRepository = new JsonShopRepository();
            ItemRepository itemRepository = new JsonItemRepository();
            return new ShopService(shopRepository, itemRepository);
        } catch (ShopRepositoryException | RepositoryException e) {
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
