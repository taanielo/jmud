package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.EquipmentResistanceResolver;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class HazardDamageEngineTest {

    private static final RoomId LAVA = RoomId.of("lava-room");
    private static final RoomId SAFE = RoomId.of("safe-room");
    private static final Username HERO = Username.of("hero");

    @Test
    void appliesDamageEachTickToPresentPlayer() {
        World world = new World();
        world.place(HERO, LAVA, healthyPlayer(HERO, PlayerEquipment.empty()));
        StubRoomRepository rooms = roomsWith(hazardRoom(LAVA, DamageType.FIRE, 10, 10));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        HazardDamageEngine engine = engine(rooms, world, EquipmentResistanceResolver.noOp(), broadcaster);

        engine.tick();
        assertEquals(90, world.hp(HERO), "10 raw damage applied on the first tick");
        engine.tick();
        assertEquals(80, world.hp(HERO), "hazard damage applies again each tick");

        assertEquals(2, broadcaster.playerMessages.size(), "each bite delivers a player-facing message");
        assertEquals(HERO, broadcaster.playerMessages.get(0).target());
    }

    @Test
    void resistanceGearReducesHazardDamage() {
        World world = new World();
        PlayerEquipment fireCloak = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, ItemId.of("fire-cloak"));
        world.place(HERO, LAVA, healthyPlayer(HERO, fireCloak));
        StubRoomRepository rooms = roomsWith(hazardRoom(LAVA, DamageType.FIRE, 10, 10));
        // 50% fire resistance halves the 10 raw damage to 5.
        EquipmentResistanceResolver resolver = resolverWithResist("fire_resist", 50);
        HazardDamageEngine engine = engine(rooms, world, resolver, new RecordingBroadcaster());

        engine.tick();

        assertEquals(95, world.hp(HERO), "50% resistance halves the hazard damage");
    }

    @Test
    void resistanceNeverGrantsFullImmunity() {
        World world = new World();
        PlayerEquipment cloak = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, ItemId.of("fire-cloak"));
        world.place(HERO, LAVA, healthyPlayer(HERO, cloak));
        StubRoomRepository rooms = roomsWith(hazardRoom(LAVA, DamageType.FIRE, 1, 1));
        // 200% resistance is capped at the combat max; 1 raw damage still deals the 1-point floor.
        EquipmentResistanceResolver resolver = resolverWithResist("fire_resist", 200);
        HazardDamageEngine engine = engine(rooms, world, resolver, new RecordingBroadcaster());

        engine.tick();

        assertEquals(99, world.hp(HERO), "a resisted hazard always deals at least 1 damage");
    }

    @Test
    void hazardCanKillAndRoutesThroughTheSink() {
        World world = new World();
        world.place(HERO, LAVA, player(HERO, new PlayerVitals(3, 100, 100, 100, 100, 100), PlayerEquipment.empty()));
        StubRoomRepository rooms = roomsWith(hazardRoom(LAVA, DamageType.FIRE, 10, 10));
        HazardDamageEngine engine = engine(rooms, world, EquipmentResistanceResolver.noOp(), new RecordingBroadcaster());

        engine.tick();

        assertEquals(0, world.hp(HERO), "damage exceeding HP floors at 0");
        assertTrue(world.player(HERO).isDead(), "a player reduced to 0 HP by a hazard is dead");
    }

    @Test
    void skipsAlreadyDeadPlayers() {
        World world = new World();
        Player dead = healthyPlayer(HERO, PlayerEquipment.empty()).die();
        world.place(HERO, LAVA, dead);
        StubRoomRepository rooms = roomsWith(hazardRoom(LAVA, DamageType.FIRE, 10, 10));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        HazardDamageEngine engine = engine(rooms, world, EquipmentResistanceResolver.noOp(), broadcaster);

        engine.tick();

        assertTrue(broadcaster.playerMessages.isEmpty(), "a downed player awaiting respawn is not hit again");
    }

    @Test
    void doesNotDamagePlayersInNonHazardRooms() {
        World world = new World();
        world.place(HERO, SAFE, healthyPlayer(HERO, PlayerEquipment.empty()));
        StubRoomRepository rooms = roomsWith(
            new Room(SAFE, "Safe Room", "A calm room.", Map.of(), List.of(), List.of()));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        HazardDamageEngine engine = engine(rooms, world, EquipmentResistanceResolver.noOp(), broadcaster);

        engine.tick();

        assertEquals(100, world.hp(HERO), "inert rooms deal no damage");
        assertTrue(broadcaster.playerMessages.isEmpty());
        assertFalse(rooms.rooms.get(SAFE).hasHazard());
    }

    // --- helpers ---

    private HazardDamageEngine engine(
        StubRoomRepository rooms, World world, EquipmentResistanceResolver resolver, RecordingBroadcaster broadcaster) {
        Function<Username, Optional<Player>> lookup = username -> Optional.ofNullable(world.player(username));
        BiConsumer<Username, Player> sink = world::update;
        return new HazardDamageEngine(
            rooms, world.locations(), resolver, new LowerBoundRandom(), broadcaster, lookup, sink);
    }

    private static Room hazardRoom(RoomId id, DamageType type, int min, int max) {
        RoomHazard hazard = new RoomHazard(type, min, max, "The hazard bites you!");
        return new Room(id, "Hazard Room", "A dangerous place.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, false, List.of(), Map.of(), hazard);
    }

    private static StubRoomRepository roomsWith(Room room) {
        StubRoomRepository rooms = new StubRoomRepository();
        rooms.add(room);
        return rooms;
    }

    private Player healthyPlayer(Username username, PlayerEquipment equipment) {
        return player(username, new PlayerVitals(100, 100, 100, 100, 100, 100), equipment);
    }

    private Player player(Username username, PlayerVitals vitals, PlayerEquipment equipment) {
        Player base = new Player(
            User.of(username, Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            new ArrayList<>(),
            "prompt",
            false,
            List.of(),
            (RaceId) null,
            null
        );
        return base.withEquipment(equipment);
    }

    private EquipmentResistanceResolver resolverWithResist(String statKey, int value) {
        ItemId cloakId = ItemId.of("fire-cloak");
        Item cloak = Item.builder(cloakId, "Ward", "A ward.", new ItemAttributes(Map.of(statKey, value)))
            .equipSlot(EquipmentSlot.CHEST)
            .weight(1)
            .value(0)
            .build();
        return new EquipmentResistanceResolver(new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return id.equals(cloakId) ? Optional.of(cloak) : Optional.empty();
            }
        });
    }

    /** In-memory stand-in for the live session world: tracks a player per username and their room. */
    private static final class World {
        private final Map<Username, Player> players = new ConcurrentHashMap<>();
        private final Map<Username, RoomId> rooms = new ConcurrentHashMap<>();

        void place(Username username, RoomId room, Player player) {
            players.put(username, player);
            rooms.put(username, room);
        }

        void update(Username username, Player player) {
            players.put(username, player);
        }

        Player player(Username username) {
            return players.get(username);
        }

        int hp(Username username) {
            return players.get(username).getVitals().hp();
        }

        PlayerLocationService locations() {
            return new PlayerLocationService(new StubRoomRepository(), RoomId.of("start")) {
                @Override
                public Set<RoomId> occupiedRooms() {
                    return Set.copyOf(rooms.values());
                }

                @Override
                public List<Username> getPlayersInRoom(RoomId roomId) {
                    return rooms.entrySet().stream()
                        .filter(e -> e.getValue().equals(roomId))
                        .map(Map.Entry::getKey)
                        .toList();
                }
            };
        }
    }

    private static final class LowerBoundRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms = new HashMap<>();

        void add(Room room) {
            rooms.put(room.getId(), room);
        }

        @Override
        public void save(Room room) {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }

    private record RecordedPlayerMessage(Username target, String text) {
    }

    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<RecordedPlayerMessage> playerMessages = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            if (!(message instanceof PlainTextMessage plain)) {
                throw new IllegalStateException("Expected a PlainTextMessage, got " + message.getClass());
            }
            playerMessages.add(new RecordedPlayerMessage(target, plain.text()));
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }
    }
}
