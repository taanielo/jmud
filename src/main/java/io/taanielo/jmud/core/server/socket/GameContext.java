package io.taanielo.jmud.core.server.socket;

import java.time.Clock;

import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistryImpl;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.JsonPlayerRepository;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickClock;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

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
    SocketCommandRegistry commandRegistry
) {

    /**
     * Builds a fully wired context for the socket server.
     */
    public static GameContext create() {
        GameConfig config = GameConfig.load();
        UserRegistry userRegistry = new UserRegistryImpl();
        AuthenticationPolicy authenticationPolicy = AuthenticationPolicy.fromConfig(config);
        AuthenticationLimiter authenticationLimiter = new AuthenticationLimiter(authenticationPolicy, Clock.systemUTC());
        PlayerRepository playerRepository = new JsonPlayerRepository();
        RoomRepository roomRepository = new InMemoryRoomRepository();
        RoomService roomService = new RoomService(roomRepository, RoomId.of("training-yard"));

        TickRegistry tickRegistry = new TickRegistry();
        TickClock tickClock = new TickClock();
        tickRegistry.register(tickClock);
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
            commandRegistry
        );
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
            return new CombatEngine(new JsonAttackRepository(), resolver, new ThreadLocalCombatRandom());
        } catch (AttackRepositoryException e) {
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
}
