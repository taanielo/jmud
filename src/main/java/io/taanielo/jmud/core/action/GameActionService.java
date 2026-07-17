package io.taanielo.jmud.core.action;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityEffectListener;
import io.taanielo.jmud.core.ability.AbilityEngine;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityMatch;
import io.taanielo.jmud.core.ability.AbilityMessageSink;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityUseResult;
import io.taanielo.jmud.core.ability.DefaultAbilityEffectResolver;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatAction;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatResult;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.DuelService;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.OnlinePlayerLookup;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerMount;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.weather.Weather;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.world.ContainerLockingService;
import io.taanielo.jmud.core.world.Corpse;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.ItemEffectOperation;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.ItemIdentificationService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.area.AreaMapService;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application-layer service that executes domain-level game actions.
 *
 * <p>Each method takes a {@link Player} and action arguments, returns a
 * {@link GameActionResult} containing updated player state and messages
 * to deliver. No I/O is performed directly.
 */
@Slf4j
public class GameActionService {

    private final AbilityRegistry abilityRegistry;
    private final AbilityCostResolver abilityCostResolver;
    private final EffectEngine abilityEffectEngine;
    private final CombatEngine combatEngine;
    private final RoomService roomService;
    private final AbilityTargetResolver abilityTargetResolver;
    private final AbilityCooldownTracker cooldownTracker;
    private final EncumbranceService encumbranceService;
    /**
     * Predicate that returns {@code true} when the given player is currently engaged in combat.
     * Used by {@link #useAbility} to enforce first-strike restrictions on
     * {@link io.taanielo.jmud.core.ability.AbilityTargeting#HARMFUL_OPENER} abilities.
     */
    private final Predicate<Player> inCombatCheck;
    /**
     * Domain service governing the rogue PICK skill: pick-success and trap rolls and container
     * unlocking. Defaults to a {@link ThreadLocalCombatRandom}-backed instance when not supplied.
     */
    private final ContainerLockingService containerLockingService;
    /**
     * Seeded RNG port used for world/game rolls made by this service — currently the random exit
     * choice in {@link #flee}. Routing it through {@link CombatRandom} keeps flee reproducible from
     * a world seed (AGENTS.md §5), rather than using bare {@code ThreadLocalRandom} in the adapter.
     */
    private final CombatRandom worldRandom;
    /**
     * Disengages the given player from active combat. Injected so the flee game rule can apply the
     * combat-state mutation without the adapter (or this service) depending on
     * {@link io.taanielo.jmud.core.mob.MobRegistry} directly. Defaults to a no-op.
     */
    private final Consumer<Player> combatDisengage;
    /**
     * Port used by the rogue STEAL skill to find a target NPC in the room, read its stealable gold,
     * and turn it hostile on a failed attempt, without depending on the concrete mob layer. Defaults
     * to {@link NpcStealPort#NONE} (never finds a target) when no mob layer is wired.
     */
    private final NpcStealPort npcStealPort;
    /**
     * Port used by the ranger TRACK skill to enumerate the mobs in each room while walking the room
     * graph, without depending on the concrete mob layer. Defaults to {@link MobLocatorPort#NONE}
     * (never finds a mob) when no mob layer is wired.
     */
    private final MobLocatorPort mobLocatorPort;
    /**
     * Registry of consensual player-vs-player duels. Defaults to a private, unshared instance so
     * tests and non-duel code paths work without extra wiring; the composition root replaces it via
     * {@link #setDuelService(DuelService)} with the single shared instance used across all sessions.
     */
    private DuelService duelService = new DuelService();
    /**
     * Shared party registry, used by {@link #resurrect} to confirm the caster and their target
     * belong to the same party. {@code null} until the composition root wires the shared instance via
     * {@link #setPartyService(PartyService)}; while absent, resurrection rejects every target.
     */
    private @Nullable PartyService partyService;
    /**
     * Port that resolves a connected player by username regardless of location, used by
     * {@link #resurrect} to fetch a dead party member's live state (they have been removed from the
     * room map while awaiting respawn). Defaults to {@link OnlinePlayerLookup#NONE} so non-wired
     * tests and code paths simply never find a target.
     */
    private OnlinePlayerLookup onlinePlayerLookup = OnlinePlayerLookup.NONE;
    /**
     * Optional weather source used to apply ambient combat modifiers to attacks. {@code null} means
     * the weather subsystem is disabled; combat then resolves with no environmental modifiers. The
     * composition root wires the shared engine via {@link #setWeatherEngine(WeatherEngine)}.
     */
    private @Nullable WeatherEngine weatherEngine;
    /**
     * Optional resolver of a caster's derived core attributes, used to scale harmful spell damage by
     * intellect and healing by wisdom. {@code null} until the composition root wires it via
     * {@link #setCharacterAttributesResolver(CharacterAttributesResolver)}; while absent, spells and
     * heals apply their base amounts unchanged.
     */
    private @Nullable CharacterAttributesResolver characterAttributesResolver;
    /**
     * Optional renderer of hand-drawn area/atlas cartography, used when a player READs a map item.
     * {@code null} until the composition root wires it via {@link #setAreaMapService(AreaMapService)};
     * while absent, reading a map reports that its markings cannot be made out.
     */
    private @Nullable AreaMapService areaMapService;
    private final MessageEmitter messageEmitter = new MessageEmitter();
    private final ItemIdentificationService identificationService = new ItemIdentificationService();
    private final AtomicLong scrollCounter = new AtomicLong();

    /** Cooldown key used to rate-limit {@link #recall}. Not a real ability id, but reuses the tracker. */
    private static final AbilityId RECALL_COOLDOWN_KEY = AbilityId.of("recall");
    /** Number of ticks a player must wait between successful recalls. */
    private static final int RECALL_COOLDOWN_TICKS = 30;
    /** Id of the consumable Scroll of Recall item, whose READ action triggers {@link #recall}. */
    private static final ItemId RECALL_SCROLL_ID = ItemId.of("scroll-of-recall");
    /** Metadata key set by {@link #recall} on a successful teleport. */
    private static final String RECALL_METADATA_KEY = "recalled";
    /** Ability id of the rogue BACKSTAB skill, which gains a bonus when opened from stealth. */
    private static final AbilityId BACKSTAB_ABILITY_ID = AbilityId.of("skill.backstab");
    /** Ability id of the Cleric RESURRECTION spell, resolved by dedicated logic in {@link #resurrect}. */
    private static final AbilityId RESURRECTION_ABILITY_ID = AbilityId.of("spell.resurrection");
    /** Extra damage a BACKSTAB deals when the attacker strikes from stealth. */
    private static final int STEALTH_BACKSTAB_BONUS_DAMAGE = 10;
    /** Base percentage chance (out of 100) that a rogue STEAL attempt succeeds, before the level bonus. */
    private static final int STEAL_BASE_SUCCESS_PERCENT = 45;
    /** Additional STEAL success percentage granted per rogue level. */
    private static final int STEAL_SUCCESS_PERCENT_PER_LEVEL = 3;
    /** Maximum STEAL success percentage, regardless of rogue level. */
    private static final int STEAL_MAX_SUCCESS_PERCENT = 90;
    /** Base probability that a single SEARCH attempt uncovers the room's undiscovered hidden exits. */
    static final double SEARCH_BASE_SUCCESS_CHANCE = 0.5d;
    /** Additional SEARCH success probability granted per rogue level, on top of the base chance. */
    static final double SEARCH_ROGUE_SUCCESS_CHANCE_PER_LEVEL = 0.02d;
    /** Maximum SEARCH success probability for a rogue, regardless of level (never guaranteed). */
    static final double SEARCH_ROGUE_MAX_SUCCESS_CHANCE = 0.9d;

