package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for the corpse lookup and targeted-removal methods added for the Cleric
 * resurrection spell: {@link RoomItemService#findCorpseByOwner(String)} and
 * {@link RoomItemService#removeCorpse(Corpse)}.
 */
class RoomItemServiceCorpseLookupTest {

    private static final RoomId ROOM = RoomId.of("crypt");

    @Test
    void findCorpseByOwnerMatchesCaseInsensitively() {
        RoomItemService service = new RoomItemService();
        Corpse spawned = service.spawnCorpse(Username.of("Ghost"), ROOM, 17);

        Optional<Corpse> found = service.findCorpseByOwner("ghost");

        assertTrue(found.isPresent());
        assertEquals(spawned.itemId(), found.get().itemId());
        assertEquals(17, found.get().gold());
    }

    @Test
    void findCorpseByOwnerReturnsMostRecentWhenPlayerDiedMultipleTimes() throws InterruptedException {
        RoomItemService service = new RoomItemService();
        service.spawnCorpse(Username.of("hero"), ROOM, 5);
        Thread.sleep(2);
        Corpse latest = service.spawnCorpse(Username.of("hero"), ROOM, 25);

        Optional<Corpse> found = service.findCorpseByOwner("hero");

        assertTrue(found.isPresent());
        assertEquals(latest.itemId(), found.get().itemId());
        assertEquals(25, found.get().gold());
    }

    @Test
    void findCorpseByOwnerReturnsEmptyForUnknownOrBlankName() {
        RoomItemService service = new RoomItemService();
        service.spawnCorpse(Username.of("hero"), ROOM, 5);

        assertTrue(service.findCorpseByOwner("nobody").isEmpty());
        assertTrue(service.findCorpseByOwner("  ").isEmpty());
    }

    @Test
    void removeCorpseRemovesTrackingAndGroundItem() {
        RoomItemService service = new RoomItemService();
        Corpse corpse = service.spawnCorpse(Username.of("hero"), ROOM, 9);
        assertFalse(service.getTransientItems(ROOM).isEmpty(), "corpse item present before removal");

        boolean removed = service.removeCorpse(corpse);

        assertTrue(removed);
        assertTrue(service.findCorpseByOwner("hero").isEmpty(), "corpse no longer tracked");
        assertTrue(service.getTransientItems(ROOM).isEmpty(), "corpse ground item cleared");
    }

    @Test
    void removeCorpseReturnsFalseWhenNotTracked() {
        RoomItemService service = new RoomItemService();
        Corpse corpse = service.spawnCorpse(Username.of("hero"), ROOM, 9);
        service.removeCorpse(corpse);

        assertFalse(service.removeCorpse(corpse), "second removal reports nothing removed");
    }
}
