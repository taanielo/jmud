package io.taanielo.jmud.core.world.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomCatalog;

/**
 * Whole-world consistency check for the formal-area cartography system (issue #529).
 *
 * <p>Given the loaded areas, rooms, shops, mobs and items, it verifies the invariants that let map
 * items exist as the sole cartography and that future areas cannot ship without one:
 *
 * <ul>
 *   <li>every room belongs to exactly one area;</li>
 *   <li>no area references an unknown room;</li>
 *   <li>every declared area connection is realized by at least one room exit crossing the border;</li>
 *   <li>every connection targets a known area;</li>
 *   <li>every area has an obtainable map item (stocked in a shop, dropped as loot, or lying in a
 *       room — directly or inside a container);</li>
 *   <li>every map item references a real area (or the atlas).</li>
 * </ul>
 *
 * <p>The checker performs no mutation and does no networking, so it is unit-testable without a
 * running server (AGENTS.md §10). It returns a list of human-readable problem strings; an empty
 * list means the world is consistent.
 */
public class AreaConsistencyChecker {

    private final AreaRepository areaRepository;
    private final RoomCatalog roomCatalog;
    private final ShopRepository shopRepository;
    private final MobTemplateRepository mobTemplateRepository;
    private final ItemRepository itemRepository;

    /**
     * Creates a checker over the given data sources.
     *
     * @param areaRepository        source of area and atlas definitions
     * @param roomCatalog           bulk access to every room
     * @param shopRepository        source of shop stock (a map-item distribution channel)
     * @param mobTemplateRepository source of mob loot tables (a map-item distribution channel)
     * @param itemRepository        source of item definitions, used to resolve map bindings
     */
    public AreaConsistencyChecker(
        AreaRepository areaRepository,
        RoomCatalog roomCatalog,
        ShopRepository shopRepository,
        MobTemplateRepository mobTemplateRepository,
        ItemRepository itemRepository
    ) {
        this.areaRepository = Objects.requireNonNull(areaRepository, "Area repository is required");
        this.roomCatalog = Objects.requireNonNull(roomCatalog, "Room catalog is required");
        this.shopRepository = Objects.requireNonNull(shopRepository, "Shop repository is required");
        this.mobTemplateRepository =
            Objects.requireNonNull(mobTemplateRepository, "Mob template repository is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Runs every consistency rule and returns the problems found.
     *
     * @return an ordered list of problem descriptions; empty when the world is consistent
     */
    public List<String> check() {
        List<String> problems = new ArrayList<>();

        List<Area> areas;
        List<Room> rooms;
        try {
            areas = areaRepository.findAll();
            rooms = roomCatalog.findAll();
        } catch (Exception e) {
            problems.add("Failed to load area/room data: " + e.getMessage());
            return problems;
        }

        Set<String> areaIds = new HashSet<>();
        for (Area area : areas) {
            areaIds.add(area.id().getValue());
        }

        Map<String, Room> roomsById = new HashMap<>();
        for (Room room : rooms) {
            roomsById.put(room.getId().getValue(), room);
        }

        Map<String, String> roomToArea = assignRoomsToAreas(areas, roomsById, problems);
        checkUnassignedRooms(roomsById, roomToArea, problems);
        checkConnections(areas, areaIds, roomsById, roomToArea, problems);
        checkObtainableMaps(areas, areaIds, roomsById, problems);

        return problems;
    }

    private Map<String, String> assignRoomsToAreas(
        List<Area> areas, Map<String, Room> roomsById, List<String> problems) {
        Map<String, String> roomToArea = new HashMap<>();
        for (Area area : areas) {
            for (RoomId roomId : area.roomIds()) {
                String rid = roomId.getValue();
                if (!roomsById.containsKey(rid)) {
                    problems.add("Area '" + area.id().getValue() + "' lists unknown room '" + rid + "'");
                    continue;
                }
                String existing = roomToArea.putIfAbsent(rid, area.id().getValue());
                if (existing != null) {
                    problems.add("Room '" + rid + "' is assigned to more than one area ('"
                        + existing + "' and '" + area.id().getValue() + "')");
                }
            }
        }
        return roomToArea;
    }

    private void checkUnassignedRooms(
        Map<String, Room> roomsById, Map<String, String> roomToArea, List<String> problems) {
        Set<String> unassigned = new TreeSet<>();
        for (String rid : roomsById.keySet()) {
            if (!roomToArea.containsKey(rid)) {
                unassigned.add(rid);
            }
        }
        for (String rid : unassigned) {
            problems.add("Room '" + rid + "' is not assigned to any area");
        }
    }

    private void checkConnections(
        List<Area> areas,
        Set<String> areaIds,
        Map<String, Room> roomsById,
        Map<String, String> roomToArea,
        List<String> problems) {
        // Undirected adjacency realized by an actual border-crossing exit.
        Set<String> realized = new HashSet<>();
        for (Room room : roomsById.values()) {
            String fromArea = roomToArea.get(room.getId().getValue());
            if (fromArea == null) {
                continue;
            }
            for (RoomId dest : room.getExits().values()) {
                String toArea = roomToArea.get(dest.getValue());
                if (toArea != null && !toArea.equals(fromArea)) {
                    realized.add(adjacencyKey(fromArea, toArea));
                }
            }
        }
        for (Area area : areas) {
            for (AreaId target : area.connections()) {
                String targetId = target.getValue();
                if (!areaIds.contains(targetId)) {
                    problems.add("Area '" + area.id().getValue() + "' declares a connection to unknown area '"
                        + targetId + "'");
                    continue;
                }
                if (!realized.contains(adjacencyKey(area.id().getValue(), targetId))) {
                    problems.add("Area '" + area.id().getValue() + "' declares a connection to '" + targetId
                        + "' that no room exit realizes");
                }
            }
        }
    }

    private void checkObtainableMaps(
        List<Area> areas, Set<String> areaIds, Map<String, Room> roomsById, List<String> problems) {
        Set<String> placedItemIds = new HashSet<>();
        try {
            for (Shop shop : shopRepository.findAll()) {
                for (StockEntry entry : shop.stock()) {
                    placedItemIds.add(entry.itemId().getValue());
                }
            }
            for (MobTemplate mob : mobTemplateRepository.findAll()) {
                for (LootEntry loot : mob.lootTable()) {
                    placedItemIds.add(loot.itemId().getValue());
                }
            }
        } catch (Exception e) {
            problems.add("Failed to load shop/mob data: " + e.getMessage());
            return;
        }
        for (Room room : roomsById.values()) {
            for (Item item : room.getItems()) {
                placedItemIds.add(item.getId().getValue());
                for (Item contained : item.getContainedItems()) {
                    placedItemIds.add(contained.getId().getValue());
                }
            }
        }

        Set<String> coveredAreas = new HashSet<>();
        Optional<WorldAtlas> atlas;
        try {
            atlas = areaRepository.findAtlas();
        } catch (Exception e) {
            problems.add("Failed to load atlas data: " + e.getMessage());
            return;
        }
        String atlasId = atlas.map(WorldAtlas::id).orElse(null);

        for (String itemIdValue : placedItemIds) {
            AreaId mapArea = mapAreaOf(itemIdValue, problems);
            if (mapArea == null) {
                continue;
            }
            String mapAreaId = mapArea.getValue();
            if (areaIds.contains(mapAreaId)) {
                coveredAreas.add(mapAreaId);
            } else if (!mapAreaId.equals(atlasId)) {
                problems.add("Map item '" + itemIdValue + "' references unknown area '" + mapAreaId + "'");
            }
        }

        Set<String> uncovered = new TreeSet<>();
        for (Area area : areas) {
            if (!coveredAreas.contains(area.id().getValue())) {
                uncovered.add(area.id().getValue());
            }
        }
        for (String areaId : uncovered) {
            problems.add("Area '" + areaId + "' has no obtainable map item (none in any shop, loot table or room)");
        }
    }

    @Nullable
    private AreaId mapAreaOf(String itemIdValue, List<String> problems) {
        try {
            Optional<Item> item = itemRepository.findById(ItemId.of(itemIdValue));
            if (item.isEmpty()) {
                return null;
            }
            return item.get().getMapAreaId();
        } catch (Exception e) {
            problems.add("Failed to load item '" + itemIdValue + "': " + e.getMessage());
            return null;
        }
    }

    private static String adjacencyKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + " " + b : b + " " + a;
    }
}
