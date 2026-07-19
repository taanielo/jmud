package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.output.AnsiTextStyler;
import io.taanielo.jmud.core.output.PlainTextStyler;

/**
 * Golden-output unit tests for {@link RoomRenderer}.
 */
class RoomRendererTest {

    private static final String ESC = String.valueOf((char) 27);
    private static final RoomId ROOM_ID = RoomId.of("town-square");
    private static final RoomId NORTH_ROOM = RoomId.of("market");
    private static final RoomRenderer RENDERER = new RoomRenderer();

    private static Item item(String id, String name) {
        return Item.builder(ItemId.of(id), name, name + " description.", ItemAttributes.empty())
            .weight(1)
            .value(0)
            .build();
    }

    private static Item rareItem(String id, String name) {
        return Item.builder(ItemId.of(id), name, name + " description.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(1)
            .value(0)
            .rarity(RarityProfile.of(Rarity.RARE, List.of()))
            .build();
    }

    @Test
    void rendersRoomNameAndDescription() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square.", Map.of(), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());

        assertTrue(lines.get(0).equals("Town Square"), "First line should be room name");
        assertTrue(lines.get(1).equals("A busy square."), "Second line should be description");
    }

    @Test
    void rendersUnlockedExits() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square.",
            Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String exitsLine = lines.stream().filter(l -> l.startsWith("Exits:")).findFirst().orElse("");