    /**
     * Creates a game action service with the given domain dependencies.
     * The in-combat check defaults to {@code false} (never in combat), which disables
     * opener-ability enforcement. Use the full constructor to supply real combat state.
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService,
            _ -> false);
    }

    /**
     * Creates a game action service with an explicit container-locking service, allowing tests to
     * inject a deterministic RNG for the rogue PICK skill. All other dependencies match the primary
     * constructors; the in-combat check defaults to {@code false}.
     *
     * @param containerLockingService the service governing pick-lock success, trap, and damage rolls
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        ContainerLockingService containerLockingService
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService,
            _ -> false, containerLockingService);
    }

    /**
     * Creates a game action service with an explicit in-combat predicate.
     *
     * @param inCombatCheck returns {@code true} when the given player is already engaged in combat;
     *                      used to block {@link io.taanielo.jmud.core.ability.AbilityTargeting#HARMFUL_OPENER}
     *                      abilities mid-combat
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService, inCombatCheck,
            new ContainerLockingService(new ThreadLocalCombatRandom()));
    }

    /**
     * Creates a game action service with both an explicit in-combat predicate and a container-locking
     * service. The world RNG defaults to {@link ThreadLocalCombatRandom} and the combat-disengage
     * callback to a no-op, so {@link #flee} is inert; use the production constructor to wire both.
     *
     * @param inCombatCheck           returns {@code true} when the given player is already engaged in
     *                                combat; used to block
     *                                {@link io.taanielo.jmud.core.ability.AbilityTargeting#HARMFUL_OPENER}
     *                                abilities mid-combat and to gate {@link #flee}
     * @param containerLockingService the service governing pick-lock success, trap, and damage rolls
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck,
        ContainerLockingService containerLockingService
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService, inCombatCheck,
            containerLockingService, new ThreadLocalCombatRandom(), _ -> { }, NpcStealPort.NONE,
            MobLocatorPort.NONE);
    }

    /**
     * Creates a game action service with an explicit in-combat predicate, seeded world RNG, and
     * combat-disengage callback, in addition to the default container-locking service. This is the
     * production constructor: the RNG powers {@link #flee}'s random exit choice and the callback
     * applies its combat-state mutation.
     *
     * @param inCombatCheck   returns {@code true} when the given player is already engaged in combat
     * @param worldRandom     seeded RNG port for world/game rolls (flee direction)
     * @param combatDisengage disengages the given player from active combat when they flee
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck,
        CombatRandom worldRandom,
        Consumer<Player> combatDisengage
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService, inCombatCheck,
            new ContainerLockingService(new ThreadLocalCombatRandom()), worldRandom, combatDisengage,
            NpcStealPort.NONE, MobLocatorPort.NONE);
    }

    /**
     * Creates a game action service with an explicit in-combat predicate, seeded world RNG,
     * combat-disengage callback, and NPC steal port. This is the production constructor used when
     * the mob layer is available to back the rogue STEAL skill.
     *
     * @param inCombatCheck   returns {@code true} when the given player is already engaged in combat
     * @param worldRandom     seeded RNG port for world/game rolls (flee direction, steal success)
     * @param combatDisengage disengages the given player from active combat when they flee
     * @param npcStealPort    port used by the STEAL skill to find/steal-from/aggro a target NPC
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck,
        CombatRandom worldRandom,
        Consumer<Player> combatDisengage,
        NpcStealPort npcStealPort
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService, inCombatCheck,
            new ContainerLockingService(new ThreadLocalCombatRandom()), worldRandom, combatDisengage,
            npcStealPort, MobLocatorPort.NONE);
    }

    /**
     * Creates a game action service with an explicit in-combat predicate, seeded world RNG,
     * combat-disengage callback, NPC steal port, and mob locator port. This is the full production
     * constructor used when the mob layer is available to back both the rogue STEAL skill and the
     * ranger TRACK skill.
     *
     * @param inCombatCheck   returns {@code true} when the given player is already engaged in combat
     * @param worldRandom     seeded RNG port for world/game rolls (flee direction, steal success)
     * @param combatDisengage disengages the given player from active combat when they flee
     * @param npcStealPort    port used by the STEAL skill to find/steal-from/aggro a target NPC
     * @param mobLocatorPort  port used by the TRACK skill to enumerate mobs per room
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck,
        CombatRandom worldRandom,
        Consumer<Player> combatDisengage,
        NpcStealPort npcStealPort,
        MobLocatorPort mobLocatorPort
    ) {
        this(abilityRegistry, abilityCostResolver, abilityEffectEngine, combatEngine,
            roomService, abilityTargetResolver, cooldownTracker, encumbranceService, inCombatCheck,
            new ContainerLockingService(new ThreadLocalCombatRandom()), worldRandom, combatDisengage,
            npcStealPort, mobLocatorPort);
    }

    /**
     * Full constructor to which all others delegate.
     *
     * @param inCombatCheck           returns {@code true} when the given player is already engaged in
     *                                combat; used to block
     *                                {@link io.taanielo.jmud.core.ability.AbilityTargeting#HARMFUL_OPENER}
     *                                abilities mid-combat and to gate {@link #flee}
     * @param containerLockingService the service governing pick-lock success, trap, and damage rolls
     * @param worldRandom             seeded RNG port for world/game rolls (flee direction)
     * @param combatDisengage         disengages the given player from active combat when they flee
     * @param npcStealPort            port used by the rogue STEAL skill to find/steal-from/aggro an NPC
     * @param mobLocatorPort          port used by the ranger TRACK skill to enumerate mobs per room
     */
    public GameActionService(
        AbilityRegistry abilityRegistry,
        AbilityCostResolver abilityCostResolver,
        EffectEngine abilityEffectEngine,
        CombatEngine combatEngine,
        RoomService roomService,
        AbilityTargetResolver abilityTargetResolver,
        AbilityCooldownTracker cooldownTracker,
        EncumbranceService encumbranceService,
        Predicate<Player> inCombatCheck,
        ContainerLockingService containerLockingService,
        CombatRandom worldRandom,
        Consumer<Player> combatDisengage,
        NpcStealPort npcStealPort,
        MobLocatorPort mobLocatorPort
    ) {
        this.abilityRegistry = Objects.requireNonNull(abilityRegistry, "Ability registry is required");
        this.abilityCostResolver = Objects.requireNonNull(abilityCostResolver, "Ability cost resolver is required");
        this.abilityEffectEngine = Objects.requireNonNull(abilityEffectEngine, "Ability effect engine is required");
        this.combatEngine = Objects.requireNonNull(combatEngine, "Combat engine is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.abilityTargetResolver = Objects.requireNonNull(abilityTargetResolver, "Ability target resolver is required");
        this.cooldownTracker = Objects.requireNonNull(cooldownTracker, "Cooldown tracker is required");
        this.encumbranceService = Objects.requireNonNull(encumbranceService, "Encumbrance service is required");
        this.inCombatCheck = Objects.requireNonNull(inCombatCheck, "In-combat check is required");
        this.containerLockingService =
            Objects.requireNonNull(containerLockingService, "Container locking service is required");
        this.worldRandom = Objects.requireNonNull(worldRandom, "World random is required");
        this.combatDisengage = Objects.requireNonNull(combatDisengage, "Combat disengage callback is required");
        this.npcStealPort = Objects.requireNonNull(npcStealPort, "NPC steal port is required");
        this.mobLocatorPort = Objects.requireNonNull(mobLocatorPort, "Mob locator port is required");
    }

    /**
     * Injects the shared duel registry used to coordinate consensual player-vs-player duels.
     *
     * <p>Called once by the composition root so that every per-session service instance references
     * the same registry; without it, {@link #initiatePlayerDuel}, {@link #acceptPlayerDuel}, and the
     * duel-aware death path fall back to a private, unshared instance.
     *
     * @param duelService the shared duel registry
     */
    public void setDuelService(DuelService duelService) {
        this.duelService = Objects.requireNonNull(duelService, "Duel service is required");
    }

    /**
     * Injects the shared weather engine so that outdoor combat picks up ambient weather modifiers.
     *
     * <p>Called once by the composition root. When absent, combat resolves with no environmental
     * modifiers (as it did before the weather system existed).
     *
     * @param weatherEngine the shared weather engine
     */
    public void setWeatherEngine(WeatherEngine weatherEngine) {
        this.weatherEngine = Objects.requireNonNull(weatherEngine, "Weather engine is required");
    }

    /**
     * Injects the resolver of a caster's derived core attributes so that harmful spells scale with the
     * caster's intellect and heals scale with wisdom.
     *
     * <p>Called once by the composition root. When absent, spell and heal amounts are applied
     * unchanged (as they were before the attribute system existed).
     *
     * @param characterAttributesResolver the shared character attributes resolver
     */
    public void setCharacterAttributesResolver(CharacterAttributesResolver characterAttributesResolver) {
        this.characterAttributesResolver =
            Objects.requireNonNull(characterAttributesResolver, "Character attributes resolver is required");
    }

    /**
     * Injects the service that renders hand-drawn area and atlas cartography so that READing a map
     * item shows its authored ASCII art.
     *
     * <p>Called once by the composition root. When absent, reading a map reports that its markings
     * cannot be made out (as before the cartography system existed).
     *
     * @param areaMapService the shared area map renderer
     */
    public void setAreaMapService(AreaMapService areaMapService) {
        this.areaMapService = Objects.requireNonNull(areaMapService, "Area map service is required");
    }

    /**
     * Injects the shared party registry used by {@link #resurrect} to verify party membership.
     *
     * <p>Called once by the composition root so every per-session service references the same
     * registry. Without it, resurrection cannot confirm a shared party and rejects every target.
     *
     * @param partyService the shared party registry
     */
    public void setPartyService(PartyService partyService) {
        this.partyService = Objects.requireNonNull(partyService, "Party service is required");
    }

    /**
     * Injects the port used by {@link #resurrect} to resolve a dead party member's live state by
     * username, independent of their (cleared) location.
     *
     * <p>Called once by the composition root; when absent, resurrection never finds a target.
     *
     * @param onlinePlayerLookup the connected-player lookup port
     */
    public void setOnlinePlayerLookup(OnlinePlayerLookup onlinePlayerLookup) {
        this.onlinePlayerLookup =
            Objects.requireNonNull(onlinePlayerLookup, "Online player lookup is required");
    }

    /**
     * Resolves the weather currently affecting the given player's room, or {@link Weather#clear()}
     * when the weather subsystem is disabled or the player's location is unknown.
     */
    private Weather weatherFor(Player player) {
        if (weatherEngine == null) {
            return Weather.clear();
        }
        return roomService.findPlayerLocation(player.getUsername())
            .map(weatherEngine::getWeatherAt)
            .orElseGet(Weather::clear);
    }

    /**
     * Returns whether the given player is currently engaged in combat, counting both mob combat and
     * an active duel.
     *
     * @param player the player to check
     * @return {@code true} when the player is in mob combat or an active duel
     */
    private boolean isEngagedInCombat(Player player) {
        return inCombatCheck.test(player) || duelService.isDueling(player.getUsername());
    }

    /**
     * Initiates a consensual duel by sending a challenge to a player in the same room.
     *
     * <p>Validation rejects a blank name, self-targeting, a target who is not present, and either
     * party already being in combat (mob or duel). On success a pending challenge is recorded with a
     * {@value DuelService#DEFAULT_TIMEOUT_TICKS}-tick acceptance window and the target is prompted to
     * {@code ACCEPT}. No game state other than the transient duel registry is mutated.
     *
     * @param initiator  the challenging player
     * @param targetName the raw name of the player to challenge
     * @return a result carrying the challenge messages, or an error describing why it was rejected
     */
    public GameActionResult initiatePlayerDuel(Player initiator, String targetName) {
        Objects.requireNonNull(initiator, "Initiator is required");
        String normalized = targetName == null ? "" : targetName.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Usage: duel <player>");
        }
        if (isEngagedInCombat(initiator)) {
            return GameActionResult.error("You cannot start a duel while in combat.");
        }
        Optional<Player> targetMatch = abilityTargetResolver.resolve(initiator, normalized);
        if (targetMatch.isEmpty()) {
            return GameActionResult.error("There is no one here by that name to duel.");
        }
        Player target = targetMatch.get();
        if (target.getUsername().equals(initiator.getUsername())) {
            return GameActionResult.error("You cannot duel yourself.");
        }
        if (isEngagedInCombat(target)) {
            return GameActionResult.error(target.getUsername().getValue() + " is already in combat.");
        }
        duelService.requestDuel(initiator.getUsername(), target.getUsername());
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You challenge " + target.getUsername().getValue() + " to a duel."));
        messages.add(GameMessage.toPlayer(
            target.getUsername(),
            initiator.getUsername().getValue()
                + " challenges you to a duel. Type ACCEPT to engage or wait 30s for timeout."));
        return new GameActionResult(null, null, messages);
    }

    /**
     * Accepts a pending duel challenge, engaging the accepting player and their challenger.
     *
     * <p>Fails when no challenge is pending for the accepting player or when they are already in an
     * active duel. On success both players become dueling combatants; the fight then proceeds through
     * the normal {@link #attack} command, with duel-aware death handling suppressing loot, gold, and
     * corpse creation.
     *
     * @param target the player accepting a challenge
     * @return a result carrying the acceptance messages, or an error when there is nothing to accept
     */
    public GameActionResult acceptPlayerDuel(Player target) {
        Objects.requireNonNull(target, "Target is required");
        if (duelService.isDueling(target.getUsername())) {
            return GameActionResult.error("You are already in a duel.");
        }
        Optional<Username> challengerMatch = duelService.pendingChallenger(target.getUsername());
        if (challengerMatch.isEmpty()) {
            return GameActionResult.error("You have no pending duel challenge.");
        }
        Username challenger = challengerMatch.get();
        duelService.activate(challenger, target.getUsername());
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You accept " + challenger.getValue() + "'s duel. Combat begins!"));
        messages.add(GameMessage.toPlayer(
            challenger,
            target.getUsername().getValue() + " accepts your duel. Combat begins!"));
        return new GameActionResult(null, null, messages);
    }

    /**
     * Ends an active duel when one participant falls to zero HP, suppressing the normal lethal
     * consequences of a player death.
     *
     * <p>Unlike {@link #resolveDeathIfNeeded}, no corpse is spawned, no gold or items are dropped, no
     * XP or reputation is awarded, and the loser is left alive at 1 HP (near death) in the same room
     * rather than being sent to respawn. Both participants are disengaged and told the duel is over.
     *
     * <p>A resolved duel is also the only outcome that updates the participants' persistent duel
     * records: the survivor's {@code duelWins} is incremented and the loser's {@code duelLosses} is
     * incremented. Forfeits and timeouts (handled via {@code DuelService.clearFor}) never reach this
     * method, so they correctly leave both records unchanged.
     *
     * @param survivor the winning participant
     * @param loser    the participant reduced to zero HP
     * @return a result carrying the survivor with an incremented win count as
     *         {@link GameActionResult#updatedSource()} and the near-death loser with an incremented
     *         loss count as {@link GameActionResult#updatedTarget()}, plus the duel-end messages
     */
    public GameActionResult endPlayerDuel(Player survivor, Player loser) {
        Objects.requireNonNull(survivor, "Survivor is required");
        Objects.requireNonNull(loser, "Loser is required");
        duelService.endDuel(survivor.getUsername(), loser.getUsername());
        Player updatedSurvivor = survivor.withDuelWins(survivor.getDuelWins() + 1);
        PlayerVitals nearDeathVitals =
            loser.getVitals().hp() <= 0 ? loser.getVitals().heal(1) : loser.getVitals();
        // Leave the loser alive at near-death rather than slain: clear the death flag combat set
        // when their HP hit zero, so no respawn/corpse cascade is triggered.
        Player nearDeathLoser = loser.withVitals(nearDeathVitals).withDead(false)
            .withDuelLosses(loser.getDuelLosses() + 1);
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You have defeated " + loser.getUsername().getValue() + " in the duel!"));
        messages.add(GameMessage.toSource("Duel ended."));
        messages.add(GameMessage.toPlayer(
            loser.getUsername(),
            "You have been defeated by " + survivor.getUsername().getValue() + "."));
        messages.add(GameMessage.toPlayer(loser.getUsername(), "Duel ended."));
        return new GameActionResult(updatedSurvivor, nearDeathLoser, messages);
    }

    /**
     * Teleports the player back to the starting/town room used by {@link RoomService} for
     * new-character placement and respawn.
     *
     * <p>Fails without teleporting if the player is currently in combat (mirrors the
     * {@code inCombatCheck} used by {@link #useAbility}; the player should FLEE first) or if
     * recall is still on cooldown from a previous use. On success, starts a fixed-length
     * cooldown so recall cannot be spammed as a defensive teleport, and returns a departure
     * message for the old room and an arrival message for the destination room, in addition to
     * a confirmation message to the player. The result's metadata contains a {@code "recalled"}
     * entry on success so callers can distinguish it from a rejected attempt.
     *
     * @param source the player attempting to recall
     * @return result with recall messages; {@link GameActionResult#updatedSource()} is always
     *         {@code null} since recall does not otherwise modify the player
     */
    public GameActionResult recall(Player source) {
        Objects.requireNonNull(source, "Source is required");
        if (duelService.isDueling(source.getUsername())) {
            return GameActionResult.error("You cannot recall while dueling!");
        }
        if (inCombatCheck.test(source)) {
            return GameActionResult.error("You are in combat! You must FLEE before you can recall.");
        }
        if (cooldownTracker.isOnCooldown(RECALL_COOLDOWN_KEY)) {
            int remaining = cooldownTracker.remainingTicks(RECALL_COOLDOWN_KEY);
            return GameActionResult.error(
                "You are still recovering from your last recall (" + remaining + " ticks remaining).");
        }
        RoomService.LookResult currentLook = roomService.look(source.getUsername());
        Room oldRoom = currentLook.room();
        var destinationId = roomService.respawnPlayer(source.getUsername());
        cooldownTracker.startCooldown(RECALL_COOLDOWN_KEY, RECALL_COOLDOWN_TICKS);

        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You recall to town in a flash of light."));
        String playerName = source.getUsername().getValue();
        if (oldRoom != null && !oldRoom.getId().equals(destinationId)) {
            messages.add(GameMessage.toRoomAt(
                oldRoom.getId(), source.getUsername(), playerName + " vanishes in a flash of light."));
        }
        messages.add(GameMessage.toRoomAt(
            destinationId, source.getUsername(), playerName + " arrives in a flash of light."));

        return new GameActionResult(null, null, messages, Map.of(RECALL_METADATA_KEY, true));
    }

