package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
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
 * Unit tests for the tame/charm companion system in {@link MobRegistry}: taming validation
 * ({@link MobRegistry#processTame}), persistence of the companion to the owner's save, room-to-room
 * following ({@link MobRegistry#runPetFollow via tick}), combat participation, dismissal on death,
 * and login re-spawn ({@link MobRegistry#spawnTamedPets}) — all without networking (AGENTS.md §10).
 */
class MobRegistryTameTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");
    private static final AttackId PUNCH = AttackId.of("attack.punch");
    private static final MobId RAT_ID = MobId.of("rat");
    private static final MobId WOLF_ID = MobId.of("wolf");
    private static final MobId GOBLIN_ID = MobId.of("goblin");

    /** A deterministic 5-damage attack shared by pets and their foes. */
    private static final AttackDefinition PUNCH_ATTACK =
        new AttackDefinition(PUNCH, "punch", 5, 5, 0, 0, 0, List.of());

    private Player tamer(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate charmable(MobId id, String name, RoomId room) {
        return new MobTemplate(
            id, name, 20, PUNCH, null, false, List.of(), room, 1, 15, 5, null, List.of(),
            false, null, null, true);
    }

    private MobTemplate goblin(int hp, RoomId room) {
        return new MobTemplate(
            GOBLIN_ID, "Goblin", hp, PUNCH, null, false, List.of(), room, 1, 10, 5, null, List.of(),
            false, null, null, false);
    }

    private static boolean containsText(GameActionResult result, String fragment) {
        return result.messages().stream().anyMatch(m -> m.text().contains(fragment));
    }

    private static boolean containsText(List<GameActionResult> results, String fragment) {
        return results.stream().flatMap(r -> r.messages().stream())
            .anyMatch(m -> m.text().contains(fragment));
    }

    private long tamedMobsInRoom(MobRegistry registry, RoomId roomId) {
        return registry.getMobsInRoom(roomId).stream().filter(MobInstance::isTamed).count();
    }

    private Harness harness(Player tamer, MobTemplate... templates) {
        return new Harness(tamer, templates);
    }

    private static final class Harness {
        final MobRegistry registry;
        final RoomService roomService;
        final StubPlayerRepository playerRepo;
        final io.taanielo.jmud.core.persistence.PersistenceQueue persistenceQueue;
        final List<GameActionResult> results = new ArrayList<>();

        Harness(Player tamer, MobTemplate... templates) {
            MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(templates));
            AttackRepository attackRepo = new StubAttackRepository(Map.of(PUNCH, PUNCH_ATTACK));
            ItemRepository itemRepo = new StubItemRepository();
            roomService = new RoomService(new StubRoomRepository(), ROOM_A);
            roomService.ensurePlayerLocation(tamer.getUsername());
            playerRepo = new StubPlayerRepository(tamer);
            persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
            PlayerEventBus bus = new PlayerEventBus();
            bus.register(tamer.getUsername(), results::add);
            registry = new MobRegistry(
                templateRepo, itemRepo, attackRepo, roomService, playerRepo,
                persistenceQueue, bus, MobRegistryTestSupport.random());
            registry.init();
        }

        Player reload(Username username) {
            // Saves are handed to the write-behind queue; flush so the assertion sees them.
            persistenceQueue.flush(Duration.ofSeconds(2));
            return playerRepo.loadPlayer(username).orElseThrow();
        }
    }

    @Test
    void tame_capturesCharmableMob_recordsCompanion_andSpawnsTamedPet() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(RAT_ID, "Giant Rat", ROOM_A));

        GameActionResult result = h.registry.processTame(tamer, "giant rat", ROOM_A);

        assertNotNull(result.updatedSource(), "A successful tame returns the updated tamer");
        assertTrue(containsText(result, "loyal companion"), "The tamer is told the mob is now theirs");
        assertEquals(1, tamedMobsInRoom(h.registry, ROOM_A), "A tamed pet now stands in the room");
        assertEquals(List.of("rat"), result.updatedSource().getTamedPets(),
            "The companion is recorded on the tamer");
        assertEquals(List.of("rat"), h.reload(tamer.getUsername()).getTamedPets(),
            "The companion is persisted to the tamer's save");
    }

    @Test
    void tame_rejectsNonCharmableMob() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, goblin(20, ROOM_A));

        GameActionResult result = h.registry.processTame(tamer, "goblin", ROOM_A);

        assertTrue(containsText(result, "cannot be tamed"), "Non-charmable mobs are rejected");
        assertNull(result.updatedSource(), "A rejected tame changes no state");
        assertEquals(0, tamedMobsInRoom(h.registry, ROOM_A), "No pet is spawned");
    }

    @Test
    void tame_rejectsWhenTargetMissing() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(RAT_ID, "Giant Rat", ROOM_B));

        GameActionResult result = h.registry.processTame(tamer, "giant rat", ROOM_A);

        assertTrue(containsText(result, "No such target here"), "A target elsewhere is not tameable");
        assertNull(result.updatedSource());
    }

    @Test
    void tame_enforcesCompanionLimit() {
        Player tamer = tamer("beastmaster");
        // Four charmable mobs in the room; only MAX_TAMED_PETS may be captured.
        Harness h = harness(tamer,
            charmable(MobId.of("rat"), "Giant Rat", ROOM_A),
            charmable(MobId.of("wolf"), "Wolf", ROOM_A),
            charmable(MobId.of("kobold"), "Kobold", ROOM_A),
            charmable(MobId.of("slime"), "Slime", ROOM_A));

        Player current = tamer;
        int tamed = 0;
        for (String name : List.of("Giant Rat", "Wolf", "Kobold", "Slime")) {
            GameActionResult result = h.registry.processTame(current, name, ROOM_A);
            if (result.updatedSource() != null) {
                current = result.updatedSource();
                tamed++;
            } else {
                assertTrue(containsText(result, "more than " + MobRegistry.MAX_TAMED_PETS),
                    "Exceeding the companion limit is rejected");
            }
        }
        assertEquals(MobRegistry.MAX_TAMED_PETS, tamed, "Only the maximum number of pets are tamed");
        assertEquals(MobRegistry.MAX_TAMED_PETS, tamedMobsInRoom(h.registry, ROOM_A));
    }

    @Test
    void tamedPet_followsOwnerToNewRoomOnNextTick() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        h.registry.processTame(tamer, "wolf", ROOM_A);
        assertEquals(1, tamedMobsInRoom(h.registry, ROOM_A));

        // Owner walks north into ROOM_B.
        h.roomService.move(tamer.getUsername(), Direction.NORTH);
        h.registry.tick();

        assertEquals(0, tamedMobsInRoom(h.registry, ROOM_A), "The pet left the old room");
        assertEquals(1, tamedMobsInRoom(h.registry, ROOM_B), "The pet followed its owner");
        assertTrue(containsText(h.results, "following you"), "The owner is told the pet followed");
    }

    @Test
    void tamedPet_assistsOwnerInCombatEachTick() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer,
            charmable(WOLF_ID, "Wolf", ROOM_A), goblin(20, ROOM_A));
        h.registry.processTame(tamer, "wolf", ROOM_A);

        h.registry.tick();

        assertTrue(containsText(h.results, "Your Wolf strikes the Goblin"),
            "The tamed pet fights hostile mobs at its owner's side");
        MobInstance foe = h.registry.getMobsInRoom(ROOM_A).stream()
            .filter(m -> !m.isPet())
            .findFirst().orElseThrow();
        assertTrue(foe.currentHp() < 20, "The struck goblin took damage");
    }

    @Test
    void tamedPet_removedFromSaveWhenSlainInCombat() {
        Player tamer = tamer("beastmaster");
        // A 1-HP tamed wolf that is slain by the goblin's retaliation.
        MobTemplate fragileWolf = new MobTemplate(
            WOLF_ID, "Wolf", 1, PUNCH, null, false, List.of(), ROOM_A, 1, 15, 5, null, List.of(),
            false, null, null, true);
        Harness h = harness(tamer, fragileWolf, goblin(50, ROOM_A));
        GameActionResult tameResult = h.registry.processTame(tamer, "wolf", ROOM_A);
        assertEquals(List.of("wolf"), h.reload(tamer.getUsername()).getTamedPets());

        h.registry.tick(); // pet strikes, goblin retaliates and slays the 1-HP pet

        assertEquals(0, tamedMobsInRoom(h.registry, ROOM_A), "The slain pet is removed from the world");
        assertTrue(h.reload(tamer.getUsername()).getTamedPets().isEmpty(),
            "A slain companion is removed from the owner's save so it does not respawn");
        assertNotNull(tameResult.updatedSource());
    }

    @Test
    void spawnTamedPets_reSpawnsPersistedCompanions_withoutDuplicating() {
        Player tamer = tamer("beastmaster")
            .withTamedPets(io.taanielo.jmud.core.player.PlayerPets.empty().tame("rat").tame("wolf"));
        Harness h = harness(tamer,
            charmable(RAT_ID, "Giant Rat", ROOM_A), charmable(WOLF_ID, "Wolf", ROOM_A));

        h.registry.spawnTamedPets(tamer, ROOM_A);
        assertEquals(2, tamedMobsInRoom(h.registry, ROOM_A), "Both persisted companions are spawned");

        // Calling again (e.g. a re-login) must not duplicate live companions.
        h.registry.spawnTamedPets(tamer, ROOM_A);
        assertEquals(2, tamedMobsInRoom(h.registry, ROOM_A), "Re-spawn does not duplicate live pets");
    }

    @Test
    void listCompanions_reportsTamedPets() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(RAT_ID, "Giant Rat", ROOM_A));
        Player owner = h.registry.processTame(tamer, "giant rat", ROOM_A).updatedSource();

        GameActionResult listed = h.registry.listCompanions(owner);
        assertTrue(containsText(listed, "Giant Rat"), "The companion is listed by name");

        GameActionResult none = h.registry.listCompanions(tamer("lonely"));
        assertTrue(containsText(none, "no companions"), "A player with no pets is told so");
    }

    @Test
    void tamedPet_cannotBeAttackedByPlayers() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(RAT_ID, "Giant Rat", ROOM_A));
        h.registry.processTame(tamer, "giant rat", ROOM_A);

        GameActionResult attack = h.registry.processPlayerAttack(tamer, "Giant Rat", ROOM_A);
        assertTrue(containsText(attack, "friendly companion"),
            "A tamed companion cannot be attacked by players");
    }

    @Test
    void tamedPet_isNotSummoned() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(RAT_ID, "Giant Rat", ROOM_A));
        h.registry.processTame(tamer, "giant rat", ROOM_A);

        MobInstance pet = h.registry.getMobsInRoom(ROOM_A).stream()
            .filter(MobInstance::isTamed).findFirst().orElseThrow();
        assertTrue(pet.isPet(), "A tamed mob is a pet");
        assertFalse(pet.isSummoned(), "A tamed mob is distinct from a temporary summon");
        assertEquals(tamer.getUsername(), pet.owner());
    }

    @Test
    void nameCompanion_setsCustomName_shownInListingAndCombat() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A), goblin(20, ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult named = h.registry.nameCompanion(owner, "wolf", "Fluffy");
        assertNotNull(named.updatedSource(), "Naming a companion returns the updated owner");
        assertEquals(List.of("wolf"), named.updatedSource().getTamedPets(),
            "The template id is unchanged by naming");
        assertEquals(List.of("Fluffy"), named.updatedSource().getTamedPetNames(),
            "The custom name is recorded on the owner");

        GameActionResult listed = h.registry.listCompanions(named.updatedSource());
        assertTrue(containsText(listed, "Fluffy"), "COMPANIONS shows the custom name");
        assertFalse(containsText(listed, "Wolf ("), "COMPANIONS no longer shows the template name");

        h.registry.tick();
        assertTrue(containsText(h.results, "Your Fluffy strikes the Goblin"),
            "Combat messages use the custom name");
    }

    @Test
    void nameCompanion_matchesByExistingCustomName_andOverwrites() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();
        owner = h.registry.nameCompanion(owner, "wolf", "Fluffy").updatedSource();

        GameActionResult renamed = h.registry.nameCompanion(owner, "Fluffy", "Rex");
        assertNotNull(renamed.updatedSource());
        assertEquals(List.of("Rex"), renamed.updatedSource().getTamedPetNames(),
            "Renaming by the current custom name overwrites it");
    }

    @Test
    void nameCompanion_persistsAcrossRespawn() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();
        h.registry.nameCompanion(owner, "wolf", "Fluffy");

        Player reloaded = h.reload(tamer.getUsername());
        assertEquals(List.of("Fluffy"), reloaded.getTamedPetNames(),
            "The custom name is persisted to the owner's save");

        // Simulate a fresh login into a new registry with no live pets.
        Harness fresh = harness(reloaded, charmable(WOLF_ID, "Wolf", ROOM_A));
        fresh.registry.spawnTamedPets(reloaded, ROOM_A);
        MobInstance pet = fresh.registry.getMobsInRoom(ROOM_A).stream()
            .filter(MobInstance::isTamed).findFirst().orElseThrow();
        assertEquals("Fluffy", pet.displayName(),
            "A respawned companion keeps its custom name on next login");
    }

    @Test
    void nameCompanion_rejectsUnknownCompanion() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult result = h.registry.nameCompanion(owner, "bear", "Fluffy");
        assertNull(result.updatedSource(), "Naming a companion you do not own changes no state");
        assertTrue(containsText(result, "no companion"), "A clear error is shown");
    }

    @Test
    void nameCompanion_rejectsTooLongName() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        String tooLong = "x".repeat(MobRegistry.MAX_PET_NAME_LENGTH + 1);
        GameActionResult result = h.registry.nameCompanion(owner, "wolf", tooLong);
        assertNull(result.updatedSource(), "An over-long name is rejected");
        assertTrue(containsText(result, "too long"), "The length limit is explained");
    }

    @Test
    void nameCompanion_rejectsBlankName() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult result = h.registry.nameCompanion(owner, "wolf", "   ");
        assertNull(result.updatedSource(), "A blank name is rejected");
    }

    @Test
    void describeCompanion_setsDescription_persistedAndShownOnLook() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult set = h.registry.describeCompanion(owner, "wolf", "A shaggy grey wolf.");
        assertNotNull(set.updatedSource(), "Describing a companion returns the updated owner");
        assertEquals(List.of("A shaggy grey wolf."), set.updatedSource().getTamedPetDescriptions(),
            "The custom description is recorded on the owner");

        // The description round-trips through the owner's save.
        assertEquals(List.of("A shaggy grey wolf."),
            h.reload(tamer.getUsername()).getTamedPetDescriptions(),
            "The description is persisted to the owner's save");

        // LOOK renders the custom description to any onlooker.
        var look = h.registry.describeMobOnLook(ROOM_A, "wolf");
        assertTrue(look.isPresent() && look.get().contains("A shaggy grey wolf."),
            "LOOK shows the custom description when set");
    }

    @Test
    void describeCompanion_queryShowsHint_thenCurrentDescription() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult none = h.registry.describeCompanion(owner, "wolf", "");
        assertNull(none.updatedSource(), "A query changes no state");
        assertTrue(containsText(none, "no custom description set"),
            "The owner is hinted when no description is set");

        owner = h.registry.describeCompanion(owner, "wolf", "A shaggy grey wolf.").updatedSource();
        GameActionResult query = h.registry.describeCompanion(owner, "wolf", "");
        assertNull(query.updatedSource(), "A query changes no state");
        assertTrue(containsText(query, "A shaggy grey wolf."),
            "The owner sees the current description when one is set");
    }

    @Test
    void describeCompanion_clearRevertsToGenericLook() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();
        owner = h.registry.describeCompanion(owner, "wolf", "A shaggy grey wolf.").updatedSource();

        GameActionResult cleared = h.registry.describeCompanion(owner, "wolf", "CLEAR");
        assertNotNull(cleared.updatedSource(), "Clearing returns the updated owner");
        assertTrue(cleared.updatedSource().getTamedPetDescriptions().get(0) == null,
            "The description is cleared on the owner's record");

        var look = h.registry.describeMobOnLook(ROOM_A, "wolf");
        assertTrue(look.isPresent() && look.get().get(0).contains("nothing special"),
            "LOOK reverts to the generic line once the description is cleared");
    }

    @Test
    void describeCompanion_clearWithNothingSetIsRejected() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult result = h.registry.describeCompanion(owner, "wolf", "NONE");
        assertNull(result.updatedSource(), "Clearing an already-blank description changes no state");
        assertTrue(containsText(result, "no custom description to clear"), "A clear error is shown");
    }

    @Test
    void describeCompanion_rejectsTooLongDescription() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        String tooLong = "x".repeat(MobRegistry.MAX_PET_DESCRIPTION_LENGTH + 1);
        GameActionResult result = h.registry.describeCompanion(owner, "wolf", tooLong);
        assertNull(result.updatedSource(), "An over-long description is rejected");
        assertTrue(containsText(result, "too long"), "The length limit is explained");
    }

    @Test
    void describeCompanion_rejectsUnknownCompanion() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        GameActionResult result = h.registry.describeCompanion(owner, "bear", "A grizzly.");
        assertNull(result.updatedSource(), "Describing a companion you do not own changes no state");
        assertTrue(containsText(result, "no companion"), "A clear error is shown");
    }

    @Test
    void describeCompanion_persistsAcrossReSpawnOnLogin() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();
        h.registry.describeCompanion(owner, "wolf", "A shaggy grey wolf.");

        Player reloaded = h.reload(tamer.getUsername());
        // Simulate a fresh login into a new registry with no live pets.
        Harness fresh = harness(reloaded, charmable(WOLF_ID, "Wolf", ROOM_A));
        fresh.registry.spawnTamedPets(reloaded, ROOM_A);

        var look = fresh.registry.describeMobOnLook(ROOM_A, "wolf");
        assertTrue(look.isPresent() && look.get().contains("A shaggy grey wolf."),
            "A re-spawned companion keeps its custom description on next login");
    }

    @Test
    void describeMobOnLook_prefersDescribedCompanionOverWildSameSpecies() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();
        h.registry.describeCompanion(owner, "wolf", "A shaggy grey wolf.");
        Player reloaded = h.reload(tamer.getUsername());

        // The fresh registry's init() spawns a live WILD Wolf into ROOM_A; spawning the tamed
        // companion puts a described Wolf beside it, so LOOK "wolf" has two live matches. The
        // described companion must win regardless of mob-instance iteration order.
        Harness fresh = harness(reloaded, charmable(WOLF_ID, "Wolf", ROOM_A));
        fresh.registry.spawnTamedPets(reloaded, ROOM_A);
        assertEquals(2, fresh.registry.getMobsInRoom(ROOM_A).stream()
                .filter(m -> m.template().name().equalsIgnoreCase("Wolf")).count(),
            "a wild and a tamed wolf should share the room for this scenario");

        var look = fresh.registry.describeMobOnLook(ROOM_A, "wolf");
        assertTrue(look.isPresent() && look.get().contains("A shaggy grey wolf."),
            "LOOK prefers the described companion over a wild same-species mob in the room");
    }

    @Test
    void ownsCompanionMatching_onlyTrueForOwnLiveCompanion() {
        Player tamer = tamer("beastmaster");
        Harness h = harness(tamer, charmable(WOLF_ID, "Wolf", ROOM_A));
        Player owner = h.registry.processTame(tamer, "wolf", ROOM_A).updatedSource();

        assertTrue(h.registry.ownsCompanionMatching(owner, "wolf"),
            "A player's own companion matches by kind");
        assertFalse(h.registry.ownsCompanionMatching(owner, "bear"),
            "A token that names no companion does not match");
        assertFalse(h.registry.ownsCompanionMatching(tamer("stranger"), "wolf"),
            "Another player does not own this companion");
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