        assertTrue(exitsLine.contains("north"), "Exit line should contain 'north'");
        assertFalse(exitsLine.contains("[locked]"), "Unlocked exit should not show [locked]");
    }

    @Test
    void rendersLockedExitWithSuffix() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square.",
            Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of());
        Set<Direction> locked = Set.of(Direction.NORTH);
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), locked);
        String exitsLine = lines.stream().filter(l -> l.startsWith("Exits:")).findFirst().orElse("");

        assertTrue(exitsLine.contains("north [locked]"), "Locked exit should show '[locked]' suffix");
    }

    @Test
    void rendersNoExitsWhenNone() {
        Room room = new Room(ROOM_ID, "Cave", "Dark and quiet.", Map.of(), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String exitsLine = lines.stream().filter(l -> l.startsWith("Exits:")).findFirst().orElse("");

        assertTrue(exitsLine.contains("none"), "Should show 'none' when there are no exits");
    }

    @Test
    void rendersItems() {
        Room room = new Room(ROOM_ID, "Cave", "Dark.",
            Map.of(), List.of(item("torch", "Torch"), item("sword", "Rusty Sword")),
            List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String itemsLine = lines.stream().filter(l -> l.startsWith("Items:")).findFirst().orElse("");

        assertTrue(itemsLine.contains("Torch"), "Items line should contain 'Torch'");
        assertTrue(itemsLine.contains("Rusty Sword"), "Items line should contain 'Rusty Sword'");
    }

    @Test
    void rendersContainerWithFillLevel() {
        Item apple = item("apple", "an apple");
        Item bag = Item.builder(
            ItemId.of("leather-bag"), "a leather bag", "A supple leather bag.", ItemAttributes.empty())
            .weight(1)
            .value(0)
            .container(ContainerState.of(5, List.of(apple)))
            .build();
        Room room = new Room(ROOM_ID, "Cave", "Dark.", Map.of(), List.of(bag), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String itemsLine = lines.stream().filter(l -> l.startsWith("Items:")).findFirst().orElse("");

        assertTrue(itemsLine.contains("a leather bag (1/5)"),
            "Container should be rendered with its fill level but was: " + itemsLine);
    }

    @Test
    void rendersRareItemColoredUnderAnsiStyler() {
        Room room = new Room(ROOM_ID, "Cave", "Dark.",
            Map.of(), List.of(rareItem("blade", "Runed Blade")), List.of());
        List<String> lines = RENDERER.describeRoom(
            room, Username.of("alice"), Set.of(), TimeOfDay.DAY, new AnsiTextStyler());
        String itemsLine = lines.stream().filter(l -> l.startsWith("Items:")).findFirst().orElse("");

        assertTrue(itemsLine.contains(ESC + "[36m"), "Rare item should be cyan-wrapped: " + itemsLine);
        assertTrue(itemsLine.contains("Runed Blade"), "Should still contain the item name");
    }

    @Test
    void rendersNoItemsWhenRoomIsEmpty() {
        Room room = new Room(ROOM_ID, "Cave", "Dark.", Map.of(), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String itemsLine = lines.stream().filter(l -> l.startsWith("Items:")).findFirst().orElse("");

        assertTrue(itemsLine.contains("none"), "Should show 'none' when no items are present");
    }

    @Test
    void rendersOccupantsExcludingViewer() {
        Username alice = Username.of("Alice");
        Username bob = Username.of("Bob");
        Room room = new Room(ROOM_ID, "Town Square", "Busy.", Map.of(), List.of(),
            List.of(alice, bob));
        List<String> lines = RENDERER.describeRoom(room, alice, Set.of());
        String occupantsLine = lines.stream().filter(l -> l.startsWith("Occupants:")).findFirst().orElse("");

        assertFalse(occupantsLine.contains("Alice"), "Viewer should be excluded from occupants");
        assertTrue(occupantsLine.contains("Bob"), "Other players should be listed in occupants");
    }

    @Test
    void rendersNoOccupantsWhenAlone() {
        Username alice = Username.of("Alice");
        Room room = new Room(ROOM_ID, "Town Square", "Busy.", Map.of(), List.of(), List.of(alice));
        List<String> lines = RENDERER.describeRoom(room, alice, Set.of());
        String occupantsLine = lines.stream().filter(l -> l.startsWith("Occupants:")).findFirst().orElse("");

        assertTrue(occupantsLine.contains("none"), "Should show 'none' when viewer is the only occupant");
    }

    @Test
    void exitsAreSortedAlphabetically() {
        RoomId east = RoomId.of("east");
        RoomId west = RoomId.of("west");
        Room room = new Room(ROOM_ID, "Junction", "A fork.",
            Map.of(Direction.WEST, west, Direction.EAST, east), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());
        String exitsLine = lines.stream().filter(l -> l.startsWith("Exits:")).findFirst().orElse("");

        int eastIdx = exitsLine.indexOf("east");
        int westIdx = exitsLine.indexOf("west");
        assertTrue(eastIdx < westIdx, "Exits should be sorted alphabetically (east before west)");
    }

    @Test
    void rendersDayDescriptionWhenTimeOfDayIsDay() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square by day.",
            Map.of(), List.of(), List.of(), Map.of(), null, "A quiet square by night.");
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(), TimeOfDay.DAY);

        assertTrue(lines.get(1).equals("A busy square by day."), "Day time should render the day description");
    }

    @Test
    void rendersNightDescriptionWhenTimeOfDayIsNightAndOneIsDefined() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square by day.",
            Map.of(), List.of(), List.of(), Map.of(), null, "A quiet square by night.");
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(), TimeOfDay.NIGHT);

        assertTrue(lines.get(1).equals("A quiet square by night."), "Night time should render the night description");
    }

    @Test
    void fallsBackToDayDescriptionAtNightWhenNoNightDescriptionIsDefined() {
        Room room = new Room(ROOM_ID, "Cave", "Dark and quiet.", Map.of(), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(), TimeOfDay.NIGHT);

        assertTrue(lines.get(1).equals("Dark and quiet."),
            "Rooms without a night description should render the day description at night");
    }

    @Test
    void threeArgOverloadAlwaysRendersDayDescription() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square by day.",
            Map.of(), List.of(), List.of(), Map.of(), null, "A quiet square by night.");
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());

        assertTrue(lines.get(1).equals("A busy square by day."),
            "The three-arg overload should default to the day description");
    }

    @Test
    void briefModeOmitsDescriptionLine() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square.",
            Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(),
            TimeOfDay.DAY, new PlainTextStyler(), RoomRenderer.DescriptionMode.BRIEF);

        assertTrue(lines.get(0).equals("Town Square"), "First line should still be the room name");
        assertFalse(lines.stream().anyMatch(l -> l.equals("A busy square.")),
            "Brief mode should omit the prose description line");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Exits:")),
            "Brief mode should still render the exits line");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Occupants:")),
            "Brief mode should still render the occupants line");
    }

    @Test
    void fullModeRendersDescriptionLine() {
        Room room = new Room(ROOM_ID, "Town Square", "A busy square.",
            Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of());
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(),
            TimeOfDay.DAY, new PlainTextStyler(), RoomRenderer.DescriptionMode.FULL);

        assertTrue(lines.get(1).equals("A busy square."),
            "Full mode should render the prose description line");
    }

    @Test
    void surfacesHazardWarningLineInFullMode() {
        Room room = hazardRoom();
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of());

        assertTrue(lines.stream().anyMatch(l -> l.contains("(Hazard)") && l.contains("fire")),
            "A hazardous room must plainly state its hazard in the rendered description");
    }

    @Test
    void surfacesHazardWarningLineEvenInBriefMode() {
        Room room = hazardRoom();
        List<String> lines = RENDERER.describeRoom(room, Username.of("alice"), Set.of(),
            TimeOfDay.DAY, new PlainTextStyler(), RoomRenderer.DescriptionMode.BRIEF);

        assertFalse(lines.stream().anyMatch(l -> l.equals("A dangerous place.")),
            "Brief mode still omits the prose description line");
        assertTrue(lines.stream().anyMatch(l -> l.contains("(Hazard)")),
            "The hazard warning is a safety notice and must show even in brief movement output");
    }

    private static Room hazardRoom() {
        RoomHazard hazard = new RoomHazard(
            io.taanielo.jmud.core.combat.DamageType.FIRE, 5, 10, "The heat sears you!");
        return new Room(ROOM_ID, "Lava Ledge", "A dangerous place.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, false, List.of(), Map.of(), hazard);
    }
}
