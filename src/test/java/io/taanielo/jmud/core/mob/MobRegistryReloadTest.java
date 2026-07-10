package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.reload.PreparedReload;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;

/**
 * Verifies {@link MobRegistry#prepareMobs()} reads templates off the tick thread and that committing
 * the prepared reload swaps the registry's cached templates so newly added templates become
 * spawnable (issue #349).
 */
class MobRegistryReloadTest {

    private static final RoomId ROOM = RoomId.of("training-yard");

    private static MobTemplate template(String id, RoomId room) {
        return new MobTemplate(
            MobId.of(id),
            id,
            20,
            AttackId.of("punch"),
            null,
            false,
            List.of(),
            room,
            1,
            10,
            5,
            null,
            List.of(),
            false);
    }

    @Test
    void commitAddsNewlyDefinedTemplateToRegistry() throws Exception {
        RoomService roomService = new RoomService(new InMemoryRoomRepository(), ROOM);
        // The stub template repository returns the same mutable list on every findAll(), so appending
        // to it simulates a new mob JSON file appearing on disk between reloads.
        List<MobTemplate> templates = new ArrayList<>();
        templates.add(template("goblin", ROOM));

        MobRegistry registry = MobRegistryTestFactory.create(roomService, templates);
        assertTrue(registry.spawnInstance(MobId.of("orc"), ROOM).isEmpty(), "orc is not defined yet");

        templates.add(template("orc", ROOM));

        PreparedReload prepared = registry.prepareMobs();
        assertEquals("mobs", prepared.contentType());
        assertEquals(2, prepared.count());
        // Prepare alone must not change the live template cache.
        assertTrue(registry.spawnInstance(MobId.of("orc"), ROOM).isEmpty(),
            "prepare must not mutate live templates");

        prepared.commit();

        assertTrue(registry.spawnInstance(MobId.of("orc"), ROOM).isPresent(),
            "committed reload should make the new template spawnable");
    }
}
