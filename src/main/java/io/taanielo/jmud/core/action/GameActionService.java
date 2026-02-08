package io.taanielo.jmud.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityEffectListener;
import io.taanielo.jmud.core.ability.AbilityEngine;
import io.taanielo.jmud.core.ability.AbilityMessageSink;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityUseResult;
import io.taanielo.jmud.core.ability.DefaultAbilityEffectResolver;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatResult;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

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
    private final MessageEmitter messageEmitter = new MessageEmitter();

    /**
     * Creates a game action service with the given domain dependencies.
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
        this.abilityRegistry = Objects.requireNonNull(abilityRegistry, "Ability registry is required");
        this.abilityCostResolver = Objects.requireNonNull(abilityCostResolver, "Ability cost resolver is required");
        this.abilityEffectEngine = Objects.requireNonNull(abilityEffectEngine, "Ability effect engine is required");
        this.combatEngine = Objects.requireNonNull(combatEngine, "Combat engine is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.abilityTargetResolver = Objects.requireNonNull(abilityTargetResolver, "Ability target resolver is required");
        this.cooldownTracker = Objects.requireNonNull(cooldownTracker, "Cooldown tracker is required");
        this.encumbranceService = Objects.requireNonNull(encumbranceService, "Encumbrance service is required");
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
            CombatResult result = combatEngine.resolve(source, target, CombatSettings.defaultAttackId());
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
            GameActionResult deathResult = resolveDeathIfNeeded(result.target(), source);
            Player updatedTarget = deathResult.updatedTarget();
            messages.addAll(deathResult.messages());
            return new GameActionResult(null, updatedTarget, messages);
        } catch (AttackRepositoryException | EffectRepositoryException e) {
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
        CollectingAbilityMessageSink sink = new CollectingAbilityMessageSink();
        DefaultAbilityEffectResolver resolver = new DefaultAbilityEffectResolver(
            abilityEffectEngine, sink, AbilityEffectListener.noop()
        );
        AbilityEngine engine = new AbilityEngine(abilityRegistry, abilityCostResolver, resolver, sink);

        AbilityUseResult result = engine.use(
            source, input, source.getLearnedAbilities(),
            abilityTargetResolver, cooldownTracker
        );

        List<GameMessage> messages = new ArrayList<>(sink.collected());
        for (String message : result.messages()) {
            messages.add(GameMessage.toSource(message));
        }

        Player updatedSource = result.source();
        Player updatedTarget = result.target();

        GameActionResult deathResult = resolveDeathIfNeeded(updatedTarget, updatedSource);
        updatedTarget = deathResult.updatedTarget();
        messages.addAll(deathResult.messages());

        if (updatedTarget.getUsername().equals(updatedSource.getUsername())) {
            updatedSource = updatedTarget;
        }

        return new GameActionResult(updatedSource, updatedTarget, messages);
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
     * Consumes an item from inventory, applying its effects to the player.
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
        if (item.getEffects().isEmpty()) {
            return GameActionResult.error("Nothing happens.");
        }
        CollectingEffectMessageSink effectSink = new CollectingEffectMessageSink(
            source.getUsername(),
            source.getUsername()
        );
        try {
            for (ItemEffect effect : item.getEffects()) {
                abilityEffectEngine.apply(source, effect.id(), effectSink);
            }
        } catch (EffectRepositoryException e) {
            return GameActionResult.error("You cannot use that item right now.");
        }
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
        messages.addAll(messageEmitter.emit(item.getMessages(), MessagePhase.QUAFF, context));
        messages.addAll(effectSink.collected());
        return new GameActionResult(updated, null, messages);
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
        Player updated = source.withEquipment(equipment.equip(slot, item.getId()));
        String message = "You equip " + item.getName() + " (" + slot.id() + ").";
        return new GameActionResult(updated, null, List.of(GameMessage.toSource(message)));
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
        Player deadTarget = target.die();

        List<GameMessage> messages = buildDeathMessages(attacker, deadTarget);

        if (room != null) {
            roomService.spawnCorpse(deadTarget.getUsername(), room.getId());
        }
        roomService.clearPlayerLocation(deadTarget.getUsername());

        return new GameActionResult(null, deadTarget, messages);
    }

    private List<GameMessage> buildDeathMessages(Player attacker, Player deadTarget) {
        List<GameMessage> messages = new ArrayList<>();
        String targetName = deadTarget.getUsername().getValue();

        messages.add(GameMessage.toPlayer(deadTarget.getUsername(), "You have died."));

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
            : targetName + " has been slain by " + attacker.getUsername().getValue() + ".";
        messages.add(GameMessage.toRoom(
            attacker.getUsername(), deadTarget.getUsername(),
            roomMessage));

        return messages;
    }

    private Item findInventoryItem(Player player, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : player.getInventory()) {
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

        public List<GameMessage> collected() {
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

        public List<GameMessage> collected() {
            return List.copyOf(messages);
        }
    }
}
