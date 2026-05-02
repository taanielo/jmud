package io.taanielo.jmud.core.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
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
    private final PlayerEventBus playerEventBus;

    private final ConcurrentHashMap<UUID, MobInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Username, UUID> playerCombatTargets = new ConcurrentHashMap<>();

    public MobRegistry(
        MobTemplateRepository templateRepository,
        ItemRepository itemRepository,
        AttackRepository attackRepository,
        RoomService roomService,
        PlayerRepository playerRepository,
        PlayerEventBus playerEventBus
    ) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "Template repository is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.playerEventBus = Objects.requireNonNull(playerEventBus, "Player event bus is required");
    }

    /**
     * Spawns initial mob instances from all templates. Call once on server start.
     */
    public void init() {
        List<MobTemplate> templates;
        try {
            templates = templateRepository.findAll();
        } catch (MobRepositoryException e) {
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
            dropLoot(mob);
            mob.scheduleRespawn();
            endCombatForMob(mob);
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
        Username targetUsername = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Player target = playerRepository.loadPlayer(targetUsername).orElse(null);
        if (target == null || target.isDead()) {
            return;
        }
        AttackDefinition attack = loadAttack(mob.template().attackId());
        if (attack == null) {
            return;
        }
        int damage = rollDamage(attack);
        Player damagedPlayer = target.withVitals(target.getVitals().damage(damage));

        if (damagedPlayer.getVitals().hp() <= 0) {
            handleMobKill(mob, damagedPlayer, candidates);
        } else {
            playerRepository.savePlayer(damagedPlayer);
            String hitMsg = "The " + mob.template().name() + " hits you for " + damage + " damage!";
            playerEventBus.publish(targetUsername,
                new GameActionResult(damagedPlayer, null, List.of(GameMessage.toSource(hitMsg))));
        }
    }

    private void handleMobKill(MobInstance mob, Player damagedPlayer, List<Username> roomOccupants) {
        Player deadPlayer = damagedPlayer.die();
        roomService.spawnCorpse(deadPlayer.getUsername(), mob.roomId());
        roomService.clearPlayerLocation(deadPlayer.getUsername());
        playerRepository.savePlayer(deadPlayer);
        mob.disengage(deadPlayer.getUsername());
        playerCombatTargets.remove(deadPlayer.getUsername());

        String slainMsg = "The " + mob.template().name() + " has slain you!";
        playerEventBus.publish(deadPlayer.getUsername(),
            new GameActionResult(deadPlayer, null, List.of(GameMessage.toSource(slainMsg))));

        String roomMsg = deadPlayer.getUsername().getValue()
            + " has been slain by the " + mob.template().name() + ".";
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
                dropLoot(mob);
                mob.scheduleRespawn();
                endCombatForMob(mob);
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

    private void dropLoot(MobInstance mob) {
        for (LootEntry entry : mob.template().lootTable()) {
            if (ThreadLocalRandom.current().nextDouble() <= entry.dropChance()) {
                try {
                    itemRepository.findById(entry.itemId()).ifPresent(item ->
                        roomService.addItem(mob.roomId(), item));
                } catch (RepositoryException e) {
                    log.warn("Failed to drop loot item {}: {}", entry.itemId(), e.getMessage());
                }
            }
        }
    }

    private AttackDefinition loadAttack(AttackId attackId) {
        try {
            return attackRepository.findById(attackId).orElse(null);
        } catch (AttackRepositoryException e) {
            log.warn("Failed to load attack {}: {}", attackId, e.getMessage());
            return null;
        }
    }

    private AttackId resolveAttackId(Player attacker) {
        ItemId weaponId = attacker.getEquipment().equipped(EquipmentSlot.WEAPON);
        if (weaponId != null) {
            for (Item item : attacker.getInventory()) {
                if (item.getId().equals(weaponId) && item.getAttackRef() != null) {
                    return item.getAttackRef();
                }
            }
        }
        return CombatSettings.defaultAttackId();
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
            ? attack.minDamage() + ThreadLocalRandom.current().nextInt(range + 1)
            : attack.minDamage();
        return Math.max(1, base + attack.damageBonus());
    }

    Collection<MobInstance> allInstances() {
        return instances.values();
    }
}
