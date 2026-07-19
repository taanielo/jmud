package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.effects.ControlType;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
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
 * Unit tests for player-cast crowd control landing on a mob (issue #763): the AI-gating happy paths
 * (a stunned mob skips its whole decision, a rooted mob does not flee below the flee threshold, a
 * silenced mob is restricted to its base attack), control expiry, and the
 * {@link MobRegistry#processPlayerSingleTargetAbility} wiring that reads the same authored
 * {@code control} classification the player effect path uses. All without networking (AGENTS.md §10).
 */
class MobRegistryControlTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("room.spawn");
    private static final RoomId NORTH_ROOM = RoomId.of("room.north");
    private static final AttackId BASE_ATTACK = AttackId.of("attack.base");
    private static final AttackId SPECIAL_ATTACK = AttackId.of("attack.special");
    private static final AttackDefinition BASE_MELEE =
        new AttackDefinition(BASE_ATTACK, "club", 2, 2, 0, 0, 0, List.of());
    private static final AttackDefinition SPECIAL_MELEE =
        new AttackDefinition(SPECIAL_ATTACK, "troll smash", 9, 9, 0, 0, 0, List.of());

    /** arcane-shackles: HARMFUL spell dealing 3 damage and applying the root-classified "shackled" effect. */
    private static final Ability SHACKLES = new AbilityDefinition(
        AbilityId.of("spell.arcane-shackles"),
        "arcane shackles",
        AbilityType.SPELL,
        1,
        new AbilityCost(4, 0),
        new AbilityCooldown(3),
        AbilityTargeting.HARMFUL,
        List.of(),
        List.of(
            new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 3, null),
            new AbilityEffect(AbilityEffectKind.EFFECT, null, null, 0, "shackled")),
        List.of()
    );

    private static final EffectDefinition SHACKLED = new EffectDefinition(
        EffectId.of("shackled"),
        "Shackled",
        6,
        1,
        EffectStacking.REFRESH,
        List.of(),
        List.of(),
        ControlType.ROOT
    );

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate template(AttackId special) {
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 100, BASE_ATTACK, special, true,
            List.of(), SPAWN_ROOM, 1, 10, 5, null, List.of(), false,
            null, null, false, null, null, false);
    }

    private record Fixture(
        MobRegistry registry, MobInstance mob, StubPlayerRepository players,
        PersistenceQueue persistenceQueue) {
    }

    private Fixture build(Player target, AttackId special, CombatRandom random, EffectEngine effectEngine) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template(special)));
        AttackRepositoryStub attackRepo = new AttackRepositoryStub(
            Map.of(BASE_ATTACK, BASE_MELEE, SPECIAL_ATTACK, SPECIAL_MELEE));
        ItemRepository itemRepo = new StubItemRepository();
        RoomService roomService = new RoomService(new StubRoomRepository(), SPAWN_ROOM);
        roomService.ensurePlayerLocation(target.getUsername());
        StubPlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue,
            new PlayerEventBus(), random);
        registry.init();
        if (effectEngine != null) {
            registry.setEffectEngine(effectEngine);
        }
        MobInstance mob = registry.allInstances().iterator().next();
        return new Fixture(registry, mob, playerRepo, persistenceQueue);
    }

    private void tickAndAwaitPersist(Fixture f) {
        f.registry().tick();
        assertTrue(f.persistenceQueue().flush(Duration.ofSeconds(5)),
            "Expected persistence queue to drain");
    }

    private int heroHp(Fixture f, Player hero) {
        return f.players().loadPlayer(hero.getUsername()).orElseThrow().getVitals().hp();
    }

    // ── AI gating ──────────────────────────────────────────────────────

    @Test
    void stunnedMob_skipsItsWholeDecision_thenResumesAfterExpiry() {
        Player hero = player("hero");
        Fixture f = build(hero, null, MobRegistryTestSupport.random(), null);
        f.mob().applyControl(ControlType.STUN, 2);
        int fullHp = heroHp(f, hero);

        tickAndAwaitPersist(f);
        assertEquals(fullHp, heroHp(f, hero), "A stunned mob takes no action, so the hero is untouched");
        assertTrue(f.mob().engagedPlayers().isEmpty(), "A stunned mob does not even engage");

        tickAndAwaitPersist(f);
        assertEquals(fullHp, heroHp(f, hero), "Still stunned on the second tick");

        // Control has now expired: the mob resumes normal aggression and attacks.
        tickAndAwaitPersist(f);
        assertTrue(heroHp(f, hero) < fullHp, "Once the stun expires the mob attacks the hero again");
    }

    @Test
    void rootedMob_doesNotFleeBelowThreshold_thenFleesOnceRootExpires() {
        Player hero = player("hero");
        Fixture f = build(hero, null, MobRegistryTestSupport.random(), null);
        f.registry().setMobFleeSettings(20, 100); // always flee when below 20% HP

        // Engage the mob and wound it below the flee threshold.
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);
        assertTrue(f.mob().currentHp() <= 20, "Precondition: mob is below the flee threshold");

        f.mob().applyControl(ControlType.ROOT, 1);
        tickAndAwaitPersist(f);
        assertEquals(SPAWN_ROOM, f.mob().roomId(),
            "A rooted mob cannot flee even below the flee HP threshold — it fights on in place");
        assertTrue(f.registry().isInCombat(hero.getUsername()), "A rooted mob keeps fighting");

        // Root has expired: the wounded mob now flees as normal.
        tickAndAwaitPersist(f);
        assertEquals(NORTH_ROOM, f.mob().roomId(), "Once the root expires the wounded mob flees");
    }

    @Test
    void silencedMob_usesOnlyItsBaseAttack_neverItsSpecial() {
        Player hero = player("hero");
        Fixture f = build(hero, SPECIAL_ATTACK, MobRegistryTestSupport.random(), null);
        int fullHp = heroHp(f, hero);
        f.mob().applyControl(ControlType.SILENCE, 3);

        tickAndAwaitPersist(f);

        int dealt = fullHp - heroHp(f, hero);
        assertEquals(2, dealt,
            "A silenced mob is restricted to its base attack (2 dmg), never its special (9 dmg)");
        assertFalse(f.mob().specialAbilityUsed(),
            "Silence bars the special without consuming it, so it is available once the silence lifts");
    }

    @Test
    void unsilencedMob_firesItsSpecialOnFirstEngagement() {
        // Control-free baseline proving the mob WOULD use its high-damage special absent silence,
        // so the silenced-mob test above is meaningfully gating behaviour rather than a no-op.
        Player hero = player("hero");
        Fixture f = build(hero, SPECIAL_ATTACK, MobRegistryTestSupport.random(), null);
        int fullHp = heroHp(f, hero);

        tickAndAwaitPersist(f);

        assertEquals(9, fullHp - heroHp(f, hero),
            "An uncontrolled boss opens with its 9-damage special attack");
    }

    // ── processPlayerSingleTargetAbility wiring ─────────────────────────

    @Test
    void controlAbility_landingOnMob_appliesTheLockoutAndNarratesIt() {
        Player caster = player("merlin");
        // Hit roll 10 (lands), crit roll 100 (no crit): a clean landing cast.
        Fixture f = build(caster, null, new ScriptedRandom(10, 100),
            new EffectEngine(new SingleEffectRepository(SHACKLED)));

        GameActionResult result =
            f.registry().processPlayerSingleTargetAbility(caster, SHACKLES, "goblin", SPAWN_ROOM);

        assertSame(ControlType.ROOT, f.mob().activeControl(),
            "A control-classified ability locks the surviving mob down for the effect's duration");
        assertTrue(result.messages().stream().anyMatch(m -> m.text().contains("rooted in place")),
            "The apply narrates the lockout landing so a party can see it");
        assertEquals(97, f.mob().currentHp(), "The ability still deals its 3 damage alongside the control");
    }

    @Test
    void controlAbility_killingTheMob_appliesNoLockout() {
        Player caster = player("merlin");
        Fixture f = build(caster, null, new ScriptedRandom(10, 100),
            new EffectEngine(new SingleEffectRepository(SHACKLED)));
        f.mob().takeDamage(98); // 2 HP left, cast deals 3 → dies

        f.registry().processPlayerSingleTargetAbility(caster, SHACKLES, "goblin", SPAWN_ROOM);

        assertFalse(f.mob().isAlive(), "The cast was a killing blow");
        assertNull(f.mob().activeControl(), "A slain mob is never locked down");
    }

    // ── stubs ───────────────────────────────────────────────────────────

    private static final class ScriptedRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        ScriptedRandom(int... values) {
            for (int value : values) {
                rolls.add(value);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer next = rolls.poll();
            int value = next == null ? minInclusive : next;
            return Math.max(minInclusive, Math.min(maxInclusive, value));
        }

        @Override
        public double nextDouble() {
            return 1.0;
        }
    }

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private record AttackRepositoryStub(Map<AttackId, AttackDefinition> attacks)
        implements io.taanielo.jmud.core.combat.repository.AttackRepository {
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
            SPAWN_ROOM, new Room(
                SPAWN_ROOM, "Spawn", "A clearing.",
                Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of()),
            NORTH_ROOM, new Room(
                NORTH_ROOM, "North", "A thicket.",
                Map.of(Direction.SOUTH, SPAWN_ROOM), List.of(), List.of()));

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }

    private record SingleEffectRepository(EffectDefinition definition) implements EffectRepository {
        @Override
        public Optional<EffectDefinition> findById(EffectId id) throws EffectRepositoryException {
            return definition.id().equals(id) ? Optional.of(definition) : Optional.empty();
        }
    }
}
