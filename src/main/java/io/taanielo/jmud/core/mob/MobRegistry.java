package io.taanielo.jmud.core.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackEffectApplication;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
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
public class MobRegistry implements Tickable {

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

    private final ConcurrentHashMap<UUID, MobInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Username, UUID> playerCombatTargets = new ConcurrentHashMap<>();

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
            for (int i = 0; i < template.maxCount(); i++) {
                MobInstance mob = new MobInstance(template);
                instances.put(mob.instanceId(), mob);
            }
        }
        log.info("Spawned {} mob instance(s) from {} template(s)", instances.size(), templates.size());
    }

    @Override
    public void tick() {
        runPlayerCombat();
        runWanderPhase();
        for (MobInstance mob : instances.values()) {
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
            if (!mob.template().aggressive() && mob.engagedPlayers().isEmpty()) {
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

            Player reloaded = playerRepository.loadPlayer(attacker.getUsername()).orElse(attacker);
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
        }
        return new GameActionResult(null, null, messages);
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
