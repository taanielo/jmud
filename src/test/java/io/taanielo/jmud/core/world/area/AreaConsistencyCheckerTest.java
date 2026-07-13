package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopId;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.area.repository.json.JsonAreaRepository;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomCatalog;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

class AreaConsistencyCheckerTest {

    private static final Path DATA_ROOT = Path.of("data");

    @Test
    void shippedWorldDataIsConsistent() throws Exception {
        ItemRepository items = new JsonItemRepository(DATA_ROOT);
        JsonRoomRepository rooms = new JsonRoomRepository(items, DATA_ROOT);
        AreaConsistencyChecker checker = new AreaConsistencyChecker(
            new JsonAreaRepository(DATA_ROOT),
            rooms,
            new io.taanielo.jmud.core.shop.repository.json.JsonShopRepository(DATA_ROOT),
            new io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository(DATA_ROOT),
            items);

        List<String> problems = checker.check();

        assertTrue(problems.isEmpty(), "shipped area data must be consistent but had: " + problems);
    }

    @Test
    void reportsUnassignedRoom() {
        Fixture f = twoAreaFixture();
        f.rooms.add(room("r3", Map.of()));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("r3") && p.contains("not assigned")),
            "expected an unassigned-room problem, got: " + problems);
    }

    @Test
    void reportsDoublyAssignedRoom() {
        Fixture f = twoAreaFixture();
        // Add r1 (already in area-a) to area-b as well.
        f.areas.set(1, new Area(AreaId.of("area-b"), "Area B",
            List.of(RoomId.of("r2"), RoomId.of("r1")), List.of(AreaId.of("area-a")), List.of("B ART"),
            new LevelRange(1, 5)));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("r1") && p.contains("more than one area")),
            "expected a doubly-assigned-room problem, got: " + problems);
    }

    @Test
    void reportsUnrealizedConnection() {
        Fixture f = twoAreaFixture();
        // Remove every border-crossing exit (in either direction) so the declared connection is
        // unrealized. Keep map-b obtainable via a shop so only the connection problem surfaces.
        f.rooms.set(0, room("r1", Map.of()));
        f.rooms.set(1, room("r2", Map.of()));
        f.shops.add(shopStocking("map-b"));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("no room exit realizes")),
            "expected an unrealized-connection problem, got: " + problems);
    }

    @Test
    void reportsConnectionToUnknownArea() {
        Fixture f = twoAreaFixture();
        f.areas.set(0, new Area(AreaId.of("area-a"), "Area A",
            List.of(RoomId.of("r1")), List.of(AreaId.of("area-b"), AreaId.of("ghost")), List.of("A ART"),
            new LevelRange(1, 5)));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("ghost") && p.contains("unknown area")),
            "expected an unknown-area connection problem, got: " + problems);
    }

    @Test
    void reportsAreaWithoutObtainableMap() {
        Fixture f = twoAreaFixture();
        // Drop area-b's map from every distribution channel: remove it from room r2 (its only
        // placement) while keeping the border-crossing exit so the connection stays realized.
        f.rooms.set(1, room2WithoutMap());

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("area-b") && p.contains("no obtainable map item")),
            "expected a missing-map problem for area-b, got: " + problems);
    }

    @Test
    void reportsMapReferencingUnknownArea() {
        Fixture f = twoAreaFixture();
        f.items.put("map-ghost",
            mapItem("map-ghost", "Ghost Map", "zzz"));
        f.shops.add(shopStocking("map-ghost"));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("map-ghost") && p.contains("unknown area")),
            "expected an unknown-area map reference problem, got: " + problems);
    }

    @Test
    void twoAreaFixtureIsConsistent() {
        assertFalse(twoAreaFixture() == null);
        List<String> problems = check(twoAreaFixture());
        assertTrue(problems.isEmpty(), "baseline fixture should be consistent but had: " + problems);
    }

    // ── fixtures & fakes ─────────────────────────────────────────────────

    private static List<String> check(Fixture f) {
        return new AreaConsistencyChecker(
            new FakeAreaRepository(f.areas, f.atlas),
            new FakeRoomCatalog(f.rooms),
            new FakeShopRepository(f.shops),
            new FakeMobRepository(),
            new FakeItemRepository(f.items)).check();
    }

    /**
     * Two areas A(r1) and B(r2) connected both ways, each with an obtainable map (map-a in a shop,
     * map-b in a room), plus an atlas. Every rule passes as-is; each test perturbs one thing.
     */
    private static Fixture twoAreaFixture() {
        Fixture f = new Fixture();
        f.areas.add(new Area(AreaId.of("area-a"), "Area A",
            List.of(RoomId.of("r1")), List.of(AreaId.of("area-b")), List.of("A ART"),
            new LevelRange(1, 5)));
        f.areas.add(new Area(AreaId.of("area-b"), "Area B",
            List.of(RoomId.of("r2")), List.of(AreaId.of("area-a")), List.of("B ART"),
            new LevelRange(1, 5)));
        f.rooms.add(room("r1", Map.of(Direction.EAST, RoomId.of("r2"))));
        Item mapB = mapItem("map-b", "Map B", "area-b");
        f.rooms.add(new Room(RoomId.of("r2"), "Room r2", "desc",
            Map.of(Direction.WEST, RoomId.of("r1")), List.of(mapB), List.of()));
        f.items.put("map-a", mapItem("map-a", "Map A", "area-a"));
        f.items.put("map-b", mapB);
        f.shops.add(shopStocking("map-a"));
        f.atlas = new WorldAtlas("atlas", "World Atlas", List.of("ATLAS ART"));
        return f;
    }

    private static Room room(String id, Map<Direction, RoomId> exits) {
        return new Room(RoomId.of(id), "Room " + id, "desc", exits, List.of(), List.of());
    }

    private static Room room2WithoutMap() {
        return new Room(RoomId.of("r2"), "Room r2", "desc",
            Map.of(Direction.WEST, RoomId.of("r1")), List.of(), List.of());
    }

    private static Item mapItem(String id, String name, String areaId) {
        return Item.builder(ItemId.of(id), name, "A map.", ItemAttributes.empty())
            .weight(1).value(10).mapAreaId(AreaId.of(areaId)).build();
    }

    private static Shop shopStocking(String itemId) {
        return new Shop(ShopId.of("shop-" + itemId), "Shop", RoomId.of("r1"),
            List.of(new StockEntry(ItemId.of(itemId), null)), 0.5);
    }

    private static final class Fixture {
        final java.util.List<Area> areas = new java.util.ArrayList<>();
        final java.util.List<Room> rooms = new java.util.ArrayList<>();
        final java.util.List<Shop> shops = new java.util.ArrayList<>();
        final java.util.Map<String, Item> items = new java.util.HashMap<>();
        WorldAtlas atlas;
    }

    private record FakeAreaRepository(List<Area> areas, WorldAtlas atlas) implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.copyOf(areas);
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return areas.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.ofNullable(atlas);
        }
    }

    private record FakeRoomCatalog(List<Room> rooms) implements RoomCatalog {
        @Override
        public List<Room> findAll() {
            return List.copyOf(rooms);
        }
    }

    private record FakeShopRepository(List<Shop> shops) implements ShopRepository {
        @Override
        public List<Shop> findAll() {
            return List.copyOf(shops);
        }

        @Override
        public Optional<Shop> findByRoomId(RoomId roomId) {
            return shops.stream().filter(s -> s.roomId().equals(roomId)).findFirst();
        }
    }

    private record FakeMobRepository() implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return List.of();
        }
    }

    private record FakeItemRepository(Map<String, Item> items) implements ItemRepository {
        @Override
        public void save(Item item) {
            items.put(item.getId().getValue(), item);
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id.getValue()));
        }
    }
}
