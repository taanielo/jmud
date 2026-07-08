package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link LightingService} visibility resolution in dark rooms.
 */
class LightingServiceTest {

    private static final RoomId ROOM_ID = RoomId.of("crypt");

    private final LightingService lightingService = new LightingService();

    private static Player playerWith(Item... items) {
        Player player = new Player(
            User.of(Username.of("digger"), Password.hash("pw", 1000)),
            1, 0, new PlayerVitals(20, 20, 20, 20, 20, 20), List.of(), "prompt", false,
            List.of(), RaceId.of("human"), ClassId.of("warrior")
        );
        for (Item item : items) {
            player = player.addItem(item);
        }
        return player;
    }

    private static Item lightSource(String id, int radius) {
        return new Item(
            ItemId.of(id), id, "A light source.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, null, List.of(), radius
        );
    }

    private static Room room(Integer lightLevel) {
        return new Room(
            ROOM_ID, "Crypt", "A dark crypt.",
            Map.of(Direction.NORTH, RoomId.of("hall")), List.of(), List.of(),
            Map.of(), null, null, lightLevel
        );
    }

    @Test
    void naturallyLitRoomIsAlwaysVisible() {
        Room lit = room(null);
        assertFalse(lit.requiresLight());
        assertTrue(lightingService.canSeeRoom(playerWith(), lit));
    }

    @Test
    void darkRoomWithoutLightSourceIsNotVisible() {
        Room dark = room(0);
        assertTrue(dark.requiresLight());
        assertFalse(lightingService.canSeeRoom(playerWith(), dark));
    }

    @Test
    void darkRoomWithCarriedTorchIsVisible() {
        Room dark = room(0);
        Player player = playerWith(lightSource("torch", 1));
        assertTrue(lightingService.canSeeRoom(player, dark));
    }

    @Test
    void droppingTheOnlyLightSourceHidesTheDarkRoomAgain() {
        Room dark = room(0);
        Item torch = lightSource("torch", 1);
        Player carrying = playerWith(torch);
        assertTrue(lightingService.canSeeRoom(carrying, dark));

        Player afterDrop = carrying.removeItem(torch);
        assertFalse(lightingService.canSeeRoom(afterDrop, dark));
    }

    @Test
    void dimRoomStaysDarkWithOnlyARadiusOneTorch() {
        Room dim = room(1);
        assertEquals(2, dim.requiredLightRadius());
        Player withTorch = playerWith(lightSource("torch", 1));
        assertFalse(lightingService.canSeeRoom(withTorch, dim),
            "A radius-1 torch is too weak to light a dim (level 1) room");
    }

    @Test
    void dimRoomIsVisibleWithARadiusTwoLantern() {
        Room dim = room(1);
        Player withLantern = playerWith(lightSource("lantern", 2));
        assertTrue(lightingService.canSeeRoom(withLantern, dim));
    }

    @Test
    void carriedLightRadiusReportsTheBrightestSource() {
        Player player = playerWith(lightSource("torch", 1), lightSource("lantern", 2));
        assertEquals(2, lightingService.carriedLightRadius(player));
        assertTrue(lightingService.brightestLightSource(player).isPresent());
        assertEquals("lantern", lightingService.brightestLightSource(player).get().getId().getValue());
    }

    @Test
    void carriedLightRadiusIsZeroWithoutLightSources() {
        Player player = playerWith();
        assertEquals(0, lightingService.carriedLightRadius(player));
        assertTrue(lightingService.brightestLightSource(player).isEmpty());
    }

    @Test
    void darknessLinesContainTheDarknessMessage() {
        assertEquals(List.of(LightingService.DARKNESS_MESSAGE), lightingService.darknessLines());
    }
}
