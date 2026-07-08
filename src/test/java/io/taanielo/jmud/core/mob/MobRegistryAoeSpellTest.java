package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for area-of-effect spell resolution ({@code AoE} targeting) in
 * {@link MobRegistry#processPlayerAoeSpell}: every hostile mob in the caster's room is struck, the
 * mana cost scales with the number of targets, and kills award normal rewards — all without
 * networking (AGENTS.md §10).
 */
class MobRegistryAoeSpellTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId UNARMED = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition UNARMED_MELEE =
        new AttackDefinition(UNARMED, "punch", 1, 1, 0, 0, 0, List.of());

    /** chain-lightning: base 4 mana + 2 mana per target, 5 damage per target. */
    private static final Ability CHAIN_LIGHTNING = new AbilityDefinition(
        AbilityId.of("spell.chain-lightning"),
        "chain-lightning",
        AbilityType.SPELL,
        1,
        new AbilityCost(4, 0, 2),
        new AbilityCooldown(4),
        AbilityTargeting.AoE,
        List.of(),
        List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 5, null)),
        List.of()
    );

    private Player mage(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistry(Player caster, int mobHp, int mobCount, RoomId mobRoom) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            mobHp,
            UNARMED,
            null,
            false,
            List.of(),
            mobRoom,
            mobCount,
            10,
            5,
            null,
            null,
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(UNARMED, UNARMED_MELEE));
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
        roomService.ensurePlayerLocation(caster.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(caster);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    private static boolean containsText(GameActionResult result, String fragment) {
        return result.messages().stream().anyMatch(m -> m.text().contains(fragment));
    }

    private static long damageLineCount(GameActionResult result) {
        return result.messages().stream()
            .filter(m -> m.text().contains("Your chain-lightning strikes the Goblin"))
            .count();
    }

    @Test
    void aoeSpell_damagesEveryMobInRoom_andScalesManaCost() {
        Player caster = mage("merlin");
        MobRegistry registry = buildRegistry(caster, 100, 3, ROOM_A);

        GameActionResult result = registry.processPlayerAoeSpell(caster, CHAIN_LIGHTNING, ROOM_A);

        assertEquals(3, damageLineCount(result), "Each of the three goblins should be struck");
        assertTrue(containsText(result, "striking 3 enemies"),
            "The caster should see a roll-up message naming the number of enemies hit");
        for (MobInstance mob : registry.getMobsInRoom(ROOM_A)) {
            assertEquals(95, mob.currentHp(), "Every mob should take the spell's 5 damage");
        }
        assertNotNull(result.updatedSource(), "A successful cast returns the mana-deducted caster");
        assertEquals(10, result.updatedSource().getVitals().getMana(),
            "Mana cost should scale: base 4 + 2 per target * 3 targets = 10, from 20");
        assertTrue(registry.isInCombat(caster.getUsername()),
            "Surviving struck mobs should engage the caster");
    }

    @Test
    void aoeSpell_noEnemiesInRoom_returnsErrorAndSpendsNoMana() {
        Player caster = mage("merlin");
        // Mobs spawn in ROOM_B; the caster casts in the (empty) ROOM_A.
        MobRegistry registry = buildRegistry(caster, 100, 3, ROOM_B);

        GameActionResult result = registry.processPlayerAoeSpell(caster, CHAIN_LIGHTNING, ROOM_A);

        assertTrue(containsText(result, "no enemies here"),
            "Casting into an empty room should fail gracefully");
        assertNull(result.updatedSource(), "A rejected cast must not deduct mana");
    }

    @Test
    void aoeSpell_insufficientMana_returnsErrorAndDealsNoDamage() {
        Player caster = mage("merlin")
            .withVitals(mage("merlin").getVitals().consumeMana(16)); // leaves 4 mana, cost is 10
        MobRegistry registry = buildRegistry(caster, 100, 3, ROOM_A);

        GameActionResult result = registry.processPlayerAoeSpell(caster, CHAIN_LIGHTNING, ROOM_A);

        assertTrue(containsText(result, "lack the mana"),
            "A caster who cannot pay the scaled cost is rejected");
        assertNull(result.updatedSource(), "A rejected cast must not deduct mana");
        for (MobInstance mob : registry.getMobsInRoom(ROOM_A)) {
            assertEquals(100, mob.currentHp(), "No mob should be damaged when the cast is refused");
        }
    }

    @Test
    void aoeSpell_slaysWeakMobs_awardsKillRewardsAndKeepsManaDeduction() {
        Player caster = mage("merlin");
        MobRegistry registry = buildRegistry(caster, 3, 2, ROOM_A); // 3 HP < 5 damage → all die

        GameActionResult result = registry.processPlayerAoeSpell(caster, CHAIN_LIGHTNING, ROOM_A);

        assertTrue(containsText(result, "You slay the Goblin"),
            "Mobs dropped to zero HP by the AoE spell should be slain");
        assertTrue(containsText(result, "experience points"),
            "Slaying mobs should award experience");
        assertNotNull(result.updatedSource(), "The caster carries mana deduction and rewards");
        assertEquals(12, result.updatedSource().getVitals().getMana(),
            "Two targets: base 4 + 2 * 2 = 8 mana spent, from 20");
        assertTrue(result.updatedSource().getTotalKills() >= 2,
            "Both slain mobs should count toward the kill total");
        assertFalse(registry.isInCombat(caster.getUsername()),
            "All targets slain — the caster is left in no combat");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks)
        implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        @Override
        public void save(Item item) throws RepositoryException {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.empty();
        }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        StubPlayerRepository(Player initial) {
            store.put(initial.getUsername(), initial);
        }

        @Override
        public void savePlayer(Player player) {
            store.put(player.getUsername(), player);
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms = Map.of(
            ROOM_A, new Room(
                ROOM_A, "Room A", "A clearing.", Map.of(Direction.NORTH, ROOM_B), List.of(), List.of()),
            ROOM_B, new Room(
                ROOM_B, "Room B", "A thicket.", Map.of(Direction.SOUTH, ROOM_A), List.of(), List.of())
        );

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
