package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
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
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatResult;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
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
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0);
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
            new TestCooldowns()
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
                    new AttackDefinition(CombatSettings.defaultAttackId(), "punch", 2, 4, 0, 0, 0)
                )),
                new CombatModifierResolver(new StubEffectRepository(Map.of())),
                new FixedCombatRandom(10, 3, 100)
            ),
            roomService,
            (source, input) -> input.equalsIgnoreCase("target") ? Optional.of(target) : Optional.empty(),
            new TestCooldowns()
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
        Item torch = new Item(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty(), List.of(), 5);
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
        Item torch = new Item(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty(), List.of(), 5);
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
            new TestCooldowns()
        );
        Item potion = new Item(
            ItemId.of("potion"),
            "Potion",
            "A small potion.",
            ItemAttributes.empty(),
            List.of(new io.taanielo.jmud.core.world.ItemEffect(effectId, 5)),
            1
        );
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
        Item rock = new Item(ItemId.of("rock"), "Rock", "A plain rock.", ItemAttributes.empty(), List.of(), 1);
        Player withItem = attacker.addItem(rock);

        GameActionResult result = service.quaffItem(withItem, "rock");
        assertEquals("Nothing happens.", result.messages().getFirst().text());
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

    // ── Helpers ──────────────────────────────────────────────────────────

    private Player player(String username) {
        return Player.of(User.of(Username.of(username), Password.hash("pw", 1000)), "prompt", false);
    }

    private AbilityRegistry testAbilityRegistry() {
        Ability bash = new AbilityDefinition(
            AbilityId.of("skill.bash"), "bash", AbilityType.SKILL, 1,
            new AbilityCost(0, 3), new AbilityCooldown(3),
            AbilityTargeting.HARMFUL, List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 4, null)),
            null
        );
        return new AbilityRegistry(List.of(bash));
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

    private static class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws AttackRepositoryException {
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