    /**
     * Resolves the Cleric RESURRECTION spell: revives a dead party member at the caster's location,
     * refunding the gold their corpse holds.
     *
     * <p>This spell sits outside the generic ability effect pipeline because its target is dead and
     * has no room location, so it is dispatched here from {@link #useAbility} (the command-only pattern
     * of the rogue PICK skill, AGENTS.md §3.3). It succeeds only when the named target:
     * <ul>
     *   <li>is a member of the caster's party ({@link PartyService#inSameParty}),</li>
     *   <li>is currently dead ({@link Player#isDead()}), and</li>
     *   <li>still has an un-decayed corpse tracked by {@link RoomService} (cast before
     *       {@link DeathSettings#corpseDecaySeconds()} elapses).</li>
     * </ul>
     * On success the target is restored via the half-vitals {@link Player#respawn()} transition, moved
     * to the caster's current room, refunded {@link Corpse#gold()}, and the corpse is consumed. Every
     * rejection (blank name, not partied, not dead, decayed corpse, on cooldown, insufficient mana)
     * returns a specific message and spends no mana. All state mutation happens on the tick thread
     * (AGENTS.md §5).
     *
     * @param caster     the Cleric casting the spell
     * @param targetName the raw name of the party member to revive
     * @return a result carrying the mana-charged caster as {@link GameActionResult#updatedSource()}
     *         and the revived target as {@link GameActionResult#updatedTarget()} on success, or an
     *         error result describing the rejection
     */
    public GameActionResult resurrect(Player caster, String targetName) {
        Objects.requireNonNull(caster, "Caster is required");
        String normalized = targetName == null ? "" : targetName.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Resurrect whom?");
        }
        if (cooldownTracker.isOnCooldown(RESURRECTION_ABILITY_ID)) {
            int remaining = cooldownTracker.remainingTicks(RESURRECTION_ABILITY_ID);
            return GameActionResult.error(
                "You cannot channel another resurrection yet (" + remaining + " ticks remaining).");
        }
        Ability spell = abilityRegistry.findById(RESURRECTION_ABILITY_ID).orElse(null);
        if (spell == null) {
            return GameActionResult.error("You do not know how to resurrect the dead.");
        }
        Player target = onlinePlayerLookup.find(Username.of(normalized)).orElse(null);
        if (target == null) {
            return GameActionResult.error("There is no one by that name to resurrect.");
        }
        if (target.getUsername().equals(caster.getUsername())) {
            return GameActionResult.error("You cannot resurrect yourself.");
        }
        if (partyService == null
            || !partyService.inSameParty(caster.getUsername(), target.getUsername())) {
            return GameActionResult.error(
                target.getUsername().getValue() + " is not in your party.");
        }
        if (!target.isDead()) {
            return GameActionResult.error(
                target.getUsername().getValue() + " is not dead.");
        }
        Corpse corpse = roomService.findCorpseByOwner(target.getUsername().getValue()).orElse(null);
        Instant decayCutoff = Instant.now().minusSeconds(DeathSettings.corpseDecaySeconds());
        if (corpse == null || !corpse.spawnedAt().isAfter(decayCutoff)) {
            return GameActionResult.error(
                target.getUsername().getValue()
                    + "'s corpse has decayed to dust. There is nothing left to restore.");
        }
        RoomId casterRoom = roomService.findPlayerLocation(caster.getUsername()).orElse(null);
        if (casterRoom == null) {
            return GameActionResult.error("You are nowhere; you cannot anchor a resurrection here.");
        }
        if (!abilityCostResolver.canAfford(caster, spell.cost())) {
            return GameActionResult.error("You do not have enough mana to cast resurrection.");
        }

        Player updatedCaster = abilityCostResolver.applyCost(caster, spell.cost());
        cooldownTracker.startCooldown(RESURRECTION_ABILITY_ID, spell.cooldown().ticks());

        Player revived = target.respawn().addGold(corpse.gold());
        roomService.movePlayerTo(revived.getUsername(), casterRoom);
        roomService.removeCorpse(corpse);

        String casterName = updatedCaster.getUsername().getValue();
        String revivedName = revived.getUsername().getValue();
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You call upon the light to restore " + revivedName + " to life."));
        messages.add(GameMessage.toPlayer(revived.getUsername(),
            casterName + " calls upon the light, and life floods back into your body."));
        messages.add(GameMessage.toPlayer(revived.getUsername(),
            "You have been resurrected."));
        messages.add(GameMessage.toRoom(updatedCaster.getUsername(), revived.getUsername(),
            casterName + " calls upon the light to restore " + revivedName + " to life."));

