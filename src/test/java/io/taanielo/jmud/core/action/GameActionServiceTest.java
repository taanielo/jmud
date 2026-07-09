package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityStat;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.tick.system.CooldownSystem;
import io.taanielo.jmud.core.world.ContainerLockingService;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class GameActionServiceTest {

    private static final RoomId ROOM_A = RoomId.of("a");

    private RoomService roomService;
    private GameActionService service;
    private Player attacker;
    private Player target;

    @BeforeEach
    void setUp() {
        Room room = new Room(
            ROOM_A, "Room A", "A quiet room.",
            Map.of(), List.of(), List.of()
        );
        roomService = new RoomService(new TestRoomRepository(Map.of(ROOM_A, room)), ROOM_A);

        AttackId defaultAttack = CombatSettings.defaultAttackId();
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of());
        CombatEngine combatEngine = new CombatEngine(
            new StubAttackRepository(Map.of(defaultAttack, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 3, 100)
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of()));
        AbilityRegistry abilityRegistry = testAbilityRegistry();
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();

        attacker = player("attacker");
        target = player("target");

        AbilityTargetResolver targetResolver = (source, input) -> {
            if (input.equalsIgnoreCase("target")) {
                return Optional.of(target);
            }
            if (input.equalsIgnoreCase("attacker")) {
                return Optional.of(attacker);
            }
            return Optional.empty();
        };

        roomService.ensurePlayerLocation(attacker.getUsername());
        roomService.ensurePlayerLocation(target.getUsername());

        service = new GameActionService(
            abilityRegistry, costResolver, effectEngine,
            combatEngine, roomService, targetResolver,
            new TestCooldowns(),
            testEncumbranceService()
        );
    }

    @Test
    void attackReturnsErrorOnEmptyInput() {
        GameActionResult result = service.attack(attacker, "");

        assertEquals(1, result.messages().size());
        assertEquals("Usage: attack <target>", result.messages().getFirst().text());
    }

    @Test
    void attackReturnsErrorWhenTargetNotFound() {
        GameActionResult result = service.attack(attacker, "nobody");

        assertEquals(1, result.messages().size());
        assertEquals("No such target to attack.", result.messages().getFirst().text());
    }

    @Test
    void attackReturnsErrorOnSelfTarget() {
        GameActionResult result = service.attack(attacker, "attacker");

        assertEquals(1, result.messages().size());
        assertEquals("You cannot attack yourself.", result.messages().getFirst().text());
    }

    @Test
    void attackResolvesHitAndReturnsMessages() {
        GameActionResult result = service.attack(attacker, "target");

        assertTrue(result.updatedTarget() != null);
        assertTrue(result.messages().stream().anyMatch(m -> m.type() == GameMessage.Type.SOURCE));
        assertEquals(17, result.updatedTarget().getVitals().hp());
    }

    @Test
    void attackMarksTargetDeadWhenHpReachesZero() {
        PlayerVitals lowVitals = new PlayerVitals(2, 20, 20, 20, 20, 20);
        target = new Player(
            User.of(Username.of("target"), Password.hash("pw", 1000)),
            1, 0, lowVitals, List.of(), "prompt", false,
            List.of(), null, null
        );

        service = new GameActionService(
            testAbilityRegistry(), new BasicAbilityCostResolver(),
            new EffectEngine(new StubEffectRepository(Map.of())),
            new CombatEngine(
                new StubAttackRepository(Map.of(
                    CombatSettings.defaultAttackId(),
                    new AttackDefinition(CombatSettings.defaultAttackId(), "punch", 2, 4, 0, 0, 0, List.of())
                )),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(10, 3, 100)
            ),
            roomService,
            (source, input) -> input.equalsIgnoreCase("target") ? Optional.of(target) : Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );

        GameActionResult result = service.attack(attacker, "target");

        assertTrue(result.updatedTarget().isDead());
        assertEquals(0, result.updatedTarget().getVitals().hp());
        assertTrue(result.messages().stream().anyMatch(m -> m.type() == GameMessage.Type.SOURCE));
    }

    @Test
    void getItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.getItem(attacker, "");
        assertEquals("Get what?", result.messages().getFirst().text());
    }

    @Test
    void getItemReturnsErrorWhenItemNotInRoom() {
        GameActionResult result = service.getItem(attacker, "sword");
        assertEquals("You don't see that here.", result.messages().getFirst().text());
    }

    @Test
    void getItemPicksUpItemFromRoom() {
        Item torch = Item.builder(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
        roomService.dropItem(attacker.getUsername(), torch);

        GameActionResult result = service.getItem(attacker, "torch");

        assertEquals("You pick up Torch.", result.messages().getFirst().text());
        assertTrue(result.messages().stream().anyMatch(message ->
            message.type() == GameMessage.Type.ROOM
                && message.text().equals("attacker picks up Torch.")
        ));
        assertTrue(result.updatedSource().getInventory().stream()
            .anyMatch(i -> i.getId().equals(ItemId.of("torch"))));
    }

    @Test
    void dropItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.dropItem(attacker, "");
        assertEquals("Drop what?", result.messages().getFirst().text());
    }

    @Test
    void dropItemReturnsErrorWhenNotCarrying() {
        GameActionResult result = service.dropItem(attacker, "torch");
        assertEquals("You aren't carrying that.", result.messages().getFirst().text());
    }

    @Test
    void dropItemRemovesItemFromInventory() {
        Item torch = Item.builder(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
        Player withItem = attacker.addItem(torch);

        GameActionResult result = service.dropItem(withItem, "torch");

        assertEquals("You drop Torch.", result.messages().getFirst().text());
        assertTrue(result.messages().stream().anyMatch(message ->
            message.type() == GameMessage.Type.ROOM
                && message.text().equals("attacker drops Torch.")
        ));
        assertTrue(result.updatedSource().getInventory().isEmpty());
    }

    @Test
    void giveItemReturnsErrorWhenTargetIsNull() {
        GameActionResult result = service.giveItem(attacker, null, "torch");
        assertEquals("That player is not here.", result.messages().getFirst().text());
    }

    @Test
    void giveItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.giveItem(attacker, target, "");
        assertEquals("Give what?", result.messages().getFirst().text());
    }

    @Test
    void giveItemReturnsErrorWhenNotCarrying() {
        GameActionResult result = service.giveItem(attacker, target, "torch");
        assertEquals("You aren't carrying that.", result.messages().getFirst().text());
    }

    @Test
    void giveItemReturnsErrorOnSelfTarget() {
        Item torch = torchItem();
        Player withItem = attacker.addItem(torch);

        GameActionResult result = service.giveItem(withItem, withItem, "torch");
        assertEquals("You cannot give an item to yourself.", result.messages().getFirst().text());
    }

    @Test
    void giveItemTransfersItemBetweenPlayers() {
        Item torch = torchItem();
        Player withItem = attacker.addItem(torch);

        GameActionResult result = service.giveItem(withItem, target, "torch");

        assertEquals("You give Torch to target.", result.messages().getFirst().text());
        assertTrue(result.messages().stream().anyMatch(message ->
            message.type() == GameMessage.Type.PLAYER
                && message.text().equals("attacker gives you Torch.")
        ));
        assertTrue(result.messages().stream().anyMatch(message ->
            message.type() == GameMessage.Type.ROOM
                && message.text().equals("attacker gives Torch to target.")
        ));
        assertTrue(result.updatedSource().getInventory().isEmpty());
        assertTrue(result.updatedTarget().getInventory().stream()
            .anyMatch(i -> i.getId().equals(ItemId.of("torch"))));
    }

    @Test
    void giveItemUnequipsWornItemBeforeGiving() {
        Item torch = torchItem();
        Player withItem = attacker.addItem(torch);
        Player equipped = withItem.withEquipment(
            withItem.getEquipment().equip(io.taanielo.jmud.core.world.EquipmentSlot.WEAPON, torch.getId())
        );
        assertTrue(equipped.getEquipment().isEquipped(torch.getId()));

        GameActionResult result = service.giveItem(equipped, target, "torch");

        assertTrue(result.updatedSource().getInventory().isEmpty());
        assertFalse(result.updatedSource().getEquipment().isEquipped(torch.getId()));
    }

    @Test
    void giveItemFailsWhenRecipientWouldBeOverburdened() {
        Item torch = torchItem();
        Player withItem = attacker.addItem(torch);
        service = new GameActionService(
            testAbilityRegistry(), new BasicAbilityCostResolver(),
            new EffectEngine(new StubEffectRepository(Map.of())),
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            new EncumbranceService(new StubRaceRepository(), new StubClassRepository()) {
                @Override
                public boolean isOverburdened(Player player) {
                    return player.getUsername().equals(target.getUsername());
                }
            }
        );

        GameActionResult result = service.giveItem(withItem, target, "torch");

        assertEquals("target cannot carry any more.", result.messages().getFirst().text());
        assertTrue(withItem.getInventory().stream().anyMatch(i -> i.getId().equals(ItemId.of("torch"))));
    }

    private static Item torchItem() {
        return Item.builder(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .equipSlot(io.taanielo.jmud.core.world.EquipmentSlot.WEAPON)
            .weight(1)
            .value(5)
            .build();
    }

    @Test
    void quaffItemEmitsRoomMessage() {
        EffectId effectId = EffectId.of("effect.test");
        EffectDefinition definition = new EffectDefinition(
            effectId,
            "Test Effect",
            5,
            1,
            EffectStacking.REFRESH,
            List.of(),
            null
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(effectId, definition)));
        service = new GameActionService(
            testAbilityRegistry(),
            new BasicAbilityCostResolver(),
            effectEngine,
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );
        Item potion = Item.builder(ItemId.of("potion"), "Potion", "A small potion.", ItemAttributes.empty())
            .effects(List.of(new io.taanielo.jmud.core.world.ItemEffect(effectId, 5)))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                ),
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.ROOM,
                    "{source} quaffs {item}."
                )
            ))
            .weight(1)
            .value(1)
            .build();
        Player withItem = attacker.addItem(potion);

        GameActionResult result = service.quaffItem(withItem, "potion");

        assertEquals("You quaff Potion.", result.messages().getFirst().text());
        assertTrue(result.messages().stream().anyMatch(message ->
            message.type() == GameMessage.Type.ROOM
                && message.text().equals("attacker quaffs Potion.")
        ));
    }

    @Test
    void quaffItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.quaffItem(attacker, "");
        assertEquals("Quaff what?", result.messages().getFirst().text());
    }

    @Test
    void quaffItemReturnsErrorWhenNotCarrying() {
        GameActionResult result = service.quaffItem(attacker, "potion");
        assertEquals("You aren't carrying that.", result.messages().getFirst().text());
    }

    @Test
    void quaffItemReturnsNothingHappensForEffectlessItem() {
        Item rock = Item.builder(ItemId.of("rock"), "Rock", "A plain rock.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .build();
        Player withItem = attacker.addItem(rock);

        GameActionResult result = service.quaffItem(withItem, "rock");
        assertEquals("Nothing happens.", result.messages().getFirst().text());
    }

    @Test
    void quaffCurePotionRemovesPoisonEffect() {
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poisonDefinition = new EffectDefinition(
            poisonId,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(new io.taanielo.jmud.core.messaging.MessageSpec(
                io.taanielo.jmud.core.messaging.MessagePhase.EXPIRE,
                io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                "The poison leaves your body."
            ))
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(poisonId, poisonDefinition)));
        service = new GameActionService(
            testAbilityRegistry(),
            new BasicAbilityCostResolver(),
            effectEngine,
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );
        Item curePotion = Item.builder(
            ItemId.of("cure-potion"), "Cure Potion", "A shimmering blue vial.", ItemAttributes.empty())
            .effects(List.of(new io.taanielo.jmud.core.world.ItemEffect(
                poisonId, 0, io.taanielo.jmud.core.world.ItemEffectOperation.REMOVE
            )))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                )
            ))
            .weight(1)
            .value(1)
            .build();
        Player poisoned = attacker.addItem(curePotion);
        poisoned.addEffect(new io.taanielo.jmud.core.effects.EffectInstance(poisonId, 5, 1));

        GameActionResult result = service.quaffItem(poisoned, "cure potion");

        assertTrue(result.updatedSource().effects().isEmpty());
        assertTrue(result.messages().stream().anyMatch(message ->
            message.text().equals("The poison leaves your body.")
        ));
    }

    @Test
    void quaffCurePotionOnHealthyPlayerDoesNotCrash() {
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poisonDefinition = new EffectDefinition(
            poisonId,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of()
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(poisonId, poisonDefinition)));
        service = new GameActionService(
            testAbilityRegistry(),
            new BasicAbilityCostResolver(),
            effectEngine,
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );
        Item curePotion = Item.builder(
            ItemId.of("cure-potion"), "Cure Potion", "A shimmering blue vial.", ItemAttributes.empty())
            .effects(List.of(new io.taanielo.jmud.core.world.ItemEffect(
                poisonId, 0, io.taanielo.jmud.core.world.ItemEffectOperation.REMOVE
            )))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                )
            ))
            .weight(1)
            .value(1)
            .build();
        Player healthy = attacker.addItem(curePotion);

        GameActionResult result = service.quaffItem(healthy, "cure potion");

        assertTrue(result.updatedSource().effects().isEmpty());
        assertEquals("You quaff Cure Potion.", result.messages().getFirst().text());
    }

    @Test
    void quaffItemHealsPlayerWhenHpStatIsPositive() {
        PlayerVitals lowVitals = new PlayerVitals(5, 20, 20, 20, 20, 20);
        Player injured = new Player(
            User.of(Username.of("attacker"), Password.hash("pw", 1000)),
            1, 0, lowVitals, List.of(), "prompt", false,
            List.of(), null, null
        );
        Item healthPotion = Item.builder(
            ItemId.of("health-potion"), "Health Potion", "A small red vial.", new ItemAttributes(Map.of("hp", 20)))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                )
            ))
            .weight(1)
            .value(15)
            .build();
        Player withPotion = injured.addItem(healthPotion);

        GameActionResult result = service.quaffItem(withPotion, "health-potion");

        assertEquals(20, result.updatedSource().getVitals().hp());
        assertTrue(result.updatedSource().getInventory().isEmpty());
    }

    @Test
    void quaffItemHpHealingIsCappedAtMaxHp() {
        // Player is already at full HP (20/20); heal(20) should stay at 20
        Item healthPotion = Item.builder(
            ItemId.of("health-potion"), "Health Potion", "A small red vial.", new ItemAttributes(Map.of("hp", 20)))
            .weight(1)
            .value(15)
            .build();
        Player withPotion = attacker.addItem(healthPotion);

        GameActionResult result = service.quaffItem(withPotion, "health-potion");

        assertEquals(20, result.updatedSource().getVitals().hp());
    }

    @Test
    void quaffItemDamagesPlayerWhenHpStatIsNegative() {
        Item poisonPotion = Item.builder(
            ItemId.of("poisonous-potion"), "Poisonous Potion", "A cloudy green vial.",
            new ItemAttributes(Map.of("hp", -10)))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                )
            ))
            .weight(1)
            .value(5)
            .build();
        Player withPotion = attacker.addItem(poisonPotion);

        GameActionResult result = service.quaffItem(withPotion, "poisonous-potion");

        assertEquals(10, result.updatedSource().getVitals().hp());
        assertTrue(result.updatedSource().getInventory().isEmpty());
    }

    @Test
    void quaffManaPotionRestoresManaFromPartialToMax() {
        PlayerVitals lowMana = new PlayerVitals(20, 20, 5, 20, 20, 20);
        Player withLowMana = new Player(
            User.of(Username.of("attacker"), Password.hash("pw", 1000)),
            1, 0, lowMana, List.of(), "prompt", false,
            List.of(), null, null
        );
        Item manaPotion = Item.builder(
            ItemId.of("mana-potion"), "Mana Potion", "A small blue vial.", new ItemAttributes(Map.of("mana", 20)))
            .messages(List.of(
                new io.taanielo.jmud.core.messaging.MessageSpec(
                    io.taanielo.jmud.core.messaging.MessagePhase.QUAFF,
                    io.taanielo.jmud.core.messaging.MessageChannel.SELF,
                    "You quaff {item}."
                )
            ))
            .weight(1)
            .value(15)
            .build();
        Player withPotion = withLowMana.addItem(manaPotion);

        GameActionResult result = service.quaffItem(withPotion, "mana-potion");

        assertEquals(20, result.updatedSource().getVitals().mana());
        assertTrue(result.updatedSource().getInventory().isEmpty());
    }

    @Test
    void quaffManaPotionIsCappedAtMaxMana() {
        // Player is already at full mana (20/20); restoreMana(20) should stay at 20
        Item manaPotion = Item.builder(
            ItemId.of("mana-potion"), "Mana Potion", "A small blue vial.", new ItemAttributes(Map.of("mana", 20)))
            .weight(1)
            .value(15)
            .build();
        Player withPotion = attacker.addItem(manaPotion);

        GameActionResult result = service.quaffItem(withPotion, "mana-potion");

        assertEquals(20, result.updatedSource().getVitals().mana());
        assertTrue(result.updatedSource().getInventory().isEmpty());
    }

    @Test
    void quaffItemNothingHappensGuardRequiresBothHpAndManaToBeZero() {
        // Item with neither hp nor mana stat and no effects should still return "Nothing happens."
        Item rock = Item.builder(ItemId.of("rock"), "Rock", "A plain rock.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .build();
        Player withItem = attacker.addItem(rock);

        GameActionResult result = service.quaffItem(withItem, "rock");
        assertEquals("Nothing happens.", result.messages().getFirst().text());
    }

    @Test
    void readItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.readItem(attacker, "");
        assertEquals("Read what?", result.messages().getFirst().text());
    }

    @Test
    void readItemReturnsErrorWhenNotCarrying() {
        GameActionResult result = service.readItem(attacker, "scroll");
        assertEquals("You aren't carrying that.", result.messages().getFirst().text());
    }

    @Test
    void readItemReturnsErrorWhenItemTeachesNoAbility() {
        Item plainScroll = Item.builder(
            ItemId.of("blank-scroll"), "Blank Scroll", "An unwritten scroll.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .build();
        Player withItem = attacker.addItem(plainScroll);

        GameActionResult result = service.readItem(withItem, "blank-scroll");
        assertEquals("There is nothing to learn from that.", result.messages().getFirst().text());
    }

    @Test
    void readItemLearnsAbilityAndConsumesScroll() {
        AbilityRegistry registry = testAbilityRegistryWithHeal();
        service = new GameActionService(
            registry, new BasicAbilityCostResolver(),
            new EffectEngine(new StubEffectRepository(Map.of())),
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );
        AbilityId healId = AbilityId.of("spell.heal");
        Item scroll = Item.builder(
            ItemId.of("scroll-of-healing"), "Scroll of Healing", "A scroll teaching healing.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .teachesAbilityRef(healId)
            .build();
        Player withItem = attacker.addItem(scroll);

        GameActionResult result = service.readItem(withItem, "scroll of healing");

        assertTrue(result.updatedSource().getLearnedAbilities().contains(healId));
        assertTrue(result.updatedSource().getInventory().isEmpty());
        assertEquals(
            "You read Scroll of Healing and learn heal.",
            result.messages().getFirst().text()
        );
    }

    @Test
    void readItemReturnsErrorWhenAbilityAlreadyLearned() {
        AbilityRegistry registry = testAbilityRegistryWithHeal();
        service = new GameActionService(
            registry, new BasicAbilityCostResolver(),
            new EffectEngine(new StubEffectRepository(Map.of())),
            new CombatEngine(
                new StubAttackRepository(Map.of()),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(1)
            ),
            roomService,
            (source, input) -> Optional.empty(),
            new TestCooldowns(),
            testEncumbranceService()
        );
        AbilityId healId = AbilityId.of("spell.heal");
        Item scroll = Item.builder(
            ItemId.of("scroll-of-healing"), "Scroll of Healing", "A scroll teaching healing.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .teachesAbilityRef(healId)
            .build();
        Player alreadyKnows = attacker.withLearnedAbilities(List.of(healId)).addItem(scroll);

        GameActionResult result = service.readItem(alreadyKnows, "scroll of healing");
        assertEquals("You already know that ability.", result.messages().getFirst().text());
    }

    @Test
    void writeItemReturnsErrorOnEmptyInput() {
        GameActionResult result = service.writeItem(attacker, "");
        assertEquals("Write what?", result.messages().getFirst().text());
    }

    @Test
    void writeItemReturnsErrorWhenAbilityUnknown() {
        GameActionResult result = service.writeItem(attacker, "bash");
        assertEquals("You don't know that ability.", result.messages().getFirst().text());
    }

    @Test
    void writeItemCreatesScrollForKnownAbility() {
        Player withAbility = attacker.withLearnedAbilities(List.of(AbilityId.of("skill.bash")));

        GameActionResult result = service.writeItem(withAbility, "bash");

        assertEquals(1, result.updatedSource().getInventory().size());
        Item scroll = result.updatedSource().getInventory().getFirst();
        assertEquals(AbilityId.of("skill.bash"), scroll.getTeachesAbilityRef());
        assertTrue(result.messages().getFirst().text().startsWith("You write"));
    }

    @Test
    void resolveDeathIfNeededDoesNothingWhenAlive() {
        GameActionResult result = service.resolveDeathIfNeeded(target, attacker);

        assertEquals(target, result.updatedTarget());
        assertTrue(result.messages().isEmpty());
    }

    @Test
    void resolveDeathIfNeededHandlesAutoDeadPlayer() {
        // Player constructor auto-marks dead when hp <= 0
        PlayerVitals zeroHp = new PlayerVitals(0, 20, 20, 20, 20, 20);
        Player alreadyDead = new Player(
            User.of(Username.of("target"), Password.hash("pw", 1000)),
            1, 0, zeroHp, List.of(), "prompt", false,
            List.of(), null, null
        );
        assertTrue(alreadyDead.isDead(), "player with hp=0 should be auto-dead via constructor");

        GameActionResult result = service.resolveDeathIfNeeded(alreadyDead, attacker);

        assertTrue(result.updatedTarget().isDead());
        assertTrue(result.messages().stream().anyMatch(message -> message.text().equals("You have died.")));
        assertTrue(roomService.findPlayerLocation(alreadyDead.getUsername()).isEmpty());
    }

    @Test
    void resolveDeathIfNeededSkipsExplicitlyDeadPlayer() {
        Player alreadyDead = target.die();
        roomService.clearPlayerLocation(alreadyDead.getUsername());

        GameActionResult result = service.resolveDeathIfNeeded(alreadyDead, attacker);

        assertTrue(result.messages().isEmpty());
    }

    // ── containers ───────────────────────────────────────────────────────

    @Nested
    class ContainerTests {

        private Item bag(int capacity) {
            return Item.builder(ItemId.of("bag"), "a bag", "A small bag.", ItemAttributes.empty())
                .weight(1)
                .value(5)
                .container(io.taanielo.jmud.core.world.ContainerState.of(capacity))
                .build();
        }

        private Item plainItem(String id, String name) {
            return Item.builder(ItemId.of(id), name, "A thing.", ItemAttributes.empty())
                .weight(1)
                .value(5)
                .build();
        }

        @Test
        void putItemPlacesItemIntoContainer() {
            Player p = attacker.addItem(bag(3)).addItem(plainItem("sword", "a sword"));

            GameActionResult result = service.putItem(p, "sword", "bag");

            assertEquals("You put a sword into a bag.", result.messages().getFirst().text());
            assertTrue(result.messages().stream().anyMatch(m ->
                m.type() == GameMessage.Type.ROOM && m.text().equals("attacker puts a sword into a bag.")));
            List<Item> inv = result.updatedSource().getInventory();
            assertEquals(1, inv.size());
            Item updatedBag = inv.getFirst();
            assertEquals(1, updatedBag.containedItemCount());
            assertEquals(ItemId.of("sword"), updatedBag.getContainedItems().getFirst().getId());
        }

        @Test
        void putItemRejectsWhenContainerFull() {
            Item full = bag(1).withContainedItem(plainItem("sword", "a sword"));
            Player p = attacker.addItem(full).addItem(plainItem("shield", "a shield"));

            GameActionResult result = service.putItem(p, "shield", "bag");

            assertEquals("a bag is full.", result.messages().getFirst().text());
            // No state change: shield still carried at top level.
            assertTrue(p.getInventory().stream().anyMatch(i -> i.getId().equals(ItemId.of("shield"))));
        }

        @Test
        void putItemRejectsNonContainer() {
            Player p = attacker.addItem(plainItem("rock", "a rock")).addItem(plainItem("sword", "a sword"));

            GameActionResult result = service.putItem(p, "sword", "rock");

            assertEquals("a rock is not a container.", result.messages().getFirst().text());
        }

        @Test
        void putItemRejectsNestingContainers() {
            Player p = attacker.addItem(bag(3)).addItem(
                Item.builder(ItemId.of("pouch"), "a pouch", "A pouch.", ItemAttributes.empty())
                    .weight(1)
                    .value(5)
                    .container(io.taanielo.jmud.core.world.ContainerState.of(2))
                    .build());

            GameActionResult result = service.putItem(p, "pouch", "bag");

            assertEquals("You can't put a container inside another container.", result.messages().getFirst().text());
        }

        @Test
        void putItemErrorWhenNotCarryingContainer() {
            Player p = attacker.addItem(plainItem("sword", "a sword"));

            GameActionResult result = service.putItem(p, "sword", "bag");

            assertEquals("You aren't carrying bag.", result.messages().getFirst().text());
        }

        @Test
        void putItemErrorWhenNotCarryingItem() {
            Player p = attacker.addItem(bag(3));

            GameActionResult result = service.putItem(p, "sword", "bag");

            assertEquals("You aren't carrying that.", result.messages().getFirst().text());
        }

        @Test
        void getFromContainerMovesItemToInventory() {
            Item filled = bag(3).withContainedItem(plainItem("sword", "a sword"));
            Player p = attacker.addItem(filled);

            GameActionResult result = service.getFromContainer(p, "sword", "bag");

            assertEquals("You get a sword from a bag.", result.messages().getFirst().text());
            List<Item> inv = result.updatedSource().getInventory();
            assertTrue(inv.stream().anyMatch(i -> i.getId().equals(ItemId.of("sword"))
                && !i.isContainer()));
            Item updatedBag = inv.stream().filter(Item::isContainer).findFirst().orElseThrow();
            assertEquals(0, updatedBag.containedItemCount());
        }

        @Test
        void getFromContainerErrorWhenItemNotInside() {
            Player p = attacker.addItem(bag(3));

            GameActionResult result = service.getFromContainer(p, "sword", "bag");

            assertEquals("There is no sword in a bag.", result.messages().getFirst().text());
        }

        @Test
        void removingItemFromContainerFreesSlot() {
            Item full = bag(1).withContainedItem(plainItem("sword", "a sword"));
            Player p = attacker.addItem(full).addItem(plainItem("shield", "a shield"));

            // Full container rejects the shield.
            GameActionResult rejected = service.putItem(p, "shield", "bag");
            assertEquals("a bag is full.", rejected.messages().getFirst().text());

            // Take the sword out, which frees the single slot.
            GameActionResult removed = service.getFromContainer(p, "sword", "bag");
            Player afterRemoval = removed.updatedSource();

            // Now the shield fits.
            GameActionResult added = service.putItem(afterRemoval, "shield", "bag");
            Item updatedBag = added.updatedSource().getInventory().stream()
                .filter(Item::isContainer).findFirst().orElseThrow();
            assertEquals(1, updatedBag.containedItemCount());
            assertEquals(ItemId.of("shield"), updatedBag.getContainedItems().getFirst().getId());
        }
    }

    // ── recall ───────────────────────────────────────────────────────────

    @Nested
    class RecallTests {

        private static final RoomId ROOM_TOWN = RoomId.of("town");
        private static final RoomId ROOM_DUNGEON = RoomId.of("dungeon");

        private RoomService recallRoomService;
        private CooldownSystem cooldownSystem;
        private boolean inCombat;
        private GameActionService recallService;
        private Player player;

        @BeforeEach
        void setUpRecall() {
            Room town = new Room(
                ROOM_TOWN, "Town Square", "The town square.",
                Map.of(Direction.NORTH, ROOM_DUNGEON), List.of(), List.of()
            );
            Room dungeon = new Room(
                ROOM_DUNGEON, "Dungeon", "A dark dungeon.",
                Map.of(Direction.SOUTH, ROOM_TOWN), List.of(), List.of()
            );
            recallRoomService = new RoomService(
                new TestRoomRepository(Map.of(ROOM_TOWN, town, ROOM_DUNGEON, dungeon)), ROOM_TOWN
            );
            cooldownSystem = new CooldownSystem();
            inCombat = false;

            player = player("wanderer");
            recallRoomService.ensurePlayerLocation(player.getUsername());
            recallRoomService.move(player.getUsername(), Direction.NORTH);
            assertEquals(ROOM_DUNGEON, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());

            recallService = new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                recallRoomService,
                (source, input) -> Optional.empty(),
                new CooldownTracker(cooldownSystem),
                testEncumbranceService(),
                p -> inCombat
            );
        }

        @Test
        void successfulRecallTeleportsPlayerToStartingRoom() {
            GameActionResult result = recallService.recall(player);

            assertEquals(ROOM_TOWN, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertTrue(result.metadata().containsKey("recalled"));
            assertTrue(result.messages().stream().anyMatch(m -> m.type() == GameMessage.Type.SOURCE));
            long roomAtMessages = result.messages().stream()
                .filter(m -> m.type() == GameMessage.Type.ROOM_AT)
                .count();
            assertEquals(2, roomAtMessages, "Expected a departure message to the dungeon and an arrival message to town");
        }

        @Test
        void recallIsBlockedWhileInCombat() {
            inCombat = true;

            GameActionResult result = recallService.recall(player);

            assertEquals(ROOM_DUNGEON, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertEquals(1, result.messages().size());
            assertTrue(result.messages().getFirst().text().contains("FLEE"));
            assertFalse(result.metadata().containsKey("recalled"));
        }

        @Test
        void recallIsBlockedOnCooldown() {
            recallService.recall(player);
            recallRoomService.move(player.getUsername(), Direction.NORTH);

            GameActionResult result = recallService.recall(player);

            assertEquals(ROOM_DUNGEON, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertEquals(1, result.messages().size());
            assertTrue(result.messages().getFirst().text().contains("recovering"));
            assertFalse(result.metadata().containsKey("recalled"));
        }

        @Test
        void recallSucceedsAgainAfterCooldownExpires() {
            recallService.recall(player);
            recallRoomService.move(player.getUsername(), Direction.NORTH);

            for (int i = 0; i < 30; i++) {
                cooldownSystem.tick();
            }

            GameActionResult result = recallService.recall(player);

            assertEquals(ROOM_TOWN, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertTrue(result.metadata().containsKey("recalled"));
        }

        private Item recallScroll() {
            return Item.builder(
                ItemId.of("scroll-of-recall"), "Scroll of Recall", "A worn parchment.", ItemAttributes.empty())
                .weight(1)
                .value(50)
                .messages(List.of(
                    new MessageSpec(MessagePhase.READ, MessageChannel.SELF, "You read {item}."),
                    new MessageSpec(MessagePhase.READ, MessageChannel.ROOM, "{source} reads {item}.")
                ))
                .build();
        }

        @Test
        void readingRecallScrollTeleportsToTownAndConsumesScroll() {
            Player withScroll = player.addItem(recallScroll());

            GameActionResult result = recallService.readItem(withScroll, "scroll of recall");

            assertEquals(ROOM_TOWN, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertTrue(result.metadata().containsKey("recalled"));
            assertNotNull(result.updatedSource());
            assertTrue(result.updatedSource().getInventory().isEmpty(), "Scroll should be consumed on success");
            assertTrue(
                result.messages().stream().anyMatch(m -> m.text().contains("You read Scroll of Recall")),
                "Expected the scroll's read flavor message"
            );
            long roomAtMessages = result.messages().stream()
                .filter(m -> m.type() == GameMessage.Type.ROOM_AT)
                .count();
            assertEquals(2, roomAtMessages, "Expected departure and arrival room messages from recall");
        }

        @Test
        void readingRecallScrollBlockedInCombatKeepsScroll() {
            inCombat = true;
            Player withScroll = player.addItem(recallScroll());

            GameActionResult result = recallService.readItem(withScroll, "scroll of recall");

            assertEquals(ROOM_DUNGEON, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertFalse(result.metadata().containsKey("recalled"));
            assertNull(result.updatedSource(), "Scroll must not be consumed when recall is blocked");
            assertTrue(result.messages().getFirst().text().contains("FLEE"));
        }

        @Test
        void readingRecallScrollBlockedOnCooldownKeepsScroll() {
            recallService.recall(player);
            recallRoomService.move(player.getUsername(), Direction.NORTH);
            Player withScroll = player.addItem(recallScroll());

            GameActionResult result = recallService.readItem(withScroll, "scroll of recall");

            assertEquals(ROOM_DUNGEON, recallRoomService.findPlayerLocation(player.getUsername()).orElseThrow());
            assertFalse(result.metadata().containsKey("recalled"));
            assertNull(result.updatedSource(), "Scroll must not be consumed when recall is on cooldown");
            assertTrue(result.messages().getFirst().text().contains("recovering"));
        }
    }

    // ── pick lock ──────────────────────────────────────────────────────────

    @Nested
    class PickLockTests {

        private static final RoomId ROOM_VAULT = RoomId.of("vault");
        private static final ItemId CHEST = ItemId.of("chest");

        private RoomService pickRoomService;
        private Player rogue;

        @BeforeEach
        void setUpPick() {
            Item chest = Item.builder(CHEST, "a treasure chest", "An iron-banded chest.", ItemAttributes.empty())
                .weight(10)
                .value(100)
                .container(io.taanielo.jmud.core.world.ContainerState.of(3))
                .build()
                .withLocked(true);
            Room vault = new Room(
                ROOM_VAULT, "Vault", "A stone vault.",
                Map.of(), List.of(chest), List.of()
            );
            pickRoomService = new RoomService(
                new TestRoomRepository(Map.of(ROOM_VAULT, vault)), ROOM_VAULT
            );
            rogue = rogueOfLevel(5);
            pickRoomService.ensurePlayerLocation(rogue.getUsername());
        }

        private Player rogueOfLevel(int level) {
            return new Player(
                User.of(Username.of("sly"), Password.hash("pw", 1000)),
                level, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("rogue")
            );
        }

        private GameActionService pickService(int... rolls) {
            return new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                pickRoomService,
                (source, input) -> Optional.empty(),
                new TestCooldowns(),
                testEncumbranceService(),
                new ContainerLockingService(new FixedCombatRandom(rolls))
            );
        }

        @Test
        void successfulPickUnlocksContainerWithoutDamage() {
            // Level 5 => 80% threshold. Pick roll 1 succeeds; trap roll 100 does not trigger.
            GameActionService service = pickService(1, 100);

            GameActionResult result = service.pickLock(rogue, "chest");

            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("pick the lock")));
            assertEquals(rogue.getVitals().hp(), result.updatedSource().getVitals().hp());
            assertFalse(pickRoomService.findItem(rogue.getUsername(), "chest").orElseThrow().isLocked());
        }

        @Test
        void sprungTrapDamagesRogueAndLeavesChestLocked() {
            // Pick roll 100 fails; trap roll 1 triggers; damage roll 10.
            GameActionService service = pickService(100, 1, 10);

            GameActionResult result = service.pickLock(rogue, "chest");

            assertEquals(rogue.getVitals().hp() - 10, result.updatedSource().getVitals().hp());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("trap")));
            assertTrue(pickRoomService.findItem(rogue.getUsername(), "chest").orElseThrow().isLocked());
        }

        @Test
        void failedPickWithoutTrapWastesActionWithoutDamage() {
            // Pick roll 100 fails; trap roll 100 does not trigger.
            GameActionService service = pickService(100, 100);

            GameActionResult result = service.pickLock(rogue, "chest");

            assertEquals(rogue.getVitals().hp(), result.updatedSource().getVitals().hp());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("fail")));
            assertTrue(pickRoomService.findItem(rogue.getUsername(), "chest").orElseThrow().isLocked());
        }

        @Test
        void nonRogueCannotPick() {
            Player fighter = new Player(
                User.of(Username.of("brute"), Password.hash("pw", 1000)),
                5, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("fighter")
            );
            pickRoomService.ensurePlayerLocation(fighter.getUsername());
            GameActionService service = pickService(1, 100);

            GameActionResult result = service.pickLock(fighter, "chest");

            assertTrue(result.messages().getFirst().text().contains("rogues"));
            assertTrue(pickRoomService.findItem(fighter.getUsername(), "chest").orElseThrow().isLocked());
        }

        @Test
        void pickingAlreadyUnlockedContainerFails() {
            GameActionService service = pickService(1, 100);
            service.pickLock(rogue, "chest");

            GameActionResult result = service.pickLock(rogue, "chest");

            assertTrue(result.messages().getFirst().text().contains("isn't locked"));
        }

        @Test
        void blankTargetIsRejected() {
            GameActionService service = pickService(1, 100);

            GameActionResult result = service.pickLock(rogue, "  ");

            assertTrue(result.messages().getFirst().text().contains("Pick what?"));
        }
    }

    // ── flee ──────────────────────────────────────────────────────────────

    @Nested
    class FleeTests {

        private static final RoomId ROOM_START = RoomId.of("flee-start");
        private static final RoomId ROOM_NORTH = RoomId.of("flee-north");
        private static final RoomId ROOM_EAST = RoomId.of("flee-east");

        private Player fighter;
        private boolean inCombat;
        private List<Username> disengaged;

        @BeforeEach
        void setUpFlee() {
            fighter = player("fighter");
            inCombat = true;
            disengaged = new ArrayList<>();
        }

        private GameActionService fleeService(CombatRandom rng) {
            return new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                roomService,
                (source, input) -> Optional.empty(),
                new TestCooldowns(),
                testEncumbranceService(),
                p -> inCombat,
                rng,
                p -> disengaged.add(p.getUsername())
            );
        }

        private Room roomWithExits(Map<Direction, RoomId> exits) {
            return new Room(ROOM_START, "Start", "A room with exits.", exits, List.of(), List.of());
        }

        @Test
        void fleeFailsWhenNotInCombat() {
            inCombat = false;
            GameActionService service = fleeService(new FixedCombatRandom(0));

            FleeResult result = service.flee(fighter, roomWithExits(Map.of(Direction.NORTH, ROOM_NORTH)));

            assertFalse(result.fled());
            assertEquals("You are not in combat.", result.message());
            assertTrue(result.direction() == null);
            assertTrue(disengaged.isEmpty(), "combat must not be disengaged when flee is rejected");
        }

        @Test
        void fleeFailsWhenRoomHasNoExits() {
            GameActionService service = fleeService(new FixedCombatRandom(0));

            FleeResult result = service.flee(fighter, roomWithExits(Map.of()));

            assertFalse(result.fled());
            assertEquals("There is nowhere to flee!", result.message());
            assertTrue(disengaged.isEmpty(), "combat must not be disengaged when there is nowhere to flee");
        }

        @Test
        void fleeFailsWhenRoomIsNull() {
            GameActionService service = fleeService(new FixedCombatRandom(0));

            FleeResult result = service.flee(fighter, null);

            assertFalse(result.fled());
            assertEquals("There is nowhere to flee!", result.message());
            assertTrue(disengaged.isEmpty());
        }

        @Test
        void fleeDisengagesCombatAndChoosesTheOnlyExit() {
            GameActionService service = fleeService(new FixedCombatRandom(0));

            FleeResult result = service.flee(fighter, roomWithExits(Map.of(Direction.NORTH, ROOM_NORTH)));

            assertTrue(result.fled());
            assertEquals(Direction.NORTH, result.direction());
            assertEquals("You flee to the " + Direction.NORTH.label() + "!", result.message());
            assertEquals(List.of(fighter.getUsername()), disengaged);
        }

        @Test
        void fleeSelectsExitByRolledIndexOverInclusiveRange() {
            RecordingCombatRandom rng = new RecordingCombatRandom(1);
            GameActionService service = fleeService(rng);
            Room room = roomWithExits(Map.of(Direction.NORTH, ROOM_NORTH, Direction.EAST, ROOM_EAST));
            // The service selects exits.get(rolledIndex) from the room's exit key set; mirror that
            // ordering here so the assertion does not depend on Map iteration order.
            Direction expected = new ArrayList<>(room.getExits().keySet()).get(1);

            FleeResult result = service.flee(fighter, room);

            assertTrue(result.fled());
            assertEquals(expected, result.direction());
            assertEquals(0, rng.lastMin());
            assertEquals(room.getExits().size() - 1, rng.lastMax(),
                "flee must roll over the inclusive [0, exitCount-1] range");
        }
    }

    // ── sneak / stealth ────────────────────────────────────────────────────

    @Nested
    class StealthTests {

        private static final AbilityId BACKSTAB = AbilityId.of("skill.backstab");

        private Player rogue;
        private Player victim;
        private GameActionService stealthService;

        @BeforeEach
        void setUpStealth() {
            rogue = new Player(
                User.of(Username.of("shadow"), Password.hash("pw", 1000)),
                5, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(BACKSTAB), null, ClassId.of("rogue")
            );
            victim = new Player(
                User.of(Username.of("victim"), Password.hash("pw", 1000)),
                3, 0L, new PlayerVitals(50, 50, 50, 50, 50, 50), List.of(), "prompt", false,
                List.of(), null, null
            );
            roomService.ensurePlayerLocation(rogue.getUsername());
            roomService.ensurePlayerLocation(victim.getUsername());

            AbilityRegistry registry = new AbilityRegistry(List.of(new AbilityDefinition(
                BACKSTAB, "backstab", AbilityType.SKILL, 1,
                new AbilityCost(0, 0), new AbilityCooldown(0),
                AbilityTargeting.HARMFUL_OPENER, List.of(),
                List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 10, null)),
                List.of()
            )));
            AbilityTargetResolver targetResolver = (source, input) ->
                input.equalsIgnoreCase("victim") ? Optional.of(victim) : Optional.empty();
            stealthService = new GameActionService(
                registry, new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                roomService, targetResolver,
                new TestCooldowns(), testEncumbranceService()
            );
        }

        @Test
        void sneakActivatesStealthForRogue() {
            GameActionResult result = stealthService.sneakToggle(rogue);

            assertTrue(result.updatedSource().isStealthActive());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("fade into the shadows")));
        }

        @Test
        void sneakTogglesStealthOffWhenAlreadyHidden() {
            GameActionResult result = stealthService.sneakToggle(rogue.withStealth(true));

            assertFalse(result.updatedSource().isStealthActive());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("emerge from stealth")));
        }

        @Test
        void nonRogueCannotSneak() {
            Player fighter = new Player(
                User.of(Username.of("brute"), Password.hash("pw", 1000)),
                5, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("fighter")
            );

            GameActionResult result = stealthService.sneakToggle(fighter);

            assertTrue(result.messages().getFirst().text().contains("rogues"));
            assertTrue(result.updatedSource() == null);
        }

        @Test
        void backstabFromStealthDealsBonusDamageAndBreaksStealth() {
            GameActionResult stealthResult = stealthService.useAbility(rogue.withStealth(true), "backstab victim");
            GameActionResult normalResult = stealthService.useAbility(rogue, "backstab victim");

            assertEquals(
                normalResult.updatedTarget().getVitals().hp() - 10,
                stealthResult.updatedTarget().getVitals().hp(),
                "A backstab from stealth must deal 10 bonus damage over a normal backstab"
            );
            assertFalse(stealthResult.updatedSource().isStealthActive(),
                "Using an ability must clear stealth");
            assertTrue(stealthResult.messages().stream().anyMatch(m -> m.text().contains("deadly precision")));
            assertTrue(stealthResult.messages().stream().anyMatch(m -> m.text().contains("emerge from the shadows")));
        }

        @Test
        void attackingBreaksStealth() {
            AttackId defaultAttack = CombatSettings.defaultAttackId();
            GameActionService attackService = new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of(
                        defaultAttack,
                        new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of())
                    )),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                roomService,
                (source, input) -> input.equalsIgnoreCase("victim") ? Optional.of(victim) : Optional.empty(),
                new TestCooldowns(), testEncumbranceService()
            );

            GameActionResult result = attackService.attack(rogue.withStealth(true), "victim");

            assertTrue(result.updatedSource() != null, "attack must return an updated source when stealth breaks");
            assertFalse(result.updatedSource().isStealthActive());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("emerge from the shadows")));
        }
    }

    // ── steal ──────────────────────────────────────────────────────────────

    @Nested
    class StealTests {

        private Player rogue;

        @BeforeEach
        void setUpSteal() {
            rogue = rogueOfLevel(5);
            roomService.ensurePlayerLocation(rogue.getUsername());
        }

        private Player rogueOfLevel(int level) {
            return new Player(
                User.of(Username.of("nimble"), Password.hash("pw", 1000)),
                level, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("rogue")
            );
        }

        private GameActionService stealService(CombatRandom rng, NpcStealPort port) {
            return new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                roomService,
                (source, input) -> Optional.empty(),
                new TestCooldowns(),
                testEncumbranceService(),
                p -> false,
                rng,
                p -> { },
                port
            );
        }

        @Test
        void successfulStealTransfersGoldToRogue() {
            // Level 5 => 45 + 3*5 = 60% threshold. Roll 1 succeeds.
            FakeVictim bandit = new FakeVictim("bandit", 25);
            GameActionService service = stealService(new FixedCombatRandom(1), portFor(bandit));

            GameActionResult result = service.steal(rogue, "bandit");

            assertEquals(25, result.updatedSource().getGold());
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("lift 25 gold coins")));
            assertFalse(bandit.turnedHostile, "a successful steal must not aggravate the victim");
        }

        @Test
        void failedStealTurnsNpcHostileWithoutTransferringGold() {
            // Roll 100 fails the 60% threshold.
            FakeVictim bandit = new FakeVictim("bandit", 25);
            GameActionService service = stealService(new FixedCombatRandom(100), portFor(bandit));

            GameActionResult result = service.steal(rogue, "bandit");

            assertTrue(result.updatedSource() == null, "a failed steal must not change the rogue's gold");
            assertTrue(bandit.turnedHostile, "a failed steal must turn the victim hostile");
            assertEquals(rogue.getUsername(), bandit.hostileToward);
            assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("caught")));
        }

        @Test
        void stealSuccessRollUsesSeededPortOverOneToHundred() {
            // Level 5 threshold is 60; a roll of exactly 60 must succeed (roll <= threshold).
            FakeVictim bandit = new FakeVictim("bandit", 7);
            RecordingCombatRandom rng = new RecordingCombatRandom(60);
            GameActionService service = stealService(rng, portFor(bandit));

            GameActionResult result = service.steal(rogue, "bandit");

            assertEquals(1, rng.lastMin());
            assertEquals(100, rng.lastMax());
            assertEquals(7, result.updatedSource().getGold());
        }

        @Test
        void nonRogueCannotSteal() {
            Player fighter = new Player(
                User.of(Username.of("brute"), Password.hash("pw", 1000)),
                5, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("fighter")
            );
            roomService.ensurePlayerLocation(fighter.getUsername());
            GameActionService service = stealService(new FixedCombatRandom(1), portFor(new FakeVictim("bandit", 25)));

            GameActionResult result = service.steal(fighter, "bandit");

            assertTrue(result.messages().getFirst().text().contains("rogues"));
            assertTrue(result.updatedSource() == null);
        }

        @Test
        void deadRogueCannotSteal() {
            GameActionService service = stealService(new FixedCombatRandom(1), portFor(new FakeVictim("bandit", 25)));

            GameActionResult result = service.steal(rogue.die(), "bandit");

            assertTrue(result.messages().getFirst().text().contains("cannot do that"));
            assertTrue(result.updatedSource() == null);
        }

        @Test
        void stealingFromMissingNpcFails() {
            GameActionService service = stealService(new FixedCombatRandom(1), NpcStealPort.NONE);

            GameActionResult result = service.steal(rogue, "ghost");

            assertTrue(result.messages().getFirst().text().contains("don't see"));
        }

        @Test
        void stealingFromNpcWithNoGoldFails() {
            FakeVictim pauper = new FakeVictim("pauper", 0);
            GameActionService service = stealService(new FixedCombatRandom(1), portFor(pauper));

            GameActionResult result = service.steal(rogue, "pauper");

            assertTrue(result.messages().getFirst().text().contains("nothing worth stealing"));
            assertFalse(pauper.turnedHostile);
        }

        @Test
        void blankTargetIsRejected() {
            GameActionService service = stealService(new FixedCombatRandom(1), NpcStealPort.NONE);

            GameActionResult result = service.steal(rogue, "  ");

            assertTrue(result.messages().getFirst().text().contains("Steal from whom?"));
        }

        private NpcStealPort portFor(NpcStealPort.StealVictim victim) {
            return (roomId, nameInput) -> Optional.of(victim);
        }
    }

    // ── track ────────────────────────────────────────────────────────────────

    @Nested
    class TrackTests {

        private static final RoomId TOWN = RoomId.of("town");
        private static final RoomId FOREST = RoomId.of("forest");
        private static final RoomId DEEP = RoomId.of("deep");

        private RoomService trackRoomService;
        private Player ranger;

        @BeforeEach
        void setUpTrack() {
            // town --north--> forest --north--> deep
            Room town = new Room(
                TOWN, "Town", "A town.",
                Map.of(Direction.NORTH, FOREST), List.of(), List.of());
            Room forest = new Room(
                FOREST, "Forest", "A forest.",
                Map.of(Direction.SOUTH, TOWN, Direction.NORTH, DEEP), List.of(), List.of());
            Room deep = new Room(
                DEEP, "Deep Forest", "A deep forest.",
                Map.of(Direction.SOUTH, FOREST), List.of(), List.of());
            trackRoomService = new RoomService(
                new TestRoomRepository(Map.of(TOWN, town, FOREST, forest, DEEP, deep)), TOWN);
            ranger = rangerOfLevel(5);
            trackRoomService.ensurePlayerLocation(ranger.getUsername());
        }

        private Player rangerOfLevel(int level) {
            return new Player(
                User.of(Username.of("strider"), Password.hash("pw", 1000)),
                level, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("ranger"));
        }

        private GameActionService trackService(MobLocatorPort locator) {
            return new GameActionService(
                testAbilityRegistry(), new BasicAbilityCostResolver(),
                new EffectEngine(new StubEffectRepository(Map.of())),
                new CombatEngine(
                    new StubAttackRepository(Map.of()),
                    new CombatModifierResolver(new StubEffectRepository(Map.of())),
                    new FixedCombatRandom(10, 3, 100)
                ),
                trackRoomService,
                (source, input) -> Optional.empty(),
                new TestCooldowns(),
                testEncumbranceService(),
                p -> false,
                new FixedCombatRandom(1),
                p -> { },
                NpcStealPort.NONE,
                locator
            );
        }

        @Test
        void tracksNearestMobAndReportsFirstStepDirection() {
            MobLocatorPort locator = roomId -> DEEP.equals(roomId) ? List.of("Goblin") : List.of();
            GameActionService service = trackService(locator);

            GameActionResult result = service.track(ranger, "goblin");

            assertTrue(result.messages().getFirst().text().contains("to the north"),
                "TRACK must point toward the first step of the shortest path");
            assertTrue(result.messages().getFirst().text().contains("Goblin"));
        }

        @Test
        void tracksMobInSameRoom() {
            MobLocatorPort locator = roomId -> TOWN.equals(roomId) ? List.of("Wolf") : List.of();
            GameActionService service = trackService(locator);

            GameActionResult result = service.track(ranger, "wolf");

            assertTrue(result.messages().getFirst().text().contains("in this room"),
                "A mob in the ranger's own room must report as being present");
        }

        @Test
        void partialNameMatchesMobDisplayName() {
            MobLocatorPort locator = roomId -> FOREST.equals(roomId) ? List.of("Giant Rat") : List.of();
            GameActionService service = trackService(locator);

            GameActionResult result = service.track(ranger, "rat");

            assertTrue(result.messages().getFirst().text().contains("to the north"));
            assertTrue(result.messages().getFirst().text().contains("Giant Rat"));
        }

        @Test
        void reportsNoTraceWhenMobAbsent() {
            GameActionService service = trackService(MobLocatorPort.NONE);

            GameActionResult result = service.track(ranger, "dragon");

            assertTrue(result.messages().getFirst().text().contains("no trace"));
        }

        @Test
        void nonRangerCannotTrack() {
            Player fighter = new Player(
                User.of(Username.of("brute"), Password.hash("pw", 1000)),
                5, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
                List.of(), null, ClassId.of("fighter"));
            trackRoomService.ensurePlayerLocation(fighter.getUsername());
            GameActionService service = trackService(
                roomId -> DEEP.equals(roomId) ? List.of("Goblin") : List.of());

            GameActionResult result = service.track(fighter, "goblin");

            assertTrue(result.messages().getFirst().text().contains("rangers"));
        }

        @Test
        void blankTargetIsRejected() {
            GameActionService service = trackService(MobLocatorPort.NONE);

            GameActionResult result = service.track(ranger, "  ");

            assertTrue(result.messages().getFirst().text().contains("Track what?"));
        }

        @Test
        void deadRangerCannotTrack() {
            GameActionService service = trackService(
                roomId -> DEEP.equals(roomId) ? List.of("Goblin") : List.of());

            GameActionResult result = service.track(ranger.die(), "goblin");

            assertTrue(result.messages().getFirst().text().contains("cannot do that"));
        }
    }

    private static class FakeVictim implements NpcStealPort.StealVictim {
        private final String name;
        private final int gold;
        private boolean turnedHostile;
        private Username hostileToward;

        FakeVictim(String name, int gold) {
            this.name = name;
            this.gold = gold;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean hasStealableGold() {
            return gold > 0;
        }

        @Override
        public int stealGold() {
            return gold;
        }

        @Override
        public void turnHostile(Username thief) {
            this.turnedHostile = true;
            this.hostileToward = thief;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Player player(String username) {
        return Player.of(User.of(Username.of(username), Password.hash("pw", 1000)), "prompt", false);
    }

    private static EncumbranceService testEncumbranceService() {
        return new EncumbranceService(new StubRaceRepository(), new StubClassRepository()) {
            @Override
            public boolean isOverburdened(Player player) {
                return false;
            }
        };
    }

    private AbilityRegistry testAbilityRegistry() {
        Ability bash = new AbilityDefinition(
            AbilityId.of("skill.bash"), "bash", AbilityType.SKILL, 1,
            new AbilityCost(0, 3), new AbilityCooldown(3),
            AbilityTargeting.HARMFUL, List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 4, null)),
            List.of()
        );
        return new AbilityRegistry(List.of(bash));
    }

    private AbilityRegistry testAbilityRegistryWithHeal() {
        Ability heal = new AbilityDefinition(
            AbilityId.of("spell.heal"), "heal", AbilityType.SPELL, 1,
            new AbilityCost(4, 0), new AbilityCooldown(3),
            AbilityTargeting.BENEFICIAL, List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.INCREASE, 6, null)),
            List.of()
        );
        return new AbilityRegistry(List.of(heal));
    }

    private static class TestCooldowns implements AbilityCooldownTracker {
        private final Map<AbilityId, Integer> cooldowns = new HashMap<>();

        @Override
        public boolean isOnCooldown(AbilityId abilityId) {
            Integer remaining = cooldowns.get(abilityId);
            return remaining != null && remaining > 0;
        }

        @Override
        public int remainingTicks(AbilityId abilityId) {
            return cooldowns.getOrDefault(abilityId, 0);
        }

        @Override
        public void startCooldown(AbilityId abilityId, int ticks) {
            cooldowns.put(abilityId, ticks);
        }
    }

    private static class StubRaceRepository implements RaceRepository {
        @Override
        public Optional<Race> findById(RaceId id) throws RaceRepositoryException {
            return Optional.empty();
        }

        @Override
        public java.util.List<Race> findAll() throws RaceRepositoryException {
            return java.util.List.of();
        }
    }

    private static class StubClassRepository implements ClassRepository {
        @Override
        public Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException {
            return Optional.empty();
        }

        @Override
        public java.util.List<ClassDefinition> findAll() throws ClassRepositoryException {
            return java.util.List.of();
        }
    }

    private static class FixedCombatRandom implements CombatRandom {
        private final int[] rolls;
        private int index;

        FixedCombatRandom(int... rolls) {
            this.rolls = rolls;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (index >= rolls.length) {
                return rolls[rolls.length - 1];
            }
            return rolls[index++];
        }
    }

    private static class RecordingCombatRandom implements CombatRandom {
        private final int value;
        private int lastMin;
        private int lastMax;

        RecordingCombatRandom(int value) {
            this.value = value;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            this.lastMin = minInclusive;
            this.lastMax = maxInclusive;
            return value;
        }

        int lastMin() {
            return lastMin;
        }

        int lastMax() {
            return lastMax;
        }
    }

    private static class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static class StubEffectRepository implements EffectRepository {
        private final Map<EffectId, EffectDefinition> definitions;

        StubEffectRepository(Map<EffectId, EffectDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }

    private record TestRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        private TestRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = new ConcurrentHashMap<>(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
