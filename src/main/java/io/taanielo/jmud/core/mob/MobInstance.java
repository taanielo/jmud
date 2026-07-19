package io.taanielo.jmud.core.mob;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.effects.ControlType;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * A live mob instance in the world.
 *
 * <p>HP is tracked via {@link AtomicInteger} so that player-thread attacks
 * (command queue) and tick-thread AI reads are both safe. All other state
 * mutations are confined to the tick thread.
 *
 * <p>{@code currentRoomId} tracks the mob's live position (it starts at
 * {@code template.spawnRoomId()} and is updated when the mob wanders or
 * reset to the spawn room on respawn).
 */
public class MobInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final MobTemplate template;
    private final AtomicInteger hp;
    /**
     * The effective max HP this instance was spawned with. For ordinary world mobs this equals
     * {@link MobTemplate#maxHp()}; for {@link #isPet() companions} it is the template max HP scaled up
     * by the owner's level (see {@link CompanionScaling}). Used by {@link #respawn()} so a mob returns
     * to its correct full HP rather than the raw template value.
     */
    private final int maxHp;
    /**
     * Owner-level scaling applied to this instance, or {@code null} for ordinary world mobs. Non-null
     * only for {@link #isPet() companions}; its damage multiplier is applied to a pet's resolved hit
     * damage via {@link #scaleCompanionDamage(int)} so a high-level owner's pet hits harder.
     */
    @Nullable
    private final CompanionScaling companionScaling;
    private final AtomicInteger respawnTicksRemaining = new AtomicInteger(0);
    private final Set<Username> engagedPlayers = ConcurrentHashMap.newKeySet();
    /**
     * Whether this mob has already used its {@link MobTemplate#specialAttackId()} in the
     * current combat encounter. Reset whenever the encounter ends (see {@link #disengage(Username)}
     * and {@link #respawn()}).
     */
    private final AtomicBoolean specialAbilityUsed = new AtomicBoolean(false);
    /**
     * The player currently forcing this mob's aggro via the Warrior TAUNT skill, or {@code null}
     * when no taunt is active. Paired with {@link #tauntTicksRemaining}: the taunt is only honoured
     * while the counter is positive. Mutated exclusively through {@link MobRegistry} on the tick
     * thread; cleared when the taunter disengages ({@link #disengage(Username)}) or on
     * {@link #respawn()}, mirroring {@link #specialAbilityUsed}.
     */
    private final AtomicReference<Username> taunter = new AtomicReference<>();
    /**
     * Remaining number of AI decisions for which {@link #taunter} holds this mob's aggro. Decremented
     * on the tick thread via {@link #consumeTauntTick()}; when it reaches zero the taunt expires and
     * the mob resumes normal random targeting.
     */
    private final AtomicInteger tauntTicksRemaining = new AtomicInteger(0);
    /**
     * Remaining AI ticks before a telegraphed special attack lands, or {@code 0} when no telegraph is
     * pending (see {@link io.taanielo.jmud.core.combat.AttackDefinition#telegraphTicks()}). Set on the
     * announce tick via {@link #beginTelegraph(AttackId, Username, int)} and decremented once per AI
     * decision via {@link #tickTelegraph()}; cleared with no damage on disengage/respawn and whenever
     * the target leaves the fight (mirroring {@link #specialAbilityUsed}). Transient, server-only
     * state that never touches player saves.
     */
    private final AtomicInteger telegraphTicksRemaining = new AtomicInteger(0);
    /**
     * The special attack a pending telegraph will resolve when its window elapses, or {@code null}
     * when no telegraph is pending. Paired with {@link #telegraphTicksRemaining} and
     * {@link #telegraphTarget}.
     */
    private final AtomicReference<AttackId> telegraphAttackId = new AtomicReference<>();
    /**
     * The player a pending telegraph is aimed at, or {@code null} when no telegraph is pending. The
     * telegraph is cancelled if this player is no longer a live, engaged occupant of the mob's room
     * when the window elapses.
     */
    private final AtomicReference<Username> telegraphTarget = new AtomicReference<>();
    /**
     * Number of committed AI attack decisions this mob has taken in the current combat encounter,
     * used to drive the per-encounter enrage clock (issue #745). Advanced once per committed attack
     * decision via {@link #advanceEnrage()}; telegraph wind-up ticks and turns spent fleeing/healing do
     * not advance it. Reset to zero whenever the encounter ends (see {@link #disengage(Username)} and
     * {@link #respawn()}), so a fresh pull always starts the clock over. Transient, server-only state
     * that never touches player saves.
     */
    private final AtomicInteger enrageDecisions = new AtomicInteger(0);
    /**
     * Whether this mob has crossed its {@link MobTemplate#enrageTicks()} threshold and enraged in the
     * current combat encounter (issue #745). Once set, its outgoing damage is boosted by
     * {@link MobTemplate#enrageDamageMultiplier()} via {@link #applyEnrageMultiplier(int)} for the rest
     * of the fight. Reset alongside {@link #enrageDecisions} when the encounter ends, mirroring
     * {@link #specialAbilityUsed}.
     */
    private final AtomicBoolean enraged = new AtomicBoolean(false);
    /**
     * The crowd-control lockout a player has landed on this mob this encounter (issue #763), or
     * {@code null} when the mob is uncontrolled. Paired with {@link #controlTicksRemaining}: the
     * lockout is only in force while the counter is positive. A {@link ControlType#STUN} suppresses
     * the mob's whole AI decision, {@link ControlType#ROOT} suppresses only its flee, and
     * {@link ControlType#SILENCE} suppresses only its special attack — mirroring what the same effect
     * forbids a player in PvP. Only the mechanical lockout is modelled here; an effect's flat stat
     * modifiers are deliberately not applied to mobs. Written on the tick thread via
     * {@link #applyControl(ControlType, int)} and decremented once per AI decision via
     * {@link #tickControl()}; cleared on disengage/respawn like the other transient encounter fields.
     * Transient, server-only state that never touches player saves.
     */
    private final AtomicReference<ControlType> control = new AtomicReference<>();
    /**
     * Remaining AI decisions the active {@link #control} lockout holds, or {@code 0} when the mob is
     * uncontrolled. Decremented on the tick thread via {@link #tickControl()}; when it reaches zero the
     * lockout expires and the mob resumes normal behaviour.
     */
    private final AtomicInteger controlTicksRemaining = new AtomicInteger(0);
    /** Mutable live location; confined to tick thread for writes, safe to read from any thread. */
    private volatile RoomId currentRoomId;
    /**
     * The player who summoned this mob, or {@code null} for ordinary world mobs. A summoned pet
     * fights alongside its summoner and never attacks them (see {@link #isSummoned()}).
     */
    @Nullable
    private final Username summoner;
    /**
     * Remaining lifetime, in ticks, of a summoned pet before it auto-dismisses. Only meaningful when
     * {@link #summoner} is non-null; decremented on the tick thread via {@link #tickSummonLifetime()}.
     */
    private final AtomicInteger summonTicksRemaining = new AtomicInteger(0);
    /**
     * The player who permanently tamed this mob (see the TAME command), or {@code null} for
     * ordinary world mobs and temporary summons. Unlike {@link #summoner}, a tamed pet has no
     * lifetime: it persists across sessions, follows its owner between rooms, and fights alongside
     * them until it is dismissed or destroyed.
     */
    @Nullable
    private final Username owner;
    /**
     * The owner-assigned custom display name of a tamed companion (see the NAME command), or
     * {@code null} when the companion is unnamed and shows its template name. Only meaningful for
     * {@link #isTamed() tamed} pets; written on the tick thread via {@link #setCustomName(String)}
     * (naming and respawn-on-login), read from any thread for message construction.
     */
    @Nullable
    private volatile String customName;
    /**
     * The owner-assigned custom roleplay description of a tamed companion (see the DESCRIBE command),
     * or {@code null} when the companion has no custom description. Only meaningful for
     * {@link #isTamed() tamed} pets; written on the tick thread via {@link #setDescription(String)}
     * (describing and respawn-on-login), read from any thread for LOOK rendering. When {@code null}
     * a LOOK falls back to a generic line rather than showing this description.
     */
    @Nullable
    private volatile String customDescription;

    public MobInstance(MobTemplate template) {
        this.template = template;
        this.maxHp = template.maxHp();
        this.companionScaling = null;
        this.hp = new AtomicInteger(maxHp);
        this.currentRoomId = template.spawnRoomId();
        this.summoner = null;
        this.owner = null;
    }

    private MobInstance(
        MobTemplate template, RoomId spawnRoom, Username summoner, int ownerLevel, int durationTicks) {
        this.template = Objects.requireNonNull(template, "Template is required");
        this.companionScaling = CompanionScaling.forOwnerLevel(ownerLevel);
        this.maxHp = companionScaling.scaleMaxHp(template.maxHp());
        this.hp = new AtomicInteger(maxHp);
        this.currentRoomId = Objects.requireNonNull(spawnRoom, "Spawn room is required");
        this.summoner = Objects.requireNonNull(summoner, "Summoner is required");
        this.owner = null;
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Summon duration must be positive");
        }
        this.summonTicksRemaining.set(durationTicks);
    }

    private MobInstance(MobTemplate template, RoomId spawnRoom, Username owner, int ownerLevel) {
        this.template = Objects.requireNonNull(template, "Template is required");
        this.companionScaling = CompanionScaling.forOwnerLevel(ownerLevel);
        this.maxHp = companionScaling.scaleMaxHp(template.maxHp());
        this.hp = new AtomicInteger(maxHp);
        this.currentRoomId = Objects.requireNonNull(spawnRoom, "Spawn room is required");
        this.summoner = null;
        this.owner = Objects.requireNonNull(owner, "Owner is required");
    }

    /**
     * Creates a summoned pet instance owned by {@code summoner}, spawned into {@code spawnRoom} and
     * living for {@code durationTicks} ticks. The pet fights hostile mobs in its room on the caster's
     * behalf and is removed on death or when its lifetime elapses.
     *
     * <p>The pet's effective max HP is scaled up from the template value by {@code ownerLevel} (see
     * {@link CompanionScaling}) so a higher-level summoner's pet is tougher.
     *
     * @param template      the pet template (must be a {@link MobTemplate#isPetTemplate() pet template})
     * @param spawnRoom     the room to spawn the pet into (normally the summoner's current room)
     * @param summoner      the player who summoned the pet
     * @param ownerLevel    the summoner's current level, used to scale the pet's HP and damage
     * @param durationTicks how many ticks the pet lives before auto-dismissing; must be positive
     * @return the new summoned pet instance
     */
    public static MobInstance summoned(
        MobTemplate template, RoomId spawnRoom, Username summoner, int ownerLevel, int durationTicks) {
        return new MobInstance(template, spawnRoom, summoner, ownerLevel, durationTicks);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner}, spawned into {@code spawnRoom}.
     * Unlike a {@link #summoned} pet, a tamed pet never expires: it follows its owner between rooms
     * and fights hostile mobs at their side until dismissed or destroyed.
     *
     * <p>The pet's effective max HP is scaled up from the template value by {@code ownerLevel} (see
     * {@link CompanionScaling}) so a higher-level tamer's companion is tougher. The scaling is fixed
     * at spawn time — a companion does not silently grow stronger as its owner levels up; it is only
     * recomputed the next time it re-enters the world (a fresh tame or a respawn on login).
     *
     * @param template   the tamed mob's template
     * @param spawnRoom  the room to spawn the pet into (normally the owner's current room)
     * @param owner      the player who tamed the mob
     * @param ownerLevel the owner's current level, used to scale the companion's HP and damage
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(
        MobTemplate template, RoomId spawnRoom, Username owner, int ownerLevel) {
        return new MobInstance(template, spawnRoom, owner, ownerLevel);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner} and carrying a custom display
     * name (see the NAME command). Used when respawning a persisted companion whose owner has already
     * named it, so the custom name is present from the moment it re-enters the world.
     *
     * @param template   the tamed mob's template
     * @param spawnRoom  the room to spawn the pet into (normally the owner's current room)
     * @param owner      the player who tamed the mob
     * @param ownerLevel the owner's current level, used to scale the companion's HP and damage
     * @param customName the companion's custom display name, or {@code null} to keep the template name
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(
        MobTemplate template,
        RoomId spawnRoom,
        Username owner,
        int ownerLevel,
        @Nullable String customName) {
        return tamed(template, spawnRoom, owner, ownerLevel, customName, null);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner} and carrying an optional custom
     * display name (see the NAME command) and custom roleplay description (see the DESCRIBE command).
     * Used when respawning a persisted companion whose owner has already named and/or described it, so
     * both are present from the moment it re-enters the world.
     *
     * @param template          the tamed mob's template
     * @param spawnRoom         the room to spawn the pet into (normally the owner's current room)
     * @param owner             the player who tamed the mob
     * @param ownerLevel        the owner's current level, used to scale the companion's HP and damage
     * @param customName        the companion's custom display name, or {@code null} to keep the
     *                          template name
     * @param customDescription the companion's custom roleplay description, or {@code null} to fall
     *                          back to the generic LOOK line
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(
        MobTemplate template,
        RoomId spawnRoom,
        Username owner,
        int ownerLevel,
        @Nullable String customName,
        @Nullable String customDescription) {
        MobInstance instance = new MobInstance(template, spawnRoom, owner, ownerLevel);
        if (customName != null && !customName.isBlank()) {
            instance.customName = customName;
        }
        if (customDescription != null && !customDescription.isBlank()) {
            instance.customDescription = customDescription;
        }
        return instance;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public MobTemplate template() {
        return template;
    }

    /** Returns the mob's current live room (may differ from the template spawn room after wandering). */
    public RoomId roomId() {
        return currentRoomId;
    }

    /**
     * Moves the mob to the given room.
     * Must only be called from the tick thread.
     *
     * @param roomId the destination room
     */
    public void moveTo(RoomId roomId) {
        this.currentRoomId = Objects.requireNonNull(roomId, "Room id is required");
    }

    public boolean isAlive() {
        return hp.get() > 0;
    }

    public int currentHp() {
        return hp.get();
    }

    /** Applies damage and returns remaining HP (clamped to 0). */
    public int takeDamage(int amount) {
        return hp.updateAndGet(current -> Math.max(0, current - amount));
    }

    /**
     * Restores HP to this mob, clamped to its effective {@link #maxHp()}, and returns the new current
     * HP. Used by healer mob AI to mend a wounded ally (issue #733); a heal never revives a mob at 0 HP
     * further than a live mob would be healed, since dead mobs are excluded from heal targeting.
     *
     * @param amount the HP to restore; must be non-negative
     * @return the mob's current HP after healing, never exceeding {@link #maxHp()}
     */
    public int heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Heal amount must be non-negative, got " + amount);
        }
        return hp.updateAndGet(current -> Math.min(maxHp, current + amount));
    }

    /**
     * Called once when the mob dies — starts the respawn countdown, using the day or night
     * respawn delay from {@link MobTemplate#respawnTicks(TimeOfDay)} as appropriate.
     *
     * @param timeOfDay the current time of day, used to pick the respawn delay
     */
    public void scheduleRespawn(TimeOfDay timeOfDay) {
        respawnTicksRemaining.set(template.respawnTicks(timeOfDay));
    }

    /**
     * Decrements the respawn countdown.
     *
     * @return true when the countdown reaches zero and the mob should respawn
     */
    public boolean tickRespawn() {
        return respawnTicksRemaining.decrementAndGet() <= 0;
    }

    public void engage(Username player) {
        engagedPlayers.add(player);
    }

    public void disengage(Username player) {
        engagedPlayers.remove(player);
        if (player.equals(taunter.get())) {
            clearTaunt();
        }
        if (engagedPlayers.isEmpty()) {
            specialAbilityUsed.set(false);
            clearTaunt();
            clearTelegraph();
            clearEnrage();
            clearControl();
        }
    }

    public Set<Username> engagedPlayers() {
        return Collections.unmodifiableSet(engagedPlayers);
    }

    /**
     * Returns whether this mob has already used its special ability
     * (see {@link MobTemplate#specialAttackId()}) in the current combat encounter.
     *
     * @return {@code true} if the special ability has already been used since the encounter began
     */
    public boolean specialAbilityUsed() {
        return specialAbilityUsed.get();
    }

    /**
     * Marks the special ability as used for the current combat encounter.
     * Must only be called from the tick thread.
     */
    public void markSpecialAbilityUsed() {
        specialAbilityUsed.set(true);
    }

    /**
     * Begins telegraphing a special attack: records the attack, its target, and the number of AI
     * ticks the mob will wind up before the blow lands. Replaces any previous pending telegraph. Must
     * only be called from the tick thread.
     *
     * @param attackId the special attack that will resolve when the window elapses
     * @param target   the player the telegraphed attack is aimed at
     * @param ticks    how many AI decisions the mob winds up before landing the attack; must be positive
     */
    public void beginTelegraph(AttackId attackId, Username target, int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Telegraph ticks must be positive");
        }
        this.telegraphAttackId.set(Objects.requireNonNull(attackId, "Telegraph attack id is required"));
        this.telegraphTarget.set(Objects.requireNonNull(target, "Telegraph target is required"));
        this.telegraphTicksRemaining.set(ticks);
    }

    /**
     * Returns whether this mob is currently winding up a telegraphed special attack.
     *
     * @return {@code true} while a telegraph is pending resolution
     */
    public boolean hasPendingTelegraph() {
        return telegraphTicksRemaining.get() > 0;
    }

    /**
     * Returns the special attack a pending telegraph will resolve, or {@code null} when none is pending.
     *
     * @return the pending telegraph's attack id, or {@code null}
     */
    @Nullable
    public AttackId telegraphAttackId() {
        return telegraphAttackId.get();
    }

    /**
     * Returns the player a pending telegraph is aimed at, or {@code null} when none is pending.
     *
     * @return the pending telegraph's target, or {@code null}
     */
    @Nullable
    public Username telegraphTarget() {
        return telegraphTarget.get();
    }

    /**
     * Consumes one AI decision from a pending telegraph's wind-up. Must only be called from the tick
     * thread and only while {@link #hasPendingTelegraph()} is {@code true}.
     *
     * @return {@code true} when the wind-up has fully elapsed and the telegraphed attack should now
     *         resolve (the pending state is cleared in that case); {@code false} while it keeps winding up
     */
    public boolean tickTelegraph() {
        if (telegraphTicksRemaining.get() <= 0) {
            return false;
        }
        if (telegraphTicksRemaining.decrementAndGet() <= 0) {
            clearTelegraph();
            return true;
        }
        return false;
    }

    /**
     * Cancels any pending telegraph with no damage. A no-op when none is pending. Must only be called
     * from the tick thread.
     */
    public void clearTelegraph() {
        telegraphTicksRemaining.set(0);
        telegraphAttackId.set(null);
        telegraphTarget.set(null);
    }

    /**
     * Forces this mob to prioritise attacking {@code taunter} for the next {@code durationTicks} AI
     * decisions, overriding its normal random-candidate targeting (see the Warrior TAUNT skill). A
     * fresh taunt fully replaces any previous one. Must only be called from the tick thread via
     * {@link MobRegistry}.
     *
     * @param taunter       the player drawing the mob's aggro
     * @param durationTicks how many AI decisions the taunt holds; must be positive
     */
    public void applyTaunt(Username taunter, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Taunt duration must be positive");
        }
        this.taunter.set(Objects.requireNonNull(taunter, "Taunter is required"));
        this.tauntTicksRemaining.set(durationTicks);
    }

    /**
     * Returns the player currently holding this mob's aggro via an active taunt, or {@code null} when
     * no taunt is active (never applied, expired, or cleared on disengage/respawn).
     *
     * @return the active taunter's username, or {@code null}
     */
    @Nullable
    public Username activeTaunter() {
        return tauntTicksRemaining.get() > 0 ? taunter.get() : null;
    }

    /**
     * Consumes one AI decision from an active taunt, expiring it (and clearing the taunter) when the
     * remaining count reaches zero. A no-op when no taunt is active. Must only be called from the tick
     * thread.
     */
    public void consumeTauntTick() {
        if (tauntTicksRemaining.get() > 0 && tauntTicksRemaining.decrementAndGet() <= 0) {
            clearTaunt();
        }
    }

    private void clearTaunt() {
        taunter.set(null);
        tauntTicksRemaining.set(0);
    }

    /**
     * Advances this encounter's enrage clock by one committed AI attack decision (issue #745),
     * enraging the mob exactly once when the {@link MobTemplate#enrageTicks()} threshold is first
     * crossed. A no-op returning {@code false} when the mob is not {@link MobTemplate#enrageCapable()
     * enrage-capable} or has already enraged this encounter. Must only be called from the tick thread,
     * once per committed attack decision, so telegraph wind-up ticks and turns spent fleeing/healing
     * never advance it.
     *
     * @return {@code true} on the single decision that first pushes this mob past its enrage threshold
     *         (the caller announces the enrage); {@code false} otherwise
     */
    public boolean advanceEnrage() {
        Integer threshold = template.enrageTicks();
        if (threshold == null || enraged.get()) {
            return false;
        }
        if (enrageDecisions.incrementAndGet() >= threshold) {
            enraged.set(true);
            return true;
        }
        return false;
    }

    /**
     * Returns whether this mob has enraged in the current combat encounter (issue #745).
     *
     * @return {@code true} once the enrage threshold has been crossed this encounter
     */
    public boolean isEnraged() {
        return enraged.get();
    }

    /**
     * Scales a landed hit's damage by this mob's {@link MobTemplate#enrageDamageMultiplier()} when it
     * has {@link #isEnraged() enraged} (issue #745), returning {@code rawDamage} unchanged otherwise.
     * The result is floored at {@code 1} so an enraged hit never rounds down to zero.
     *
     * @param rawDamage the unscaled resolved damage of a single landed hit
     * @return the damage to actually apply after any enrage boost
     */
    public int applyEnrageMultiplier(int rawDamage) {
        if (!enraged.get()) {
            return rawDamage;
        }
        return Math.max(1, (int) Math.round(rawDamage * template.enrageDamageMultiplier()));
    }

    private void clearEnrage() {
        enrageDecisions.set(0);
        enraged.set(false);
    }

    /**
     * Locks this mob down under a player-cast crowd-control effect for {@code durationTicks} AI
     * decisions (issue #763), mirroring what the same {@link ControlType} already does to a player in
     * PvP. A fresh control fully replaces any previous one (the newest lockout wins). Only the
     * mechanical lockout is applied — the effect's flat stat modifiers are deliberately not carried to
     * mobs. Must only be called from the tick thread via {@link MobRegistry}.
     *
     * @param type          the control classification to impose (root, silence, or stun)
     * @param durationTicks how many AI decisions the lockout holds; must be positive
     */
    public void applyControl(ControlType type, int durationTicks) {
        Objects.requireNonNull(type, "Control type is required");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Control duration must be positive");
        }
        this.control.set(type);
        this.controlTicksRemaining.set(durationTicks);
    }

    /**
     * Returns the crowd-control lockout currently in force on this mob, or {@code null} when it is
     * uncontrolled (never applied, expired, or cleared on disengage/respawn). Safe to read from any
     * thread.
     *
     * @return the active control type, or {@code null}
     */
    @Nullable
    public ControlType activeControl() {
        return controlTicksRemaining.get() > 0 ? control.get() : null;
    }

    /**
     * Returns whether this mob is currently stunned, i.e. fully incapacitated for its AI decision.
     *
     * @return {@code true} while a {@link ControlType#STUN} lockout is in force
     */
    public boolean isStunned() {
        return activeControl() == ControlType.STUN;
    }

    /**
     * Returns whether this mob is currently rooted, i.e. unable to flee.
     *
     * @return {@code true} while a {@link ControlType#ROOT} lockout is in force
     */
    public boolean isRooted() {
        return activeControl() == ControlType.ROOT;
    }

    /**
     * Returns whether this mob is currently silenced, i.e. barred from firing its special attack.
     *
     * @return {@code true} while a {@link ControlType#SILENCE} lockout is in force
     */
    public boolean isSilenced() {
        return activeControl() == ControlType.SILENCE;
    }

    /**
     * Consumes one AI decision from an active control lockout, expiring it (and clearing the control
     * type) when the remaining count reaches zero. A no-op when no control is active. Must only be
     * called from the tick thread, once per AI decision.
     */
    public void tickControl() {
        if (controlTicksRemaining.get() > 0 && controlTicksRemaining.decrementAndGet() <= 0) {
            clearControl();
        }
    }

    private void clearControl() {
        control.set(null);
        controlTicksRemaining.set(0);
    }

    /**
     * Returns whether this mob is a temporary summoned pet (as opposed to an ordinary world mob).
     *
     * @return {@code true} when this instance was created via {@link #summoned}
     */
    public boolean isSummoned() {
        return summoner != null;
    }

    /**
     * Returns the player who summoned this pet, or {@code null} for ordinary world mobs.
     *
     * @return the summoner's username, or {@code null}
     */
    @Nullable
    public Username summoner() {
        return summoner;
    }

    /**
     * Returns whether this mob is a permanently tamed pet (see the TAME command).
     *
     * @return {@code true} when this instance was created via {@link #tamed}
     */
    public boolean isTamed() {
        return owner != null;
    }

    /**
     * Returns the player who permanently tamed this pet, or {@code null} for ordinary mobs and
     * temporary summons.
     *
     * @return the owner's username, or {@code null}
     */
    @Nullable
    public Username owner() {
        return owner;
    }

    /**
     * Returns the name to show for this mob: a tamed companion's owner-assigned custom name when one
     * is set (see the NAME command), otherwise the template name. Safe to call from any thread. Use
     * this everywhere a mob's identity is shown to players so named companions read as their custom
     * name in listings, room descriptions, and combat messages.
     *
     * @return the display name, never {@code null}
     */
    public String displayName() {
        String name = customName;
        return name != null ? name : template.name();
    }

    /**
     * Returns this tamed companion's custom display name, or {@code null} when it is unnamed.
     *
     * @return the custom name, or {@code null}
     */
    @Nullable
    public String customName() {
        return customName;
    }

    /**
     * Assigns a custom display name to this tamed companion (see the NAME command). Must only be
     * called from the tick thread.
     *
     * @param customName the new custom name; blank/{@code null} clears it back to the template name
     */
    public void setCustomName(@Nullable String customName) {
        this.customName = customName == null || customName.isBlank() ? null : customName;
    }

    /**
     * Returns this tamed companion's custom roleplay description, or {@code null} when it has none.
     * Safe to call from any thread.
     *
     * @return the custom description, or {@code null}
     */
    @Nullable
    public String customDescription() {
        return customDescription;
    }

    /**
     * Assigns a custom roleplay description to this tamed companion (see the DESCRIBE command). Once
     * set, this replaces the generic line shown when a player LOOKs at the companion. Must only be
     * called from the tick thread.
     *
     * @param customDescription the new custom description; blank/{@code null} clears it back to the
     *                          generic LOOK line
     */
    public void setDescription(@Nullable String customDescription) {
        this.customDescription =
            customDescription == null || customDescription.isBlank() ? null : customDescription;
    }

    /**
     * Returns whether this mob is a player-controlled pet — either a temporary {@link #isSummoned()
     * summon} or a permanently {@link #isTamed() tamed} companion. Pets never attack players, never
     * respawn as world mobs, and fight hostile mobs on their master's behalf.
     *
     * @return {@code true} when this instance has a summoner or an owner
     */
    public boolean isPet() {
        return summoner != null || owner != null;
    }

    /**
     * Returns the player this pet belongs to — its summoner if summoned, otherwise its owner if
     * tamed, or {@code null} for ordinary world mobs.
     *
     * @return the controlling player's username, or {@code null}
     */
    @Nullable
    public Username petOwner() {
        return summoner != null ? summoner : owner;
    }

    /** Returns the pet's remaining lifetime in ticks (zero for non-summoned mobs). */
    public int summonTicksRemaining() {
        return summonTicksRemaining.get();
    }

    /**
     * Decrements a summoned pet's remaining lifetime by one tick. Must only be called from the tick
     * thread and only for summoned pets.
     *
     * @return {@code true} when the lifetime has elapsed and the pet should be dismissed
     */
    public boolean tickSummonLifetime() {
        return summonTicksRemaining.decrementAndGet() <= 0;
    }

    /**
     * Returns this instance's effective max HP. For an ordinary world mob this equals the template's
     * {@link MobTemplate#maxHp()}; for a {@link #isPet() companion} it is the template value scaled up
     * by the owner's level at spawn time (see {@link CompanionScaling}).
     *
     * @return the effective max HP
     */
    public int maxHp() {
        return maxHp;
    }

    /**
     * Scales a companion's resolved hit damage by this instance's owner-level damage multiplier so a
     * higher-level owner's pet hits harder. Returns {@code rawDamage} unchanged for ordinary world
     * mobs (those with no {@link #companionScaling}).
     *
     * @param rawDamage the unscaled resolved damage of a single hit
     * @return the damage to actually apply
     */
    public int scaleCompanionDamage(int rawDamage) {
        return companionScaling == null ? rawDamage : companionScaling.scaleDamage(rawDamage);
    }

    /** Resets the mob to full HP and returns it to its spawn room, ready to act again. */
    public void respawn() {
        hp.set(maxHp);
        respawnTicksRemaining.set(0);
        engagedPlayers.clear();
        specialAbilityUsed.set(false);
        clearTaunt();
        clearTelegraph();
        clearEnrage();
        clearControl();
        currentRoomId = template.spawnRoomId();
    }
}
