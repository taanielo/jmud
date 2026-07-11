package io.taanielo.jmud.core.gathering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link ResourceGatheringService}, exercising harvesting, depletion, respawn, and
 * same-tick contention without any networking.
 */
class ResourceGatheringServiceTest {

    private static final RoomId VEIN_ROOM = RoomId.of("sewers-tunnel");
    private static final RoomId EMPTY_ROOM = RoomId.of("courtyard");
    private static final ItemId IRON_ORE = ItemId.of("iron-ore");
    private static final ResourceNodeId VEIN_ID = ResourceNodeId.of("sewers-iron-vein");

    private final Map<ItemId, Item> catalogue = new HashMap<>();
    private final ItemRepository itemRepository = new ItemRepository() {
        @Override
        public void save(Item item) {
            catalogue.put(item.getId(), item);
        }

        @Override
        public Optional<Item> findById(ItemId id) {
            return Optional.ofNullable(catalogue.get(id));
        }
    };

    private final ResourceNode ironVein = new ResourceNode(
        VEIN_ID, VEIN_ROOM, IRON_ORE, 3, "iron ore vein", "A vein of iron ore runs here.");

    private final ResourceGatheringService service =
        new ResourceGatheringService(List.of(ironVein), itemRepository);

    ResourceGatheringServiceTest() {
        catalogue.put(IRON_ORE, Item.builder(IRON_ORE, "Iron Ore", "Raw ore.", ItemAttributes.empty())
            .weight(2).value(8).build());
    }

    private static Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("pw", 1000)), "prompt");
    }

    private static int countOf(Player player, ItemId id) {
        return (int) player.getInventory().stream().filter(item -> item.getId().equals(id)).count();
    }

    @Test
    void gatherAddsYieldItemAndDepletesNode() {
        Player player = player("miner");

        GatherOutcome outcome = service.gather(player, VEIN_ROOM);

        assertTrue(outcome.success());
        assertEquals(1, countOf(outcome.updatedPlayer(), IRON_ORE));
        assertTrue(service.isDepleted(VEIN_ID));
    }

    @Test
    void gatherWithNoNodeInRoomFails() {
        GatherOutcome outcome = service.gather(player("miner"), EMPTY_ROOM);

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().toLowerCase(Locale.ROOT).contains("nothing here"));
    }

    @Test
    void gatherOnDepletedNodeFails() {
        service.gather(player("miner"), VEIN_ROOM);

        GatherOutcome second = service.gather(player("miner"), VEIN_ROOM);

        assertFalse(second.success());
        assertNull(second.updatedPlayer());
        assertTrue(second.message().toLowerCase(Locale.ROOT).contains("stripped bare"));
    }

    @Test
    void nodeRespawnsAfterConfiguredTicks() {
        service.gather(player("miner"), VEIN_ROOM);
        assertTrue(service.isDepleted(VEIN_ID));

        // respawnTicks == 3: still depleted after two ticks, available after the third.
        service.tickRespawns();
        service.tickRespawns();
        assertTrue(service.isDepleted(VEIN_ID));

        service.tickRespawns();
        assertFalse(service.isDepleted(VEIN_ID));

        GatherOutcome outcome = service.gather(player("miner"), VEIN_ROOM);
        assertTrue(outcome.success());
    }

    @Test
    void twoPlayersSameTickOnlyFirstHarvestSucceeds() {
        // Commands run serially on the single tick thread, so the second harvest sees the depleted
        // node the first one just created (AGENTS.md §5).
        GatherOutcome first = service.gather(player("alice"), VEIN_ROOM);
        GatherOutcome second = service.gather(player("bob"), VEIN_ROOM);

        assertTrue(first.success());
        assertFalse(second.success());
        assertTrue(second.message().toLowerCase(Locale.ROOT).contains("stripped bare"));
    }

    @Test
    void availableNodeAppearsInRoomDescriptionThenDisappearsWhenDepleted() {
        assertEquals(List.of("A vein of iron ore runs here."), service.describeAvailableNodes(VEIN_ROOM));

        service.gather(player("miner"), VEIN_ROOM);

        assertTrue(service.describeAvailableNodes(VEIN_ROOM).isEmpty());
    }
}