        return new GameActionResult(updatedCaster, revived, messages);
    }

    /**
     * Attempts to flee from active combat by disengaging and choosing a random available exit.
     *
     * <p>This is the flee game rule, kept out of the transport adapter so it is deterministic and
     * unit-testable without sockets (AGENTS.md §5, §10). Fails without any state change when the
     * player is not currently in combat, or when their room has no exits to flee through. On success
     * it picks one of the room's exits uniformly at random through the seeded {@link CombatRandom}
     * port, applies the injected combat-disengage mutation, and returns the chosen direction so the
     * caller can perform the actual movement.
     *
     * @param source      the player attempting to flee
     * @param currentRoom the player's current room, or {@code null} when their location is unknown
     * @return a {@link FleeResult} describing the outcome and, on success, the chosen exit direction
     */
    public FleeResult flee(Player source, Room currentRoom) {
        Objects.requireNonNull(source, "Source is required");
        if (duelService.isDueling(source.getUsername())) {
            return FleeResult.failure("You cannot flee from a duel!");
        }
        if (!inCombatCheck.test(source)) {
            return FleeResult.failure("You are not in combat.");
        }
        if (currentRoom == null || currentRoom.getExits().isEmpty()) {
            return FleeResult.failure("There is nowhere to flee!");
        }
        List<Direction> exits = new ArrayList<>(currentRoom.getExits().keySet());
        Direction chosen = exits.get(worldRandom.roll(0, exits.size() - 1));
        combatDisengage.accept(source);
        return FleeResult.success(chosen, "You flee to the " + chosen.label() + "!");
    }

    /**
     * Resolves an attack against a target player in the same room.
     *
     * @param source the attacking player
     * @param targetInput the raw target input string
     * @return result with updated target and combat messages
     */
    public GameActionResult attack(Player source, String targetInput) {
        if (encumbranceService.isOverburdened(source)) {
            return GameActionResult.error("You are carrying too much to do that.");
        }
        String normalized = targetInput == null ? "" : targetInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Usage: attack <target>");
        }
        Optional<Player> targetMatch = abilityTargetResolver.resolve(source, normalized);
        if (targetMatch.isEmpty()) {
            return GameActionResult.error("No such target to attack.");
        }
        Player target = targetMatch.get();
        if (target.getUsername().equals(source.getUsername())) {
            return GameActionResult.error("You cannot attack yourself.");
        }
        try {
            Weather weather = weatherFor(source);
            CombatAction combatAction = new CombatAction(
                source, target, resolveAttackId(source),
                weather.combatHitModifier(), weather.rangedHitModifier());
            CombatResult result = combatEngine.resolve(combatAction);
            List<GameMessage> messages = new ArrayList<>();
            if (result.sourceMessage() != null && !result.sourceMessage().isBlank()) {
                messages.add(GameMessage.toSource(result.sourceMessage()));
            }
            if (result.targetMessage() != null && !result.targetMessage().isBlank()) {
                messages.add(GameMessage.toPlayer(target.getUsername(), result.targetMessage()));
            }
            if (result.roomMessage() != null && !result.roomMessage().isBlank()) {
                messages.add(GameMessage.toRoom(source.getUsername(), target.getUsername(), result.roomMessage()));
            }
            for (String effectTargetMessage : result.effectTargetMessages()) {
                messages.add(GameMessage.toPlayer(target.getUsername(), effectTargetMessage));
            }
            for (String effectRoomMessage : result.effectRoomMessages()) {
                messages.add(GameMessage.toRoom(source.getUsername(), target.getUsername(), effectRoomMessage));
            }
            // Dual-wield: an off-hand weapon adds a second, independent attack this round, reported
            // with its own messages so both hits are distinguishable (AGENTS.md §5 — same tick).
            CombatResult.OffhandResult offhand = result.offhand();
            if (offhand != null) {
                if (offhand.sourceMessage() != null && !offhand.sourceMessage().isBlank()) {
                    messages.add(GameMessage.toSource(offhand.sourceMessage()));
                }
                if (offhand.targetMessage() != null && !offhand.targetMessage().isBlank()) {
                    messages.add(GameMessage.toPlayer(target.getUsername(), offhand.targetMessage()));
                }
                if (offhand.roomMessage() != null && !offhand.roomMessage().isBlank()) {
                    messages.add(GameMessage.toRoom(source.getUsername(), target.getUsername(), offhand.roomMessage()));
                }
                for (String effectTargetMessage : offhand.effectTargetMessages()) {
                    messages.add(GameMessage.toPlayer(target.getUsername(), effectTargetMessage));
                }
                for (String effectRoomMessage : offhand.effectRoomMessages()) {
                    messages.add(GameMessage.toRoom(source.getUsername(), target.getUsername(), effectRoomMessage));
                }
            }
            Player combatTarget = result.target();
            Player updatedTarget;
            // Survivor state carried over from a resolved duel (with an incremented win count), or null.
            Player duelSurvivor = null;
            if (combatTarget.getVitals().hp() <= 0
                && duelService.areDueling(source.getUsername(), target.getUsername())) {
                // Duel loss: end the duel with no corpse, no gold/item drop, no XP or reputation.
                GameActionResult duelEnd = endPlayerDuel(source, combatTarget);
                updatedTarget = duelEnd.updatedTarget();
                duelSurvivor = duelEnd.updatedSource();
                messages.addAll(duelEnd.messages());
            } else {
                GameActionResult deathResult = resolveDeathIfNeeded(combatTarget, source);
                updatedTarget = deathResult.updatedTarget();
                messages.addAll(deathResult.messages());
            }
            // Attacking always breaks stealth (AGENTS.md §5 — same tick as the action).
            Player updatedSource = duelSurvivor;
            if (source.isStealthActive()) {
                Player stealthBase = updatedSource != null ? updatedSource : source;
                updatedSource = stealthBase.withStealth(false);
                messages.add(GameMessage.toSource("You emerge from the shadows."));
                messages.add(GameMessage.toRoom(
                    source.getUsername(), target.getUsername(),
                    source.getUsername().getValue() + " emerges from the shadows!"));
            }
            // Entering combat also throws the attacker off any mount they were riding.
            Player mountBase = updatedSource != null ? updatedSource : source;
            if (mountBase.isMounted()) {
                updatedSource = breakMountOnCombat(mountBase, messages);
            }
            // Parry riposte (issue #639): if the target parried and riposted, the CombatEngine already
            // reduced the attacker's vitals in result.attacker(). Fold that HP loss into the persisted
            // source so the counter-strike sticks, and resolve the attacker's own death if the riposte
            // proved lethal — symmetric to the target-death handling above, but only when the target
            // survived (a parry deals the target zero damage, so this is the common case).
            int riposteToSource = source.getVitals().hp() - result.attacker().getVitals().hp();
            if (riposteToSource > 0) {
                Player riposteBase = updatedSource != null ? updatedSource : source;
                Player riposted = riposteBase.withVitals(riposteBase.getVitals().damage(riposteToSource));
                if (riposted.getVitals().hp() <= 0 && updatedTarget.getVitals().hp() > 0) {
                    if (duelService.areDueling(source.getUsername(), target.getUsername())) {
                        GameActionResult duelEnd = endPlayerDuel(target, riposted);
                        updatedSource = duelEnd.updatedTarget();
                        updatedTarget = duelEnd.updatedSource();
                        messages.addAll(duelEnd.messages());
                    } else {
                        GameActionResult deathResult = resolveDeathIfNeeded(riposted, target);
                        updatedSource = deathResult.updatedTarget();
                        messages.addAll(deathResult.messages());
                    }
                } else {
                    updatedSource = riposted;
                }
            }
            Map<String, Object> meta = result.rngSeed() != 0L
                ? Map.of("rngSeed", result.rngSeed())
                : Map.of();
            return new GameActionResult(updatedSource, updatedTarget, messages, meta);
        } catch (RepositoryException | EffectRepositoryException e) {
            return GameActionResult.error("Combat failed: " + e.getMessage());
        }
    }

    /**
     * Uses an ability on a target in the same room.
     *
     * @param source the player using the ability
     * @param input the raw ability input string
     * @return result with updated source/target and ability messages
     */
    public GameActionResult useAbility(Player source, String input) {
        // Level gate: a save-edited or legacy character may hold an ability above its level. Refuse
        // to use any learned ability whose required level exceeds the caster's, mirroring the gate
        // enforced when learning it at the trainer (issue #522).
        Optional<AbilityMatch> levelCheck = abilityRegistry.findBestMatch(input, source.getLearnedAbilities());
        if (levelCheck.isPresent()) {
            Ability ability = levelCheck.get().ability();
            if (ability.level() > source.getLevel()) {
                return GameActionResult.error("You are not yet skilled enough to use "
                    + ability.name() + " (requires level " + ability.level() + ").");
            }
        }

        // The RESURRECTION spell targets a dead, locationless party member, so it cannot flow through
        // the generic effect pipeline (which resolves targets in the caster's room). Intercept it here
        // when the caster has learned it and route to the dedicated logic in resurrect() — mirroring
        // the command-only pattern used by the rogue PICK skill (AGENTS.md §3.3).
        Optional<AbilityMatch> resurrectionMatch = abilityRegistry
            .findBestMatch(input, source.getLearnedAbilities())
            .filter(match -> RESURRECTION_ABILITY_ID.equals(match.ability().id()));
        if (resurrectionMatch.isPresent()) {
            return resurrect(source, resurrectionMatch.get().remainingTarget());
        }

        CollectingAbilityMessageSink sink = new CollectingAbilityMessageSink();
        DefaultAbilityEffectResolver resolver = new DefaultAbilityEffectResolver(
            abilityEffectEngine, sink, AbilityEffectListener.noop(), characterAttributesResolver
        );
        AbilityEngine engine = new AbilityEngine(abilityRegistry, abilityCostResolver, resolver, sink);

        boolean struckFromStealth = source.isStealthActive();

        AbilityUseResult result = engine.use(
            source, input, source.getLearnedAbilities(),
            abilityTargetResolver, cooldownTracker, inCombatCheck
        );

        List<GameMessage> messages = new ArrayList<>(sink.collected());
        for (String message : result.messages()) {
            messages.add(GameMessage.toSource(message));
        }

        Player updatedSource = result.source();
        Player updatedTarget = result.target();

        // Stealth resolution: a genuine ability use against a distinct target breaks stealth;
        // a backstab landed from stealth deals bonus damage first. This runs synchronously in the
        // same tick as the ability so no reader thread ever observes a half-broken stealth state.
        boolean hitDistinctTarget = !updatedTarget.getUsername().equals(updatedSource.getUsername());
        if (struckFromStealth && hitDistinctTarget) {
            if (isBackstab(input, source.getLearnedAbilities())) {
                PlayerVitals boosted = updatedTarget.getVitals().damage(STEALTH_BACKSTAB_BONUS_DAMAGE);
                updatedTarget = updatedTarget.withVitals(boosted);
                messages.add(GameMessage.toSource(
                    "Your strike from the shadows lands with deadly precision (+"
                        + STEALTH_BACKSTAB_BONUS_DAMAGE + " damage)!"));
            }
            updatedSource = updatedSource.withStealth(false);
            messages.add(GameMessage.toSource("You emerge from the shadows."));
            messages.add(GameMessage.toRoom(
                source.getUsername(), updatedTarget.getUsername(),
                source.getUsername().getValue() + " emerges from the shadows!"));
        }

        // A hostile ability landed on a distinct target throws the caster off any mount they rode.
        if (updatedSource.isMounted() && hitDistinctTarget) {
            updatedSource = breakMountOnCombat(updatedSource, messages);
        }

        boolean duelKill = updatedTarget.getVitals().hp() <= 0
            && !updatedTarget.getUsername().equals(updatedSource.getUsername())
            && duelService.areDueling(updatedSource.getUsername(), updatedTarget.getUsername());
        if (duelKill) {
            // Duel loss via an ability: end the duel with no corpse, gold, XP, or reputation.
            GameActionResult duelEnd = endPlayerDuel(updatedSource, updatedTarget);
            updatedTarget = duelEnd.updatedTarget();
            if (duelEnd.updatedSource() != null) {
                updatedSource = duelEnd.updatedSource();
            }
            messages.addAll(duelEnd.messages());
        } else {
            GameActionResult deathResult = resolveDeathIfNeeded(updatedTarget, updatedSource);
            updatedTarget = deathResult.updatedTarget();
            messages.addAll(deathResult.messages());
        }

        if (updatedTarget.getUsername().equals(updatedSource.getUsername())) {
            updatedSource = updatedTarget;
        }

        return new GameActionResult(updatedSource, updatedTarget, messages);
    }

    /**
     * Returns whether the given raw ability {@code input} resolves to the rogue BACKSTAB skill
     * among the player's learned abilities.
     */
    private boolean isBackstab(String input, List<AbilityId> learnedAbilities) {
        return abilityRegistry.findBestMatch(input, learnedAbilities)
            .map(match -> BACKSTAB_ABILITY_ID.equals(match.ability().id()))
            .orElse(false);
    }

    /**
     * Picks up an item from the current room.
     *
     * @param source the player picking up the item
     * @param itemInput the item name or id to pick up
     * @return result with updated source inventory
     */
    public GameActionResult getItem(Player source, String itemInput) {
        if (encumbranceService.isOverburdened(source)) {
            return GameActionResult.error("You are carrying too much to do that.");
        }
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Get what?");
        }
        RoomService.LookResult look = roomService.look(source.getUsername());
        Room room = look.room();
        if (room == null) {
            return GameActionResult.error("You cannot get items here.");
        }
        Optional<Item> item = roomService.takeItem(source.getUsername(), normalized);
        if (item.isEmpty()) {
            return GameActionResult.error("You don't see that here.");
        }
        Player updated = source.addItem(item.get());
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.get().getName(),
            null,
            null,
            null
        );
        List<GameMessage> emitted = messageEmitter.emit(item.get().getMessages(), MessagePhase.PICKUP, context);
        if (emitted.isEmpty()) {
            messages.add(GameMessage.toSource("You pick up " + item.get().getName() + "."));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " picks up " + item.get().getName() + "."
            ));
        } else {
            messages.addAll(emitted);
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Drops an item from inventory into the current room.
     *
     * @param source the player dropping the item
     * @param itemInput the item name or id to drop
     * @return result with updated source inventory
     */
    public GameActionResult dropItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Drop what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        roomService.dropItem(source.getUsername(), item);
        PlayerEquipment equipment = source.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        Player updated = source.removeItem(item).withEquipment(equipment);
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.getName(),
            null,
            null,
            null
        );
        List<GameMessage> emitted = messageEmitter.emit(item.getMessages(), MessagePhase.DROP, context);
        if (emitted.isEmpty()) {
            messages.add(GameMessage.toSource("You drop " + item.getName() + "."));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " drops " + item.getName() + "."
            ));
        } else {
            messages.addAll(emitted);
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Gives an item from the source player's inventory to another player in the same room.
     *
     * <p>The item is removed from the source's inventory (unequipping it first if worn, matching
     * {@link #dropItem}'s behavior) and added to the recipient's inventory. Fails without any
     * state change if the item is not in the source's inventory, or if giving it would leave the
     * recipient {@linkplain EncumbranceService#isOverburdened(Player) overburdened}.
     *
     * @param source the player giving the item
     * @param target the recipient player, already confirmed to be online and in the same room
     * @param itemInput the item name or id to give
     * @return result with updated source and target inventories
     */
    public GameActionResult giveItem(Player source, Player target, String itemInput) {
        if (target == null) {
            return GameActionResult.error("That player is not here.");
        }
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Give what?");
        }
        if (target.getUsername().equals(source.getUsername())) {
            return GameActionResult.error("You cannot give an item to yourself.");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        Player updatedTarget = target.addItem(item);
        if (encumbranceService.isOverburdened(updatedTarget)) {
            return GameActionResult.error(target.getUsername().getValue() + " cannot carry any more.");
        }
        PlayerEquipment equipment = source.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        Player updatedSource = source.removeItem(item).withEquipment(equipment);

        String targetName = target.getUsername().getValue();
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You give " + item.getName() + " to " + targetName + "."));
        messages.add(GameMessage.toPlayer(
            target.getUsername(),
            source.getUsername().getValue() + " gives you " + item.getName() + "."
        ));
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            target.getUsername(),
            source.getUsername().getValue() + " gives " + item.getName() + " to " + targetName + "."
        ));

        return new GameActionResult(updatedSource, updatedTarget, messages);
    }

    /**
     * Places an item from the player's inventory into a container the player is carrying.
     *
     * <p>Validation happens before any state change: the container must be carried and actually be
     * a {@linkplain Item#isContainer() container}, the item must be carried, it may not be the
     * container itself nor another container (nesting is not supported), and the container must not
     * be {@linkplain Item#isFull() full}. On success the item leaves the top-level inventory and is
     * packed inside the container (contents are weightless while stored). A worn item is unequipped
     * first, mirroring {@link #dropItem}.
     *
     * @param source        the player performing the PUT
     * @param itemInput      the item name or id to place inside the container
     * @param containerInput the container name or id to place the item into
     * @return result with the updated source inventory, or an error with no state change
     */
    // Identity comparisons below (inv == item, inv == container) are intentional: item and container
    // are the exact instances resolved from this inventory, so identity removes/replaces precisely
    // those references without disturbing value-equal duplicates elsewhere in the inventory.
    @SuppressWarnings("ReferenceEquality")
    public GameActionResult putItem(Player source, String itemInput, String containerInput) {
        String itemNorm = itemInput == null ? "" : itemInput.trim();
        String containerNorm = containerInput == null ? "" : containerInput.trim();
        if (itemNorm.isEmpty() || containerNorm.isEmpty()) {
            return GameActionResult.error("Usage: put <item> into <container>");
        }
        Item container = findInventoryItem(source, containerNorm);
        if (container == null) {
            return GameActionResult.error("You aren't carrying " + containerNorm + ".");
        }
        if (!container.isContainer()) {
            return GameActionResult.error(container.getName() + " is not a container.");
        }
        Item item = findInventoryItem(source, itemNorm);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        if (item.getId().equals(container.getId())) {
            return GameActionResult.error("You can't put something inside itself.");
        }
        if (item.isContainer()) {
            return GameActionResult.error("You can't put a container inside another container.");
        }
        if (container.isFull()) {
            return GameActionResult.error(container.getName() + " is full.");
        }
        Item updatedContainer = container.withContainedItem(item);
        List<Item> next = new ArrayList<>();
        boolean itemRemoved = false;
        boolean containerReplaced = false;
        for (Item inv : source.getInventory()) {
            if (!itemRemoved && inv == item) {
                itemRemoved = true;
                continue;
            }
            if (!containerReplaced && inv == container) {
                next.add(updatedContainer);
                containerReplaced = true;
                continue;
            }
            next.add(inv);
        }
        PlayerEquipment equipment = source.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        Player updated = source.withInventory(next).withEquipment(equipment);
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You put " + item.getName() + " into " + container.getName() + "."));
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            null,
            source.getUsername().getValue() + " puts " + item.getName() + " into " + container.getName() + "."
        ));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Removes an item from a container the player is carrying and moves it into the top-level
     * inventory.
     *
     * <p>Validation happens before any state change: the container must be carried and actually be
     * a {@linkplain Item#isContainer() container}, and it must hold an item matching
     * {@code itemInput}. On success the item is added back to the carried inventory (regaining its
     * carry weight) and removed from the container's contents.
     *
     * @param source        the player performing the GET FROM
     * @param itemInput      the item name or id to retrieve from the container
     * @param containerInput the container name or id to retrieve the item from
     * @return result with the updated source inventory, or an error with no state change
     */
    // Identity comparison below (inv == container) is intentional: container is the exact instance
    // resolved from this inventory, so identity replaces precisely that reference without disturbing
    // value-equal duplicates elsewhere in the inventory.
    @SuppressWarnings("ReferenceEquality")
    public GameActionResult getFromContainer(Player source, String itemInput, String containerInput) {
        String itemNorm = itemInput == null ? "" : itemInput.trim();
        String containerNorm = containerInput == null ? "" : containerInput.trim();
        if (itemNorm.isEmpty() || containerNorm.isEmpty()) {
            return GameActionResult.error("Usage: get <item> from <container>");
        }
        Item container = findInventoryItem(source, containerNorm);
        if (container == null) {
            return GameActionResult.error("You aren't carrying " + containerNorm + ".");
        }
        if (!container.isContainer()) {
            return GameActionResult.error(container.getName() + " is not a container.");
        }
        if (container.isLocked()) {
            return GameActionResult.error(container.getName() + " is locked.");
        }
        Item contained = matchItem(container.getContainedItems(), itemNorm);
        if (contained == null) {
            return GameActionResult.error("There is no " + itemNorm + " in " + container.getName() + ".");
        }
        Item updatedContainer = container.withoutContainedItem(contained.getId());
        List<Item> next = new ArrayList<>();
        boolean containerReplaced = false;
        for (Item inv : source.getInventory()) {
            if (!containerReplaced && inv == container) {
                next.add(updatedContainer);
                containerReplaced = true;
                continue;
            }
            next.add(inv);
        }
        next.add(contained);
        Player updated = source.withInventory(next);
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You get " + contained.getName() + " from " + container.getName() + "."));
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            null,
            source.getUsername().getValue() + " gets " + contained.getName() + " from " + container.getName() + "."
        ));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Attempts the rogue PICK skill on a locked container in the player's current room.
     *
     * <p>Only rogues of level 1 or higher may pick locks. The named target must be a container in
     * the room that is currently locked. Two independent rolls resolve the attempt (§5 determinism —
     * both go through {@link ContainerLockingService}'s injected RNG):
     * <ul>
     *   <li>a pick-success roll whose chance scales with rogue level; on success the container is
     *       unlocked in place (persisted for static room items, swapped for transient ones);</li>
     *   <li>a trap roll, independent of the pick outcome; when it triggers, the rogue takes
     *       {@code 5–15} HP of damage. A failed pick by itself never deals damage.</li>
     * </ul>
     *
     * @param source         the player attempting the pick
     * @param containerInput the container name or id to pick
     * @return result with the (possibly damaged) source player and outcome messages, or an error
     */
    public GameActionResult pickLock(Player source, String containerInput) {
        String normalized = containerInput == null ? "" : containerInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Pick what?");
        }
        if (!isRogue(source)) {
            return GameActionResult.error("Only rogues know how to pick locks.");
        }
        int level = source.getLevel();
        if (level < 1) {
            return GameActionResult.error("You are not skilled enough to pick locks.");
        }
        Optional<Item> found = roomService.findItem(source.getUsername(), normalized);
        if (found.isEmpty()) {
            return GameActionResult.error("You don't see " + normalized + " here.");
        }
        Item container = found.get();
        if (!container.isContainer()) {
            return GameActionResult.error(container.getName() + " is not a container.");
        }
        if (!container.isLocked()) {
            return GameActionResult.error(container.getName() + " isn't locked.");
        }
        boolean pickSucceeded = containerLockingService.rollPickSuccess(level);
        boolean trapTriggered = containerLockingService.shouldTrapTrigger();
        List<GameMessage> messages = new ArrayList<>();
        Player updated = source;
        if (trapTriggered) {
            int damage = containerLockingService.rollTrapDamage();
            PlayerVitals damaged = source.getVitals().damage(damage);
            updated = source.withVitals(damaged);
            messages.add(GameMessage.toSource(
                "A hidden trap on " + container.getName() + " springs, dealing " + damage + " damage!"));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " springs a trap while fiddling with " + container.getName() + "."
            ));
        }
        if (pickSucceeded) {
            Item unlocked = containerLockingService.unlockContainer(container);
            roomService.replaceItem(source.getUsername(), container.getId(), unlocked);
            messages.add(GameMessage.toSource("You deftly pick the lock on " + container.getName() + "."));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " picks the lock on " + container.getName() + "."
            ));
        } else {
            messages.add(GameMessage.toSource("You fail to pick the lock on " + container.getName() + "."));
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Toggles the rogue stealth (SNEAK/HIDE) state on the given player.
     *
     * <p>Only rogues may sneak. When stealth is inactive it is activated ("You fade into the
     * shadows"); when already active it is deactivated ("You emerge from stealth"). While hidden a
     * rogue is skipped by aggressive mobs choosing a fresh target (see
     * {@link io.taanielo.jmud.core.mob.MobRegistry}); the state is cleared automatically the moment
     * the rogue attacks, casts an ability, or uses a skill (see {@link #useAbility} and
     * {@link #attack}). The flag lives on the {@link Player} aggregate and is mutated only on the
     * tick thread (AGENTS.md §5).
     *
     * @param source the player toggling stealth
     * @return result with the updated (hidden/revealed) player and outcome messages, or an error
     */
    public GameActionResult sneakToggle(Player source) {
        if (!isRogue(source)) {
            return GameActionResult.error("Only rogues know how to move unseen.");
        }
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        boolean activating = !source.isStealthActive();
        Player updated = source.withStealth(activating);
        List<GameMessage> messages = new ArrayList<>();
        if (activating) {
            messages.add(GameMessage.toSource("You fade into the shadows."));
            messages.add(GameMessage.toRoom(
                source.getUsername(), null,
                source.getUsername().getValue() + " slips into the shadows and vanishes."));
        } else {
            messages.add(GameMessage.toSource("You emerge from stealth."));
            messages.add(GameMessage.toRoom(
                source.getUsername(), null,
                source.getUsername().getValue() + " steps out of the shadows."));
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Attempts the SEARCH skill: looks for any undiscovered hidden exits in the player's current
     * room and, on a successful roll, reveals them for every player.
     *
     * <p>SEARCH takes no arguments, is available to every class and level, and may be repeated
     * freely. It is rejected while the player is dead or engaged in combat (mirroring other
     * non-combat actions). Searching breaks stealth like any other deliberate action. When there is
     * nothing left to find — the room has no hidden exits, or all have already been discovered — a
     * neutral "nothing new" line is shown. Otherwise the outcome is decided by a single roll through
     * the seeded {@link CombatRandom} port against
     * {@link #calculateSearchSuccessChance(boolean, int)}; a success reveals the exit (via
     * {@link RoomService#revealHiddenExits(Username)}) but never unlocks it. Non-rogues use a flat
     * {@link #SEARCH_BASE_SUCCESS_CHANCE}; rogues gain a per-level bonus (capped at
     * {@link #SEARCH_ROGUE_MAX_SUCCESS_CHANCE}), mirroring the PICK skill's scaling.
     *
     * <p>Runs on the tick thread (AGENTS.md §5).
     *
     * @param source the player searching
     * @return result with the (possibly un-stealthed) player and outcome messages
     */
    public GameActionResult searchForHiddenExits(Player source) {
        Objects.requireNonNull(source, "Source player is required");
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        if (inCombatCheck.test(source)) {
            return GameActionResult.error("You are too busy fighting to search the room.");
        }
        List<GameMessage> messages = new ArrayList<>();
        Player updated = source;
        if (source.isStealthActive()) {
            updated = source.withStealth(false);
            messages.add(GameMessage.toSource("You emerge from the shadows."));
            messages.add(GameMessage.toRoom(
                source.getUsername(), null,
                source.getUsername().getValue() + " emerges from the shadows!"));
        }
        Set<Direction> undiscovered = roomService.undiscoveredHiddenExits(source.getUsername());
        if (undiscovered.isEmpty()) {
            messages.add(GameMessage.toSource("You search but find nothing new."));
            return new GameActionResult(updated, null, messages);
        }
        double successChance = calculateSearchSuccessChance(isRogue(source), source.getLevel());
        if (worldRandom.nextDouble() >= successChance) {
            messages.add(GameMessage.toSource("You search but find nothing new."));
            return new GameActionResult(updated, null, messages);
        }
        Set<Direction> revealed = roomService.revealHiddenExits(source.getUsername());
        if (revealed.isEmpty()) {
            revealed = undiscovered;
        }
        String directions = revealed.stream()
            .sorted(Comparator.comparing(Direction::label))
            .map(Direction::label)
            .collect(Collectors.joining(", "));
        messages.add(GameMessage.toSource("You discover a hidden passage leading " + directions + "!"));
        messages.add(GameMessage.toRoom(
            source.getUsername(), null,
            source.getUsername().getValue() + " uncovers a hidden passage!"));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Calculates the probability, in {@code [0, 1]}, that a single SEARCH attempt uncovers a hidden
     * exit. Non-rogues always use the flat {@link #SEARCH_BASE_SUCCESS_CHANCE}. Rogues add
     * {@link #SEARCH_ROGUE_SUCCESS_CHANCE_PER_LEVEL} per level on top of the base chance, capped at
     * {@link #SEARCH_ROGUE_MAX_SUCCESS_CHANCE} so a search is never guaranteed — mirroring the shape
     * of {@link io.taanielo.jmud.core.world.ContainerLockingService#calculatePickSuccessChance(int)}.
     *
     * <p>Pure function of {@code (class, level)}, with no RNG or state, so it is unit-testable
     * independently of the roll.
     *
     * @param isRogue whether the searching player is a rogue
     * @param level   the player's level (only used for rogues)
     * @return the SEARCH success probability in {@code [0, 1]}
     */
    static double calculateSearchSuccessChance(boolean isRogue, int level) {
        if (!isRogue) {
            return SEARCH_BASE_SUCCESS_CHANCE;
        }
        double bonusLevels = Math.max(0, level);
        double chance = SEARCH_BASE_SUCCESS_CHANCE + SEARCH_ROGUE_SUCCESS_CHANCE_PER_LEVEL * bonusLevels;
        return Math.min(SEARCH_ROGUE_MAX_SUCCESS_CHANCE, chance);
    }

    /**
     * Saddles the player up on a rideable mount they own, reducing their per-step travel cost while
     * ridden.
     *
     * <p>The named mount must be a {@linkplain Item#isMount() mount item} the player is carrying, the
     * player must not already be mounted, and mounts may only be summoned in an
     * {@linkplain Room#isOutdoor() outdoor} room (never indoors or underground). The ridden state is
     * transient — it lives on the {@link Player} aggregate, is never persisted, and is broken
     * automatically the moment the rider enters combat (see {@link #attack} and {@link #useAbility})
     * or moves indoors. Mutated only on the tick thread (AGENTS.md §5).
     *
     * @param source    the player mounting up
     * @param mountName the name (or a fragment of it) of the mount item to ride
     * @return result with the updated (mounted) player and outcome messages, or an error
     */
    public GameActionResult mount(Player source, String mountName) {
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        if (source.isMounted()) {
            return GameActionResult.error("You are already mounted on " + source.mount().mountName() + ".");
        }
        String query = mountName == null ? "" : mountName.trim();
        if (query.isEmpty()) {
            return GameActionResult.error("Usage: MOUNT <mount>");
        }
        RoomService.LookResult look = roomService.look(source.getUsername());
        Room room = look.room();
        if (room != null && !room.isOutdoor()) {
            return GameActionResult.error("You cannot ride a mount indoors or underground.");
        }
        String lowered = query.toLowerCase(Locale.ROOT);
        Item mountItem = null;
        boolean ownsMatchingItem = false;
        for (Item item : source.getInventory()) {
            if (!item.getName().toLowerCase(Locale.ROOT).contains(lowered)) {
                continue;
            }
            ownsMatchingItem = true;
            if (item.isMount()) {
                mountItem = item;
                break;
            }
        }
        if (mountItem == null) {
            if (ownsMatchingItem) {
                return GameActionResult.error("You cannot ride that.");
            }
            return GameActionResult.error("You do not own a mount called \"" + query + "\".");
        }
        Integer discount = mountItem.getMountMoveDiscount();
        PlayerMount ridden = PlayerMount.riding(mountItem.getName(), discount == null ? 0 : discount);
        Player updated = source.withMount(ridden);
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You climb onto " + mountItem.getName() + ". Your travel is swifter now (reduced move cost)."));
        messages.add(GameMessage.toRoom(
            source.getUsername(), null,
            source.getUsername().getValue() + " mounts " + mountItem.getName() + "."));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Puts away the player's current mount, returning them to normal per-step travel cost.
     *
     * @param source the player dismounting
     * @return result with the updated (dismounted) player and outcome messages, or an error when the
     *         player is not riding anything
     */
    public GameActionResult dismount(Player source) {
        if (!source.isMounted()) {
            return GameActionResult.error("You are not riding anything.");
        }
        String name = source.mount().mountName();
        Player updated = source.withMount(PlayerMount.dismounted());
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You dismount " + name + ". Your travel returns to normal."));
        messages.add(GameMessage.toRoom(
            source.getUsername(), null,
            source.getUsername().getValue() + " dismounts " + name + "."));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Auto-dismounts a rider the instant they enter combat, appending the self/room messages to the
     * supplied list, mirroring how attacking breaks stealth. Returns the player unchanged (and adds
     * no messages) when they are not currently mounted.
     *
     * @param player   the combatant who may be mounted
     * @param messages the running message list to append the dismount lines to
     * @return the dismounted player, or the same instance when they were already on foot
     */
    private Player breakMountOnCombat(Player player, List<GameMessage> messages) {
        if (!player.isMounted()) {
            return player;
        }
        String name = player.mount().mountName();
        messages.add(GameMessage.toSource(
            "The clash of combat spooks " + name + " and you drop down to fight on foot!"));
        messages.add(GameMessage.toRoom(
            player.getUsername(), null,
            player.getUsername().getValue() + " leaps down from " + name + " as battle is joined."));
        return player.withMount(PlayerMount.dismounted());
    }

    /**
     * Attempts the rogue STEAL skill to pickpocket gold from a target NPC in the player's current
     * room.
     *
     * <p>Only rogues of level 1 or higher may steal. The named target must be a live mob in the
     * room; a mob carrying no gold ("nothing worth stealing") is rejected before any roll. A single
     * success roll — whose chance scales with rogue level, capped at
     * {@link #STEAL_MAX_SUCCESS_PERCENT}% and rolled through the seeded {@link CombatRandom} port for
     * determinism (§5) — resolves the attempt:
     * <ul>
     *   <li>on success, gold is transferred from the NPC to the thief and a discreet-lift message
     *       is returned;</li>
     *   <li>on failure, the NPC is {@linkplain NpcStealPort.StealVictim#turnHostile(io.taanielo.jmud.core.authentication.Username)
     *       turned hostile} (aggressing the thief on the next tick) and a discovery message is
     *       returned.</li>
     * </ul>
     *
     * @param source   the player attempting the theft
     * @param npcInput the NPC name to steal from
     * @return result with the (possibly gold-richer) source player and outcome messages, or an error
     */
    public GameActionResult steal(Player source, String npcInput) {
        Objects.requireNonNull(source, "Source is required");
        String normalized = npcInput == null ? "" : npcInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Steal from whom?");
        }
        if (!isRogue(source)) {
            return GameActionResult.error("Only rogues know how to pick pockets.");
        }
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        int level = source.getLevel();
        if (level < 1) {
            return GameActionResult.error("You are not skilled enough to steal.");
        }
        Optional<RoomId> roomId = roomService.findPlayerLocation(source.getUsername());
        if (roomId.isEmpty()) {
            return GameActionResult.error("You don't see " + normalized + " here.");
        }
        Optional<NpcStealPort.StealVictim> found = npcStealPort.findStealTarget(roomId.get(), normalized);
        if (found.isEmpty()) {
            return GameActionResult.error("You don't see " + normalized + " here.");
        }
        NpcStealPort.StealVictim victim = found.get();
        if (!victim.hasStealableGold()) {
            return GameActionResult.error("The " + victim.name() + " has nothing worth stealing.");
        }
        List<GameMessage> messages = new ArrayList<>();
        if (rollStealSuccess(level)) {
            int gold = victim.stealGold();
            if (gold <= 0) {
                return GameActionResult.error("The " + victim.name() + " has nothing worth stealing.");
            }
            Player updated = source.addGold(gold);
            messages.add(GameMessage.toSource(
                "You deftly lift " + gold + " gold coin" + (gold == 1 ? "" : "s")
                    + " from the " + victim.name() + "."));
            return new GameActionResult(updated, null, messages);
        }
        victim.turnHostile(source.getUsername());
        messages.add(GameMessage.toSource(
            "You are caught red-handed! The " + victim.name() + " turns on you!"));
        messages.add(GameMessage.toRoom(
            source.getUsername(), null,
            source.getUsername().getValue() + " is caught trying to rob the " + victim.name() + "!"));
        return new GameActionResult(null, null, messages);
    }

    /**
     * Rolls whether a STEAL attempt by a rogue of the given level succeeds. The success chance is
     * {@link #STEAL_BASE_SUCCESS_PERCENT}% plus {@link #STEAL_SUCCESS_PERCENT_PER_LEVEL}% per level,
     * capped at {@link #STEAL_MAX_SUCCESS_PERCENT}%, rolled through the seeded {@link CombatRandom}
     * port so the outcome is deterministic under a world seed (AGENTS.md §5).
     *
     * @param level the thief's level (at least 1)
     * @return {@code true} when the pickpocket succeeds
     */
    private boolean rollStealSuccess(int level) {
        int chance = Math.min(
            STEAL_MAX_SUCCESS_PERCENT,
            STEAL_BASE_SUCCESS_PERCENT + STEAL_SUCCESS_PERCENT_PER_LEVEL * level);
        return worldRandom.roll(1, 100) <= chance;
    }

    /** Maximum number of rooms the ranger TRACK skill will explore before giving up. */
    private static final int TRACK_MAX_ROOMS_EXPLORED = 512;

    /**
     * Attempts the ranger TRACK skill, searching the world for the nearest mob whose name matches
     * the given type and returning a directional hint toward it.
     *
     * <p>Only rangers may track. Starting from the ranger's current room, this performs a
     * breadth-first walk of the room graph via {@link RoomService#getExits} (so distance is measured
     * in rooms) and, at each room, consults the injected {@link MobLocatorPort} for the live mobs
     * present. The nearest room containing a name match wins:
     * <ul>
     *   <li>a match in the ranger's own room yields "You sense a {@code type} in this room.";</li>
     *   <li>a match elsewhere yields "The {@code type} lies somewhere to the {@code direction}." where
     *       {@code direction} is the first step of the shortest path toward it.</li>
     * </ul>
     * The walk is bounded by {@link #TRACK_MAX_ROOMS_EXPLORED} so a pathological world cannot stall
     * the tick. This method only reads game state (AGENTS.md §5) and returns messages; it never
     * mutates the player or the world.
     *
     * @param source       the ranger attempting to track
     * @param mobTypeInput the mob type/name to search for (e.g. {@code "goblin"})
     * @return a directional hint on success, or an error when the caller is not a ranger, gives no
     *         target, or no matching mob can be found anywhere reachable
     */
    public GameActionResult track(Player source, String mobTypeInput) {
        Objects.requireNonNull(source, "Source is required");
        String normalized = mobTypeInput == null ? "" : mobTypeInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Track what?");
        }
        if (!isRanger(source)) {
            return GameActionResult.error("Only rangers know how to track.");
        }
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        Optional<RoomId> start = roomService.findPlayerLocation(source.getUsername());
        if (start.isEmpty()) {
            return GameActionResult.error("You are lost and cannot get your bearings.");
        }
        String query = normalized.toLowerCase(Locale.ROOT);
        Optional<TrackHit> hit = findNearestMob(start.get(), query);
        if (hit.isEmpty()) {
            return GameActionResult.error(
                "You search for signs of " + normalized + " but find no trace anywhere.");
        }
        TrackHit found = hit.get();
        String message = found.direction() == null
            ? "You sense " + article(found.mobName()) + found.mobName() + " in this room."
            : "The " + found.mobName() + " lies somewhere to the " + found.direction().label() + ".";
        return new GameActionResult(null, null, List.of(GameMessage.toSource(message)));
    }

    /**
     * Points a player toward the target of their active kill quest, mirroring the ranger TRACK
     * skill but available to every class and race as read-only quest guidance.
     *
     * <p>Unlike {@link #track(Player, String)}, this variant matches on the mob's template id (the
     * quest's {@code targetMobId}) rather than its display name, so it finds the right quarry even
     * when the mob's display name differs from its id (e.g. id {@code "rat"} → name
     * {@code "Giant Rat"}). It reuses the same bounded breadth-first walk of the room graph as the
     * ranger skill via {@link #findNearest(RoomId, Function)}. The method only reads game state
     * (AGENTS.md §5) and never mutates the player or the world; it consumes no moves, mana, or
     * cooldown.
     *
     * @param source      the player consulting their quest; must not be null
     * @param targetMobId the template id of the quest's target mob, or {@code null} when the active
     *                    quest has no mob to hunt (delivery or exploration quests)
     * @return a directional hint on success; an error when the quest has no mob target, the player
     *         is lost or dead, or no matching mob can be found anywhere reachable
     */
    public GameActionResult trackQuestTarget(Player source, @Nullable String targetMobId) {
        Objects.requireNonNull(source, "Source is required");
        if (targetMobId == null || targetMobId.isBlank()) {
            return GameActionResult.error(
                "Your current contract has no quarry to hunt down. Use QUEST STATUS for details.");
        }
        if (source.isDead()) {
            return GameActionResult.error("You cannot do that right now.");
        }
        Optional<RoomId> start = roomService.findPlayerLocation(source.getUsername());
        if (start.isEmpty()) {
            return GameActionResult.error("You are lost and cannot get your bearings.");
        }
        String targetId = targetMobId.trim().toLowerCase(Locale.ROOT);
        Optional<TrackHit> hit = findNearest(start.get(), roomId -> firstMobNameByTemplateId(roomId, targetId));
        if (hit.isEmpty()) {
            return GameActionResult.error(
                "You search for signs of your quarry but find no trace anywhere.");
        }
        TrackHit found = hit.get();
        String message = found.direction() == null
            ? "You sense " + article(found.mobName()) + found.mobName() + " in this room."
            : "The " + found.mobName() + " lies somewhere to the " + found.direction().label() + ".";
        return new GameActionResult(null, null, List.of(GameMessage.toSource(message)));
    }

    /**
     * Breadth-first searches the room graph from {@code start} for the nearest room containing a
     * live mob whose name matches {@code query} (case-insensitive substring). Distance is measured
     * in rooms; the returned hit carries the display name and the first-step direction of the
     * shortest path (or {@code null} when the match is in the starting room). The search is bounded
     * by {@link #TRACK_MAX_ROOMS_EXPLORED}.
     */
    private Optional<TrackHit> findNearestMob(RoomId start, String query) {
        return findNearest(start, roomId -> firstMatchingMobName(roomId, query));
    }

    /**
     * Breadth-first searches the room graph from {@code start} for the nearest room where
     * {@code matcher} returns a non-null mob display name. Distance is measured in rooms; the
     * returned hit carries that display name and the first-step direction of the shortest path
     * (or {@code null} when the match is in the starting room). The search is bounded by
     * {@link #TRACK_MAX_ROOMS_EXPLORED} so a pathological world cannot stall the tick.
     */
    private Optional<TrackHit> findNearest(RoomId start, Function<RoomId, @Nullable String> matcher) {
        Deque<RoomId> frontier = new ArrayDeque<>();
        Map<RoomId, Direction> firstStep = new HashMap<>();
        Set<RoomId> visited = new HashSet<>();
        frontier.add(start);
        visited.add(start);
        int explored = 0;
        while (!frontier.isEmpty() && explored < TRACK_MAX_ROOMS_EXPLORED) {
            RoomId current = frontier.removeFirst();
            explored++;
            String match = matcher.apply(current);
            if (match != null) {
                return Optional.of(new TrackHit(match, firstStep.get(current)));
            }
            for (Map.Entry<Direction, RoomId> exit : roomService.getExits(current).entrySet()) {
                RoomId neighbour = exit.getValue();
                if (visited.add(neighbour)) {
                    // The first step from the start room is the exit's own direction; deeper rooms
                    // inherit the direction of the first hop that discovered them.
                    Direction step = current.equals(start) ? exit.getKey() : firstStep.get(current);
                    firstStep.put(neighbour, step);
                    frontier.add(neighbour);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the display name of the first live mob in the given room whose name contains
     * {@code query} (case-insensitive), or {@code null} when none match.
     */
    @Nullable
    private String firstMatchingMobName(RoomId roomId, String query) {
        for (String name : mobLocatorPort.liveMobNamesInRoom(roomId)) {
            if (name.toLowerCase(Locale.ROOT).contains(query)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Returns the display name of the first live mob in the given room whose template id equals
     * {@code targetId} (case-insensitive), or {@code null} when none match. Used by
     * {@code QUEST TRACK} to locate the quest's target mob by id.
     */
    @Nullable
    private String firstMobNameByTemplateId(RoomId roomId, String targetId) {
        for (MobLocatorPort.TrackableMob mob : mobLocatorPort.liveMobsInRoom(roomId)) {
            if (mob.templateId().toLowerCase(Locale.ROOT).equals(targetId)) {
                return mob.displayName();
            }
        }
        return null;
    }

    /** Returns {@code "a "} or {@code "an "} for the given name, for readable TRACK output. */
    private static String article(String name) {
        if (name.isEmpty()) {
            return "";
        }
        return "aeiouAEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ";
    }

    /**
     * A resolved TRACK result: the matched mob's display name and the first-step direction toward
     * it, or {@code null} direction when the mob is in the ranger's own room.
     */
    private record TrackHit(String mobName, @Nullable Direction direction) {
    }

    /**
     * Returns whether the given player belongs to the ranger class, the only class permitted to use
     * the TRACK skill.
     */
    private static boolean isRanger(Player player) {
        return player.getClassId() != null
            && "ranger".equalsIgnoreCase(player.getClassId().getValue());
    }

    /**
     * Returns whether the given player belongs to the rogue class, the only class permitted to use
     * the PICK skill.
     */
    private static boolean isRogue(Player player) {
        return player.getClassId() != null
            && "rogue".equalsIgnoreCase(player.getClassId().getValue());
    }

    /**
     * Consumes an item from inventory, applying its hp stat and any item effects to the player.
     *
     * <p>A positive {@code hp} stat in {@link io.taanielo.jmud.core.world.ItemAttributes} heals
     * the player (capped at max HP); a negative value deals damage. Item effects such as
     * poison are applied after the stat change; an effect whose operation is
     * {@link ItemEffectOperation#REMOVE} instead cures a matching active effect (e.g. a
     * cure potion removing poison), silently doing nothing if the target has no such
     * effect active. An item that has neither an {@code hp} stat nor any effects results
     * in a "Nothing happens." message.
     *
     * @param source the player quaffing the item
     * @param itemInput the item name or id to quaff
     * @return result with updated source (effects applied, item removed)
     */
    public GameActionResult quaffItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Quaff what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        int hpDelta = item.getAttributes().getStats().getOrDefault("hp", 0);
        int manaDelta = item.getAttributes().getStats().getOrDefault("mana", 0);
        boolean hasHpStat = hpDelta != 0;
        boolean hasManaStat = manaDelta != 0;
        if (!hasHpStat && !hasManaStat && item.getEffects().isEmpty()) {
            return GameActionResult.error("Nothing happens.");
        }
        // Apply hp stat: positive heals, negative damages
        PlayerVitals vitals = source.getVitals();
        if (hpDelta > 0) {
            vitals = vitals.heal(hpDelta);
        } else if (hpDelta < 0) {
            vitals = vitals.damage(-hpDelta);
        }
        // Apply mana stat: positive restores mana
        if (manaDelta > 0) {
            vitals = vitals.restoreMana(manaDelta);
        }
        Player working = source.withVitals(vitals);
        CollectingEffectMessageSink effectSink = new CollectingEffectMessageSink(
            source.getUsername(),
            source.getUsername()
        );
        try {
            for (ItemEffect effect : item.getEffects()) {
                if (effect.operation() == ItemEffectOperation.REMOVE) {
                    abilityEffectEngine.remove(working, effect.id(), effectSink);
                } else {
                    abilityEffectEngine.apply(working, effect.id(), effectSink);
                }
            }
        } catch (EffectRepositoryException e) {
            return GameActionResult.error("You cannot use that item right now.");
        }
        PlayerEquipment equipment = working.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        Player updated = working.removeItem(item).withEquipment(equipment);
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.getName(),
            null,
            null,
            null
        );
        messages.addAll(messageEmitter.emit(item.getMessages(), MessagePhase.QUAFF, context));
        messages.addAll(effectSink.collected());
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Eats a food item from inventory, restoring the player's hunger by the item's
     * {@code hunger} stat and consuming the item.
     *
     * <p>The item's {@code hunger} attribute stat determines how many hunger points are
     * restored (capped at {@link io.taanielo.jmud.core.player.PlayerSustenance#MAX}). Items
     * without a positive {@code hunger} stat are rejected as inedible. Any timed
     * {@code effects} declared on the item are then applied (or, for a {@link
     * ItemEffectOperation#REMOVE} effect, cured), mirroring {@link #quaffItem}, so that cooked
     * buff meals take hold on eating. Any {@code eat}-phase messages defined on the item are
     * emitted; otherwise a default eat message is produced, followed by any effect messages.
     *
     * @param source the player eating the item
     * @param itemInput the item name or id to eat
     * @return result with the updated source (hunger restored, effects applied, item removed)
     */
    public GameActionResult eatItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Eat what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        int hungerRestore = item.getAttributes().getStats().getOrDefault("hunger", 0);
        if (hungerRestore <= 0) {
            return GameActionResult.error("You can't eat that.");
        }
        Player working = source.withSustenance(source.getSustenance().feed(hungerRestore));
        CollectingEffectMessageSink effectSink = new CollectingEffectMessageSink(
            source.getUsername(),
            source.getUsername()
        );
        try {
            for (ItemEffect effect : item.getEffects()) {
                if (effect.operation() == ItemEffectOperation.REMOVE) {
                    abilityEffectEngine.remove(working, effect.id(), effectSink);
                } else {
                    abilityEffectEngine.apply(working, effect.id(), effectSink);
                }
            }
        } catch (EffectRepositoryException e) {
            return GameActionResult.error("You cannot eat that right now.");
        }
        Player updated = working.removeItem(item);
        List<GameMessage> messages = new ArrayList<>(
            consumptionMessages(source, item, MessagePhase.EAT, "eat", "eats"));
        messages.addAll(effectSink.collected());
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Drinks a beverage item from inventory, restoring the player's thirst by the item's
     * {@code thirst} stat and consuming the item.
     *
     * <p>The item's {@code thirst} attribute stat determines how many thirst points are
     * restored (capped at {@link io.taanielo.jmud.core.player.PlayerSustenance#MAX}). Items
     * without a positive {@code thirst} stat are rejected as undrinkable. Any {@code drink}-phase
     * messages defined on the item are emitted; otherwise a default drink message is produced.
     *
     * @param source the player drinking the item
     * @param itemInput the item name or id to drink
     * @return result with the updated source (thirst restored, item removed)
     */
    public GameActionResult drinkItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Drink what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        int thirstRestore = item.getAttributes().getStats().getOrDefault("thirst", 0);
        if (thirstRestore <= 0) {
            return GameActionResult.error("You can't drink that.");
        }
        Player updated = source.withSustenance(source.getSustenance().quench(thirstRestore)).removeItem(item);
        List<GameMessage> messages = consumptionMessages(source, item, MessagePhase.DRINK, "drink", "drinks");
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Builds the self/room messages for consuming an item, using item-defined messages for
     * the given phase when present, or a default verb-based pair otherwise.
     */
    private List<GameMessage> consumptionMessages(
        Player source,
        Item item,
        MessagePhase phase,
        String selfVerb,
        String roomVerb
    ) {
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.getName(),
            null,
            null,
            null
        );
        List<GameMessage> emitted = messageEmitter.emit(item.getMessages(), phase, context);
        if (!emitted.isEmpty()) {
            return emitted;
        }
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You " + selfVerb + " " + item.getName() + "."));
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            null,
            source.getUsername().getValue() + " " + roomVerb + " " + item.getName() + "."
        ));
        return messages;
    }

    /**
     * Reads a scroll from inventory, permanently teaching the player the ability it references.
     *
     * <p>The scroll is consumed (removed from inventory) on success. Fails with a clear message
     * if the named item teaches no ability, if the referenced ability no longer exists in the
     * {@link AbilityRegistry}, or if the player already knows the ability.
     *
     * @param source the player reading the scroll
     * @param itemInput the item name or id to read
     * @return result with the updated learned-abilities list and the scroll removed
     */
    public GameActionResult readItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Read what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        if (!item.isIdentified()) {
            return identifyInventoryItem(source, item);
        }
        if (RECALL_SCROLL_ID.equals(item.getId())) {
            return readRecallScroll(source, item);
        }
        if (item.isMap()) {
            return readMapItem(source, item);
        }
        AbilityId abilityId = item.getTeachesAbilityRef();
        if (abilityId == null) {
            return GameActionResult.error("There is nothing to learn from that.");
        }
        if (source.getLearnedAbilities().contains(abilityId)) {
            return GameActionResult.error("You already know that ability.");
        }
        Optional<Ability> ability = abilityRegistry.findById(abilityId);
        if (ability.isEmpty()) {
            return GameActionResult.error("That scroll's magic has faded.");
        }
        // Level gate: refuse to learn an ability above the reader's level, keeping the scroll intact so
        // it is not wasted. Mirrors both the use-time gate in useAbility() and the trainer's LevelTooLow
        // refusal (issue #538) — otherwise an above-level scroll would be consumed for a dead ability.
        if (ability.get().level() > source.getLevel()) {
            return GameActionResult.error("You are not yet skilled enough to learn "
                + ability.get().name() + " (requires level " + ability.get().level() + ").");
        }
        List<AbilityId> learned = new ArrayList<>(source.getLearnedAbilities());
        learned.add(abilityId);
        Player updated = source.withLearnedAbilities(learned).removeItem(item);
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.getName(),
            null,
            ability.get().name(),
            null
        );
        List<GameMessage> emitted = messageEmitter.emit(item.getMessages(), MessagePhase.READ, context);
        if (emitted.isEmpty()) {
            messages.add(GameMessage.toSource(
                "You read " + item.getName() + " and learn " + ability.get().name() + "."));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " reads " + item.getName() + "."
            ));
        } else {
            messages.addAll(emitted);
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Renders a map item's hand-drawn cartography to the reader without consuming it.
     *
     * <p>A map shows fixed authored ASCII art — an area's paths or the World Atlas overview — and
     * never the reader's position (issue #529). The map is reusable, so it stays in the inventory;
     * onlookers merely see the reader studying it. Fails gracefully when the cartography subsystem
     * is not wired or the item's area has no art.
     *
     * @param source the player reading the map
     * @param item   the resolved map item from the player's inventory
     * @return a result whose messages carry the framed map art, with the item retained
     */
    private GameActionResult readMapItem(Player source, Item item) {
        if (areaMapService == null) {
            return GameActionResult.error("You can't make out the markings on " + item.getName() + ".");
        }
        Optional<List<String>> art = areaMapService.render(item.getMapAreaId());
        if (art.isEmpty()) {
            return GameActionResult.error("The markings on " + item.getName() + " are too faded to read.");
        }
        List<GameMessage> messages = new ArrayList<>();
        for (String line : art.get()) {
            messages.add(GameMessage.toSource(line));
        }
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            null,
            source.getUsername().getValue() + " studies " + item.getName() + "."
        ));
        return new GameActionResult(source, null, messages);
    }

    /**
     * Reads a Scroll of Recall, delegating to {@link #recall(Player)} so the teleport behaves
     * identically to the {@code RECALL} command — including the in-combat, dueling and cooldown
     * checks. The scroll is consumed (removed from inventory) only on a successful recall; a
     * rejected attempt (in combat, dueling, or on cooldown) leaves the scroll in place so the
     * player does not waste it.
     *
     * <p>On success the scroll's {@code read}-phase flavor messages are emitted first, followed by
     * the recall departure/arrival messages produced by {@link #recall(Player)}. The recall
     * metadata (including the {@code "recalled"} marker) is preserved so the adapter can re-render
     * the destination room exactly as it does for the {@code RECALL} command.
     *
     * @param source the player reading the scroll
     * @param item   the resolved Scroll of Recall from the player's inventory
     * @return a rejection result (scroll retained) when recall is blocked, otherwise a success
     *         result with the scroll removed and the flavor + recall messages combined
     */
    private GameActionResult readRecallScroll(Player source, Item item) {
        GameActionResult recallResult = recall(source);
        if (!recallResult.metadata().containsKey(RECALL_METADATA_KEY)) {
            // Recall was rejected (combat, duel, or cooldown): keep the scroll unconsumed.
            return recallResult;
        }
        Player updated = source.removeItem(item);
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            item.getName(),
            null,
            null,
            null
        );
        messages.addAll(messageEmitter.emit(item.getMessages(), MessagePhase.READ, context));
        messages.addAll(recallResult.messages());
        return new GameActionResult(updated, null, messages, recallResult.metadata());
    }

    /**
     * Identifies a single carried item, revealing its rarity tier and stat affixes so that it
     * displays with full coloring and affix labels instead of its generic
     * {@code "an unidentified ..."} name.
     *
     * <p>Fails with a clear message when the named item is not carried or is already identified.
     *
     * @param source    the player performing the identification
     * @param itemInput the item name or id to identify
     * @return result with the identified item swapped into the player's inventory
     */
    public GameActionResult identifyItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Identify what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        if (item.isIdentified()) {
            return GameActionResult.error("You already know all there is to know about " + item.getName() + ".");
        }
        return identifyInventoryItem(source, item);
    }

    /**
     * Shared identification path used by both the {@code IDENTIFY} command and reading an
     * unidentified item: swaps the item's identified copy into the player's inventory (preserving
     * order) and emits the reveal messages.
     *
     * @param source the player identifying the item
     * @param item   the unidentified inventory item to reveal
     * @return result with the identified item swapped into the player's inventory
     */
    private GameActionResult identifyInventoryItem(Player source, Item item) {
        Player updated = source.withInventory(
            identificationService.identifyInInventory(source.getInventory(), item));
        Item identified = item.withIdentified(true);
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You study " + item.presentationName() + " and reveal it to be " + identified.presentationName() + "."));
        messages.add(GameMessage.toRoom(
            source.getUsername(),
            null,
            source.getUsername().getValue() + " studies " + item.presentationName() + "."
        ));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Inscribes a scroll for a known ability, adding the newly written scroll to the player's
     * inventory. The scroll is an ephemeral item (not persisted to {@code data/items/}),
     * analogous to how corpses are created dynamically by {@link RoomService}.
     *
     * @param source the player writing the scroll
     * @param abilityInput the name of an ability the player already knows
     * @return result with the new scroll added to the player's inventory
     */
    public GameActionResult writeItem(Player source, String abilityInput) {
        String normalized = abilityInput == null ? "" : abilityInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Write what?");
        }
        Optional<AbilityMatch> match = abilityRegistry.findBestMatch(normalized, source.getLearnedAbilities());
        if (match.isEmpty()) {
            return GameActionResult.error("You don't know that ability.");
        }
        Ability ability = match.get().ability();
        Item scroll = createScroll(ability);
        Player updated = source.addItem(scroll);
        List<GameMessage> messages = new ArrayList<>();
        MessageContext context = new MessageContext(
            source.getUsername(),
            source.getUsername(),
            source.getUsername().getValue(),
            source.getUsername().getValue(),
            scroll.getName(),
            null,
            ability.name(),
            null
        );
        List<GameMessage> emitted = messageEmitter.emit(scroll.getMessages(), MessagePhase.WRITE, context);
        if (emitted.isEmpty()) {
            messages.add(GameMessage.toSource("You write " + scroll.getName() + "."));
            messages.add(GameMessage.toRoom(
                source.getUsername(),
                null,
                source.getUsername().getValue() + " writes " + scroll.getName() + "."
            ));
        } else {
            messages.addAll(emitted);
        }
        return new GameActionResult(updated, null, messages);
    }

    private Item createScroll(Ability ability) {
        String id = "scroll-" + ability.id().getValue().toLowerCase(Locale.ROOT).replace('.', '-')
            + "-" + scrollCounter.incrementAndGet();
        String name = "a scroll of " + ability.name();
        String description = "A hastily inscribed scroll teaching the " + ability.name() + " ability.";
        return Item.builder(ItemId.of(id), name, description, ItemAttributes.empty())
            .weight(1)
            .value(0)
            .teachesAbilityRef(ability.id())
            .build();
    }

    /**
     * Equips an item from inventory into its equipment slot.
     *
     * @param source the player equipping the item
     * @param itemInput the item name or id to equip
     * @return result with updated equipment state
     */
    public GameActionResult equipItem(Player source, String itemInput) {
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Equip what?");
        }
        Item item = findInventoryItem(source, normalized);
        if (item == null) {
            return GameActionResult.error("You aren't carrying that.");
        }
        EquipmentSlot slot = item.getEquipSlot();
        if (slot == null) {
            return GameActionResult.error("You cannot equip that.");
        }
        PlayerEquipment equipment = source.getEquipment();
        // A two-handed weapon occupies the off hand: block equipping an off-hand item while one is worn.
        if (slot == EquipmentSlot.OFFHAND) {
            Item twoHander = equippedTwoHandedWeapon(source);
            if (twoHander != null) {
                return GameActionResult.error(
                    "You are wielding " + twoHander.getName()
                        + " with both hands. Unequip it before using your off hand.");
            }
        }
        List<GameMessage> messages = new ArrayList<>();
        // Equipping a two-handed weapon frees the off hand: auto-unequip any off-hand item.
        if (slot == EquipmentSlot.WEAPON && item.isTwoHanded()) {
            ItemId offhandId = equipment.equipped(EquipmentSlot.OFFHAND);
            if (offhandId != null) {
                Item offhandItem = findEquippedItem(source, offhandId);
                String offhandName = offhandItem != null ? offhandItem.getName() : "your off-hand item";
                equipment = equipment.unequip(EquipmentSlot.OFFHAND);
                messages.add(GameMessage.toSource(
                    "You need both hands for " + item.getName() + ", so you return "
                        + offhandName + " to your pack."));
            }
        }
        Player updated = source.withEquipment(equipment.equip(slot, item.getId()));
        messages.add(GameMessage.toSource("You equip " + item.getName() + " (" + slot.id() + ")."));
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Returns the two-handed weapon currently worn in the player's {@link EquipmentSlot#WEAPON}
     * slot, or {@code null} when the main hand is empty or holds a one-handed weapon. Equipped items
     * remain in the player's inventory, so the weapon is resolved by matching the equipped id there.
     *
     * @param player the player whose main hand to inspect
     * @return the equipped two-handed weapon, or {@code null} if none is worn
     */
    private Item equippedTwoHandedWeapon(Player player) {
        ItemId weaponId = player.getEquipment().equipped(EquipmentSlot.WEAPON);
        if (weaponId == null) {
            return null;
        }
        Item weapon = findEquippedItem(player, weaponId);
        return weapon != null && weapon.isTwoHanded() ? weapon : null;
    }

    /**
     * Finds the inventory item backing an equipped slot by its id. Equipped items are never removed
     * from the inventory, so the id always resolves for a currently-worn item.
     *
     * @param player the player whose inventory to search
     * @param itemId the id of the equipped item to resolve
     * @return the matching item, or {@code null} when not found
     */
    private Item findEquippedItem(Player player, ItemId itemId) {
        for (Item item : player.getInventory()) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Unequips an item from the specified equipment slot.
     *
     * @param source the player unequipping the slot
     * @param slotInput the slot name to clear
     * @return result with updated equipment state
     */
    public GameActionResult unequipSlot(Player source, String slotInput) {
        String normalized = slotInput == null ? "" : slotInput.trim();
        if (normalized.isEmpty()) {
            return GameActionResult.error("Unequip what?");
        }
        EquipmentSlot slot = EquipmentSlot.fromId(normalized);
        if (slot == null) {
            return GameActionResult.error("Unknown equipment slot.");
        }
        PlayerEquipment equipment = source.getEquipment();
        if (equipment.equipped(slot) == null) {
            return GameActionResult.error("That slot is already empty.");
        }
        Player updated = source.withEquipment(equipment.unequip(slot));
        String message = "You unequip your " + slot.id() + ".";
        return new GameActionResult(updated, null, List.of(GameMessage.toSource(message)));
    }

    /**
     * Checks whether a player should die and resolves death if so.
     *
     * <p>If the target's HP is zero or below and not already dead, this method
     * kills the target, spawns a corpse, clears their location, and produces
     * death messages.
     *
     * @param target the player to check
     * @param attacker the attacker, or null for environmental deaths
     * @return result with the (possibly dead) target and death messages
     */
    public GameActionResult resolveDeathIfNeeded(Player target, Player attacker) {
        Objects.requireNonNull(target, "Target is required");
        if (target.getVitals().hp() > 0) {
            return new GameActionResult(null, target, List.of());
        }
        if (target.isDead() && roomService.findPlayerLocation(target.getUsername()).isEmpty()) {
            return new GameActionResult(null, target, List.of());
        }
        RoomService.LookResult look = roomService.look(target.getUsername());
        Room room = look.room();

        // Newbie death grace: low-level characters keep all gold and items instead of dropping a
        // corpse, sparing them the corpse-run death spiral (see issue #520). At or above the grace
        // level the classic corpse-drop behaviour applies.
        boolean graceProtected = DeathSettings.isGraceProtected(target.getLevel());
        boolean corpseSpawned = false;
        Player deadTarget;
        if (graceProtected) {
            deadTarget = target.die();
        } else {
            int droppedGold = target.getGold();
            deadTarget = target.withGold(0).die();
            if (room != null) {
                roomService.spawnCorpse(deadTarget.getUsername(), room.getId(), droppedGold);
                corpseSpawned = true;
            }
        }

        List<GameMessage> messages = buildDeathMessages(attacker, deadTarget, graceProtected, corpseSpawned, room);

        roomService.clearPlayerLocation(deadTarget.getUsername());

        return new GameActionResult(null, deadTarget, messages);
    }

    private List<GameMessage> buildDeathMessages(
            Player attacker, Player deadTarget, boolean graceProtected, boolean corpseSpawned, Room room) {
        List<GameMessage> messages = new ArrayList<>();
        String targetName = deadTarget.getUsername().getValue();

        messages.add(GameMessage.toPlayer(deadTarget.getUsername(), "You have died."));
        messages.add(GameMessage.toPlayer(
            deadTarget.getUsername(),
            "You will awaken in the " + io.taanielo.jmud.core.player.DeathSettings.RESPAWN_ROOM_ID + "."));
        if (graceProtected) {
            messages.add(GameMessage.toPlayer(
                deadTarget.getUsername(),
                "You keep your belongings - for now."));
        } else if (corpseSpawned) {
            String where = room != null ? room.getName() : "where you fell";
            messages.add(GameMessage.toPlayer(
                deadTarget.getUsername(),
                "Your corpse lies in " + where + ", holding your gold and items. "
                    + "Return to loot it before it decays."));
        }

        if (attacker == null) {
            messages.add(GameMessage.toRoom(
                deadTarget.getUsername(), deadTarget.getUsername(),
                targetName + " has died."));
            return messages;
        }

        if (!attacker.getUsername().equals(deadTarget.getUsername())) {
            messages.add(GameMessage.toPlayer(
                attacker.getUsername(),
                "You have slain " + targetName + "."));
        }

        String roomMessage = attacker.getUsername().equals(deadTarget.getUsername())
            ? targetName + " has died."
            : targetName + " has been slain by " + attacker.getUsername().getValue() + "!";
        messages.add(GameMessage.toRoom(
            attacker.getUsername(), deadTarget.getUsername(),
            roomMessage));

        return messages;
    }

    private AttackId resolveAttackId(Player attacker) {
        ItemId weaponId = attacker.getEquipment().equipped(EquipmentSlot.WEAPON);
        if (weaponId != null) {
            for (Item item : attacker.getInventory()) {
                // A broken weapon cannot be used in combat; fall back to the unarmed attack.
                if (item.getId().equals(weaponId) && item.getAttackRef() != null && !item.isBroken()) {
                    return item.getAttackRef();
                }
            }
        }
        return CombatSettings.defaultAttackId();
    }

    private Item findInventoryItem(Player player, String input) {
        return matchItem(player.getInventory(), input);
    }

    /**
     * Finds the first item in {@code items} whose name or id equals or is prefixed by
     * {@code input} (case-insensitive), or {@code null} when none match.
     */
    private static Item matchItem(List<Item> items, String input) {
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

    private static class CollectingAbilityMessageSink implements AbilityMessageSink {
        private final List<GameMessage> messages = new ArrayList<>();

        @Override
        public void sendToSource(Player source, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            messages.add(GameMessage.toSource(message));
        }

        @Override
        public void sendToTarget(Player target, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            messages.add(GameMessage.toPlayer(target.getUsername(), message));
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            messages.add(GameMessage.toRoom(source.getUsername(), target.getUsername(), message));
        }

        List<GameMessage> collected() {
            return List.copyOf(messages);
        }
    }

    private static class CollectingEffectMessageSink implements EffectMessageSink {
        private final List<GameMessage> messages = new ArrayList<>();
        private final io.taanielo.jmud.core.authentication.Username source;
        private final io.taanielo.jmud.core.authentication.Username target;

        private CollectingEffectMessageSink(
            io.taanielo.jmud.core.authentication.Username source,
            io.taanielo.jmud.core.authentication.Username target
        ) {
            this.source = source;
            this.target = target;
        }

        @Override
        public void sendToTarget(String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            if (target != null) {
                messages.add(GameMessage.toPlayer(target, message));
            } else if (source != null) {
                messages.add(GameMessage.toSource(message));
            }
        }

        @Override
        public void sendToRoom(String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            messages.add(GameMessage.toRoom(source, target, message));
        }

        List<GameMessage> collected() {
            return List.copyOf(messages);
        }
    }
}
