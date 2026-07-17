package io.taanielo.jmud.core.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.NpcStealPort;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackEffectApplication;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.ClassArmorBonusResolver;
import io.taanielo.jmud.core.combat.CombatAttributeBonusResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.EquipmentResistanceResolver;
import io.taanielo.jmud.core.combat.ParryResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.ShieldBlockResolver;
import io.taanielo.jmud.core.combat.flavor.DamageVerb;
import io.taanielo.jmud.core.combat.flavor.DamageVerbTable;
import io.taanielo.jmud.core.combat.flavor.TargetConditionTable;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.party.LootMode;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.LevelUpService.LevelUpResult;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerIdentity;
import io.taanielo.jmud.core.player.PlayerMount;
import io.taanielo.jmud.core.player.PlayerPets;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.reload.MobContentReloader;
import io.taanielo.jmud.core.reload.PreparedReload;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemDurabilityService;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.TimeOfDay;
import io.taanielo.jmud.core.world.WorldClock;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Manages all live mob instances and drives mob AI on each tick.
 *
 * <p>All state mutations happen on the tick thread. Player HP updates are
 * delivered via {@link PlayerEventBus} so no transport code lives here.
 */
@Slf4j
public class MobRegistry implements Tickable, NpcStealPort, MobContentReloader, WorldEventStage {

    private final MobTemplateRepository templateRepository;
    private final ItemRepository itemRepository;
    private final AttackRepository attackRepository;
    private final RoomService roomService;
    private final PlayerRepository playerRepository;
    private final PersistenceQueue persistenceQueue;
    private final PlayerEventBus playerEventBus;
    private final CombatRandom random;
    private LevelUpService levelUpService = new LevelUpService();
    private final MessageRenderer messageRenderer = new MessageRenderer();
    /** Optional quest kill hook; may be null when quests are disabled. */
    private QuestKillService questKillService;
    /** Optional party service for XP splitting and round-robin loot; may be null when disabled. */
    private PartyService partyService;
    /** Optional encumbrance service used to skip full-inventory members in round-robin loot; may be null. */
    private EncumbranceService encumbranceService;
    /** Optional effect engine used to apply on-hit status effects (e.g. poison); may be null when disabled. */
    private EffectEngine effectEngine;
    /** Optional world clock used to pick day/night respawn delays; may be null when disabled. */
    private WorldClock worldClock;
    /** Optional durability service used to wear down equipped gear on hit; may be null when disabled. */
    private ItemDurabilityService itemDurabilityService;
    /** Optional reputation service governing faction-based aggression and kill standing; may be null. */
    private ReputationService reputationService;
    /** Optional achievement service that unlocks milestone achievements on kill/level-up; may be null. */
    private AchievementService achievementService;
    /** Optional world-boss announcer that broadcasts boss spawn/death server-wide; may be null. */
    private WorldBossAnnouncer worldBossAnnouncer;
    /**
     * Resolves a player attacker's strength-based bonus damage against mobs. Defaults to a no-op so
     * mob combat is unchanged until the composition root injects the real resolver.
     */
    private CombatAttributeBonusResolver attributeBonusResolver = CombatAttributeBonusResolver.noOp();
    /**
     * Resolves the defending player's elemental resistance from equipped armour, so a mob's typed
     * attack (e.g. a fire wyrm's cinder breath) is mitigated by the player's {@code *_resist} gear.
     * Defaults to a no-op so mob combat is unchanged (physical damage only) until the composition
     * root injects the real resolver.
     */
    private EquipmentResistanceResolver resistanceResolver = EquipmentResistanceResolver.noOp();
    /**
     * Resolves the total armour class (AC) a defending player contributes from equipped armour, so a
     * mob's to-hit roll is reduced by the player's gear exactly as it is in a PvP duel. Defaults to a
     * no-op (zero AC) so mob combat is unchanged until the composition root injects the real resolver.
     */
    private EquipmentArmorResolver armorResolver = EquipmentArmorResolver.noOp();
    /**
     * Resolves the class-based armour bonus a defending player adds to their AC against a mob's
     * to-hit roll (e.g. a Paladin's heavy-armour proficiency), mirroring PvP. Defaults to a no-op
     * until the composition root injects the real resolver.
     */
    private ClassArmorBonusResolver classArmorBonusResolver = ClassArmorBonusResolver.noOp();
    /**
     * Resolves the race-based armour bonus a defending player adds to their AC against a mob's
     * to-hit roll, mirroring PvP. Defaults to a no-op until the composition root injects the real
     * resolver.
     */
    private RaceArmorBonusResolver raceArmorBonusResolver = RaceArmorBonusResolver.noOp();
    /**
     * Resolves a shield-equipped defending player's block chance/reduction from their off-hand item,
     * so a mob's landing hit can be blocked for reduced damage exactly as in a PvP duel. Defaults to
     * a no-op (no block possible) until the composition root injects the real resolver.
     */
    private ShieldBlockResolver shieldBlockResolver = ShieldBlockResolver.noOp();
    /**
     * Resolves whether a defending player wielding a melee weapon parries a mob's landing melee swing
     * (taking zero damage) and ripostes the mob for their mainhand weapon's damage. Defaults to a
     * no-op (no parry possible) until the composition root injects the real resolver. This resolver
     * governs the <em>player</em>-side parry of a mob's swing; a mob's own parry of a player's melee
     * attack is a separate, data-authored trait (see {@link MobTemplate#parryChancePercent()} and
     * {@link #resolveMobParry}).
     */
    private ParryResolver parryResolver = ParryResolver.noOp();
    /**
     * Optional worded-damage verb table. When set, a mob's hit message can substitute {@code {verb}}
     * with the classic-MUD damage tier (e.g. "MAULS") resolved from the damage dealt as a percentage
     * of the victim's maximum HP. Null leaves {@code {verb}} rendering as an empty string.
     */
    private DamageVerbTable damageVerbTable;
    /**
     * Optional target-condition table. When set alongside {@link #damageVerbTable}, a player's strike
     * against a mob reports the mob's condition ("looks pretty hurt") instead of a numeric HP total.
     * Null falls back to the legacy numeric "(N HP remaining)" line.
     */
    private TargetConditionTable targetConditionTable;
    /**
     * HP threshold, as a percentage of a mob's maximum HP, at or below which a wounded mob may try to
     * flee (see {@link #tryMobFlee}). Seeded from {@link CombatSettings#mobFleeHpPercent()} and
     * overridable via {@link #setMobFleeSettings(int, int)} for deterministic tests.
     */
    private int mobFleeHpPercent = CombatSettings.mobFleeHpPercent();
    /**
     * Per-AI-tick percent chance a below-threshold mob breaks off and flees instead of attacking.
     * Seeded from {@link CombatSettings#mobFleeChancePercent()} and overridable via
     * {@link #setMobFleeSettings(int, int)} for deterministic tests.
     */
    private int mobFleeChancePercent = CombatSettings.mobFleeChancePercent();

    /** Data tag marking a mob that never flees regardless of how badly wounded it is. */
    private static final String FEARLESS_TAG = "fearless";

    /** Ability id of the rogue BACKSTAB skill, which gains bonus damage when opened from stealth. */
    private static final AbilityId BACKSTAB_ABILITY_ID = AbilityId.of("skill.backstab");

    /**
     * Extra flat damage a BACKSTAB deals when the attacker strikes a mob from stealth, mirroring the
     * PvP path in {@link io.taanielo.jmud.core.action.GameActionService}. Applied after the hit/crit
     * roll so, like PvP, the crit multiplier scales only the ability's own damage, not this bonus.
     */
    private static final int STEALTH_BACKSTAB_BONUS_DAMAGE = 10;

    /**
     * Data tag marking a <em>pack</em> mob. A pack mob never starts a fresh fight on its own: it
     * only reinforces an existing one, joining against a player already engaged with a different
     * alive mob in the same room (see {@link #playersEngagedWithRoommates} and
     * {@link #isPackOnlyInitiator}). Additive and optional — see {@code docs/data-schema.md}.
     */
    private static final String PACK_TAG = "pack";

    private final ConcurrentHashMap<UUID, MobInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Username, UUID> playerCombatTargets = new ConcurrentHashMap<>();
    /**
     * Pet templates (see {@link MobTemplate#isPetTemplate()}) cached at {@link #init()} so the
     * on-demand SUMMON path never touches the JSON repository from the tick thread (AGENTS.md §5).
     */
    private final List<MobTemplate> petTemplates = new ArrayList<>();
    /**
     * World-event templates (see {@link MobTemplate#worldEvent()}) cached at {@link #init()} so the
     * {@link WorldEventScheduler} can enumerate the rare-elite pool without touching the JSON
     * repository from the tick thread (AGENTS.md §5). Like pet templates these are never spawned
     * into the world at start-up; an instance exists only while a scheduled world event is open.
     */
    private final List<MobTemplate> worldEventTemplates = new ArrayList<>();
    /**
     * All mob templates keyed by their id string, cached at {@link #init()} so tamed-pet respawn on
     * login can rebuild a companion instance from its template without touching disk on the tick
     * thread (AGENTS.md §5).
     */
    private final ConcurrentHashMap<String, MobTemplate> templatesById = new ConcurrentHashMap<>();

