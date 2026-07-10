package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

/**
 * Integration smoke-test confirming that the Shrouded Isle zone loads from the
 * production data directory: its rooms are present and interconnected, it is
 * reachable from South Dock (the coastal-ferry destination), and its mobs,
 * attacks and loot all resolve against the shared repositories.
 */
class ShroudedIsleZoneSmokeTest {

    private static final Path DATA_ROOT = Path.of("data");

    @Test
    void island_isReachableFromSouthDock() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(DATA_ROOT);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, DATA_ROOT);

        Room southDock = roomRepository.findById(RoomId.of("south-dock"))
            .orElseThrow(() -> new AssertionError("South Dock room must be present"));
        assertEquals(RoomId.of("shrouded-isle-shore"), southDock.getExits().get(Direction.NORTH),
            "South Dock should have a 'north' exit onto the Shrouded Isle");
        assertEquals(RoomId.of("coastal-ferry-deck"), southDock.getExits().get(Direction.DOWN),
            "South Dock must preserve its existing 'down' exit back onto the ferry deck");
    }

    @Test
    void island_roomsFormAConnectedZone() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(DATA_ROOT);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, DATA_ROOT);

        Room shore = room(roomRepository, "shrouded-isle-shore");
        assertEquals(RoomId.of("south-dock"), shore.getExits().get(Direction.SOUTH));
        assertEquals(RoomId.of("shrouded-isle-driftwood-path"), shore.getExits().get(Direction.NORTH));

        Room path = room(roomRepository, "shrouded-isle-driftwood-path");
        assertEquals(RoomId.of("shrouded-isle-shore"), path.getExits().get(Direction.SOUTH));
        assertEquals(RoomId.of("shrouded-isle-shipwreck"), path.getExits().get(Direction.NORTH));
        assertEquals(RoomId.of("shrouded-isle-cove"), path.getExits().get(Direction.EAST));

        Room cove = room(roomRepository, "shrouded-isle-cove");
        assertEquals(RoomId.of("shrouded-isle-driftwood-path"), cove.getExits().get(Direction.WEST));

        Room wreck = room(roomRepository, "shrouded-isle-shipwreck");
        assertEquals(RoomId.of("shrouded-isle-driftwood-path"), wreck.getExits().get(Direction.SOUTH));
        assertEquals(RoomId.of("shrouded-isle-lighthouse"), wreck.getExits().get(Direction.WEST));

        Room lighthouse = room(roomRepository, "shrouded-isle-lighthouse");
        assertEquals(RoomId.of("shrouded-isle-shipwreck"), lighthouse.getExits().get(Direction.EAST));
        assertEquals(RoomId.of("shrouded-isle-lighthouse-summit"), lighthouse.getExits().get(Direction.UP));

        Room summit = room(roomRepository, "shrouded-isle-lighthouse-summit");
        assertEquals(RoomId.of("shrouded-isle-lighthouse"), summit.getExits().get(Direction.DOWN));
    }

    @Test
    void island_mobsAttacksAndLootAllResolve() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(DATA_ROOT);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, DATA_ROOT);
        JsonMobTemplateRepository mobRepository = new JsonMobTemplateRepository(DATA_ROOT);
        AttackRepository attackRepository = new JsonAttackRepository(DATA_ROOT);

        List<MobTemplate> islandMobs = new ArrayList<>();
        for (MobTemplate template : mobRepository.findAll()) {
            boolean onIsland = roomRepository.findById(template.spawnRoomId())
                .map(r -> r.getId().getValue().startsWith("shrouded-isle-"))
                .orElse(false);
            if (onIsland) {
                islandMobs.add(template);
            }
        }

        // shore (tide-crab + keeper), driftwood (fog-wraith), shipwreck (drowned-sailor),
        // summit (drowned-captain) — five templates in all.
        assertEquals(5, islandMobs.size(), "Expected five mob templates on the Shrouded Isle");

        for (MobTemplate mob : islandMobs) {
            if (mob.attackId() != null) {
                assertResolves(attackRepository, mob.attackId(),
                    "Attack " + mob.attackId().getValue() + " for mob " + mob.id().getValue());
            }
            if (mob.specialAttackId() != null) {
                assertResolves(attackRepository, mob.specialAttackId(),
                    "Special attack " + mob.specialAttackId().getValue() + " for mob " + mob.id().getValue());
            }
            for (LootEntry loot : mob.lootTable()) {
                assertTrue(itemRepository.findById(loot.itemId()).isPresent(),
                    "Loot item " + loot.itemId().getValue() + " for mob " + mob.id().getValue()
                        + " must exist");
            }
        }

        MobTemplate boss = islandMobs.stream()
            .filter(m -> "drowned-captain".equals(m.id().getValue()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("The Drowned Captain mini-boss must be present"));
        assertNotNull(boss.specialAttackId(), "The mini-boss must have a special attack");
        assertTrue(boss.maxHp() >= 100 && boss.maxHp() <= 150,
            "The mini-boss should sit in the 100-150 hp band, was " + boss.maxHp());
        assertTrue(boss.lootTable().stream()
                .anyMatch(l -> "drowned-captains-cutlass".equals(l.itemId().getValue())),
            "The mini-boss must drop its signature cutlass");
    }

    private static void assertResolves(AttackRepository attacks, AttackId id, String label)
            throws RepositoryException {
        Optional<?> definition = attacks.findById(id);
        assertTrue(definition.isPresent(), label + " must resolve to an attack definition");
    }

    private static Room room(JsonRoomRepository repository, String id) throws RepositoryException {
        return repository.findById(RoomId.of(id))
            .orElseThrow(() -> new AssertionError("Room " + id + " must be present"));
    }
}
