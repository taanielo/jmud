package io.taanielo.jmud.core.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.RangeType;
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
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.LevelUpService.LevelUpResult;
import io.taanielo.jmud.core.player.Player;
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
public class MobRegistry implements Tickable, NpcStealPort, MobContentReloader {

    private final MobTemplateRepository templateRepository;
    private final ItemRepository itemRepository;
    private final AttackRepository attackRepository;
    private final RoomService roomService;
    private final PlayerRepository playerRepository;
    private final PersistenceQueue persistenceQueue;
    private final PlayerEventBus playerEventBus;
    private final CombatRandom random;
    private final LevelUpService levelUpService = new LevelUpService();
    private final MessageRenderer messageRenderer = new MessageRenderer();
    /** Optional quest kill hook; may be null when quests are disabled. */
    private QuestKillService questKillService;
    /** Optional party service for XP splitting; may be null when parties are disabled. */
    private PartyService partyService;
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

    private final ConcurrentHashMap<UUID, MobInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Username, UUID> playerCombatTargets = new ConcurrentHashMap<>();
    /**
     * Pet templates (see {@link MobTemplate#isPetTemplate()}) cached at {@link #init()} so the
     * on-demand SUMMON path never touches the JSON repository from the tick thread (AGENTS.md §5).
     */
    private final List<MobTemplate> petTemplates = new ArrayList<>();
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
     * Registers the effect engine used to apply a mob attack's on-hit status effect
     * (see {@link AttackDefinition#effectOnHit()}) to the player it hits.
     *
     * @param effectEngine the effect engine; may be null to disable on-hit effect application
     */
    public void setEffectEngine(EffectEngine effectEngine) {
        this.effectEngine = effectEngine;
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
            for (int i = 0; i < template.maxCount(); i++) {
                MobInstance mob = new MobInstance(template);
                instances.put(mob.instanceId(), mob);
            }
        }
        log.info("Spawned {} mob instance(s) from {} template(s); cached {} pet template(s)",
            instances.size(), templates.size(), petTemplates.size());
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
        for (MobTemplate template : templates) {
            templatesById.put(template.id().getValue(), template);
            if (template.isPetTemplate()) {
                petTemplates.add(template);
            }
        }
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
                if (mob.tickRespawn()) {
                    mob.respawn();
                    log.debug("Mob {} respawned in {}", mob.template().name(), mob.roomId());
                }
                continue;
            }
            if (mob.template().attackId() == null) {
                continue;
            }
            // A mob may initiate combat when it is inherently aggressive, when it belongs to a
            // faction (its hostility is then decided per-player in runMobAi from reputation), or
            // when it is already engaged in a fight.
            boolean couldInitiate = mob.template().aggressive()
                || (reputationService != null && mob.template().factionId() != null);
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
        instances.remove(mob.instanceId());
        endCombatForMob(mob);
        log.debug("Purged mob {} from {} via admin command", mob.template().name(), roomId);
        return Optional.of(mob.template().name());
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
        int damage = rollDamage(attack);
        int remaining = mob.takeDamage(damage);

        mob.engage(attacker.getUsername());
        playerCombatTargets.put(attacker.getUsername(), mob.instanceId());

        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource(
            "You strike the " + mob.template().name() + " for " + damage + " damage. ("
                + remaining + " HP remaining)"));

        if (!mob.isAlive()) {
            awardMobKill(mob, attacker, roomId, messages);
        }
        return new GameActionResult(null, null, messages);
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
        for (Item dropped : dropLoot(mob)) {
            messages.add(GameMessage.toSource(
                "A " + dropped.getName() + " drops to the ground."));
        }
        mob.scheduleRespawn(currentTimeOfDay());
        endCombatForMob(mob);

        // Determine XP share: split equally among party members in the same room.
        List<Username> xpRecipients = partyService != null
            ? partyService.getPartyMembersInRoom(
                attacker.getUsername(), roomId, roomService::findPlayerLocation)
            : List.of(attacker.getUsername());
        int xpPerMember = (int) Math.floor(
            (double) mob.template().xpReward() / Math.max(1, xpRecipients.size()));

        LevelUpResult levelUpResult = levelUpService.awardXp(attacker, xpPerMember);
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