    public MobRegistry(
        MobTemplateRepository templateRepository,
        ItemRepository itemRepository,
        AttackRepository attackRepository,
        RoomService roomService,
        PlayerRepository playerRepository,
        PersistenceQueue persistenceQueue,
        PlayerEventBus playerEventBus,
        CombatRandom random
    ) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "Template repository is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.persistenceQueue = Objects.requireNonNull(persistenceQueue, "Persistence queue is required");
        this.playerEventBus = Objects.requireNonNull(playerEventBus, "Player event bus is required");
        this.random = Objects.requireNonNull(random, "Random is required");
    }

    /**
     * Registers the quest kill service used to record mob kills toward active quests.
     *
     * @param questKillService the service to notify on mob death; may be null to disable
     */
    public void setQuestKillService(QuestKillService questKillService) {
        this.questKillService = questKillService;
    }

    /**
     * Registers the level-up service used to award XP and apply class-differentiated vitals gains
     * when a mob kill grants experience. When not set, a service applying the legacy default gains
     * to every class is used.
     *
     * @param levelUpService the level-up service; must not be null
     */
    public void setLevelUpService(LevelUpService levelUpService) {
        this.levelUpService = Objects.requireNonNull(levelUpService, "Level-up service is required");
    }

    /**
     * Registers the effect engine used to apply a mob attack's on-hit status effect
     * (see {@link AttackDefinition#effectOnHit()}) to the player it hits.
     *
     * @param effectEngine the effect engine; may be null to disable on-hit effect application
     */
    public void setEffectEngine(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
    }

    /**
     * Registers the resolver that adds a player attacker's strength-based bonus damage when striking
     * a mob, so core attributes matter in player-vs-mob combat exactly as they do in player-vs-player
     * combat. When not set, a no-op resolver leaves mob damage unchanged.
     *
     * @param attributeBonusResolver the combat attribute bonus resolver; must not be null
     */
    public void setCombatAttributeBonusResolver(CombatAttributeBonusResolver attributeBonusResolver) {
        this.attributeBonusResolver =
            Objects.requireNonNull(attributeBonusResolver, "Attribute bonus resolver is required");
    }

    /**
     * Registers the resolver that mitigates a mob's typed (non-physical) attack by the defending
     * player's equipped elemental resistance, so zone-appropriate resist gear matters against a
     * fire/cold/poison-flavoured mob attack. When not set, a no-op resolver leaves mob damage
     * unchanged.
     *
     * @param resistanceResolver the equipment resistance resolver; must not be null
     */
    public void setEquipmentResistanceResolver(EquipmentResistanceResolver resistanceResolver) {
        this.resistanceResolver =
            Objects.requireNonNull(resistanceResolver, "Equipment resistance resolver is required");
    }

    /**
     * Registers the resolver that sums a defending player's equipped-armour AC, reducing a mob's
     * chance to hit them so armour matters against mobs, not only in PvP duels. When not set, a no-op
     * resolver contributing zero AC leaves mob hit chance unchanged.
     *
     * @param armorResolver the equipment armour resolver; must not be null
     */
    public void setEquipmentArmorResolver(EquipmentArmorResolver armorResolver) {
        this.armorResolver = Objects.requireNonNull(armorResolver, "Equipment armor resolver is required");
    }

    /**
     * Registers the resolver that adds a defending player's class-based armour bonus to their AC
     * against a mob's to-hit roll (mirroring PvP). When not set, a no-op resolver contributing zero
     * leaves mob hit chance unchanged.
     *
     * @param classArmorBonusResolver the class armour bonus resolver; must not be null
     */
    public void setClassArmorBonusResolver(ClassArmorBonusResolver classArmorBonusResolver) {
        this.classArmorBonusResolver =
            Objects.requireNonNull(classArmorBonusResolver, "Class armor bonus resolver is required");
    }

    /**
     * Registers the resolver that adds a defending player's race-based armour bonus to their AC
     * against a mob's to-hit roll (mirroring PvP). When not set, a no-op resolver contributing zero
     * leaves mob hit chance unchanged.
     *
     * @param raceArmorBonusResolver the race armour bonus resolver; must not be null
     */
    public void setRaceArmorBonusResolver(RaceArmorBonusResolver raceArmorBonusResolver) {
        this.raceArmorBonusResolver =
            Objects.requireNonNull(raceArmorBonusResolver, "Race armor bonus resolver is required");
    }

    /**
     * Registers the resolver that grants a shield-equipped defending player a chance to block a mob's
     * landing attack for reduced damage, mirroring PvP. When not set, a no-op resolver leaves mob
     * damage unblockable.
     *
     * @param shieldBlockResolver the shield block resolver; must not be null
     */
    public void setShieldBlockResolver(ShieldBlockResolver shieldBlockResolver) {
        this.shieldBlockResolver =
            Objects.requireNonNull(shieldBlockResolver, "Shield block resolver is required");
    }

    /**
     * Registers the resolver that lets a player wielding a melee weapon parry a mob's landing melee
     * swing and riposte the mob, mirroring the PvP {@link io.taanielo.jmud.core.combat.CombatEngine}.
     * When not set, a no-op resolver leaves mob swings unparryable. This governs only the player-side
     * parry; a mob's own parry of a player's melee attack is a data-authored trait resolved separately
     * (see {@link MobTemplate#parryChancePercent()} and {@link #resolveMobParry}).
     *
     * @param parryResolver the parry resolver; must not be null
     */
    public void setParryResolver(ParryResolver parryResolver) {
        this.parryResolver = Objects.requireNonNull(parryResolver, "Parry resolver is required");
    }

    /**
     * Registers the worded-damage verb table used to substitute {@code {verb}} in a mob's hit message
     * with the classic-MUD damage tier (e.g. "MAULS"). When not set, {@code {verb}} renders empty.
     *
     * @param damageVerbTable the verb table; may be null to disable worded-damage substitution
     */
    public void setDamageVerbTable(DamageVerbTable damageVerbTable) {
        this.damageVerbTable = damageVerbTable;
    }

    /**
     * Registers the target-condition table used to describe a struck mob's remaining health in words
     * ("looks pretty hurt") instead of a numeric HP total. When not set, the legacy numeric line is
     * used.
     *
     * @param targetConditionTable the condition table; may be null to keep the numeric HP line
     */
    public void setTargetConditionTable(TargetConditionTable targetConditionTable) {
        this.targetConditionTable = targetConditionTable;
    }

    /**
     * Overrides the mob-flee tunables that decide when and how often a badly wounded mob breaks off
     * combat to flee (see {@link #tryMobFlee}). Both values are percentages in {@code [0, 100]}. The
     * registry is seeded from {@link CombatSettings} at construction; this setter exists mainly so
     * tests can force a deterministic flee (100% chance) or suppress it (0% chance) without depending
     * on the world configuration.
     *
     * @param hpPercent     HP threshold as a percentage of max HP at or below which a mob may flee
     * @param chancePercent per-AI-tick percent chance a below-threshold mob flees instead of attacking
     * @throws IllegalArgumentException when either value is outside {@code [0, 100]}
     */
    public void setMobFleeSettings(int hpPercent, int chancePercent) {
        if (hpPercent < 0 || hpPercent > 100) {
            throw new IllegalArgumentException("Mob flee HP percent must be in [0, 100]");
        }
        if (chancePercent < 0 || chancePercent > 100) {
            throw new IllegalArgumentException("Mob flee chance must be in [0, 100]");
        }
        this.mobFleeHpPercent = hpPercent;
        this.mobFleeChancePercent = chancePercent;
    }

    /**
     * Builds the self-facing line shown to a player who has just struck a mob in melee. When the
     * worded-damage tables are configured this reads as a classic-MUD verb line with no numbers
     * ("You maul the Goblin! The Goblin looks pretty hurt."); otherwise it falls back to the legacy
     * numeric summary. The condition clause is omitted when the mob has been killed (a slay message
     * follows instead).
     *
     * @param mob       the struck mob
     * @param damage    the damage dealt this strike
     * @param remaining the mob's HP after the strike
     * @param crit      whether this strike was a critical hit (prefixed with a "critical hit!" notice)
     * @return the message text to show the attacker
     */
    private String playerStrikeMessage(MobInstance mob, int damage, int remaining, boolean crit) {
        String mobName = mob.template().name();
        String critPrefix = crit ? "A critical hit! " : "";
        if (damageVerbTable == null) {
            return critPrefix + "You strike the " + mobName + " for " + damage
                + " damage. (" + remaining + " HP remaining)";
        }
        int maxHp = mob.template().maxHp();
        DamageVerb verb = damageVerbTable.verbFor(Math.max(1, damage), maxHp);
        StringBuilder text = new StringBuilder(critPrefix)
            .append("You ")
            .append(verb.secondPerson())
            .append(" the ")
            .append(mobName)
            .append('!');
        if (targetConditionTable != null && remaining > 0) {
            text.append(" The ")
                .append(mobName)
                .append(' ')
                .append(targetConditionTable.describe(remaining, maxHp))
                .append('.');
        }
        return text.toString();
    }

    /**
     * Builds the self-facing line shown to a player whose melee attack missed a mob outright,
     * so a player's accuracy investment (agility) and a weapon's {@code hit_bonus} produce a
     * visible miss rather than an unconditional strike.
     *
     * @param mob the mob that was swung at
     * @return the miss message text to show the attacker
     */
    private String playerMissMessage(MobInstance mob) {
        return "You swing at the " + mob.template().name() + " but miss.";
    }

    /**
     * Builds the self-facing line shown to a player who has parried a mob's melee swing and riposted
     * it, so the zero-damage swing and the free counter-strike are both explained. Uses the
     * worded-damage verb table (and the target-condition table for the mob's remaining health) when
     * available, mirroring {@link #playerStrikeMessage}; otherwise falls back to a numeric line.
     *
     * @param mob           the mob whose swing was parried and which now takes the riposte
     * @param riposteDamage the damage the player's riposte dealt to the mob
     * @param remaining     the mob's remaining HP after the riposte
     * @return the parry/riposte message text to show the defending player
     */
    private String playerParryMessage(MobInstance mob, int riposteDamage, int remaining) {
        String mobName = mob.template().name();
        if (damageVerbTable == null) {
            return "You parry the " + mobName + "'s attack and riposte for " + riposteDamage
                + " damage. (" + remaining + " HP remaining)";
        }
        int maxHp = mob.template().maxHp();
        DamageVerb verb = damageVerbTable.verbFor(Math.max(1, riposteDamage), maxHp);
        StringBuilder text = new StringBuilder("You parry the ")
            .append(mobName)
            .append("'s attack and ")
            .append(verb.secondPerson())
            .append(" it in return!");
        if (targetConditionTable != null && remaining > 0) {
            text.append(" The ")
                .append(mobName)
                .append(' ')
                .append(targetConditionTable.describe(remaining, maxHp))
                .append('.');
        }
        return text.toString();
    }

    /**
     * Builds the self-facing line shown to a player who has just landed a ranged shot (SHOOT) on a
     * mob in an adjacent room, mirroring the melee {@link #playerStrikeMessage} but keeping the
     * ranged "You fire at ... to the <direction>" phrasing. A critical hit is prefixed with a
     * "critical hit!" notice so a crit reads distinctly from a normal shot.
     *
     * @param mob       the struck mob
     * @param damage    the damage dealt by this shot
     * @param remaining the mob's HP after the shot
     * @param direction the direction of the adjacent room the mob is in
     * @param crit      whether this shot was a critical hit
     * @return the message text to show the shooter
     */
    private String rangedStrikeMessage(
        MobInstance mob, int damage, int remaining, Direction direction, boolean crit) {
        String critPrefix = crit ? "A critical hit! " : "";
        return critPrefix + "You fire at the " + mob.template().name() + " to the "
            + direction.label() + " for " + damage + " damage. (" + remaining + " HP remaining)";
    }

    /**
     * Builds the self-facing line shown to a player whose ranged shot (SHOOT) missed a mob outright,
     * so a shooter's accuracy investment (agility) and a bow's {@code hit_bonus} produce a visible
     * miss rather than an unconditional hit.
     *
     * @param mob the mob that was fired at
     * @return the miss message text to show the shooter
     */
    private String rangedMissMessage(MobInstance mob) {
        return "Your arrow sails wide of the " + mob.template().name() + ".";
    }

    /**
     * Rolls a player attacker's melee to-hit and (on a hit) crit against a mob, mirroring the PvP
     * {@link io.taanielo.jmud.core.combat.CombatEngine} formula: hit chance is
     * {@link CombatSettings#baseHitChance()} plus the attack's {@code hit_bonus} plus the attacker's
     * agility accuracy bonus, clamped to {@code [MIN_HIT_CHANCE, MAX_HIT_CHANCE]}; a mob has no dodge
     * or armour term yet (implicitly zero). On a hit, crit chance is
     * {@link CombatSettings#baseCritChance()} plus the attack's {@code crit_bonus} plus the attacker's
     * agility crit bonus, and a crit multiplies damage by {@link CombatSettings#critMultiplier()}. All
     * rolls go through the seeded {@link CombatRandom} port so outcomes stay replayable.
     *
     * @param attacker the attacking player
     * @param attack   the resolved attack definition (weapon or unarmed)
     * @return the resolved hit/crit/damage outcome
     */
    private HitOutcome resolvePlayerHit(Player attacker, AttackDefinition attack) {
        return resolveHit(
            CombatSettings.baseHitChance()
                + attack.hitBonus()
                + attributeBonusResolver.hitChanceBonus(attacker),
            CombatSettings.baseCritChance()
                + attack.critBonus()
                + attributeBonusResolver.critChanceBonus(attacker),
            () -> rollDamage(attack, attacker));
    }

    /**
     * Rolls a caster's area-of-effect spell to-hit and (on a hit) crit against a single mob target,
     * mirroring the melee {@link #resolvePlayerHit(Player, AttackDefinition)} formula so an AoE spell
     * can miss a target outright or crit it for bonus damage. AoE spells deal damage through ability
     * effects rather than an {@link AttackDefinition}, so no authored {@code hit_bonus}/{@code
     * crit_bonus} exists: hit chance is {@link CombatSettings#baseHitChance()} plus the caster's
     * agility accuracy bonus, and crit chance is {@link CombatSettings#baseCritChance()} plus the
     * caster's agility crit bonus. A crit multiplies the fixed per-target spell damage by
     * {@link CombatSettings#critMultiplier()}. All rolls go through the seeded {@link CombatRandom}
     * port so outcomes stay replayable.
     *
     * @param caster     the casting player
     * @param baseDamage the fixed per-target spell damage before any crit multiplier
     * @return the resolved hit/crit/damage outcome for this target
     */
    private HitOutcome resolveSpellHit(Player caster, int baseDamage) {
        return resolveHit(
            CombatSettings.baseHitChance() + attributeBonusResolver.hitChanceBonus(caster),
            CombatSettings.baseCritChance() + attributeBonusResolver.critChanceBonus(caster),
            () -> baseDamage);
    }

    /**
     * Rolls a mob-versus-mob attack's to-hit and (on a hit) crit, used for a summoned/tamed pet's
     * swing at a hostile mob and for the foe's retaliation swing against the pet. Neither combatant is
     * a player, so there is no agility accuracy term and the defending mob contributes no dodge or
     * armour (implicitly zero, exactly as a mob defender does in {@link #resolvePlayerHit}): hit
     * chance is {@link CombatSettings#baseHitChance()} plus the attack's {@code hit_bonus} and crit
     * chance is {@link CombatSettings#baseCritChance()} plus the attack's {@code crit_bonus}. A crit
     * multiplies the rolled damage by {@link CombatSettings#critMultiplier()}. All rolls go through the
     * seeded {@link CombatRandom} port so outcomes stay replayable.
     *
     * @param attack the attacking mob's resolved attack definition
     * @return the resolved hit/crit/damage outcome
     */
    private HitOutcome resolveMobVsMobHit(AttackDefinition attack) {
        return resolveHit(
            CombatSettings.baseHitChance() + attack.hitBonus(),
            CombatSettings.baseCritChance() + attack.critBonus(),
            () -> rollDamage(attack));
    }

    /**
     * Shared hit/crit resolution mirroring the PvP {@link io.taanielo.jmud.core.combat.CombatEngine}
     * formula: the {@code hitChance} is clamped to {@code [MIN_HIT_CHANCE, MAX_HIT_CHANCE]} and rolled
     * against the seeded {@link CombatRandom}; on a hit the damage is drawn from {@code damageRoll}
     * (invoked exactly once, after the hit roll, so a miss consumes no damage RNG) and, on a further
     * crit roll against the {@code critChance} clamped to {@code [0, 100]}, multiplied by
     * {@link CombatSettings#critMultiplier()}. Kept in a single place so the melee, ranged, AoE-spell,
     * and pet damage sources all share one deterministic roll order.
     *
     * @param hitChance  the pre-clamp hit chance (base plus any bonuses/penalties)
     * @param critChance the pre-clamp crit chance (base plus any bonuses)
     * @param damageRoll supplies the pre-crit damage, invoked only on a landing hit
     * @return the resolved hit/crit/damage outcome (zero damage on a miss)
     */
    private HitOutcome resolveHit(int hitChance, int critChance, IntSupplier damageRoll) {
        int clampedHit = clamp(hitChance, CombatSettings.MIN_HIT_CHANCE, CombatSettings.MAX_HIT_CHANCE);
        if (random.roll(1, 100) > clampedHit) {
            return new HitOutcome(false, false, 0);
        }
        int damage = damageRoll.getAsInt();
        boolean crit = random.roll(1, 100) <= clamp(critChance, 0, 100);
        if (crit) {
            damage = Math.max(1, damage * CombatSettings.critMultiplier());
        }
        return new HitOutcome(true, crit, damage);
    }

    /**
     * The outcome of an attacker's strike (player melee/ranged, AoE spell, or pet swing) against a
     * defender: whether it landed, whether it was a critical hit, and the final damage dealt (zero on
     * a miss).
     */
    private record HitOutcome(boolean hit, boolean crit, int damage) {
    }

    /**
     * The outcome of a mob's melee attack against a player: whether it landed, whether a shield
     * blocked it (reducing damage), the final damage dealt to the player after block and
     * elemental-resistance mitigation (zero on a miss or a parry), whether the player parried the
     * swing (taking zero damage) and, when parried, the riposte damage the player deals back to the
     * mob.
     */
    private record MobAttackOutcome(boolean hit, boolean blocked, int damage, boolean parried, int riposteDamage) {
    }

    /**
     * Rolls a mob's melee to-hit against a defending player and, on a hit, resolves a shield block
     * plus elemental-resistance mitigation, mirroring the PvP
     * {@link io.taanielo.jmud.core.combat.CombatEngine} formula. Hit chance is
     * {@link CombatSettings#baseHitChance()} plus the attack's {@code hit_bonus} minus the defender's
     * total armour AC (equipped armour plus class and race armour bonuses), clamped to
     * {@code [MIN_HIT_CHANCE, MAX_HIT_CHANCE]}; a mob has no accuracy attribute of its own yet. On a
     * hit, a shield-equipped defender may block for reduced damage
     * ({@link ShieldBlockResolver}), after which the existing elemental-resistance mitigation
     * ({@link #mitigateForDefender}) still applies. All rolls go through the seeded
     * {@link CombatRandom} port so outcomes stay replayable.
     *
     * @param attack the mob's resolved attack definition
     * @param target the defending player
     * @return the resolved hit/block/damage outcome
     */
    private MobAttackOutcome resolveMobAttack(AttackDefinition attack, Player target) {
        int defenderAc = armorResolver.totalAc(target)
            + classArmorBonusResolver.armorBonus(target)
            + raceArmorBonusResolver.armorBonus(target);
        int hitChance = clamp(
            CombatSettings.baseHitChance() + attack.hitBonus() - defenderAc,
            CombatSettings.MIN_HIT_CHANCE, CombatSettings.MAX_HIT_CHANCE);
        if (random.roll(1, 100) > hitChance) {
            return new MobAttackOutcome(false, false, 0, false, 0);
        }
        // Parry takes precedence over the shield block: a player wielding a melee weapon may fully
        // avoid the mob's melee swing (zero damage) and riposte the mob with their mainhand weapon.
        // Ranged mob attacks are never parried. Only roll when a parry is possible so a non-parrying
        // defender leaves the RNG stream — and every legacy result — unchanged. (The mirror case — a
        // mob parrying the player's own melee attack — is handled separately in resolveMobParry.)
        ParryResolver.Parry parry = attack.isRanged()
            ? ParryResolver.Parry.none()
            : parryResolver.resolve(target);
        if (parry.canParry() && random.roll(1, 100) <= clamp(parry.chancePercent(), 0, 100)) {
            int riposte = rollDamage(parry.riposteAttack(), target);
            return new MobAttackOutcome(true, false, 0, true, riposte);
        }
        int damage = rollDamage(attack);
        boolean blocked = false;
        ShieldBlockResolver.ShieldBlock shieldBlock = shieldBlockResolver.resolve(target);
        if (shieldBlock.canBlock()) {
            blocked = random.roll(1, 100) <= clamp(shieldBlock.chancePercent(), 0, 100);
            if (blocked) {
                int reduction = clamp(shieldBlock.reductionPercent(), 0, 100);
                damage = Math.max(1, (int) Math.round(damage * ((100 - reduction) / 100.0)));
            }
        }
        damage = mitigateForDefender(damage, target, attack.damageType());
        return new MobAttackOutcome(true, blocked, damage, false, 0);
    }

    /**
     * The outcome of a defensively-trained mob's parry roll against a player's just-landed melee hit:
     * whether it parried (fully negating that swing's damage) and, when it did, the riposte damage the
     * mob deals back to the player together with the mob's own attack definition that produced it (so
     * an authored {@link MessagePhase#ATTACK_PARRY} line can be rendered).
     */
    private record MobParryOutcome(boolean parried, int riposteDamage, @Nullable AttackDefinition riposteAttack) {

        private static final MobParryOutcome NONE = new MobParryOutcome(false, 0, null);

        static MobParryOutcome none() {
            return NONE;
        }
    }

    /**
     * Rolls a defensively-trained mob's parry against a player's just-landed <em>melee</em> hit,
     * mirroring the player-side {@link ParryResolver} on the mob defender. The chance is the mob's
     * authored {@link MobTemplate#parryChancePercent()} clamped to
     * {@code [CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE]}; a mob with no
     * authored parry chance never rolls, so every existing (non-parrying) mob leaves the seeded
     * {@link CombatRandom} stream — and every legacy fight — numerically unchanged. On a parry the
     * mob ripostes for a fresh roll of its own basic attack's damage. Only the melee attack paths
     * ({@link #processPlayerAttack} and the tick-driven {@link #runPlayerCombat}) call this; the
     * ranged (SHOOT), AoE-spell, and summoned-pet damage paths never do (documented melee-only scope).
     *
     * @param mob the defending mob
     * @return the parry outcome; {@link MobParryOutcome#none()} when the mob cannot or does not parry
     */
    private MobParryOutcome resolveMobParry(MobInstance mob) {
        int chance = clamp(
            mob.template().parryChancePercent(),
            CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE);
        if (chance <= 0 || random.roll(1, 100) > chance) {
            return MobParryOutcome.none();
        }
        AttackDefinition riposteAttack =
            mob.template().attackId() != null ? loadAttack(mob.template().attackId()) : null;
        int riposteDamage = riposteAttack != null ? rollDamage(riposteAttack) : 0;
        return new MobParryOutcome(true, riposteDamage, riposteAttack);
    }

    /**
     * Builds the self-facing line shown to a player whose melee attack was parried by a defensively
     * trained mob, which then riposted for {@code riposteDamage}. Renders an authored
     * {@link MessagePhase#ATTACK_PARRY} SELF-channel message on the mob's attack when present (so a
     * guard's flavour text surfaces), else a sensible generic line using the worded-damage verb table
     * when available, mirroring {@link #mobAttackMessage}.
     *
     * @param mob           the parrying mob
     * @param riposteAttack the mob's attack definition powering the riposte (may be {@code null})
     * @param riposteDamage the riposte damage dealt back to the attacking player
     * @param playerMaxHp   the attacker's maximum HP, used to resolve the {@code {verb}} damage tier
     * @return the rendered message text to show the parried attacker
     */
    private String mobParryMessage(
        MobInstance mob, @Nullable AttackDefinition riposteAttack, int riposteDamage, int playerMaxHp) {
        if (riposteAttack != null) {
            String rendered = renderMobParrySpec(
                mob, riposteAttack, MessageChannel.SELF, riposteDamage, playerMaxHp);
            if (rendered != null) {
                return rendered;
            }
        }
        String mobName = mob.template().name();
        if (damageVerbTable != null && riposteDamage > 0) {
            DamageVerb verb = damageVerbTable.verbFor(riposteDamage, playerMaxHp);
            return "The " + mobName + " parries your attack and " + verb.thirdPerson()
                + " you for " + riposteDamage + "!";
        }
        return "The " + mobName + " parries your attack and ripostes for "
            + riposteDamage + " damage!";
    }

    /**
     * Broadcasts a mob-parry to the other players in the combat room, so bystanders see a defensively
     * trained mob turn aside an attacker's blow and strike back. Renders an authored
     * {@link MessagePhase#ATTACK_PARRY} ROOM-channel message on the mob's attack when present, else a
     * generic line. The parrying attacker is skipped (they receive the SELF line instead).
     *
     * @param mob           the parrying mob
     * @param attacker      the attacker whose blow was parried (excluded from the broadcast)
     * @param riposteAttack the mob's attack definition powering the riposte (may be {@code null})
     * @param riposteDamage the riposte damage dealt back to the attacker
     * @param roomId        the combat room
     */
    private void broadcastMobParry(
        MobInstance mob, Username attacker, @Nullable AttackDefinition riposteAttack,
        int riposteDamage, RoomId roomId) {
        String rendered = riposteAttack == null ? null
            : renderMobParrySpec(mob, riposteAttack, MessageChannel.ROOM, riposteDamage, 0);
        String roomMsg = rendered != null ? rendered
            : "The " + mob.template().name() + " parries " + attacker.getValue()
                + "'s attack and strikes back!";
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(attacker)) {
                continue;
            }
            playerEventBus.publish(occupant,
                new GameActionResult(null, null, List.of(GameMessage.toSource(roomMsg))));
        }
    }

    /**
     * Renders the mob's authored {@link MessagePhase#ATTACK_PARRY} message on the given channel, or
     * {@code null} when the attack declares none (or it renders blank), so callers fall back to a
     * generic line. Mirrors the placeholder/verb substitution used by {@link #mobAttackMessage}.
     */
    @Nullable
    private String renderMobParrySpec(
        MobInstance mob, AttackDefinition attack, MessageChannel channel, int riposteDamage, int maxHp) {
        for (MessageSpec spec : attack.messages()) {
            if (spec.phase() != MessagePhase.ATTACK_PARRY || spec.channel() != channel) {
                continue;
            }
            DamageVerb verb = riposteDamage > 0 && damageVerbTable != null && maxHp > 0
                ? damageVerbTable.verbFor(riposteDamage, maxHp)
                : null;
            MessageContext context = new MessageContext(
                null, null, mob.template().name(), null, null, null, attack.name(), riposteDamage,
                verb == null ? null : verb.thirdPerson(),
                verb == null ? null : verb.secondPerson());
            String rendered = messageRenderer.render(spec, context);
            if (rendered != null && !rendered.isBlank()) {
                return rendered;
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Registers the party service used to split XP among party members on mob kills.
     *
     * @param partyService the party service; may be null to disable party XP splitting
     */
    public void setPartyService(PartyService partyService) {
        this.partyService = partyService;
    }

    /**
     * Registers the encumbrance service used to decide whether a party member can hold a
     * round-robin loot item without becoming overburdened (see
     * {@link EncumbranceService#isOverburdened(Player)}). When null, members are treated as always
     * able to receive an item.
     *
     * @param encumbranceService the encumbrance service; may be null to disable the capacity check
     */
    public void setEncumbranceService(EncumbranceService encumbranceService) {
        this.encumbranceService = encumbranceService;
    }

    /**
     * Registers the world clock consulted to pick day/night respawn delays
     * (see {@link MobTemplate#respawnTicks(TimeOfDay)}).
     *
     * @param worldClock the world clock; may be null to disable the day/night cycle
     */
    public void setWorldClock(WorldClock worldClock) {
        this.worldClock = worldClock;
    }

    /**
     * Registers the durability service that wears down a player's equipped gear each time a mob
     * hits them (see {@link ItemDurabilityService#degradeEquipped}).
     *
     * @param itemDurabilityService the durability service; may be null to disable gear wear
     */
    public void setItemDurabilityService(ItemDurabilityService itemDurabilityService) {
        this.itemDurabilityService = itemDurabilityService;
    }

    /**
     * Registers the reputation service that governs faction-based mob aggression (whether a faction
     * mob engages a given player) and the standing change applied when a faction mob is slain.
     *
     * @param reputationService the reputation service; may be null to disable faction reputation
     */
    public void setReputationService(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    /**
     * Registers the achievement service that unlocks milestone achievements (e.g. first kill, 100
     * kills, level 10) whenever a player's kill or level state advances on a mob kill.
     *
     * @param achievementService the achievement service; may be null to disable achievements
     */
    public void setAchievementService(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    /**
     * Registers the world-boss announcer that broadcasts the server-wide spawn and death
     * announcements when a mob flagged {@link MobTemplate#worldBoss()} awakens or is slain.
     *
     * @param worldBossAnnouncer the announcer; may be null to disable world-boss announcements
     */
    public void setWorldBossAnnouncer(WorldBossAnnouncer worldBossAnnouncer) {
        this.worldBossAnnouncer = worldBossAnnouncer;
    }

    private TimeOfDay currentTimeOfDay() {
        return worldClock != null ? worldClock.timeOfDay() : TimeOfDay.DAY;
    }

    /**
     * Spawns initial mob instances from all templates. Call once on server start.
     */
    public void init() {
        List<MobTemplate> templates;
        try {
            templates = templateRepository.findAll();
        } catch (RepositoryException e) {
            log.error("Failed to load mob templates: {}", e.getMessage(), e);
            return;
        }
        for (MobTemplate template : templates) {
            templatesById.put(template.id().getValue(), template);
            // Pet templates are never spawned into the world at start-up; an instance exists only
            // while a player's SUMMON is active. Cache them for the on-demand summon path instead.
            if (template.isPetTemplate()) {
                petTemplates.add(template);
                continue;
            }
            // World-event mobs are placed on-demand by the WorldEventScheduler, never at start-up.
            if (template.worldEvent()) {
                worldEventTemplates.add(template);
                continue;
            }
            for (int i = 0; i < template.maxCount(); i++) {
                MobInstance mob = new MobInstance(template);
                instances.put(mob.instanceId(), mob);
                announceWorldBossSpawn(mob);
            }
        }
        log.info("Spawned {} mob instance(s) from {} template(s); cached {} pet template(s), "
                + "{} world-event template(s)",
            instances.size(), templates.size(), petTemplates.size(), worldEventTemplates.size());
    }

    /**
     * Reads and validates every mob-template JSON file off the tick thread for a hot reload
     * (issue #349). Committing the returned {@link PreparedReload} replaces the registry's cached
     * templates ({@code templatesById} and the pet-template list) so subsequent spawns and
     * tamed-pet respawns use the updated definitions; mobs already living in the world are left
     * untouched. The commit runs on the tick thread (AGENTS.md §5).
     */
    @Override
    public PreparedReload prepareMobs() throws RepositoryException {
        List<MobTemplate> templates = templateRepository.findAll();
        return PreparedReload.of("mobs", templates.size(), () -> applyTemplates(templates));
    }

    private void applyTemplates(List<MobTemplate> templates) {
        templatesById.clear();
        petTemplates.clear();
        worldEventTemplates.clear();
        for (MobTemplate template : templates) {
            templatesById.put(template.id().getValue(), template);
            if (template.isPetTemplate()) {
                petTemplates.add(template);
            }
            if (template.worldEvent()) {
                worldEventTemplates.add(template);
            }
        }
    }

    /**
     * Returns the world-event templates cached at {@link #init()} (see {@link MobTemplate#worldEvent()}).
     * Backs the {@link WorldEventScheduler}'s selection of which rare-elite encounter to open next.
     *
     * @return an immutable copy of the world-event templates (never null, may be empty)
     */
    @Override
    public List<MobTemplate> worldEventTemplates() {
        return List.copyOf(worldEventTemplates);
    }

    @Override
    public void tick() {
        runPlayerCombat();
        runPetFollow();
        runPetCombat();
        runWanderPhase();
        for (MobInstance mob : instances.values()) {
            // Pets (summoned or tamed) are driven by runPetCombat and never respawn or attack players.
            if (mob.isPet()) {
                continue;
            }
            if (!mob.isAlive()) {
                // World-event mobs never auto-respawn; the WorldEventScheduler purges the slain
                // instance and opens the next event on its own randomized timer.
                if (!mob.template().worldEvent() && mob.tickRespawn()) {
                    mob.respawn();
                    log.debug("Mob {} respawned in {}", mob.template().name(), mob.roomId());
                    announceWorldBossSpawn(mob);
                }
                continue;
            }
            if (mob.template().attackId() == null) {
                continue;
            }
            // A mob may initiate combat when it is inherently aggressive, when it belongs to a
            // faction (its hostility is then decided per-player in runMobAi from reputation), when
            // it is a pack mob and a room-mate already has a player engaged (it reinforces that
            // fight — see runMobAi), or when it is already engaged in a fight.
            boolean couldInitiate = mob.template().aggressive()
                || (reputationService != null && mob.template().factionId() != null)
                || (mob.template().hasTag(PACK_TAG) && !playersEngagedWithRoommates(mob).isEmpty());
            if (!couldInitiate && mob.engagedPlayers().isEmpty()) {
                continue;
            }
            runMobAi(mob);
        }
    }

    /**
     * Wander phase: for each alive, non-NPC, non-combat, wandering mob, with 30% probability
     * move it through a randomly chosen exit and notify nearby players.
     */
    private void runWanderPhase() {
        for (MobInstance mob : instances.values()) {
            if (!mob.isAlive()) {
                continue;
            }
            // Pets (summoned or tamed) never wander off on their own; they follow their owner.
            if (mob.isPet()) {
                continue;
            }
            if (!mob.template().wanders()) {
                continue;
            }
            if (mob.template().hasTag("npc")) {
                continue;
            }
            if (!mob.engagedPlayers().isEmpty()) {
                continue;
            }
            // ~30 % chance to wander this tick
            if (random.nextDouble() >= 0.30) {
                continue;
            }
            RoomId fromRoomId = mob.roomId();
            Map<Direction, RoomId> exits = roomService.getExits(fromRoomId);
            if (exits.isEmpty()) {
                continue;
            }
            List<Map.Entry<Direction, RoomId>> exitList = List.copyOf(exits.entrySet());
            Map.Entry<Direction, RoomId> chosen =
                exitList.get(random.roll(0, exitList.size() - 1));
            Direction dir = chosen.getKey();
            RoomId toRoomId = chosen.getValue();

            // Notify players in departure room
            String departMsg = "A " + mob.template().name() + " wanders off to the " + dir.label() + ".";
            for (Username occupant : roomService.getPlayersInRoom(fromRoomId)) {
                playerEventBus.publish(occupant,
                    new GameActionResult(null, null, List.of(GameMessage.toSource(departMsg))));
            }

            mob.moveTo(toRoomId);

            // Notify players in arrival room
            String arriveMsg = "A " + mob.template().name()
                + " wanders in from the " + dir.opposite().label() + ".";
            for (Username occupant : roomService.getPlayersInRoom(toRoomId)) {
                playerEventBus.publish(occupant,
                    new GameActionResult(null, null, List.of(GameMessage.toSource(arriveMsg))));
            }

            log.debug("Mob {} wandered from {} to {} ({})",
                mob.template().name(), fromRoomId, toRoomId, dir.label());
        }
    }

    /**
     * Returns all live mobs currently in the given room.
     */
    public List<MobInstance> getMobsInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return instances.values().stream()
            .filter(m -> m.isAlive() && m.roomId().equals(roomId))
            .toList();
    }

    /**
     * Spawns a fresh instance of the mob template with the given id into the specified room, used by
     * the wizard {@code SPAWN} command.
     *
     * <p>The template is resolved from the in-memory cache populated at {@link #init()}, so no disk
     * I/O occurs on the tick thread; the new instance is registered so it participates in AI and
     * combat from the next tick. Runs on the tick thread via the admin's command queue (AGENTS.md §5).
     *
     * @param mobId  the template id to instantiate
     * @param roomId the room to place the new instance in
     * @return the spawned instance, or empty when no template with that id exists
     */
    @Override
    public Optional<MobInstance> spawnInstance(MobId mobId, RoomId roomId) {
        Objects.requireNonNull(mobId, "Mob id is required");
        Objects.requireNonNull(roomId, "Room id is required");
        MobTemplate template = templatesById.get(mobId.getValue());
        if (template == null) {
            return Optional.empty();
        }
        MobInstance mob = new MobInstance(template);
        mob.moveTo(roomId);
        instances.put(mob.instanceId(), mob);
        log.debug("Spawned mob {} into {} via admin command", template.name(), roomId);
        return Optional.of(mob);
    }

    /**
     * Removes a live mob matching {@code nameInput} from the given room outright, used by the wizard
     * {@code PURGE} command.
     *
     * <p>Matching reuses the same name/prefix rules as combat targeting. The instance is removed with
     * no respawn scheduled, so a purged mob stays gone until the world is reloaded; any player combat
     * engagement against it is torn down. Runs on the tick thread (AGENTS.md §5).
     *
     * @param roomId    the room to search
     * @param nameInput the mob name (or prefix) to purge
     * @return the display name of the purged mob, or empty when no live mob in the room matches
     */
    public Optional<String> purgeMob(RoomId roomId, String nameInput) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (nameInput == null || nameInput.isBlank()) {
            return Optional.empty();
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), nameInput);
        if (mob == null) {
            return Optional.empty();
        }
        purgeInstance(mob);
        log.debug("Purged mob {} from {} via admin command", mob.template().name(), roomId);
        return Optional.of(mob.template().name());
    }

    /**
     * Removes a specific live mob instance outright with no respawn scheduled, tearing down any
     * player combat engagement against it. Shares the removal path used by the wizard {@code PURGE}
     * command ({@link #purgeMob}); the {@link WorldEventScheduler} uses it to clear a world-event mob
     * when its window closes on a timeout. Runs on the tick thread (AGENTS.md §5).
     *
     * @param mob the instance to remove
     */
    @Override
    public void purgeInstance(MobInstance mob) {
        Objects.requireNonNull(mob, "Mob is required");
        instances.remove(mob.instanceId());
        endCombatForMob(mob);
    }

    /**
     * Processes a player's attack against a mob in their current room.
     *
     * @param attacker  the attacking player
     * @param input     raw mob name input from the player
     * @param roomId    the room where the attack takes place
     * @return result containing messages to deliver to the attacker
     */
    public GameActionResult processPlayerAttack(Player attacker, String input, RoomId roomId) {
        if (input == null || input.isBlank()) {
            return GameActionResult.error("Attack what?");
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), input);
        if (mob == null) {
            return GameActionResult.error("No such target here.");
        }
        if (mob.isPet()) {
            return GameActionResult.error("You cannot attack a friendly companion.");
        }
        if (mob.template().hasTag("npc")) {
            return GameActionResult.error("You cannot attack that.");
        }
        AttackId attackId = resolveAttackId(attacker);
        AttackDefinition attack = loadAttack(attackId);
        if (attack == null) {
            return GameActionResult.error("Combat error: attack definition not found.");
        }
        // Engage first so a swing that misses still commits the attacker to the fight, exactly as a
        // landing strike does; the auto-attack then continues on the next tick (runPlayerCombat).
        mob.engage(attacker.getUsername());
        playerCombatTargets.put(attacker.getUsername(), mob.instanceId());

        List<GameMessage> messages = new ArrayList<>();
        HitOutcome outcome = resolvePlayerHit(attacker, attack);
        if (!outcome.hit()) {
            messages.add(GameMessage.toSource(playerMissMessage(mob)));
            return new GameActionResult(null, null, messages);
        }
        // A defensively-trained mob may parry the otherwise-landing melee blow: the player deals zero
        // damage this swing and the mob ripostes with its own attack. Melee-only — the SHOOT/AoE/pet
        // paths never reach here. A mob with no authored parry chance never rolls (see resolveMobParry).
        MobParryOutcome parry = resolveMobParry(mob);
        if (parry.parried()) {
            int playerMaxHp = attacker.getVitals().getMaxHp();
            messages.add(GameMessage.toSource(
                mobParryMessage(mob, parry.riposteAttack(), parry.riposteDamage(), playerMaxHp)));
            broadcastMobParry(mob, attacker.getUsername(), parry.riposteAttack(),
                parry.riposteDamage(), roomId);
            Player damaged = attacker.withVitals(attacker.getVitals().damage(parry.riposteDamage()));
            if (damaged.getVitals().hp() <= 0) {
                handleMobKill(mob, damaged, roomService.getPlayersInRoom(roomId));
                return new GameActionResult(null, null, messages);
            }
            return new GameActionResult(damaged, null, messages);
        }
        int damage = outcome.damage();
        int remaining = mob.takeDamage(damage);
        messages.add(GameMessage.toSource(playerStrikeMessage(mob, damage, remaining, outcome.crit())));

        if (!mob.isAlive()) {
            awardMobKill(mob, attacker, roomId, messages);
        }
        return new GameActionResult(null, null, messages);
    }

    /**
     * Processes an {@code ASSIST} command: engages the assisting player in combat against the mob the
     * named target player is currently fighting, reusing the same per-tick auto-attack engagement as
     * {@link #processPlayerAttack} (the assister begins striking the shared target on the next tick).
     *
     * <p>The target player is resolved by (case-insensitive) name among the players in {@code roomId};
     * their active combat target is read from the existing {@code playerCombatTargets} map. The assist
     * fails — with no state change — when the named player is not present in the room, is the assister
     * themselves, or is not currently engaged with a live mob in that room (e.g. the mob already died).
     * No new engagement state is introduced: this is a thin entry point onto the existing mechanism.
     * Runs entirely on the tick thread via the assister's command queue (AGENTS.md §5).
     *
     * @param assister         the player joining the fight
     * @param targetPlayerName the name of the player to assist (matched among players in the room)
     * @param roomId           the assister's current room
     * @return result with a confirmation message on success, or an error with no state change
     */
    public GameActionResult processPlayerAssist(Player assister, String targetPlayerName, RoomId roomId) {
        Objects.requireNonNull(assister, "Assister is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (targetPlayerName == null || targetPlayerName.isBlank()) {
            return GameActionResult.error("Assist whom?");
        }
        if (assister.isDead()) {
            return GameActionResult.error("You cannot do that while dead.");
        }
        if (encumbranceService != null && encumbranceService.isOverburdened(assister)) {
            return GameActionResult.error("You are carrying too much to do that.");
        }
        String trimmedName = targetPlayerName.trim();
        Username target = findPlayerInRoom(trimmedName, roomId);
        if (target == null) {
            return GameActionResult.error("You don't see " + trimmedName + " here.");
        }
        if (target.equals(assister.getUsername())) {
            return GameActionResult.error("You cannot assist yourself.");
        }
        UUID mobId = playerCombatTargets.get(target);
        MobInstance mob = mobId == null ? null : instances.get(mobId);
        if (mob == null || !mob.isAlive() || !mob.roomId().equals(roomId)) {
            return GameActionResult.error(target.getValue() + " is not fighting anyone.");
        }
        mob.engage(assister.getUsername());
        playerCombatTargets.put(assister.getUsername(), mob.instanceId());
        return new GameActionResult(null, null, List.of(GameMessage.toSource(
            "You join the fight against the " + mob.template().name() + "!")));
    }

    /**
     * Number of AI decisions a single Warrior TAUNT holds a mob's aggro before it expires and normal
     * random targeting resumes. Kept short so TAUNT is a burst peel, not a permanent lock.
     */
    private static final int TAUNT_DURATION_TICKS = 3;

    /**
     * Processes the Warrior {@code TAUNT} skill: forces a mob in the taunter's room that is already in
     * combat to prioritise attacking the taunter for the next few AI decisions (see
     * {@link #selectAiTarget}). The taunter is engaged with the mob as a side effect so they become a
     * valid target, exactly like {@link #processPlayerAttack}.
     *
     * <p>The named mob is resolved case-insensitively among the mobs in {@code roomId}. The taunt fails
     * — with no state change and no move-point cost — when the taunter is dead, over-encumbered, cannot
     * afford the move cost, no matching (live, attackable) mob is present, or the mob is not currently
     * engaged in combat. Class/learned-skill and cooldown gating are enforced by the calling command
     * before this method is reached (mirroring the AoE/summon dispatch pattern). All mutation runs on
     * the tick thread via the taunter's command queue (AGENTS.md §5).
     *
     * @param taunter    the Warrior invoking the skill
     * @param mobName    the raw mob-name input naming the target
     * @param tauntSkill the resolved {@code skill.taunt} ability (supplies the move cost)
     * @param roomId     the taunter's current room
     * @return result whose {@link GameActionResult#updatedSource()} is the move-charged taunter on
     *         success (signalling the caller to start the cooldown), or an error with no state change
     */
    public GameActionResult processPlayerTaunt(
        Player taunter, String mobName, Ability tauntSkill, RoomId roomId) {
        Objects.requireNonNull(taunter, "Taunter is required");
        Objects.requireNonNull(tauntSkill, "Taunt skill is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (mobName == null || mobName.isBlank()) {
            return GameActionResult.error("Taunt what?");
        }
        if (taunter.isDead()) {
            return GameActionResult.error("You cannot do that while dead.");
        }
        if (encumbranceService != null && encumbranceService.isOverburdened(taunter)) {
            return GameActionResult.error("You are carrying too much to do that.");
        }
        if (taunter.getVitals().move() < tauntSkill.cost().move()) {
            return GameActionResult.error("You are too winded to bellow a challenge.");
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), mobName.trim());
        if (mob == null || !mob.isAlive()) {
            return GameActionResult.error("No such target here.");
        }
        if (mob.isPet()) {
            return GameActionResult.error("You cannot taunt a friendly companion.");
        }
        if (mob.template().hasTag("npc")) {
            return GameActionResult.error("You cannot taunt that.");
        }
        if (mob.engagedPlayers().isEmpty()) {
            return GameActionResult.error("The " + mob.template().name() + " is not fighting anyone.");
        }

        Player updated = taunter.withVitals(taunter.getVitals().consumeMove(tauntSkill.cost().move()));
        mob.engage(taunter.getUsername());
        playerCombatTargets.put(taunter.getUsername(), mob.instanceId());
        mob.applyTaunt(taunter.getUsername(), TAUNT_DURATION_TICKS);

        String mobDisplayName = mob.template().name();
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You bellow a challenge, drawing the " + mobDisplayName + "'s attention squarely onto you!"));
        messages.add(GameMessage.toRoomAt(roomId, taunter.getUsername(),
            taunter.getUsername().getValue() + " bellows a challenge at the " + mobDisplayName + "!"));
        // Entering combat via a successful taunt throws the taunter off any mount they were riding.
        // Folding the dismount into the returned source (rather than a caller-side side effect) keeps it
        // consistent with the ATTACK path and ensures a failed taunt never dismounts (AGENTS.md §5).
        if (updated.isMounted()) {
            String mountName = updated.mount().mountName();
            updated = updated.withMount(PlayerMount.dismounted());
            messages.add(GameMessage.toSource(
                "The clash of combat spooks " + mountName + " and you drop down to fight on foot!"));
            messages.add(GameMessage.toRoomAt(roomId, taunter.getUsername(),
                taunter.getUsername().getValue() + " leaps down from " + mountName
                    + " as battle is joined."));
        }
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Finds the {@link Username} of a player currently in {@code roomId} whose name matches
     * {@code name} case-insensitively (usernames compare case-insensitively), or {@code null} when no
     * such player is present.
     *
     * @param name   the raw player-name input
     * @param roomId the room to search
     * @return the matching in-room username, or {@code null}
     */
    private Username findPlayerInRoom(String name, RoomId roomId) {
        Username lookup = Username.of(name);
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(lookup)) {
                return occupant;
            }
        }
        return null;
    }

    /**
     * Applies the shared post-kill rewards when a player's attack (melee or ranged) drops a mob:
     * loot drops, respawn scheduling, combat teardown, party-split XP, gold, quest-kill credit, and
     * level-up notifications. Appends the attacker-facing messages to {@code messages} and publishes
     * per-member messages to other party members in the room. Runs entirely on the tick thread
     * (AGENTS.md §5).
     *
     * @param mob      the slain mob
     * @param attacker the player who landed the killing blow
     * @param roomId   the room used to resolve the attacker's party members for XP sharing
     * @param messages the mutable attacker-facing message list to append reward messages to
     */
    private void awardMobKill(MobInstance mob, Player attacker, RoomId roomId, List<GameMessage> messages) {
        Player reloaded = playerRepository.loadPlayer(attacker.getUsername()).orElse(attacker);
        Player rewarded = applyMobKillRewards(mob, reloaded, roomId, messages);
        saveOrLog(rewarded);
    }

    /**
     * Applies the shared post-kill rewards to {@code attacker} <em>in memory</em> and returns the
     * updated player without persisting it, so the caller controls when and how the result is saved.
     *
     * <p>Unlike {@link #awardMobKill}, this method does not reload the player from the repository:
     * it awards XP, gold, quest-kill credit, and the kill count on top of the exact {@code attacker}
     * instance passed in. This lets area-of-effect casters ({@link #processPlayerAoeSpell}) chain
     * multiple kills through a single evolving player object that already carries an unpersisted
     * mana deduction, without a stale reload clobbering that deduction. Loot drops, respawn
     * scheduling, combat teardown, and per-member party XP (which is saved and published for other
     * members) are handled here identically to {@link #awardMobKill}. Runs entirely on the tick
     * thread (AGENTS.md §5).
     *
     * @param mob      the slain mob
     * @param attacker the player who landed the killing blow, already carrying any pending in-memory
     *                 changes (e.g. a mana deduction) the caller wants preserved
     * @param roomId   the room used to resolve the attacker's party members for XP sharing
     * @param messages the mutable attacker-facing message list to append reward messages to
     * @return the attacker with all rewards applied, not yet persisted
     */
    private Player applyMobKillRewards(
        MobInstance mob, Player attacker, RoomId roomId, List<GameMessage> messages) {
        messages.add(GameMessage.toSource("You slay the " + mob.template().name() + "!"));
        List<Item> drops = rollLoot(mob);
        handleWorldBossKill(mob, attacker.getUsername(), drops, messages);
        mob.scheduleRespawn(currentTimeOfDay());
        endCombatForMob(mob);

        // Determine recipients: party members present in the room (includes the attacker), or just
        // the attacker when solo. Used both for XP splitting and round-robin loot assignment.
        List<Username> partyRecipients = partyService != null
            ? partyService.getPartyMembersInRoom(
                attacker.getUsername(), roomId, roomService::findPlayerLocation)
            : List.of(attacker.getUsername());

        // Build working snapshots for every alive recipient so loot can be assigned to inventories
        // before XP is applied and a single combined save is written; this avoids a stale reload in
        // a later pass clobbering an item just assigned (the persistence queue writes behind).
        Map<Username, Player> working = new LinkedHashMap<>();
        List<Username> eligible = new ArrayList<>();
        for (Username member : partyRecipients) {
            if (member.equals(attacker.getUsername())) {
                working.put(member, attacker);
                eligible.add(member);
                continue;
            }
            Player memberPlayer = playerRepository.loadPlayer(member).orElse(null);
            if (memberPlayer == null || memberPlayer.isDead()) {
                continue;
            }
            working.put(member, memberPlayer);
            eligible.add(member);
        }

        Map<Username, List<GameMessage>> memberMessages = new HashMap<>();
        distributeLoot(mob, attacker.getUsername(), drops, eligible, working, messages, memberMessages);

        // XP is split by every party member in the room (dead members still count toward the divisor
        // but receive nothing, preserving the historical split), matching pre-loot behaviour.
        int xpPerMember = (int) Math.floor(
            (double) mob.template().xpReward() / Math.max(1, partyRecipients.size()));

        LevelUpResult levelUpResult = levelUpService.awardXp(working.get(attacker.getUsername()), xpPerMember);
        Player afterXp = levelUpResult.player();
        if (mob.template().goldDrop() != null) {
            int gold = mob.template().goldDrop().roll(random);
            if (gold > 0) {
                afterXp = afterXp.addGold(gold);
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + " drops " + gold + " gold coin"
                        + (gold == 1 ? "" : "s") + "."));
            }
        }
        if (questKillService != null) {
            var killResult = questKillService.recordKill(afterXp, mob.template().id().getValue());
            if (killResult.isPresent()) {
                afterXp = killResult.get().player();
                for (String msg : killResult.get().messages()) {
                    messages.add(GameMessage.toSource(msg));
                }
            }
        }
        FactionId factionId = mob.template().factionId();
        if (reputationService != null && factionId != null) {
            int before = afterXp.reputation().standing(factionId);
            afterXp = reputationService.recordKill(afterXp, factionId);
            int after = afterXp.reputation().standing(factionId);
            if (after != before) {
                String factionName = reputationService.findFaction(factionId)
                    .map(Faction::name).orElse(factionId.getValue());
                messages.add(GameMessage.toSource("Your reputation with " + factionName
                    + (after < before ? " decreases." : " increases.")));
            }
        }
        afterXp = afterXp.withTotalKills(afterXp.getTotalKills() + 1);
        messages.add(GameMessage.toSource(
            "You gain " + xpPerMember + " experience points."));
        if (levelUpResult.leveledUp()) {
            messages.add(GameMessage.toSource(
                "You have advanced to level " + afterXp.getLevel() + "!"));
        }
        // Unlock any milestone achievements the new kill count / level just satisfied.
        if (achievementService != null) {
            AchievementService.UnlockResult achievementResult = achievementService.checkAndUnlock(afterXp);
            afterXp = achievementResult.player();
            for (Achievement unlocked : achievementResult.newlyUnlocked()) {
                messages.add(GameMessage.toSource("Achievement unlocked: " + unlocked.name() + "!"));
            }
        }

        // Award XP to other party members in the same room, folding in any loot they received this
        // kill so a single save carries both (their working snapshot already holds the loot items).
        // Kill-quest progress and faction reputation are credited to each eligible member too, exactly
        // as if they had landed the kill individually (issue #395), so grouped questing rewards the
        // whole party consistently with the XP/loot split above.
        for (Username member : eligible) {
            if (member.equals(attacker.getUsername())) {
                continue;
            }
            LevelUpResult memberLvl = levelUpService.awardXp(working.get(member), xpPerMember);
            Player memberAfterXp = memberLvl.player()
                .withTotalKills(memberLvl.player().getTotalKills() + 1);
            List<GameMessage> memberMsgs = new ArrayList<>();
            memberMsgs.add(GameMessage.toSource(
                "Your party slay the " + mob.template().name()
                    + "! You gain " + xpPerMember + " experience points."));
            if (memberLvl.leveledUp()) {
                memberMsgs.add(GameMessage.toSource(
                    "You have advanced to level " + memberAfterXp.getLevel() + "!"));
            }
            if (questKillService != null) {
                var memberKillResult = questKillService.recordKill(memberAfterXp, mob.template().id().getValue());
                if (memberKillResult.isPresent()) {
                    memberAfterXp = memberKillResult.get().player();
                    for (String msg : memberKillResult.get().messages()) {
                        memberMsgs.add(GameMessage.toSource(msg));
                    }
                }
            }
            if (reputationService != null && factionId != null) {
                int memberBefore = memberAfterXp.reputation().standing(factionId);
                memberAfterXp = reputationService.recordKill(memberAfterXp, factionId);
                int memberAfter = memberAfterXp.reputation().standing(factionId);
                if (memberAfter != memberBefore) {
                    String factionName = reputationService.findFaction(factionId)
                        .map(Faction::name).orElse(factionId.getValue());
                    memberMsgs.add(GameMessage.toSource("Your reputation with " + factionName
                        + (memberAfter < memberBefore ? " decreases." : " increases.")));
                }
            }
            saveOrLog(memberAfterXp);
            List<GameMessage> lootMsgs = memberMessages.get(member);
            if (lootMsgs != null) {
                memberMsgs.addAll(lootMsgs);
            }
            playerEventBus.publish(member, new GameActionResult(memberAfterXp, null, memberMsgs));
        }
        return afterXp;
    }

    /**
     * Assigns each rolled loot item to its destination. In {@link LootMode#FREE} (or when the killer
     * is not in a party) every item drops to the room floor as before — unless the killer has autoloot
     * enabled ({@link Player#isAutoLootEnabled()}) and can still carry the item, in which case it goes
     * straight into their inventory with a {@code "You loot ..."} message; an item the killer cannot
     * carry still drops to the floor so nothing is lost. In {@link LootMode#ROUND_ROBIN}
     * each item is handed to the next eligible party member in rotation — skipping members whose
     * inventory is full — and only falls to the floor (with an explanatory message) when no eligible
     * member can carry it, so an item is never lost. In {@link LootMode#ROLL} every eligible member
     * who can carry the item rolls 1-100 through the seeded {@link CombatRandom}; the highest roll
     * wins, ties re-roll among only the tied members until a single winner remains, and the whole
     * party sees the roll breakdown and the winner — an unclaimable item still falls to the floor
     * exactly as in round-robin. Recipients get a {@code "You loot ..."} message
     * and the rest of the party in the room sees who received it. Working inventory snapshots are
     * mutated in place so capacity checks and the eventual save reflect every assignment. Runs on the
     * tick thread (AGENTS.md §5).
     *
     * @param mob            the slain mob (source of the room to floor unclaimed drops into)
     * @param attacker       the killer, whose party and rotation pointer drive the assignment
     * @param drops          the items rolled from the loot table this kill
     * @param eligible       ordered list of alive recipients present in the room (includes attacker)
     * @param working        mutable per-recipient player snapshots, updated as items are assigned
     * @param attackerMessages the attacker-facing message list to append the attacker's notices to
     * @param memberMessages the per-member message map to append other recipients' notices to
     */
    private void distributeLoot(
        MobInstance mob,
        Username attacker,
        List<Item> drops,
        List<Username> eligible,
        Map<Username, Player> working,
        List<GameMessage> attackerMessages,
        Map<Username, List<GameMessage>> memberMessages) {
        LootMode mode = partyService != null ? partyService.lootMode(attacker) : LootMode.FREE;
        boolean roundRobin = mode == LootMode.ROUND_ROBIN && !eligible.isEmpty();
        boolean roll = mode == LootMode.ROLL && !eligible.isEmpty();
        for (Item item : drops) {
            if (roll) {
                distributeByRoll(mob, attacker, item, eligible, working,
                    attackerMessages, memberMessages);
                continue;
            }
            if (!roundRobin) {
                Player attackerSnapshot = working.get(attacker);
                if (attackerSnapshot != null
                    && attackerSnapshot.isAutoLootEnabled()
                    && canReceiveItem(attackerSnapshot, item)) {
                    working.put(attacker, attackerSnapshot.addItem(item));
                    attackerMessages.add(GameMessage.toSource(
                        "You loot a " + item.getName() + "."));
                    continue;
                }
                roomService.addItem(mob.roomId(), item);
                attackerMessages.add(GameMessage.toSource(
                    "A " + item.getName() + " drops to the ground."));
                continue;
            }
            Optional<Username> recipientOpt = partyService.nextLootRecipient(
                attacker, eligible, candidate -> canReceiveItem(working.get(candidate), item));
            if (recipientOpt.isEmpty()) {
                roomService.addItem(mob.roomId(), item);
                String note = "No one has room for the " + item.getName()
                    + ", so it drops to the ground.";
                for (Username member : eligible) {
                    routeLootMessage(member, attacker, note, attackerMessages, memberMessages);
                }
                continue;
            }
            Username recipient = recipientOpt.get();
            working.put(recipient, working.get(recipient).addItem(item));
            routeLootMessage(recipient, attacker,
                "You loot a " + item.getName() + ".", attackerMessages, memberMessages);
            for (Username member : eligible) {
                if (member.equals(recipient)) {
                    continue;
                }
                routeLootMessage(member, attacker,
                    recipient.getValue() + " loots a " + item.getName() + ".",
                    attackerMessages, memberMessages);
            }
        }
    }

    /**
     * Safety cap on how many tie-breaking re-roll rounds {@link #distributeByRoll} performs for a
     * single item before falling back to the first tied member in party order. With a real seeded
     * {@link CombatRandom} a tie among 1-100 rolls is broken almost immediately, so this cap only ever
     * matters for a degenerate RNG (e.g. a fixed-value test source) and exists purely to guarantee the
     * loop terminates on the tick thread (AGENTS.md §5).
     */
    private static final int MAX_ROLL_ROUNDS = 20;

    /**
     * Awards a single dropped item under {@link LootMode#ROLL}: every eligible member who can carry it
     * rolls 1-100 through the seeded {@link CombatRandom}, the highest roll wins, and ties re-roll
     * among only the tied members until one winner remains (bounded by {@link #MAX_ROLL_ROUNDS}). The
     * whole party in the room sees the roll breakdown and the winner. When nobody present can carry the
     * item it falls to the floor with the same message round-robin uses, so an item is never lost. The
     * winner's working snapshot is mutated in place so the eventual save carries the item. Runs on the
     * tick thread (AGENTS.md §5).
     *
     * @param mob              the slain mob (source of the room to floor an unclaimable drop into)
     * @param attacker         the killer, used to route the attacker's messages inline
     * @param item             the dropped item being rolled for
     * @param eligible         ordered list of alive recipients present in the room (includes attacker)
     * @param working          mutable per-recipient player snapshots, updated when the winner is chosen
     * @param attackerMessages the attacker-facing message list to append the attacker's notices to
     * @param memberMessages   the per-member message map to append other recipients' notices to
     */
    private void distributeByRoll(
        MobInstance mob,
        Username attacker,
        Item item,
        List<Username> eligible,
        Map<Username, Player> working,
        List<GameMessage> attackerMessages,
        Map<Username, List<GameMessage>> memberMessages) {
        List<Username> capable = eligible.stream()
            .filter(member -> canReceiveItem(working.get(member), item))
            .toList();
        if (capable.isEmpty()) {
            roomService.addItem(mob.roomId(), item);
            String note = "No one has room for the " + item.getName()
                + ", so it drops to the ground.";
            for (Username member : eligible) {
                routeLootMessage(member, attacker, note, attackerMessages, memberMessages);
            }
            return;
        }

        StringBuilder breakdown = new StringBuilder();
        List<Username> contenders = capable;
        Map<Username, Integer> rolls = rollFor(contenders);
        breakdown.append(describeRolls(rolls));
        List<Username> top = topRollers(rolls);
        int rounds = 0;
        while (top.size() > 1 && rounds < MAX_ROLL_ROUNDS) {
            breakdown.append(" — tie at ").append(rolls.get(top.get(0)))
                .append(", re-rolling: ");
            contenders = top;
            rolls = rollFor(contenders);
            breakdown.append(describeRolls(rolls));
            top = topRollers(rolls);
            rounds++;
        }
        Username winner = top.get(0);

        working.put(winner, working.get(winner).addItem(item));
        String text = breakdown + " for the " + item.getName() + "... "
            + winner.getValue() + " wins the roll!";
        for (Username member : eligible) {
            routeLootMessage(member, attacker, text, attackerMessages, memberMessages);
        }
    }

    /**
     * Rolls 1-100 through the seeded {@link CombatRandom} for each candidate, preserving iteration
     * order so the breakdown reads in party order.
     *
     * @param candidates the members rolling this round
     * @return an ordered map of each candidate to their 1-100 roll
     */
    private Map<Username, Integer> rollFor(List<Username> candidates) {
        LinkedHashMap<Username, Integer> rolls = new LinkedHashMap<>();
        for (Username candidate : candidates) {
            rolls.put(candidate, random.roll(1, 100));
        }
        return rolls;
    }

    /**
     * Returns the members whose roll equals the highest value in {@code rolls}, in iteration order.
     * A single-element result means there is an outright winner; more than one means a tie to break.
     *
     * @param rolls the members and their rolls this round
     * @return the top-rolling members (never empty)
     */
    private static List<Username> topRollers(Map<Username, Integer> rolls) {
        int max = rolls.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return rolls.entrySet().stream()
            .filter(entry -> entry.getValue() == max)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Formats a roll round as a human-readable breakdown, e.g. {@code "Aria rolls 87, Boren rolls 42"}.
     *
     * @param rolls the members and their rolls this round
     * @return the joined breakdown text
     */
    private static String describeRolls(Map<Username, Integer> rolls) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Username, Integer> entry : rolls.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey().getValue()).append(" rolls ").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Returns whether the given member could hold {@code item} without becoming overburdened. A null
     * member (offline/dead) can never receive; when no {@link EncumbranceService} is configured the
     * member is treated as always able to carry the item.
     *
     * @param member the working player snapshot, or null when the member is unavailable
     * @param item   the item to test
     * @return whether the member can receive the item
     */
    private boolean canReceiveItem(Player member, Item item) {
        if (member == null) {
            return false;
        }
        if (encumbranceService == null) {
            return true;
        }
        return !encumbranceService.isOverburdened(member.addItem(item));
    }

    /**
     * Routes a loot-related message to the correct sink: the attacker's inline message list when the
     * recipient is the attacker, otherwise the per-member message map delivered via the event bus.
     *
     * @param recipient        the player the message is for
     * @param attacker         the killer whose messages travel back inline
     * @param text             the message text
     * @param attackerMessages the attacker's inline message list
     * @param memberMessages   the per-member message map
     */
    private void routeLootMessage(
        Username recipient,
        Username attacker,
        String text,
        List<GameMessage> attackerMessages,
        Map<Username, List<GameMessage>> memberMessages) {
        if (recipient.equals(attacker)) {
            attackerMessages.add(GameMessage.toSource(text));
        } else {
            memberMessages.computeIfAbsent(recipient, k -> new ArrayList<>())
                .add(GameMessage.toSource(text));
        }
    }

    /**
     * Processes a player's ranged attack (SHOOT command) against a mob in an adjacent room.
     *
     * <p>The player must be wielding a weapon whose attack is classified {@link RangeType#RANGED}
     * (see {@link AttackDefinition#isRanged()}); melee weapons and being unarmed are rejected. The
     * named direction must be a valid exit from {@code roomId}, and a live, attackable mob matching
     * {@code targetName} must occupy that adjacent room. The shot rolls to hit and crit through the
     * same seeded {@link #resolvePlayerHit} resolution as a melee swing (issue #591), so the
     * shooter's agility and the bow's {@code hit_bonus}/{@code crit_bonus} matter and a shot can
     * miss or crit. A missed shot deals no damage and gives the target no reason to close — the mob
     * stays put and does not engage. On a landing hit (normal or critical) the mob takes damage and,
     * if it survives, retaliates by closing the distance — moving into the shooter's room and
     * engaging them so it attacks in melee on subsequent ticks. All mutation happens on the tick
     * thread via the player command queue (AGENTS.md §5).
     *
     * @param attacker   the shooting player
     * @param targetName the raw mob-name input to fire at
     * @param direction  the direction of the adjacent room the target is in
     * @param roomId     the shooter's current room
     * @return result containing messages to deliver to the shooter
     */
    public GameActionResult processPlayerRangedAttack(
        Player attacker, String targetName, Direction direction, RoomId roomId) {
        Objects.requireNonNull(attacker, "Attacker is required");
        Objects.requireNonNull(direction, "Direction is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (targetName == null || targetName.isBlank()) {
            return GameActionResult.error("Shoot what?");
        }
        AttackId attackId = resolveAttackId(attacker);
        AttackDefinition attack = loadAttack(attackId);
        if (attack == null) {
            return GameActionResult.error("Combat error: attack definition not found.");
        }
        if (!attack.isRanged()) {
            return GameActionResult.error("You are not wielding a ranged weapon.");
        }
        RoomId adjacentRoomId = roomService.getExits(roomId).get(direction);
        if (adjacentRoomId == null) {
            return GameActionResult.error("There is no exit to the " + direction.label() + ".");
        }
        MobInstance mob = findMobByName(getMobsInRoom(adjacentRoomId), targetName);
        if (mob == null) {
            return GameActionResult.error(
                "You don't see " + targetName + " to the " + direction.label() + ".");
        }
        if (mob.isPet()) {
            return GameActionResult.error("You cannot attack a friendly companion.");
        }
        if (mob.template().hasTag("npc")) {
            return GameActionResult.error("You cannot attack that.");
        }
        List<GameMessage> messages = new ArrayList<>();
        String mobName = mob.template().name();
        String shooterName = attacker.getUsername().getValue();

        // A shot rolls to hit and crit through the same seeded resolution as a melee swing
        // (issue #591), so the shooter's agility and the bow's hit/crit bonuses matter.
        HitOutcome outcome = resolvePlayerHit(attacker, attack);
        if (!outcome.hit()) {
            // A missed shot deals no damage and gives the target no reason to close the distance:
            // the mob does not aggro, engage, or charge into the shooter's room (AGENTS.md §5).
            messages.add(GameMessage.toSource(rangedMissMessage(mob)));
            for (Username occupant : roomService.getPlayersInRoom(roomId)) {
                if (occupant.equals(attacker.getUsername())) {
                    continue;
                }
                playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                    GameMessage.toSource(shooterName + " fires at the " + mobName
                        + " to the " + direction.label() + " but misses."))));
            }
            for (Username occupant : roomService.getPlayersInRoom(adjacentRoomId)) {
                playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                    GameMessage.toSource("An arrow flies in from the " + direction.opposite().label()
                        + " and sails wide of the " + mobName + "."))));
            }
            return new GameActionResult(null, null, messages);
        }

        int damage = outcome.damage();
        int remaining = mob.takeDamage(damage);
        messages.add(GameMessage.toSource(
            rangedStrikeMessage(mob, damage, remaining, direction, outcome.crit())));

        // Announce the shot to bystanders in the shooter's room.
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(attacker.getUsername())) {
                continue;
            }
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource(shooterName + " fires at the " + mobName
                    + " to the " + direction.label() + "."))));
        }
        // Announce the incoming fire to anyone in the target's room.
        for (Username occupant : roomService.getPlayersInRoom(adjacentRoomId)) {
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource("An arrow flies in from the " + direction.opposite().label()
                    + " and strikes the " + mobName + "."))));
        }

        if (!mob.isAlive()) {
            awardMobKill(mob, attacker, roomId, messages);
            return new GameActionResult(null, null, messages);
        }

        // Retaliation: a surviving ranged-attacked mob closes the distance into the shooter's room
        // and engages them, so it fights in melee on subsequent ticks (AGENTS.md §5 — tick thread).
        for (Username occupant : roomService.getPlayersInRoom(adjacentRoomId)) {
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource("The " + mobName + " charges off to the "
                    + direction.opposite().label() + "."))));
        }
        mob.moveTo(roomId);
        mob.engage(attacker.getUsername());
        playerCombatTargets.put(attacker.getUsername(), mob.instanceId());
        messages.add(GameMessage.toSource(
            "The " + mobName + " charges in from the " + direction.label() + " to close with you!"));
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(attacker.getUsername())) {
                continue;
            }
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource("The " + mobName + " charges in from the "
                    + direction.label() + "."))));
        }
        return new GameActionResult(null, null, messages);
    }

    /**
     * Processes an area-of-effect spell cast, striking every hostile mob in the caster's room in a
     * single cast (AGENTS.md §5 — all mutation runs on the tick thread via the caster's command
     * queue). Backs {@link io.taanielo.jmud.core.ability.AbilityTargeting#AoE} spells (e.g.
     * chain-lightning) routed here from the CAST/USE command because hostile mobs, not players, are
     * the targets.
     *
     * <p>Resolution: every live, attackable (non-{@code "npc"}-tagged) mob in {@code roomId} is a
     * target. If none are present, the cast fails with no mana spent. Otherwise the mana cost scales
     * with the crowd — {@link io.taanielo.jmud.core.ability.AbilityCost#totalMana(int)} charges the
     * spell's base mana plus its {@code mana_per_target} once per target — and the cast is refused
     * (again spending nothing) if the caster cannot pay the scaled total. On success the caster's
     * mana is deducted once, then each mob is resolved independently through the same seeded hit/crit
     * roll as a melee swing (issue #595): a target may be missed outright (no damage) or crit for
     * bonus damage, rather than every mob eating the same flat number. Slain mobs award their normal
     * kill rewards (loot, XP, gold, quest credit), accumulated onto the caster so a single persisted
     * snapshot carries both the mana deduction and every reward.
     *
     * @param caster the casting player
     * @param spell  the resolved AoE spell ability
     * @param roomId the caster's current room
     * @return result whose {@code updatedSource} is the caster with mana (and any kill rewards)
     *         applied, plus per-target and roll-up messages; or an error with no state change
     */
    public GameActionResult processPlayerAoeSpell(Player caster, Ability spell, RoomId roomId) {
        Objects.requireNonNull(caster, "Caster is required");
        Objects.requireNonNull(spell, "Spell is required");
        Objects.requireNonNull(roomId, "Room id is required");

        List<MobInstance> targets = getMobsInRoom(roomId).stream()
            .filter(m -> !m.template().hasTag("npc") && !m.isPet())
            .toList();
        if (targets.isEmpty()) {
            return GameActionResult.error("There are no enemies here to strike.");
        }

        int scaledMana = spell.cost().totalMana(targets.size());
        if (caster.getVitals().getMana() < scaledMana) {
            return GameActionResult.error(
                "You lack the mana to unleash " + spell.name() + " (" + scaledMana + " needed).");
        }
        int baseDamage = abilityHpDamage(spell);
        DamageType damageType = abilityDamageType(spell);

        Player updated = caster.withVitals(caster.getVitals().consumeMana(scaledMana));

        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("You unleash " + spell.name() + ", striking "
            + targets.size() + (targets.size() == 1 ? " enemy" : " enemies") + "!"));
        String casterName = caster.getUsername().getValue();
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(caster.getUsername())) {
                continue;
            }
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource(casterName + " unleashes " + spell.name()
                    + " across the room!"))));
        }

        UUID firstSurvivor = null;
        for (MobInstance mob : targets) {
            String mobName = mob.template().name();
            // Each target rolls hit and crit independently (issue #595): the spell can miss some
            // mobs outright and crit others rather than dealing the same flat number to all.
            HitOutcome outcome = resolveSpellHit(caster, baseDamage);
            if (!outcome.hit()) {
                messages.add(GameMessage.toSource(aoeMissMessage(spell, mobName)));
                // A missed mob takes no damage but is still drawn into the fight, exactly as a
                // missed melee swing engages its target.
                mob.engage(caster.getUsername());
                if (firstSurvivor == null) {
                    firstSurvivor = mob.instanceId();
                }
                continue;
            }
            // Apply this mob's elemental resistance/vulnerability for the spell's damage type
            // (untyped/physical spells are unaffected) before the hit lands.
            ElementalOutcome elemental = applyMobElemental(outcome.damage(), mob, damageType);
            int damage = elemental.damage();
            String qualifier = elementalQualifier(damageType, elemental.reaction());
            int remaining = mob.takeDamage(damage);
            messages.add(GameMessage.toSource(
                aoeStrikeMessage(spell, mobName, damage, remaining, outcome.crit(), qualifier)));
            if (!mob.isAlive()) {
                updated = applyMobKillRewards(mob, updated, roomId, messages);
                continue;
            }
            mob.engage(caster.getUsername());
            if (firstSurvivor == null) {
                firstSurvivor = mob.instanceId();
            }
        }
        if (firstSurvivor != null) {
            playerCombatTargets.put(caster.getUsername(), firstSurvivor);
        }

        return new GameActionResult(updated, null, messages);
    }

    /**
     * Returns the damage a harmful ability deals to a mob target: the sum of its HP-decreasing
     * {@link io.taanielo.jmud.core.ability.AbilityEffectKind#VITALS} effects. Shared by the
     * area-of-effect ({@link #processPlayerAoeSpell}) and single-target
     * ({@link #processPlayerSingleTargetAbility}) mob paths. Non-damage effects (status effects,
     * cures) are ignored on the mob path, which has no persistent effect model.
     *
     * @param ability the harmful ability
     * @return the damage (zero when the ability defines no HP-decrease effect)
     */
    private static int abilityHpDamage(Ability ability) {
        int damage = 0;
        for (AbilityEffect effect : ability.effects()) {
            if (effect.kind() == AbilityEffectKind.VITALS
                && effect.stat() == AbilityStat.HP
                && effect.operation() == AbilityOperation.DECREASE) {
                damage += effect.amount();
            }
        }
        return damage;
    }

    /**
     * Returns the elemental {@link DamageType} a harmful ability's damage carries, parsed from the
     * (raw-string) {@code damageType} tag on its first damaging {@code VITALS} effect that declares
     * one. Abilities with no tagged effect deal {@link DamageType#PHYSICAL} (untyped) damage, which is
     * never resisted or amplified by a mob's elemental resistance/vulnerability — so today's untyped
     * spells and every melee/ranged attack are unaffected.
     *
     * @param ability the harmful ability
     * @return the ability's damage type, or {@link DamageType#PHYSICAL} when untyped
     */
    private static DamageType abilityDamageType(Ability ability) {
        for (AbilityEffect effect : ability.effects()) {
            if (effect.kind() == AbilityEffectKind.VITALS
                && effect.stat() == AbilityStat.HP
                && effect.operation() == AbilityOperation.DECREASE) {
                DamageType type = DamageType.fromString(effect.damageType());
                if (type != DamageType.PHYSICAL) {
                    return type;
                }
            }
        }
        return DamageType.PHYSICAL;
    }

    /**
     * How a mob's elemental identity reacted to an incoming typed ability hit: it resisted the
     * element (took reduced damage), was vulnerable to it (took extra damage), or was unaffected
     * (untyped damage, or a type it neither resists nor is weak to).
     */
    private enum ElementalReaction {
        NONE, RESISTED, VULNERABLE
    }

    /**
     * The result of applying a mob's elemental resistance/vulnerability to a rolled ability hit: the
     * adjusted damage (never below 1) and which reaction, if any, occurred (used to narrate the hit).
     */
    private record ElementalOutcome(int damage, ElementalReaction reaction) {
    }

    /**
     * Applies the target mob's elemental resistance or vulnerability for the given {@link DamageType}
     * to a rolled ability hit, mirroring the player-side equipment mitigation in
     * {@link #mitigateForDefender}. Untyped/{@link DamageType#PHYSICAL} damage is returned unchanged so
     * melee, ranged, and untagged spells behave exactly as before. Resistance is capped at
     * {@link CombatSettings#maxResistancePercent()} so a resisted hit always lands for at least a
     * fraction of its rolled damage (never fully negated); vulnerability is capped generously at
     * {@link MobTemplate#MAX_VULNERABILITY_PERCENT}. Rounding matches the equipment path
     * ({@link Math#round}) and the result is floored at 1.
     *
     * @param rawDamage the rolled (post-crit) damage before elemental adjustment, at least 1
     * @param mob       the target mob whose elemental identity applies
     * @param type      the incoming ability's damage type
     * @return the adjusted damage and the reaction that occurred
     */
    private ElementalOutcome applyMobElemental(int rawDamage, MobInstance mob, DamageType type) {
        if (!type.isResistible()) {
            return new ElementalOutcome(rawDamage, ElementalReaction.NONE);
        }
        MobTemplate template = mob.template();
        int resist = template.resistancePercent(type);
        if (resist > 0) {
            int capped = Math.min(resist, CombatSettings.maxResistancePercent());
            int adjusted = Math.max(1, (int) Math.round(rawDamage * ((100 - capped) / 100.0)));
            return new ElementalOutcome(adjusted, ElementalReaction.RESISTED);
        }
        int vuln = template.vulnerabilityPercent(type);
        if (vuln > 0) {
            int capped = Math.min(vuln, MobTemplate.MAX_VULNERABILITY_PERCENT);
            int adjusted = Math.max(1, (int) Math.round(rawDamage * ((100 + capped) / 100.0)));
            return new ElementalOutcome(adjusted, ElementalReaction.VULNERABLE);
        }
        return new ElementalOutcome(rawDamage, ElementalReaction.NONE);
    }

    /**
     * Builds the short strike-message qualifier that makes a mob's elemental reaction legible to the
     * caster (e.g. "The flames sizzle weakly against its resilient hide — " for a resisted fire hit),
     * so players learn a matchup without consulting a spreadsheet. Returns an empty string for
     * {@link ElementalReaction#NONE}.
     *
     * @param type     the incoming damage type
     * @param reaction the reaction the mob's elemental identity produced
     * @return a qualifier prefix (with trailing separator), or an empty string when there is no reaction
     */
    private static String elementalQualifier(DamageType type, ElementalReaction reaction) {
        return switch (reaction) {
            case NONE -> "";
            case RESISTED -> switch (type) {
                case FIRE -> "The flames sizzle weakly against its resilient hide — ";
                case COLD -> "The frost barely bites its hardened form — ";
                case POISON -> "The venom struggles against its resilient blood — ";
                case PHYSICAL -> "";
            };
            case VULNERABLE -> switch (type) {
                case FIRE -> "The flames roar hungrily against it — ";
                case COLD -> "The frost sinks deep into it — ";
                case POISON -> "The venom courses freely through it — ";
                case PHYSICAL -> "";
            };
        };
    }

    /**
     * Builds the per-target line shown to a caster whose AoE spell landed on a mob, mirroring the
     * melee/ranged strike phrasing. A critical hit is prefixed with a "critical hit!" notice so a
     * crit reads distinctly from a normal strike.
     *
     * @param spell     the AoE spell being cast
     * @param mobName   the struck mob's display name
     * @param damage    the damage this target took
     * @param remaining the target's HP after the strike
     * @param crit      whether this target was struck for a critical hit
     * @param qualifier an elemental resist/vulnerability prefix (empty when the hit was neither)
     * @return the message text to show the caster for this target
     */
    private static String aoeStrikeMessage(
        Ability spell, String mobName, int damage, int remaining, boolean crit, String qualifier) {
        String critPrefix = crit ? "A critical hit! " : "";
        return qualifier + critPrefix + "Your " + spell.name() + " strikes the " + mobName + " for "
            + damage + " damage. (" + remaining + " HP remaining)";
    }

    /**
     * Builds the per-target line shown to a caster whose AoE spell missed a mob outright, so an AoE
     * spell can miss a target rather than always landing for a flat amount (issue #595).
     *
     * @param spell   the AoE spell being cast
     * @param mobName the missed mob's display name
     * @return the miss message text to show the caster for this target
     */
    private static String aoeMissMessage(Ability spell, String mobName) {
        return "Your " + spell.name() + " crackles harmlessly past the " + mobName + ".";
    }

    /**
     * Returns whether a live, attackable mob matching {@code nameInput} is present in {@code roomId},
     * so the CAST/USE command routing can decide whether a harmful single-target ability names a mob
     * (routed here) or a player (left to the generic ability path for duels/PvP). A pet or an
     * {@code "npc"}-tagged mob is not attackable and does not match, so naming one falls through to the
     * generic path exactly as it does today.
     *
     * @param roomId    the room to search
     * @param nameInput the raw target-name input typed after the ability
     * @return {@code true} when an attackable mob in the room matches the name
     */
    public boolean hasAttackableMob(RoomId roomId, String nameInput) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (nameInput == null || nameInput.isBlank()) {
            return false;
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), nameInput);
        return mob != null && !mob.isPet() && !mob.template().hasTag("npc");
    }

    /**
     * Processes a harmful single-target ability (a {@link AbilityTargeting#HARMFUL},
     * {@link AbilityTargeting#HARMFUL_OPENER}, or {@link AbilityTargeting#HARMFUL_UNDEAD} spell or
     * skill) cast at a mob in the caster's room, mirroring {@link #processPlayerAoeSpell} for a single
     * target. Backs {@code CAST <ability> <mob>} / {@code USE <ability> <mob>} against a monster; the
     * generic ability engine ({@code GameActionService.useAbility}) still owns the same abilities aimed
     * at another <em>player</em> (duels/PvP), so this path is entered only when the named target is a
     * mob (see {@link #hasAttackableMob}).
     *
     * <p>All mutation runs on the tick thread via the caster's command queue (AGENTS.md §5). The strike
     * rolls to hit and crit through the same seeded {@link #resolveHit} used by melee, ranged, and AoE
     * (a single-target ability can therefore miss or crit, not just land a flat number). Damage is the
     * sum of the ability's HP-decreasing {@code VITALS} effects ({@link #abilityHpDamage}). A killing
     * blow awards the normal kill rewards (loot, XP, gold, quest credit, reputation, achievements)
     * through {@link #applyMobKillRewards}. The ability's mana/move cost is deducted on any resolved
     * cast (hit or miss) and folded into the returned {@link GameActionResult#updatedSource()}; a
     * validation failure (no target, undead gate, opener gate, or insufficient resources) spends
     * nothing and returns an error with no {@code updatedSource}, so the caller starts no cooldown.
     *
     * <p>{@link AbilityTargeting#HARMFUL_UNDEAD} gates on the mob's {@code "undead"} tag; a
     * {@link AbilityTargeting#HARMFUL_OPENER} refuses when the caster is already engaged in combat and,
     * when opened from stealth, deals {@link #STEALTH_BACKSTAB_BONUS_DAMAGE} bonus damage before
     * breaking stealth — matching the PvP behaviour in
     * {@link io.taanielo.jmud.core.action.GameActionService}.
     *
     * @param caster      the casting player
     * @param ability     the resolved harmful single-target ability
     * @param targetInput the raw mob-name input naming the target
     * @param roomId      the caster's current room
     * @return result whose {@code updatedSource} is the caster with cost (and any kill rewards) applied
     *         on a resolved cast, plus strike/miss messages; or an error with no state change
     */
    public GameActionResult processPlayerSingleTargetAbility(
        Player caster, Ability ability, String targetInput, RoomId roomId) {
        Objects.requireNonNull(caster, "Caster is required");
        Objects.requireNonNull(ability, "Ability is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (targetInput == null || targetInput.isBlank()) {
            return GameActionResult.error("You must specify a target.");
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), targetInput);
        if (mob == null || !mob.isAlive() || mob.isPet() || mob.template().hasTag("npc")) {
            return GameActionResult.error("No such target here.");
        }
        // Opener gate: a HARMFUL_OPENER (backstab) may only start a fight, never continue one.
        if (ability.targeting() == AbilityTargeting.HARMFUL_OPENER
            && isInCombat(caster.getUsername())) {
            return GameActionResult.error(
                "You can only backstab as an opener — you are already in combat.");
        }
        // Undead gate: HARMFUL_UNDEAD (holy damage) only affects undead-tagged mobs.
        if (ability.targeting() == AbilityTargeting.HARMFUL_UNDEAD
            && !mob.template().hasTag("undead")) {
            return GameActionResult.error("Your holy power has no effect on that creature.");
        }
        AbilityCost cost = ability.cost();
        if (caster.getVitals().mana() < cost.mana() || caster.getVitals().move() < cost.move()) {
            return GameActionResult.error("You lack the resources to use that ability.");
        }
        Player updated = caster.withVitals(
            caster.getVitals().consumeMana(cost.mana()).consumeMove(cost.move()));

        int baseDamage = abilityHpDamage(ability);
        DamageType damageType = abilityDamageType(ability);
        String mobName = mob.template().name();
        boolean struckFromStealth = caster.isStealthActive();
        List<GameMessage> messages = new ArrayList<>();

        // The strike rolls hit and crit through the same seeded resolution as a melee swing, ranged
        // shot, and AoE spell (issue #651): a single-target ability can miss a mob or crit it.
        HitOutcome outcome = resolveSpellHit(caster, baseDamage);
        if (!outcome.hit()) {
            messages.add(GameMessage.toSource(singleTargetMissMessage(ability, mobName)));
            // A missed strike still engages the mob, exactly as a missed melee swing does.
            mob.engage(caster.getUsername());
            playerCombatTargets.put(caster.getUsername(), mob.instanceId());
            updated = breakStealthAfterStrike(updated, struckFromStealth, mobName, roomId, messages);
            return new GameActionResult(updated, null, messages);
        }
        int damage = outcome.damage();
        // Stealth opener bonus (backstab): a strike from the shadows adds flat bonus damage before it
        // lands, matching the PvP path (the crit multiplier has already been applied to the ability's
        // own damage, so the bonus itself is never doubled).
        if (struckFromStealth && BACKSTAB_ABILITY_ID.equals(ability.id())) {
            damage += STEALTH_BACKSTAB_BONUS_DAMAGE;
            messages.add(GameMessage.toSource(
                "Your strike from the shadows lands with deadly precision (+"
                    + STEALTH_BACKSTAB_BONUS_DAMAGE + " damage)!"));
        }
        // Apply the target mob's elemental resistance/vulnerability for this ability's damage type
        // (untyped/physical abilities are unaffected) before the hit lands.
        ElementalOutcome elemental = applyMobElemental(damage, mob, damageType);
        damage = elemental.damage();
        String qualifier = elementalQualifier(damageType, elemental.reaction());
        int remaining = mob.takeDamage(damage);
        messages.add(GameMessage.toSource(
            singleTargetStrikeMessage(ability, mobName, damage, remaining, outcome.crit(), qualifier)));
        updated = breakStealthAfterStrike(updated, struckFromStealth, mobName, roomId, messages);
        if (!mob.isAlive()) {
            updated = applyMobKillRewards(mob, updated, roomId, messages);
            return new GameActionResult(updated, null, messages);
        }
        mob.engage(caster.getUsername());
        playerCombatTargets.put(caster.getUsername(), mob.instanceId());
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Breaks a caster's stealth after a harmful single-target strike lands or misses a mob, mirroring
     * the PvP behaviour where any deliberate ability use against a distinct target emerges the caster
     * from the shadows. A no-op (returning the caster unchanged) when the caster was not stealthed.
     *
     * @param caster            the (cost-charged) caster
     * @param struckFromStealth whether the caster was stealthed when the strike resolved
     * @param mobName           the struck mob's display name, used in the room broadcast
     * @param roomId            the caster's room, whose other players see the reveal
     * @param messages          the mutable self-facing message list to append the reveal notice to
     * @return the caster with stealth cleared, or unchanged when not stealthed
     */
    private Player breakStealthAfterStrike(
        Player caster, boolean struckFromStealth, String mobName, RoomId roomId, List<GameMessage> messages) {
        if (!struckFromStealth) {
            return caster;
        }
        Player revealed = caster.withStealth(false);
        messages.add(GameMessage.toSource("You emerge from the shadows."));
        String casterName = caster.getUsername().getValue();
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (occupant.equals(caster.getUsername())) {
                continue;
            }
            playerEventBus.publish(occupant, new GameActionResult(null, null, List.of(
                GameMessage.toSource(casterName + " emerges from the shadows and strikes the "
                    + mobName + "!"))));
        }
        return revealed;
    }

    /**
     * Builds the self-facing line shown to a caster whose harmful single-target ability landed on a
     * mob, mirroring the AoE/melee/ranged strike phrasing. A critical hit is prefixed with a "critical
     * hit!" notice so a crit reads distinctly from a normal strike.
     *
     * @param ability   the ability being used
     * @param mobName   the struck mob's display name
     * @param damage    the damage this strike dealt
     * @param remaining the mob's HP after the strike
     * @param crit      whether this strike was a critical hit
     * @param qualifier an elemental resist/vulnerability prefix (empty when the hit was neither)
     * @return the message text to show the caster
     */
    private static String singleTargetStrikeMessage(
        Ability ability, String mobName, int damage, int remaining, boolean crit, String qualifier) {
        String critPrefix = crit ? "A critical hit! " : "";
        return qualifier + critPrefix + "Your " + ability.name() + " strikes the " + mobName + " for "
            + damage + " damage. (" + remaining + " HP remaining)";
    }

    /**
     * Builds the self-facing line shown to a caster whose harmful single-target ability missed a mob
     * outright, so a single-target ability can miss rather than always landing for a flat amount
     * (issue #651).
     *
     * @param ability the ability being used
     * @param mobName the missed mob's display name
     * @return the miss message text to show the caster
     */
    private static String singleTargetMissMessage(Ability ability, String mobName) {
        return "Your " + ability.name() + " fails to connect with the " + mobName + ".";
    }

    /**
     * Finds a live mob in the given room matching {@code nameInput} and wraps it as a
     * {@link NpcStealPort.StealVictim} for the rogue STEAL skill. Backs
     * {@link io.taanielo.jmud.core.action.GameActionService#steal(Player, String)}: the game rule
     * (success roll, validation, messages) lives in the action service, while this port exposes only
     * the mob-owned operations (reading stealable gold, turning hostile).
     *
     * @param roomId    the room the thief is in
     * @param nameInput the raw NPC-name input (case-insensitive, prefix match)
     * @return the matched victim, or empty when no live mob in the room matches
     */
    @Override
    public Optional<StealVictim> findStealTarget(RoomId roomId, String nameInput) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (nameInput == null || nameInput.isBlank()) {
            return Optional.empty();
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), nameInput);
        return mob == null ? Optional.empty() : Optional.of(new MobStealVictim(mob));
    }

    /**
     * Adapter exposing a {@link MobInstance} to the STEAL skill through the {@link NpcStealPort}
     * contract, so the action service never depends on the concrete mob type. All mutations run on
     * the tick thread via the player command queue (AGENTS.md §5).
     */
    private final class MobStealVictim implements StealVictim {
        private final MobInstance mob;

        private MobStealVictim(MobInstance mob) {
            this.mob = mob;
        }

        @Override
        public String name() {
            return mob.template().name();
        }

        @Override
        public boolean hasStealableGold() {
            GoldDrop goldDrop = mob.template().goldDrop();
            return goldDrop != null && goldDrop.max() > 0;
        }

        @Override
        public int stealGold() {
            GoldDrop goldDrop = mob.template().goldDrop();
            return goldDrop == null ? 0 : goldDrop.roll(random);
        }

        @Override
        public void turnHostile(Username thief) {
            mob.engage(thief);
            playerCombatTargets.put(thief, mob.instanceId());
        }
    }

    // ── Summoned pets ─────────────────────────────────────────────────

    /**
     * Summons a temporary pet mob (necromancer-style SUMMON spell) that fights hostile mobs on the
     * caster's behalf. Validates the caster's level and mana, enforces the one-active-pet-at-a-time
     * rule, then spawns a pet from the cached pet template into the caster's room and registers it so
     * it participates in combat from the next tick. Runs entirely on the tick thread via the caster's
     * command queue (AGENTS.md §5); the pet template is read from an in-memory cache, never from disk.
     *
     * @param caster the summoning player
     * @param spell  the resolved SUMMON spell ability, supplying the level requirement and mana cost
     * @param roomId the caster's current room, where the pet is spawned
     * @return result whose {@code updatedSource} is the caster with mana deducted on success, plus
     *         summon messages; or an error with no state change when validation fails
     */
    public GameActionResult processSummon(Player caster, Ability spell, RoomId roomId) {
        Objects.requireNonNull(caster, "Caster is required");
        Objects.requireNonNull(spell, "Spell is required");
        Objects.requireNonNull(roomId, "Room id is required");

        if (caster.getLevel() < spell.level()) {
            return GameActionResult.error(
                "You are not experienced enough to cast " + spell.name() + ".");
        }
        int manaCost = spell.cost().mana();
        if (caster.getVitals().getMana() < manaCost) {
            return GameActionResult.error(
                "You lack the mana to cast " + spell.name() + " (" + manaCost + " needed).");
        }
        if (hasActivePet(caster.getUsername())) {
            return GameActionResult.error(
                "You already have a summoned pet. Dismiss it before summoning another.");
        }
        MobTemplate petTemplate = petTemplates.isEmpty() ? null : petTemplates.get(0);
        if (petTemplate == null || petTemplate.summonDurationTicks() == null) {
            return GameActionResult.error("Your summons fizzles — nothing answers the call.");
        }
        MobInstance pet = MobInstance.summoned(
            petTemplate, roomId, caster.getUsername(), caster.getLevel(),
            petTemplate.summonDurationTicks());
        instances.put(pet.instanceId(), pet);

        Player updated = caster.withVitals(caster.getVitals().consumeMana(manaCost));
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You chant the words of summoning and a " + petTemplate.name()
                + " rises to fight at your side!"));
        broadcastToRoomExcept(roomId, caster.getUsername(),
            caster.getUsername().getValue() + " summons a " + petTemplate.name() + "!");
        return new GameActionResult(updated, null, messages);
    }

    /**
     * Dismisses the caster's active summoned pet on command, removing it from the world immediately.
     * Runs on the tick thread via the caster's command queue (AGENTS.md §5).
     *
     * @param caster the player dismissing their pet
     * @param roomId the caster's current room, used to announce the dismissal to bystanders
     * @return result with a dismissal message, or an error when the caster has no active pet
     */
    public GameActionResult dismissPet(Player caster, RoomId roomId) {
        Objects.requireNonNull(caster, "Caster is required");
        Objects.requireNonNull(roomId, "Room id is required");
        MobInstance pet = findActivePet(caster.getUsername());
        if (pet == null) {
            return GameActionResult.error("You have no summoned pet to dismiss.");
        }
        instances.remove(pet.instanceId());
        broadcastToRoomExcept(pet.roomId(), caster.getUsername(),
            "The " + pet.template().name() + " fades back into the ether.");
        return new GameActionResult(null, null, List.of(GameMessage.toSource(
            "You release your " + pet.template().name() + " and it fades away.")));
    }

    private boolean hasActivePet(Username summoner) {
        return findActivePet(summoner) != null;
    }

    private MobInstance findActivePet(Username summoner) {
        for (MobInstance mob : instances.values()) {
            if (mob.isSummoned() && mob.isAlive() && summoner.equals(mob.summoner())) {
                return mob;
            }
        }
        return null;
    }

    // ── Tamed pets ────────────────────────────────────────────────────

    /** Maximum number of tamed companions a single player may control at once. */
    static final int MAX_TAMED_PETS = 3;

    /** Maximum length of a companion's custom name (see the NAME command). */
    static final int MAX_PET_NAME_LENGTH = 24;

    /**
     * Maximum length of a companion's custom roleplay description (see the DESCRIBE command). Shares
     * the player-description cap so a companion description and a player description use one limit.
     */
    static final int MAX_PET_DESCRIPTION_LENGTH = PlayerIdentity.MAX_DESCRIPTION_LENGTH;

    /**
     * Permanently tames (charms) a charmable mob in the tamer's room, turning it into a persistent
     * companion that follows its owner between rooms and fights at their side. The captured wild mob
     * is removed from the world and scheduled to respawn, a tamed instance is spawned in its place,
     * and the companion is recorded on the tamer's save so it survives logout/login. Runs entirely on
     * the tick thread via the tamer's command queue (AGENTS.md §5).
     *
     * @param tamer  the taming player
     * @param input  the raw mob-name input to tame
     * @param roomId the tamer's current room
     * @return result whose {@code updatedSource} is the tamer with the new companion recorded on
     *         success, plus tame messages; or an error with no state change when validation fails
     */
    public GameActionResult processTame(Player tamer, String input, RoomId roomId) {
        Objects.requireNonNull(tamer, "Tamer is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (input == null || input.isBlank()) {
            return GameActionResult.error("Tame what?");
        }
        MobInstance mob = findMobByName(getMobsInRoom(roomId), input);
        if (mob == null) {
            return GameActionResult.error("No such target here.");
        }
        if (mob.isPet()) {
            return GameActionResult.error("That creature already serves someone.");
        }
        if (mob.template().hasTag("npc")) {
            return GameActionResult.error("You cannot tame that.");
        }
        if (!mob.template().charmable()) {
            return GameActionResult.error("The " + mob.template().name() + " cannot be tamed.");
        }
        if (countTamedPets(tamer.getUsername()) >= MAX_TAMED_PETS) {
            return GameActionResult.error(
                "You cannot care for more than " + MAX_TAMED_PETS + " companions at once.");
        }

        String petName = mob.template().name();
        // Consume the wild mob: reduce it to zero HP and schedule its respawn so the world
        // repopulates, then spawn a fresh tamed companion from the same template.
        mob.takeDamage(mob.currentHp());
        mob.scheduleRespawn(currentTimeOfDay());
        endCombatForMob(mob);

        MobInstance pet = MobInstance.tamed(mob.template(), roomId, tamer.getUsername(), tamer.getLevel());
        instances.put(pet.instanceId(), pet);

        Player updated = tamer.withTamedPets(tamer.pets().tame(mob.template().id().getValue()));
        saveOrLog(updated);

        broadcastToRoomExcept(roomId, tamer.getUsername(),
            tamer.getUsername().getValue() + " tames a " + petName + "!");
        return new GameActionResult(updated, null, List.of(GameMessage.toSource(
            "You calm the " + petName + " and it now follows you as a loyal companion!")));
    }

    /**
     * Lists the player's active tamed companions, one per line, with each pet's current room and HP.
     * Runs on the tick thread via the player's command queue (AGENTS.md §5).
     *
     * @param owner the player whose companions to list
     * @return result with the companion listing (or a "no companions" notice)
     */
    public GameActionResult listCompanions(Player owner) {
        Objects.requireNonNull(owner, "Owner is required");
        Username username = owner.getUsername();
        List<MobInstance> pets = instances.values().stream()
            .filter(m -> m.isTamed() && m.isAlive() && username.equals(m.owner()))
            .toList();
        if (pets.isEmpty()) {
            return new GameActionResult(null, null, List.of(
                GameMessage.toSource("You have no companions.")));
        }
        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("Your companions:"));
        for (MobInstance pet : pets) {
            messages.add(GameMessage.toSource("  " + pet.displayName()
                + " (" + pet.currentHp() + "/" + pet.template().maxHp() + " HP) in "
                + pet.roomId().getValue()));
        }
        return new GameActionResult(null, null, messages);
    }

    /**
     * Assigns a custom display name to one of the player's own tamed companions (see the NAME
     * command). The companion is matched by its current display name or template name (prefix match,
     * first live occurrence, consistent with other pet targeting); its live instance and the owner's
     * persisted {@link PlayerPets} record are both updated so the name shows everywhere immediately
     * and survives logout/login and respawn. Runs on the tick thread via the owner's command queue
     * (AGENTS.md §5).
     *
     * @param owner           the player naming their companion
     * @param companionInput  the target companion token (template or existing custom name)
     * @param newName         the desired custom name (validated: non-blank, capped length)
     * @return result whose {@code updatedSource} is the owner with the renamed companion persisted on
     *         success; or an error with no state change when validation/targeting fails
     */
    public GameActionResult nameCompanion(Player owner, String companionInput, String newName) {
        Objects.requireNonNull(owner, "Owner is required");
        if (companionInput == null || companionInput.isBlank()) {
            return GameActionResult.error("Usage: NAME <companion> <new name>");
        }
        if (newName == null || newName.isBlank()) {
            return GameActionResult.error("Name your companion what? Usage: NAME <companion> <new name>");
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() > MAX_PET_NAME_LENGTH) {
            return GameActionResult.error(
                "That name is too long; keep it to " + MAX_PET_NAME_LENGTH + " characters or fewer.");
        }
        Username username = owner.getUsername();
        List<MobInstance> pets = instances.values().stream()
            .filter(m -> m.isTamed() && m.isAlive() && username.equals(m.owner()))
            .toList();
        MobInstance target = findMobByName(pets, companionInput);
        if (target == null) {
            return GameActionResult.error("You have no companion called \"" + companionInput.trim() + "\".");
        }
        String templateId = target.template().id().getValue();
        String previousName = target.customName();
        target.setCustomName(trimmedName);
        Player updated = owner.withTamedPets(
            owner.pets().withName(templateId, previousName, trimmedName));
        saveOrLog(updated);
        return new GameActionResult(updated, null, List.of(GameMessage.toSource(
            "Your companion shall henceforth be known as " + trimmedName + ".")));
    }

    /**
     * Returns the given player's own live tamed companions, in no particular order. Shared by the
     * companion-targeting commands (NAME/DESCRIBE) so they resolve against exactly the pets the player
     * controls.
     *
     * @param owner the owning player's username
     * @return the owner's live tamed companions (never null, may be empty)
     */
    private List<MobInstance> ownedLiveCompanions(Username owner) {
        return instances.values().stream()
            .filter(m -> m.isTamed() && m.isAlive() && owner.equals(m.owner()))
            .toList();
    }

    /**
     * Returns whether the given player owns a live tamed companion matching {@code token} by its
     * current display name or template name (same resolution the NAME command uses). Lets the
     * DESCRIBE command decide whether a leading token targets one of the player's companions (pet
     * description) or is the start of the player's own roleplay description. Runs on the tick thread.
     *
     * @param owner the player whose companions to check
     * @param token the leading target token typed by the player
     * @return {@code true} when the token resolves to one of the player's live companions
     */
    public boolean ownsCompanionMatching(Player owner, String token) {
        Objects.requireNonNull(owner, "Owner is required");
        if (token == null || token.isBlank()) {
            return false;
        }
        return findMobByName(ownedLiveCompanions(owner.getUsername()), token) != null;
    }

    /**
     * Applies a DESCRIBE action to one of the player's own tamed companions (see the DESCRIBE
     * command). The companion is matched among the player's live pets by its current display name or
     * template name (same targeting as NAME). The {@code descriptionArg} selects the action:
     * <ul>
     *   <li>blank/{@code null} — query: report the current custom description, or a not-set hint,
     *       to the owner only, with no state change.</li>
     *   <li>{@code CLEAR}/{@code NONE} — clear the custom description back to the generic LOOK line.</li>
     *   <li>anything else — set the custom description (capped at
     *       {@link #MAX_PET_DESCRIPTION_LENGTH} characters).</li>
     * </ul>
     * On a set/clear the live instance and the owner's persisted {@link PlayerPets} record are both
     * updated so the description shows on LOOK immediately and survives logout/login and respawn. Runs
     * on the tick thread via the owner's command queue (AGENTS.md §5).
     *
     * @param owner          the player describing their companion
     * @param companionInput the target companion token (template or existing custom name)
     * @param descriptionArg the description text, or a query/CLEAR/NONE directive as above
     * @return result whose {@code updatedSource} is the owner with the change persisted on a
     *         successful set/clear; a message-only result on a query; or an error with no state change
     *         when targeting/validation fails
     */
    public GameActionResult describeCompanion(
        Player owner, String companionInput, @Nullable String descriptionArg) {
        Objects.requireNonNull(owner, "Owner is required");
        if (companionInput == null || companionInput.isBlank()) {
            return GameActionResult.error("Usage: DESCRIBE <companion> <description>");
        }
        MobInstance target = findMobByName(ownedLiveCompanions(owner.getUsername()), companionInput);
        if (target == null) {
            return GameActionResult.error(
                "You have no companion called \"" + companionInput.trim() + "\".");
        }
        String templateId = target.template().id().getValue();
        String customName = target.customName();
        String arg = descriptionArg == null ? "" : descriptionArg.trim();

        if (arg.isEmpty()) {
            String current = target.customDescription();
            if (current == null) {
                return new GameActionResult(null, null, List.of(GameMessage.toSource(
                    "Your " + target.displayName() + " has no custom description set. Use DESCRIBE "
                        + companionInput.trim() + " <text> to set one.")));
            }
            return new GameActionResult(null, null, List.of(
                GameMessage.toSource("Description of " + target.displayName() + ":"),
                GameMessage.toSource("  " + current)));
        }

        if (arg.equalsIgnoreCase("CLEAR") || arg.equalsIgnoreCase("NONE")) {
            if (target.customDescription() == null) {
                return GameActionResult.error(
                    "Your " + target.displayName() + " has no custom description to clear.");
            }
            target.setDescription(null);
            Player updated = owner.withTamedPets(
                owner.pets().withDescription(templateId, customName, null));
            saveOrLog(updated);
            return new GameActionResult(updated, null, List.of(GameMessage.toSource(
                "Your " + target.displayName() + "'s description has been cleared.")));
        }

        if (arg.length() > MAX_PET_DESCRIPTION_LENGTH) {
            return GameActionResult.error("That description is too long (" + arg.length()
                + " characters); the limit is " + MAX_PET_DESCRIPTION_LENGTH + ".");
        }
        target.setDescription(arg);
        Player updated = owner.withTamedPets(
            owner.pets().withDescription(templateId, customName, arg));
        saveOrLog(updated);
        return new GameActionResult(updated, null, List.of(GameMessage.toSource(
            "Your " + target.displayName() + "'s description has been set.")));
    }

    /**
     * Resolves the LOOK-at-a-creature text for a mob matching {@code token} in {@code roomId}, used by
     * the LOOK command when its target is not a player. When the mob is a tamed companion carrying a
     * custom description (see the DESCRIBE command) that description is shown; otherwise a generic
     * "nothing special" line is shown — a companion's description hint ("none set") is never shown here,
     * only to its owner via DESCRIBE. Returns empty when no live mob in the room matches the token, so
     * the caller can report that nothing was seen. Safe to call from the tick thread.
     *
     * @param roomId the room to search
     * @param token  the target token typed by the player
     * @return the LOOK lines to display, or empty when no mob matches
     */
    public Optional<List<String>> describeMobOnLook(RoomId roomId, String token) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        List<MobInstance> matches = getMobsInRoom(roomId).stream()
            .filter(m -> nameMatches(m.displayName(), normalized) || nameMatches(m.template().name(), normalized))
            .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        // Several mobs in the room may match the token — e.g. a tamed companion carrying a custom
        // description standing beside a wild mob of the same species freshly spawned at start-up.
        // Prefer a match with a custom description so a described pet's text is shown deterministically
        // rather than depending on the (unordered) mob-instance iteration order, which otherwise makes
        // LOOK non-deterministic when a wild same-species mob shares the room.
        MobInstance mob = matches.stream()
            .filter(m -> m.customDescription() != null)
            .findFirst()
            .orElse(matches.get(0));
        String description = mob.customDescription();
        if (description != null) {
            return Optional.of(List.of(description));
        }
        return Optional.of(List.of("You see nothing special about " + mob.displayName() + "."));
    }

    /** Composite key pairing a tamed pet's template id and custom name for spawn deduplication. */
    private static String liveKey(String templateId, @Nullable String customName) {
        return templateId + " " + (customName == null ? "" : customName);
    }

    /**
     * Spawns any of the given owner's persisted tamed companions that are not already live in the
     * world, placing them into the owner's current room. Called when a player enters the world so
     * their companions rejoin them (AGENTS.md §5 — tick thread). Companions whose template can no
     * longer be found are skipped.
     *
     * @param owner  the player entering the world
     * @param roomId the owner's current room
     */
    public void spawnTamedPets(Player owner, RoomId roomId) {
        Objects.requireNonNull(owner, "Owner is required");
        Objects.requireNonNull(roomId, "Room id is required");
        Username username = owner.getUsername();
        // Dedup against companions already live in the world by (template, custom name) so a named
        // duplicate is not double-spawned and each persisted entry keeps its own name.
        List<String> alreadyLive = new ArrayList<>(instances.values().stream()
            .filter(m -> m.isTamed() && username.equals(m.owner()))
            .map(m -> liveKey(m.template().id().getValue(), m.customName()))
            .toList());
        for (PlayerPets.TamedPet entry : owner.pets().entries()) {
            String templateId = entry.templateId();
            if (alreadyLive.remove(liveKey(templateId, entry.customName()))) {
                continue;
            }
            MobTemplate template = templatesById.get(templateId);
            if (template == null) {
                log.warn("Tamed pet template {} for player {} no longer exists", templateId, username);
                continue;
            }
            MobInstance pet = MobInstance.tamed(
                template, roomId, username, owner.getLevel(), entry.customName(),
                entry.customDescription());
            instances.put(pet.instanceId(), pet);
        }
    }

    private int countTamedPets(Username owner) {
        return (int) instances.values().stream()
            .filter(m -> m.isTamed() && m.isAlive() && owner.equals(m.owner()))
            .count();
    }

    /**
     * Pet phase: for each pet (summoned or tamed), remove it if it has been destroyed in combat,
     * decrement a summon's lifetime (auto-dismissing on expiry), and otherwise let it strike a
     * hostile mob in its room. Tamed pets have no lifetime — they persist until dismissed or slain.
     * Runs on the tick thread (AGENTS.md §5).
     */
    private void runPetCombat() {
        for (MobInstance pet : List.copyOf(instances.values())) {
            if (!pet.isPet()) {
                continue;
            }
            if (!pet.isAlive()) {
                String petName = pet.displayName();
                if (pet.isTamed()) {
                    releasePersistedPet(pet);
                    removePet(pet, "Your " + petName + " has been slain!",
                        "The " + petName + " collapses, slain.");
                } else {
                    removePet(pet, "Your " + petName + " has been destroyed!",
                        "The " + petName + " collapses into dust.");
                }
                continue;
            }
            if (pet.isSummoned() && pet.tickSummonLifetime()) {
                removePet(pet, "Your " + pet.template().name() + " fades back into the ether.",
                    "The " + pet.template().name() + " fades back into the ether.");
                continue;
            }
            runPetAttack(pet);
        }
    }

    /**
     * Follow phase: moves each tamed pet to its owner's current room when the owner is present and
     * standing elsewhere, so a companion trails its master between rooms on the next tick. Offline or
     * roomless owners are skipped, leaving the pet where it was. Runs on the tick thread (AGENTS.md
     * §5).
     */
    private void runPetFollow() {
        for (MobInstance pet : List.copyOf(instances.values())) {
            if (!pet.isTamed() || !pet.isAlive()) {
                continue;
            }
            Username owner = pet.owner();
            if (owner == null) {
                continue;
            }
            RoomId ownerRoom = roomService.findPlayerLocation(owner).orElse(null);
            if (ownerRoom == null || ownerRoom.equals(pet.roomId())) {
                continue;
            }
            String petName = pet.displayName();
            broadcastToRoomExcept(pet.roomId(), owner,
                "The " + petName + " trots away, following its master.");
            pet.moveTo(ownerRoom);
            playerEventBus.publish(owner, new GameActionResult(null, null, List.of(
                GameMessage.toSource("Your " + petName + " pads into the room, following you."))));
            broadcastToRoomExcept(ownerRoom, owner,
                "A " + petName + " pads in, following " + owner.getValue() + ".");
        }
    }

    /**
     * Removes a tamed pet from its owner's persisted companion list and saves the owner, so a pet
     * that is slain does not respawn on the owner's next login. Runs on the tick thread.
     *
     * @param pet the tamed pet being removed
     */
    private void releasePersistedPet(MobInstance pet) {
        Username owner = pet.owner();
        if (owner == null) {
            return;
        }
        Player ownerPlayer = playerRepository.loadPlayer(owner).orElse(null);
        if (ownerPlayer == null) {
            return;
        }
        saveOrLog(ownerPlayer.withTamedPets(
            ownerPlayer.pets().release(pet.template().id().getValue(), pet.customName())));
    }

    /**
     * Removes a pet from the world and notifies its owner and any bystanders in its room.
     *
     * @param pet      the pet to remove
     * @param ownerMsg the message shown to the pet's owner/summoner
     * @param roomMsg  the message shown to other players in the pet's room
     */
    private void removePet(MobInstance pet, String ownerMsg, String roomMsg) {
        instances.remove(pet.instanceId());
        Username owner = pet.petOwner();
        if (owner != null) {
            playerEventBus.publish(owner,
                new GameActionResult(null, null, List.of(GameMessage.toSource(ownerMsg))));
            broadcastToRoomExcept(pet.roomId(), owner, roomMsg);
        }
    }

    /**
     * Resolves and applies a single pet attack: the pet strikes a hostile mob in its room; a slain
     * foe awards its full kill rewards to the summoner (never split with the pet), while a surviving
     * foe retaliates against the pet, destroying it when its HP is exhausted.
     *
     * @param pet the acting summoned pet
     */
    private void runPetAttack(MobInstance pet) {
        MobInstance foe = findPetTarget(pet);
        if (foe == null) {
            return;
        }
        Username owner = pet.petOwner();
        if (owner == null) {
            return;
        }
        AttackDefinition petAttack = loadAttack(pet.template().attackId());
        if (petAttack == null) {
            return;
        }
        String petName = pet.displayName();
        String foeName = foe.template().name();

        List<GameMessage> messages = new ArrayList<>();
        // The pet's swing rolls to hit and crit through the same seeded resolution as a player's
        // melee swing (issue #595), so a pet can miss its foe outright or crit it for bonus damage.
        HitOutcome petOutcome = resolveMobVsMobHit(petAttack);
        if (petOutcome.hit()) {
            // Scale the resolved hit by the owner's level (see CompanionScaling) so a high-level
            // owner's companion hits harder than the same template obtained by a low-level owner.
            int damage = pet.scaleCompanionDamage(petOutcome.damage());
            int remaining = foe.takeDamage(damage);
            messages.add(GameMessage.toSource(
                petStrikeMessage(petName, foeName, damage, remaining, petOutcome.crit())));

            if (!foe.isAlive()) {
                Player ownerPlayer = playerRepository.loadPlayer(owner).orElse(null);
                if (ownerPlayer != null && !ownerPlayer.isDead()) {
                    Player rewarded = applyMobKillRewards(foe, ownerPlayer, foe.roomId(), messages);
                    saveOrLog(rewarded);
                    playerEventBus.publish(owner, new GameActionResult(rewarded, null, messages));
                } else {
                    // Owner offline/dead: still tear the encounter down cleanly.
                    foe.scheduleRespawn(currentTimeOfDay());
                    endCombatForMob(foe);
                }
                return;
            }
        } else {
            messages.add(GameMessage.toSource(
                "Your " + petName + " lunges at the " + foeName + " but misses."));
        }

        // Surviving foe retaliates against the pet (not its owner), rolling its own hit/crit.
        AttackDefinition foeAttack = loadAttack(foe.template().attackId());
        if (foeAttack != null) {
            HitOutcome foeOutcome = resolveMobVsMobHit(foeAttack);
            if (!foeOutcome.hit()) {
                messages.add(GameMessage.toSource(
                    "The " + foeName + " swings at your " + petName + " but misses."));
            } else {
                int retaliation = foeOutcome.damage();
                int petRemaining = pet.takeDamage(retaliation);
                messages.add(GameMessage.toSource(foeRetaliationMessage(
                    foeName, petName, retaliation, petRemaining, foeOutcome.crit())));
                if (!pet.isAlive()) {
                    instances.remove(pet.instanceId());
                    if (pet.isTamed()) {
                        releasePersistedPet(pet);
                        messages.add(GameMessage.toSource("Your " + petName + " has been slain!"));
                        broadcastToRoomExcept(pet.roomId(), owner,
                            "The " + petName + " collapses, slain.");
                    } else {
                        messages.add(GameMessage.toSource("Your " + petName + " has been destroyed!"));
                        broadcastToRoomExcept(pet.roomId(), owner,
                            "The " + petName + " collapses into dust.");
                    }
                }
            }
        }
        playerEventBus.publish(owner, new GameActionResult(null, null, messages));
    }

    /**
     * Builds the line shown to a pet's owner when the pet lands a swing on a hostile mob, mirroring
     * the player melee strike phrasing. A critical hit is prefixed with a "critical hit!" notice.
     *
     * @param petName   the pet's display name
     * @param foeName   the struck foe's display name
     * @param damage    the damage the pet dealt
     * @param remaining the foe's HP after the strike
     * @param crit      whether the pet scored a critical hit
     * @return the message text to show the pet's owner
     */
    private static String petStrikeMessage(
        String petName, String foeName, int damage, int remaining, boolean crit) {
        String critPrefix = crit ? "A critical hit! " : "";
        return critPrefix + "Your " + petName + " strikes the " + foeName + " for "
            + damage + " damage. (" + remaining + " HP remaining)";
    }

    /**
     * Builds the line shown to a pet's owner when a surviving foe retaliates against the pet,
     * mirroring the strike phrasing. A critical hit is prefixed with a "critical hit!" notice.
     *
     * @param foeName   the retaliating foe's display name
     * @param petName   the pet's display name
     * @param damage    the damage the foe dealt to the pet
     * @param remaining the pet's HP after the retaliation
     * @param crit      whether the foe scored a critical hit
     * @return the message text to show the pet's owner
     */
    private static String foeRetaliationMessage(
        String foeName, String petName, int damage, int remaining, boolean crit) {
        String critPrefix = crit ? "A critical hit! " : "";
        return critPrefix + "The " + foeName + " retaliates against your " + petName + " for "
            + damage + " damage. (" + remaining + " HP remaining)";
    }

    /**
     * Selects the mob a pet should attack this tick: a live, non-pet, attackable
     * ({@code "npc"}-untagged) mob in the pet's room, preferring one already engaged with the pet's
     * owner so the pet backs its master up.
     *
     * @param pet the acting pet
     * @return the chosen foe, or {@code null} when the pet's room holds no hostile mob
     */
    private MobInstance findPetTarget(MobInstance pet) {
        Username owner = pet.petOwner();
        List<MobInstance> foes = instances.values().stream()
            .filter(m -> m.isAlive()
                && !m.isPet()
                && m.roomId().equals(pet.roomId())
                && !m.template().hasTag("npc"))
            .toList();
        if (owner != null) {
            for (MobInstance foe : foes) {
                if (foe.engagedPlayers().contains(owner)) {
                    return foe;
                }
            }
        }
        return foes.isEmpty() ? null : foes.get(0);
    }

    private void broadcastToRoomExcept(RoomId roomId, Username excluded, String message) {
        for (Username occupant : roomService.getPlayersInRoom(roomId)) {
            if (!occupant.equals(excluded)) {
                playerEventBus.publish(occupant,
                    new GameActionResult(null, null, List.of(GameMessage.toSource(message))));
            }
        }
    }

    // ── Mob AI ────────────────────────────────────────────────────────

    /**
     * Gives a badly wounded mob a chance to break off combat and flee to a random adjacent room
     * instead of attacking this AI decision, mirroring the player {@code FLEE} mechanic
     * ({@link io.taanielo.jmud.core.action.GameActionService#flee}): the exit is chosen through the
     * seeded world-random port and a mob in a dead-end room (no exits) cannot flee and fights on.
     *
     * <p>The mob only flees when it is engaged in combat and its HP is at or below
     * {@link #mobFleeHpPercent} percent of its maximum, and only on a successful
     * {@link #mobFleeChancePercent} roll. Pets, world bosses, and {@code fearless} mobs never flee
     * (see {@link #canFlee(MobInstance)}). Fleeing disengages every player currently fighting the mob
     * (the same teardown as {@link #endCombatForMob}) and moves it to the chosen room at its current
     * (low) HP — it is not a defeat, so no loot, XP, gold, or respawn timer is produced and the mob
     * may wander and be re-engaged normally afterward. Departure- and arrival-room players are
     * notified in the tone of the wander-phase messages. Runs on the tick thread (AGENTS.md §5).
     *
     * @param mob the acting mob
     * @return {@code true} when the mob fled (the caller must skip its attack), {@code false} otherwise
     */
    private boolean tryMobFlee(MobInstance mob) {
        if (!canFlee(mob)) {
            return false;
        }
        // Only a mob actually in combat "breaks off" — this also avoids reacting to a stealthed
        // player the mob has not engaged (stealth-avoidance first-engagement rules).
        if (mob.engagedPlayers().isEmpty()) {
            return false;
        }
        int maxHp = mob.template().maxHp();
        int threshold = (int) Math.floor(maxHp * (mobFleeHpPercent / 100.0));
        if (mob.currentHp() > threshold) {
            return false;
        }
        if (random.roll(1, 100) > mobFleeChancePercent) {
            return false;
        }
        RoomId fromRoomId = mob.roomId();
        Map<Direction, RoomId> exits = roomService.getExits(fromRoomId);
        if (exits.isEmpty()) {
            return false;
        }
        List<Map.Entry<Direction, RoomId>> exitList = List.copyOf(exits.entrySet());
        Map.Entry<Direction, RoomId> chosen = exitList.get(random.roll(0, exitList.size() - 1));
        Direction dir = chosen.getKey();
        RoomId toRoomId = chosen.getValue();

        // Disengage every player currently fighting this mob before it leaves, so no one keeps
        // auto-attacking a mob that is no longer present (mirrors the player FLEE teardown).
        List<Username> formerlyEngaged = List.copyOf(mob.engagedPlayers());
        endCombatForMob(mob);
        for (Username engaged : formerlyEngaged) {
            mob.disengage(engaged);
        }

        String mobName = mob.template().name();
        String departMsg = "The " + mobName + ", badly wounded, turns and flees to the " + dir.label() + "!";
        for (Username occupant : roomService.getPlayersInRoom(fromRoomId)) {
            playerEventBus.publish(occupant,
                new GameActionResult(null, null, List.of(GameMessage.toSource(departMsg))));
        }

        mob.moveTo(toRoomId);

        String arriveMsg = "A " + mobName
            + ", badly wounded, flees in from the " + dir.opposite().label() + ".";
        for (Username occupant : roomService.getPlayersInRoom(toRoomId)) {
            playerEventBus.publish(occupant,
                new GameActionResult(null, null, List.of(GameMessage.toSource(arriveMsg))));
        }

        log.debug("Mob {} fled from {} to {} ({}) at {} HP",
            mobName, fromRoomId, toRoomId, dir.label(), mob.currentHp());
        return true;
    }

    /**
     * Returns whether the given mob is ever allowed to flee. Pets/summons never flee (they fight
     * alongside their master), world bosses fight to the death, and mobs tagged {@code fearless}
     * (mindless or undead creatures with no self-preservation instinct) never break off.
     *
     * @param mob the mob to test
     * @return {@code true} when this mob may attempt to flee when badly wounded
     */
    private boolean canFlee(MobInstance mob) {
        if (mob.isPet()) {
            return false;
        }
        if (mob.template().worldBoss()) {
            return false;
        }
        return !mob.template().hasTag(FEARLESS_TAG);
    }

    /**
     * Chooses which engaged player a mob attacks this AI decision. When an active Warrior TAUNT is in
     * force ({@link MobInstance#activeTaunter()}) and the taunter is still a live candidate — present
     * in the room and engaged — the mob is forced onto the taunter and one taunt decision is consumed.
     * Otherwise, or once the taunt has expired or its taunter left combat/the room, the mob falls back
     * to the normal uniform-random pick among {@code candidates}. Runs on the tick thread (AGENTS.md
     * §5).
     *
     * @param mob        the acting mob
     * @param candidates the engaged players present in the mob's room (never empty)
     * @return the username the mob will attack this decision
     */
    private Username selectAiTarget(MobInstance mob, List<Username> candidates) {
        Username taunter = mob.activeTaunter();
        if (taunter != null && candidates.contains(taunter)) {
            Player tauntingPlayer = playerRepository.loadPlayer(taunter).orElse(null);
            if (tauntingPlayer != null && !tauntingPlayer.isDead()) {
                mob.consumeTauntTick();
                return taunter;
            }
        }
        return candidates.get(random.roll(0, candidates.size() - 1));
    }

    /**
     * Builds the first-engagement flavour line shown to a player as a mob joins the fight against
     * them. A {@code pack} mob reinforcing an existing fight ({@code packJoin}) gets a distinct
     * "snarls and lunges to its packmate's defense" line so the player can tell a den has woken,
     * rather than the generic solo-aggro lunge.
     *
     * @param mob      the mob entering combat
     * @param packJoin whether this engagement is a pack mob reinforcing a room-mate's fight
     * @return the self-facing first-engagement message
     */
    private String firstEngagementMessage(MobInstance mob, boolean packJoin) {
        if (packJoin) {
            return "Another " + mob.template().name()
                + " snarls and lunges to its packmate's defense!";
        }
        return "The " + mob.template().name() + " lunges at you!";
    }

    /**
     * Reports whether {@code mob} is a mob that would <em>not</em> initiate combat on its own but
     * carries the {@code pack} tag, so its first engagement must be restricted to reinforcing an
     * existing fight (see {@link #playersEngagedWithRoommates}). A mob that is inherently aggressive
     * or belongs to a faction already initiates independently, so the pack restriction does not
     * apply to it even when it also carries the tag.
     *
     * @param mob the mob under evaluation
     * @return {@code true} when the mob may only act as a pack reinforcement this engagement
     */
    private boolean isPackOnlyInitiator(MobInstance mob) {
        boolean independentInitiator = mob.template().aggressive()
            || (reputationService != null && mob.template().factionId() != null);
        return !independentInitiator && mob.template().hasTag(PACK_TAG);
    }

    /**
     * Returns the players currently in {@code mob}'s room who are already engaged in combat with a
     * different alive, non-pet mob in that same room. These are the only players a {@code pack} mob
     * may join against: a pack mob reinforces a fight already started (by the player attacking any
     * mob, or by an aggressive/faction room-mate), it never starts a fresh one. Runs on the tick
     * thread over the live instance map (AGENTS.md §5).
     *
     * @param mob the pack mob looking for a fight to join
     * @return the in-room players engaged with a room-mate mob, or an empty list when none
     */
    private List<Username> playersEngagedWithRoommates(MobInstance mob) {
        RoomId roomId = mob.roomId();
        Set<Username> engagedByRoommates = new HashSet<>();
        for (MobInstance other : instances.values()) {
            if (other.instanceId().equals(mob.instanceId()) || other.isPet() || !other.isAlive()) {
                continue;
            }
            if (!other.roomId().equals(roomId)) {
                continue;
            }
            engagedByRoommates.addAll(other.engagedPlayers());
        }
        if (engagedByRoommates.isEmpty()) {
            return List.of();
        }
        return roomService.getPlayersInRoom(roomId).stream()
            .filter(engagedByRoommates::contains)
            .toList();
    }

    private void runMobAi(MobInstance mob) {
        List<Username> candidates;
        Set<Username> engaged = mob.engagedPlayers();
        boolean packJoin = false;
        if (!engaged.isEmpty()) {
            List<Username> inRoom = roomService.getPlayersInRoom(mob.roomId());
            candidates = engaged.stream().filter(inRoom::contains).toList();
        } else if (isPackOnlyInitiator(mob)) {
            // A pack mob with no fight of its own only reinforces an existing one: its first target
            // must already be engaged with a different mob in the same room, never a fresh victim.
            // Restricting the candidate set here is what keeps "pack" from behaving like "aggressive".
            candidates = playersEngagedWithRoommates(mob);
            packJoin = true;
        } else {
            candidates = roomService.getPlayersInRoom(mob.roomId());
        }
        if (candidates.isEmpty()) {
            return;
        }
        // Before committing to an attack (and before TAUNT target selection, so an active taunter
        // cannot pin a cornered mob), give a badly wounded mob a chance to break off and flee.
        if (tryMobFlee(mob)) {
            return;
        }
        Username targetUsername = selectAiTarget(mob, candidates);
        Player target = playerRepository.loadPlayer(targetUsername).orElse(null);
        if (target == null || target.isDead()) {
            return;
        }
        boolean firstEngagement = !mob.engagedPlayers().contains(targetUsername);
        // A player hidden in stealth (rogue SNEAK/HIDE) avoids fresh aggro: an aggressive mob will
        // not engage them for the first time. Mobs already engaged with a player keep attacking —
        // stealth only prevents the initial engagement.
        if (firstEngagement && target.isStealthActive()) {
            return;
        }
        // Faction membership governs first aggression: a faction mob only picks a fight with a player
        // its faction is hostile toward (standing below the faction's threshold). Once engaged it
        // keeps fighting regardless. Mobs with no faction (or when reputation is disabled) fall back
        // to their inherent aggressive flag, filtered by the tick() gate above.
        if (firstEngagement && reputationService != null && mob.template().factionId() != null
            && !reputationService.isHostile(target.reputation(), mob.template().factionId())) {
            return;
        }
        boolean useSpecial = mob.template().specialAttackId() != null
            && !mob.specialAbilityUsed()
            && firstEngagement;
        AttackDefinition attack = loadAttack(
            useSpecial ? mob.template().specialAttackId() : mob.template().attackId());
        if (attack == null) {
            return;
        }
        if (useSpecial) {
            mob.markSpecialAbilityUsed();
        }

        mob.engage(targetUsername);
        playerCombatTargets.put(targetUsername, mob.instanceId());

        MobAttackOutcome outcome = resolveMobAttack(attack, target);
        if (!outcome.hit()) {
            // A miss deals no damage, wears no gear, and applies no on-hit effect. The player may
            // still be drawn into the fight — thrown from a mount and shown the first-engagement
            // lunge — mirroring the non-damage side effects of a landing hit.
            List<GameMessage> messages = new ArrayList<>();
            Player missedPlayer = target;
            boolean playerChanged = false;
            if (missedPlayer.isMounted()) {
                String mountName = missedPlayer.mount().mountName();
                missedPlayer = missedPlayer.withMount(PlayerMount.dismounted());
                playerChanged = true;
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + "'s assault throws you from " + mountName + "!"));
            }
            if (firstEngagement && !useSpecial) {
                messages.add(GameMessage.toSource(firstEngagementMessage(mob, packJoin)));
            }
            messages.add(GameMessage.toSource(
                mobAttackMessage(mob, attack, MessagePhase.ATTACK_MISS, useSpecial, 0,
                    missedPlayer.getVitals().getMaxHp())));
            // Only persist/publish an updated source when the miss actually changed the player (a
            // mount throw); an ordinary miss leaves the player untouched, so no save is needed.
            Player publishedSource = playerChanged ? missedPlayer : null;
            if (playerChanged) {
                saveOrLog(missedPlayer);
            }
            playerEventBus.publish(targetUsername,
                new GameActionResult(publishedSource, null, messages));
            return;
        }

        if (outcome.parried()) {
            // The player parried the mob's melee swing: they take zero damage and riposte the mob with
            // their mainhand weapon. Being drawn into combat still throws a rider from their mount and
            // shows the first-engagement lunge, mirroring a landing hit's non-damage side effects.
            List<GameMessage> messages = new ArrayList<>();
            Player parriedPlayer = target;
            boolean playerChanged = false;
            if (parriedPlayer.isMounted()) {
                String mountName = parriedPlayer.mount().mountName();
                parriedPlayer = parriedPlayer.withMount(PlayerMount.dismounted());
                playerChanged = true;
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + "'s assault throws you from " + mountName + "!"));
            }
            if (firstEngagement && !useSpecial) {
                messages.add(GameMessage.toSource(firstEngagementMessage(mob, packJoin)));
            }
            int riposteDamage = outcome.riposteDamage();
            int remaining = mob.takeDamage(riposteDamage);
            messages.add(GameMessage.toSource(playerParryMessage(mob, riposteDamage, remaining)));
            if (!mob.isAlive()) {
                // A riposte can slay the mob outright — award the kill in memory so any mount change is
                // preserved through the reward path, exactly as a normal player kill would.
                parriedPlayer = applyMobKillRewards(mob, parriedPlayer, mob.roomId(), messages);
                playerChanged = true;
            }
            if (playerChanged) {
                saveOrLog(parriedPlayer);
            }
            Player publishedSource = playerChanged ? parriedPlayer : null;
            playerEventBus.publish(targetUsername,
                new GameActionResult(publishedSource, null, messages));
            return;
        }

        int damage = outcome.damage();
        Player damagedPlayer = target.withVitals(target.getVitals().damage(damage));

        if (damagedPlayer.getVitals().hp() <= 0) {
            handleMobKill(mob, damagedPlayer, candidates);
        } else {
            List<GameMessage> messages = new ArrayList<>();
            // Being drawn into combat throws a rider off their mount, mirroring how attacking does.
            if (damagedPlayer.isMounted()) {
                String mountName = damagedPlayer.mount().mountName();
                damagedPlayer = damagedPlayer.withMount(PlayerMount.dismounted());
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + "'s assault throws you from " + mountName + "!"));
            }
            if (firstEngagement && !useSpecial) {
                messages.add(GameMessage.toSource(firstEngagementMessage(mob, packJoin)));
            }
            MessagePhase phase = outcome.blocked() ? MessagePhase.ATTACK_BLOCK : MessagePhase.ATTACK_HIT;
            messages.add(GameMessage.toSource(
                mobAttackMessage(mob, attack, phase, useSpecial, damage,
                    damagedPlayer.getVitals().getMaxHp())));
            applyOnHitEffect(attack, damagedPlayer, targetUsername, candidates, messages);
            damagedPlayer = degradeEquippedGear(damagedPlayer, messages);
            saveOrLog(damagedPlayer);
            playerEventBus.publish(targetUsername,
                new GameActionResult(damagedPlayer, null, messages));
        }
    }

    /**
     * Builds the self-facing message shown to a player targeted by a mob's attack for the given
     * outcome {@code phase} (hit, miss, or shield-block). When the attack carries a configured
     * {@link MessageSpec} for that phase on the {@link MessageChannel#SELF} channel (e.g. a wolf's
     * authored "snaps at you but misses" line, or a boss's special-ability flavour text), that
     * message is rendered and used instead of the generic line, so authored miss/crit/block flavour
     * finally surfaces in the PvE combat players actually do. When the attack defines no message for
     * the phase, a sensible generic line is produced per phase.
     *
     * @param mob         the attacking mob
     * @param attack      the attack definition being resolved
     * @param phase       the outcome phase whose SELF-channel message to render (hit/miss/block)
     * @param useSpecial  whether this was the mob's special ability rather than its basic attack
     * @param damage      the damage dealt, substituted into the {@code {damage}} placeholder (0 on a miss)
     * @param targetMaxHp the victim's maximum HP, used to resolve the {@code {verb}} damage tier
     * @return the rendered message text to show the target player
     */
    private String mobAttackMessage(
        MobInstance mob, AttackDefinition attack, MessagePhase phase,
        boolean useSpecial, int damage, int targetMaxHp) {
        for (MessageSpec spec : attack.messages()) {
            if (spec.phase() == phase && spec.channel() == MessageChannel.SELF) {
                DamageVerb verb = damage > 0 && damageVerbTable != null
                    ? damageVerbTable.verbFor(damage, targetMaxHp)
                    : null;
                MessageContext context = new MessageContext(
                    null, null, mob.template().name(), null, null, null, attack.name(), damage,
                    verb == null ? null : verb.thirdPerson(),
                    verb == null ? null : verb.secondPerson());
                String rendered = messageRenderer.render(spec, context);
                if (rendered != null && !rendered.isBlank()) {
                    return rendered;
                }
            }
        }
        String mobName = mob.template().name();
        return switch (phase) {
            case ATTACK_MISS -> "The " + mobName + " misses you.";
            case ATTACK_BLOCK -> "You block the " + mobName + "'s attack with your shield.";
            default -> useSpecial
                ? "The " + mobName + " unleashes " + attack.name() + " on you for " + damage + " damage!"
                : "The " + mobName + " hits you for " + damage + " damage!";
        };
    }

    /**
     * Applies a mob attack's on-hit status effect (see {@link AttackDefinition#effectOnHit()})
     * to the player it hit, respecting the configured application chance.
     *
     * @param attack           the attack that just landed a hit
     * @param target           the player hit by the attack; mutated in place with the new effect
     * @param targetUsername   the target's username, used to route room messages
     * @param roomOccupants     usernames of players in the mob's room, used to deliver room messages
     * @param targetMessages    mutable list of self-facing messages to append to
     */
    private void applyOnHitEffect(
        AttackDefinition attack,
        Player target,
        Username targetUsername,
        List<Username> roomOccupants,
        List<GameMessage> targetMessages
    ) {
        AttackEffectApplication effectApplication = attack.effectOnHit();
        if (effectEngine == null || effectApplication == null) {
            return;
        }
        int roll = random.roll(1, 100);
        if (roll > effectApplication.chancePercent()) {
            return;
        }
        List<String> roomMessages = new ArrayList<>();
        try {
            effectEngine.apply(target, effectApplication.effectId(), new EffectMessageSink() {
                @Override
                public void sendToTarget(String message) {
                    if (message != null && !message.isBlank()) {
                        targetMessages.add(GameMessage.toSource(message));
                    }
                }

                @Override
                public void sendToRoom(String message) {
                    if (message != null && !message.isBlank()) {
                        roomMessages.add(message);
                    }
                }
            });
        } catch (EffectRepositoryException e) {
            log.warn("Failed to apply on-hit effect {}: {}", effectApplication.effectId(), e.getMessage());
            return;
        }
        for (String roomMessage : roomMessages) {
            for (Username occupant : roomOccupants) {
                if (!occupant.equals(targetUsername)) {
                    playerEventBus.publish(occupant,
                        new GameActionResult(null, null, List.of(GameMessage.toSource(roomMessage))));
                }
            }
        }
    }

    private void handleMobKill(MobInstance mob, Player damagedPlayer, List<Username> roomOccupants) {
        int droppedGold = damagedPlayer.getGold();
        Player deadPlayer = damagedPlayer.withGold(0).die();
        roomService.spawnCorpse(deadPlayer.getUsername(), mob.roomId(), droppedGold);
        roomService.clearPlayerLocation(deadPlayer.getUsername());
        saveOrLog(deadPlayer);
        mob.disengage(deadPlayer.getUsername());
        playerCombatTargets.remove(deadPlayer.getUsername());

        List<GameMessage> deathMessages = new ArrayList<>();
        deathMessages.add(GameMessage.toSource(
            "The " + mob.template().name() + " has slain you!"));
        deathMessages.add(GameMessage.toSource(
            "You will awaken in the " + io.taanielo.jmud.core.player.DeathSettings.RESPAWN_ROOM_ID + "."));
        playerEventBus.publish(deadPlayer.getUsername(),
            new GameActionResult(deadPlayer, null, deathMessages));

        String roomMsg = deadPlayer.getUsername().getValue()
            + " has been slain by the " + mob.template().name() + "!";
        for (Username occupant : roomOccupants) {
            if (!occupant.equals(deadPlayer.getUsername())) {
                playerEventBus.publish(occupant,
                    new GameActionResult(null, null, List.of(GameMessage.toSource(roomMsg))));
            }
        }
    }

    private void runPlayerCombat() {
        for (var entry : playerCombatTargets.entrySet()) {
            Username username = entry.getKey();
            UUID mobId = entry.getValue();

            MobInstance mob = instances.get(mobId);
            if (mob == null || !mob.isAlive()) {
                playerCombatTargets.remove(username);
                if (mob != null) mob.disengage(username);
                continue;
            }

            Player player = playerRepository.loadPlayer(username).orElse(null);
            if (player == null || player.isDead()) {
                playerCombatTargets.remove(username);
                mob.disengage(username);
                continue;
            }

            RoomId playerRoom = roomService.findPlayerLocation(username).orElse(null);
            if (playerRoom == null || !playerRoom.equals(mob.roomId())) {
                playerCombatTargets.remove(username);
                mob.disengage(username);
                continue;
            }

            AttackId attackId = resolveAttackId(player);
            AttackDefinition attack = loadAttack(attackId);
            if (attack == null) {
                continue;
            }
            List<GameMessage> messages = new ArrayList<>();
            HitOutcome outcome = resolvePlayerHit(player, attack);
            if (!outcome.hit()) {
                messages.add(GameMessage.toSource(playerMissMessage(mob)));
                playerEventBus.publish(username, new GameActionResult(null, null, messages));
                continue;
            }
            // A defensively-trained mob may parry this auto-attack swing: the player deals zero damage
            // and the mob ripostes with its own attack, symmetric to the player-side parry on the mob's
            // swing (resolveMobAttack). Melee-only; a non-parrying mob never rolls (resolveMobParry).
            MobParryOutcome parry = resolveMobParry(mob);
            if (parry.parried()) {
                int playerMaxHp = player.getVitals().getMaxHp();
                messages.add(GameMessage.toSource(
                    mobParryMessage(mob, parry.riposteAttack(), parry.riposteDamage(), playerMaxHp)));
                broadcastMobParry(mob, username, parry.riposteAttack(),
                    parry.riposteDamage(), playerRoom);
                Player damaged = player.withVitals(player.getVitals().damage(parry.riposteDamage()));
                if (damaged.getVitals().hp() <= 0) {
                    playerEventBus.publish(username, new GameActionResult(null, null, messages));
                    handleMobKill(mob, damaged, roomService.getPlayersInRoom(playerRoom));
                    continue;
                }
                saveOrLog(damaged);
                playerEventBus.publish(username, new GameActionResult(damaged, null, messages));
                continue;
            }
            int damage = outcome.damage();
            int remaining = mob.takeDamage(damage);
            messages.add(GameMessage.toSource(playerStrikeMessage(mob, damage, remaining, outcome.crit())));

            if (!mob.isAlive()) {
                // Shared post-kill rewards (loot distribution, party XP, gold, quest credit, etc.)
                // live in applyMobKillRewards so this tick-driven auto-attack path and the direct
                // ATTACK/SHOOT/AoE paths behave identically.
                awardMobKill(mob, player, playerRoom, messages);
            }
            playerEventBus.publish(username, new GameActionResult(null, null, messages));
        }
    }

    private void endCombatForMob(MobInstance mob) {
        for (Username engaged : mob.engagedPlayers()) {
            playerCombatTargets.remove(engaged);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Rolls loot for a dead mob and returns the list of items that actually dropped, without placing
     * them anywhere. Where each item ends up (the room floor, or a party member's inventory under
     * {@link LootMode#ROUND_ROBIN}) is decided by {@link #distributeLoot}.
     *
     * @param mob the mob that just died
     * @return the rolled items (never null, may be empty)
     */
    private List<Item> rollLoot(MobInstance mob) {
        List<Item> dropped = new ArrayList<>();
        for (LootEntry entry : mob.template().lootTable()) {
            if (random.nextDouble() <= entry.dropChance()) {
                try {
                    itemRepository.findById(entry.itemId()).ifPresent(dropped::add);
                } catch (RepositoryException e) {
                    log.warn("Failed to roll loot item {}: {}", entry.itemId(), e.getMessage());
                }
            }
        }
        return dropped;
    }

    /**
     * Broadcasts the server-wide "it's up!" announcement for a freshly spawned or respawned
     * world-boss instance (see {@link MobTemplate#worldBoss()}). A no-op for ordinary mobs and when
     * no {@link WorldBossAnnouncer} is configured. Runs on the tick thread (AGENTS.md §5).
     *
     * @param mob the mob instance that has just entered the world
     */
    private void announceWorldBossSpawn(MobInstance mob) {
        if (worldBossAnnouncer != null && mob.template().worldBoss()) {
            worldBossAnnouncer.announceSpawn(mob.template().name(), mob.roomId());
        }
    }

    /**
     * Applies the world-boss-only consequences of a kill: a guaranteed rare-or-higher loot drop on
     * top of the normal loot rolls, and the server-wide death announcement naming the killer and
     * their guild/party. A no-op for ordinary mobs. Runs on the tick thread (AGENTS.md §5).
     *
     * @param mob         the slain mob
     * @param killer      the player who landed the killing blow
     * @param normalDrops the items already dropped by the normal loot roll this kill
     * @param messages    the mutable attacker-facing message list to append the bonus-drop notice to
     */
    private void handleWorldBossKill(
        MobInstance mob, Username killer, List<Item> normalDrops, List<GameMessage> messages) {
        if (!mob.template().worldBoss()) {
            return;
        }
        boolean alreadyRare = normalDrops.stream().anyMatch(item -> isRareOrHigher(item.getRarity()));
        if (!alreadyRare) {
            Item bonus = dropGuaranteedRareLoot(mob);
            if (bonus != null) {
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + " yields " + bonus.getName()
                        + ", still glittering with power!"));
            }
        }
        if (worldBossAnnouncer != null) {
            worldBossAnnouncer.announceDeath(mob.template().name(), killer);
        }
    }

    /**
     * Force-drops one rare-or-higher item from the world boss's loot table into its room, bypassing
     * the per-entry drop-chance roll, so a world-boss kill always yields at least one rare-or-higher
     * reward (reusing the existing item rarity/affix system). When several qualify, one is chosen at
     * random; when none do (a misconfigured boss), a warning is logged and nothing drops.
     *
     * @param mob the slain world boss
     * @return the guaranteed item placed in the room, or {@code null} when the loot table defines no
     *         rare-or-higher item
     */
    private Item dropGuaranteedRareLoot(MobInstance mob) {
        List<Item> candidates = new ArrayList<>();
        for (LootEntry entry : mob.template().lootTable()) {
            try {
                itemRepository.findById(entry.itemId())
                    .filter(item -> isRareOrHigher(item.getRarity()))
                    .ifPresent(candidates::add);
            } catch (RepositoryException e) {
                log.warn("Failed to resolve world-boss loot item {}: {}", entry.itemId(), e.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            log.warn("World boss {} has no rare-or-higher loot entry; no guaranteed drop",
                mob.template().id().getValue());
            return null;
        }
        Item chosen = candidates.get(random.roll(0, candidates.size() - 1));
        roomService.addItem(mob.roomId(), chosen);
        return chosen;
    }

    private static boolean isRareOrHigher(Rarity rarity) {
        return rarity.isAtLeast(Rarity.RARE);
    }

    /**
     * Hands the given player off to the write-behind persistence queue rather than
     * saving synchronously, so a slow disk write during mob AI processing (XP/damage
     * application) never stalls the tick thread (AGENTS.md §5). Failures (including
     * retries) are logged and audited by the queue itself.
     *
     * @param player the player to save
     */
    private void saveOrLog(Player player) {
        persistenceQueue.enqueueSave(player);
    }

    private AttackDefinition loadAttack(AttackId attackId) {
        try {
            return attackRepository.findById(attackId).orElse(null);
        } catch (RepositoryException e) {
            log.warn("Failed to load attack {}: {}", attackId, e.getMessage());
            return null;
        }
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

    /**
     * Wears down the player's equipped gear after a mob hit, appending any break messages to the
     * player's self-facing message list. A no-op when no durability service is configured.
     *
     * @param player   the player who was just hit
     * @param messages the mutable self-facing message list to append break notices to
     * @return the player with worn-down gear applied (or unchanged when durability is disabled)
     */
    private Player degradeEquippedGear(Player player, List<GameMessage> messages) {
        if (itemDurabilityService == null) {
            return player;
        }
        ItemDurabilityService.DegradeResult result = itemDurabilityService.degradeEquipped(player);
        for (String message : result.messages()) {
            messages.add(GameMessage.toSource(message));
        }
        return result.player();
    }

    private MobInstance findMobByName(List<MobInstance> mobs, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (MobInstance mob : mobs) {
            // A tamed companion may be targeted by its custom name (see NAME) or its template name.
            if (nameMatches(mob.displayName(), normalized)
                || nameMatches(mob.template().name(), normalized)) {
                return mob;
            }
        }
        return null;
    }

    /**
     * Tests whether the already-lowercased {@code input} targets the given mob {@code candidate} name.
     * A match is an exact or prefix match on the full name, or on the name with a leading article
     * ({@code the}/{@code a}/{@code an}) stripped, so a mob named "the Rimewrought Stalker" is
     * reachable by typing "rimewrought" as players naturally expect.
     *
     * @param candidate the mob display or template name (any case)
     * @param input     the trimmed, lowercased target text typed by the player
     * @return true when {@code input} matches {@code candidate} directly or article-stripped
     */
    private boolean nameMatches(String candidate, String input) {
        String name = candidate.toLowerCase(Locale.ROOT);
        if (name.equals(input) || name.startsWith(input)) {
            return true;
        }
        String withoutArticle = stripLeadingArticle(name);
        return !withoutArticle.equals(name)
            && (withoutArticle.equals(input) || withoutArticle.startsWith(input));
    }

    private String stripLeadingArticle(String name) {
        for (String article : List.of("the ", "an ", "a ")) {
            if (name.startsWith(article)) {
                return name.substring(article.length());
            }
        }
        return name;
    }

    private int rollDamage(AttackDefinition attack) {
        int range = attack.maxDamage() - attack.minDamage();
        int base = range > 0
            ? attack.minDamage() + random.roll(0, range)
            : attack.minDamage();
        return Math.max(1, base + attack.damageBonus());
    }

    /**
     * Reduces a mob's rolled damage by the defending player's equipped elemental resistance for the
     * attack's {@link DamageType}. Physical (and untyped) attacks are never reduced. The summed
     * resistance percentage is capped at {@link CombatSettings#maxResistancePercent()} so a typed
     * blow always lands for at least 1 damage — resistance never grants full immunity.
     *
     * @param rawDamage  the pre-mitigation damage rolled for the attack (at least 1)
     * @param defender   the player being struck
     * @param damageType the elemental type of the incoming attack
     * @return the mitigated damage, never below 1
     */
    private int mitigateForDefender(int rawDamage, Player defender, DamageType damageType) {
        if (!damageType.isResistible()) {
            return rawDamage;
        }
        int resistPercent = Math.max(0, Math.min(
            resistanceResolver.totalResistance(defender, damageType),
            CombatSettings.maxResistancePercent()));
        if (resistPercent <= 0) {
            return rawDamage;
        }
        return Math.max(1, (int) Math.round(rawDamage * ((100 - resistPercent) / 100.0)));
    }

    /**
     * Rolls damage for an attack made by the given player attacker, adding their strength-based
     * physical bonus damage on top of the weapon roll so core attributes carry into player-vs-mob
     * combat. Final damage is never below 1.
     *
     * @param attack   the attack definition being rolled
     * @param attacker the player making the attack
     * @return the damage dealt, at least 1
     */
    private int rollDamage(AttackDefinition attack, Player attacker) {
        int range = attack.maxDamage() - attack.minDamage();
        int base = range > 0
            ? attack.minDamage() + random.roll(0, range)
            : attack.minDamage();
        int strengthBonus = attributeBonusResolver.meleeDamageBonus(attacker);
        return Math.max(1, base + attack.damageBonus() + strengthBonus);
    }

    /**
     * Disengages a player from combat, clearing their combat target and removing
     * them from any mob's engaged set. Called when a player successfully flees.
     *
     * @param username the player fleeing from combat
     */
    public void fleeCombat(Username username) {
        Objects.requireNonNull(username, "Username is required");
        UUID mobId = playerCombatTargets.remove(username);
        if (mobId != null) {
            MobInstance mob = instances.get(mobId);
            if (mob != null) {
                mob.disengage(username);
            }
        }
        // Also disengage from any other mobs that may have engaged this player.
        for (MobInstance mob : instances.values()) {
            mob.disengage(username);
        }
    }

    /**
     * Returns whether the given player is currently engaged in combat.
     *
     * @param username the player to check
     * @return {@code true} if the player has an active combat target
     */
    public boolean isInCombat(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return playerCombatTargets.containsKey(username);
    }

    Collection<MobInstance> allInstances() {
        return instances.values();
    }
}