        // Award XP to other party members in the same room.
        for (Username member : xpRecipients) {
            if (member.equals(attacker.getUsername())) {
                continue;
            }
            Player memberPlayer = playerRepository.loadPlayer(member).orElse(null);
            if (memberPlayer == null || memberPlayer.isDead()) {
                continue;
            }
            LevelUpResult memberLvl = levelUpService.awardXp(memberPlayer, xpPerMember);
            Player memberAfterXp = memberLvl.player()
                .withTotalKills(memberLvl.player().getTotalKills() + 1);
            saveOrLog(memberAfterXp);
            List<GameMessage> memberMsgs = new ArrayList<>();
            memberMsgs.add(GameMessage.toSource(
                "Your party slay the " + mob.template().name()
                    + "! You gain " + xpPerMember + " experience points."));
            if (memberLvl.leveledUp()) {
                memberMsgs.add(GameMessage.toSource(
                    "You have advanced to level " + memberAfterXp.getLevel() + "!"));
            }
            playerEventBus.publish(member, new GameActionResult(memberAfterXp, null, memberMsgs));
        }
        return afterXp;
    }

    /**
     * Processes a player's ranged attack (SHOOT command) against a mob in an adjacent room.
     *
     * <p>The player must be wielding a weapon whose attack is classified {@link RangeType#RANGED}
     * (see {@link AttackDefinition#isRanged()}); melee weapons and being unarmed are rejected. The
     * named direction must be a valid exit from {@code roomId}, and a live, attackable mob matching
     * {@code targetName} must occupy that adjacent room. On a hit the mob takes damage and, if it
     * survives, retaliates by closing the distance — moving into the shooter's room and engaging
     * them so it attacks in melee on subsequent ticks. All mutation happens on the tick thread via
     * the player command queue (AGENTS.md §5).
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
        int damage = rollDamage(attack);
        int remaining = mob.takeDamage(damage);

        List<GameMessage> messages = new ArrayList<>();
        String mobName = mob.template().name();
        messages.add(GameMessage.toSource(
            "You fire at the " + mobName + " to the " + direction.label() + " for " + damage
                + " damage. (" + remaining + " HP remaining)"));

        // Announce the shot to bystanders in the shooter's room.
        String shooterName = attacker.getUsername().getValue();
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
     * mana is deducted once, then each mob takes the spell's summed HP-decrease damage; slain mobs
     * award their normal kill rewards (loot, XP, gold, quest credit), accumulated onto the caster so
     * a single persisted snapshot carries both the mana deduction and every reward.
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
        int damage = aoeSpellDamage(spell);

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
            int remaining = mob.takeDamage(damage);
            messages.add(GameMessage.toSource("Your " + spell.name() + " strikes the " + mobName
                + " for " + damage + " damage. (" + remaining + " HP remaining)"));
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
     * Returns the damage an AoE spell deals to each target: the sum of its HP-decreasing
     * {@link io.taanielo.jmud.core.ability.AbilityEffectKind#VITALS} effects. Non-damage effects
     * (status effects, cures) are ignored on the mob path, which has no persistent effect model.
     *
     * @param spell the AoE spell
     * @return the per-target damage (zero when the spell defines no HP-decrease effect)
     */
    private static int aoeSpellDamage(Ability spell) {
        int damage = 0;
        for (AbilityEffect effect : spell.effects()) {
            if (effect.kind() == AbilityEffectKind.VITALS
                && effect.stat() == AbilityStat.HP
                && effect.operation() == AbilityOperation.DECREASE) {
                damage += effect.amount();
            }
        }
        return damage;
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
            petTemplate, roomId, caster.getUsername(), petTemplate.summonDurationTicks());
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

        MobInstance pet = MobInstance.tamed(mob.template(), roomId, tamer.getUsername());
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
            messages.add(GameMessage.toSource("  " + pet.template().name()
                + " (" + pet.currentHp() + "/" + pet.template().maxHp() + " HP) in "
                + pet.roomId().getValue()));
        }
        return new GameActionResult(null, null, messages);
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
        List<String> alreadyLive = new ArrayList<>(instances.values().stream()
            .filter(m -> m.isTamed() && username.equals(m.owner()))
            .map(m -> m.template().id().getValue())
            .toList());
        for (String templateId : owner.pets().tamedTemplateIds()) {
            if (alreadyLive.remove(templateId)) {
                continue;
            }
            MobTemplate template = templatesById.get(templateId);
            if (template == null) {
                log.warn("Tamed pet template {} for player {} no longer exists", templateId, username);
                continue;
            }
            MobInstance pet = MobInstance.tamed(template, roomId, username);
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
                if (pet.isTamed()) {
                    releasePersistedPet(pet);
                    removePet(pet, "Your " + pet.template().name() + " has been slain!",
                        "The " + pet.template().name() + " collapses, slain.");
                } else {
                    removePet(pet, "Your " + pet.template().name() + " has been destroyed!",
                        "The " + pet.template().name() + " collapses into dust.");
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
            String petName = pet.template().name();
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
        saveOrLog(ownerPlayer.withTamedPets(ownerPlayer.pets().release(pet.template().id().getValue())));
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
        String petName = pet.template().name();
        String foeName = foe.template().name();
        int damage = rollDamage(petAttack);
        int remaining = foe.takeDamage(damage);

        List<GameMessage> messages = new ArrayList<>();
        messages.add(GameMessage.toSource("Your " + petName + " strikes the " + foeName + " for "
            + damage + " damage. (" + remaining + " HP remaining)"));

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

        // Surviving foe retaliates against the pet (not its owner).
        AttackDefinition foeAttack = loadAttack(foe.template().attackId());
        if (foeAttack != null) {
            int retaliation = rollDamage(foeAttack);
            int petRemaining = pet.takeDamage(retaliation);
            messages.add(GameMessage.toSource("The " + foeName + " retaliates against your " + petName
                + " for " + retaliation + " damage. (" + petRemaining + " HP remaining)"));
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
        playerEventBus.publish(owner, new GameActionResult(null, null, messages));
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

    private void runMobAi(MobInstance mob) {
        List<Username> candidates;
        Set<Username> engaged = mob.engagedPlayers();
        if (!engaged.isEmpty()) {
            List<Username> inRoom = roomService.getPlayersInRoom(mob.roomId());
            candidates = engaged.stream().filter(inRoom::contains).toList();
        } else {
            candidates = roomService.getPlayersInRoom(mob.roomId());
        }
        if (candidates.isEmpty()) {
            return;
        }
        Username targetUsername = candidates.get(random.roll(0, candidates.size() - 1));
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

        int damage = rollDamage(attack);
        Player damagedPlayer = target.withVitals(target.getVitals().damage(damage));

        if (damagedPlayer.getVitals().hp() <= 0) {
            handleMobKill(mob, damagedPlayer, candidates);
        } else {
            List<GameMessage> messages = new ArrayList<>();
            if (firstEngagement && !useSpecial) {
                messages.add(GameMessage.toSource(
                    "The " + mob.template().name() + " lunges at you!"));
            }
            messages.add(GameMessage.toSource(hitMessage(mob, attack, useSpecial, damage)));
            applyOnHitEffect(attack, damagedPlayer, targetUsername, candidates, messages);
            damagedPlayer = degradeEquippedGear(damagedPlayer, messages);
            saveOrLog(damagedPlayer);
            playerEventBus.publish(targetUsername,
                new GameActionResult(damagedPlayer, null, messages));
        }
    }

    /**
     * Builds the self-facing message shown to a player hit by a mob's attack. When the attack
     * carries a configured {@link MessageSpec} for the {@link MessagePhase#ATTACK_HIT} phase on the
     * {@link MessageChannel#SELF} channel (e.g. a boss's special ability flavour text), that message
     * is rendered and used instead of the generic damage line, so special-ability hits read
     * distinctly from normal attacks.
     *
     * @param mob       the attacking mob
     * @param attack    the attack definition that just landed a hit
     * @param useSpecial whether this hit was the mob's special ability rather than its basic attack
     * @param damage    the damage dealt, substituted into the {@code {damage}} placeholder
     * @return the rendered message text to show the target player
     */
    private String hitMessage(MobInstance mob, AttackDefinition attack, boolean useSpecial, int damage) {
        for (MessageSpec spec : attack.messages()) {
            if (spec.phase() == MessagePhase.ATTACK_HIT && spec.channel() == MessageChannel.SELF) {
                MessageContext context = new MessageContext(
                    null, null, mob.template().name(), null, null, null, attack.name(), damage);
                return messageRenderer.render(spec, context);
            }
        }
        if (useSpecial) {
            return "The " + mob.template().name() + " unleashes " + attack.name()
                + " on you for " + damage + " damage!";
        }
        return "The " + mob.template().name() + " hits you for " + damage + " damage!";
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
            int damage = rollDamage(attack);
            int remaining = mob.takeDamage(damage);

            List<GameMessage> messages = new ArrayList<>();
            messages.add(GameMessage.toSource(
                "You strike the " + mob.template().name() + " for " + damage + " damage. ("
                    + remaining + " HP remaining)"));

            if (!mob.isAlive()) {
                messages.add(GameMessage.toSource("You slay the " + mob.template().name() + "!"));
                for (Item dropped : dropLoot(mob)) {
                    messages.add(GameMessage.toSource(
                        "A " + dropped.getName() + " drops to the ground."));
                }
                mob.scheduleRespawn(currentTimeOfDay());
                endCombatForMob(mob);

                // Determine XP share: split equally among party members in the same room.
                RoomId killRoom = playerRoom;
                List<Username> xpRecipients = partyService != null
                    ? partyService.getPartyMembersInRoom(
                        username, killRoom, roomService::findPlayerLocation)
                    : List.of(username);
                int xpPerMember = (int) Math.floor(
                    (double) mob.template().xpReward() / Math.max(1, xpRecipients.size()));

                Player reloaded = playerRepository.loadPlayer(username).orElse(player);
                LevelUpResult levelUpResult = levelUpService.awardXp(reloaded, xpPerMember);
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
                saveOrLog(afterXp);
                messages.add(GameMessage.toSource(
                    "You gain " + xpPerMember + " experience points."));
                if (levelUpResult.leveledUp()) {
                    messages.add(GameMessage.toSource(
                        "You have advanced to level " + afterXp.getLevel() + "!"));
                }

                // Award XP to other party members in the same room.
                for (Username member : xpRecipients) {
                    if (member.equals(username)) {
                        continue;
                    }
                    Player memberPlayer = playerRepository.loadPlayer(member).orElse(null);
                    if (memberPlayer == null || memberPlayer.isDead()) {
                        continue;
                    }
                    LevelUpResult memberLvl = levelUpService.awardXp(memberPlayer, xpPerMember);
                    Player memberAfterXp = memberLvl.player()
                        .withTotalKills(memberLvl.player().getTotalKills() + 1);
                    saveOrLog(memberAfterXp);
                    List<GameMessage> memberMsgs = new ArrayList<>();
                    memberMsgs.add(GameMessage.toSource(
                        "Your party slay the " + mob.template().name()
                            + "! You gain " + xpPerMember + " experience points."));
                    if (memberLvl.leveledUp()) {
                        memberMsgs.add(GameMessage.toSource(
                            "You have advanced to level " + memberAfterXp.getLevel() + "!"));
                    }
                    playerEventBus.publish(member, new GameActionResult(memberAfterXp, null, memberMsgs));
                }
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
     * Rolls loot for a dead mob, places each dropped item in the mob's room,
     * and returns the list of items that actually dropped.
     *
     * @param mob the mob that just died
     * @return items placed on the room floor (never null, may be empty)
     */
    private List<Item> dropLoot(MobInstance mob) {
        List<Item> dropped = new ArrayList<>();
        for (LootEntry entry : mob.template().lootTable()) {
            if (random.nextDouble() <= entry.dropChance()) {
                try {
                    itemRepository.findById(entry.itemId()).ifPresent(item -> {
                        roomService.addItem(mob.roomId(), item);
                        dropped.add(item);
                    });
                } catch (RepositoryException e) {
                    log.warn("Failed to drop loot item {}: {}", entry.itemId(), e.getMessage());
                }
            }
        }
        return dropped;
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
            String name = mob.template().name().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return mob;
            }
        }
        return null;
    }

    private int rollDamage(AttackDefinition attack) {
        int range = attack.maxDamage() - attack.minDamage();
        int base = range > 0
            ? attack.minDamage() + random.roll(0, range)
            : attack.minDamage();
        return Math.max(1, base + attack.damageBonus());
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
