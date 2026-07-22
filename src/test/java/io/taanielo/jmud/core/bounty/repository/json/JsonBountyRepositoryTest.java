package io.taanielo.jmud.core.bounty.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bounty.Bounty;
import io.taanielo.jmud.core.bounty.BountyTargetKind;

/**
 * Verifies {@link JsonBountyRepository} loads pre-v2 mob-only bounty files unchanged and round-trips
 * both mob-target and player-target bounties under schema v2 (issue #807) — all without networking.
 */
class JsonBountyRepositoryTest {

    private Path writeStore(Path dataRoot, String json) throws Exception {
        Path dir = dataRoot.resolve("world-state");
        Files.createDirectories(dir);
        Path file = dir.resolve("bounties.json");
        Files.writeString(file, json);
        return file;
    }

    @Test
    void loadsLegacyV1MobOnlyFileAsMobBounty(@TempDir Path dataRoot) throws Exception {
        writeStore(dataRoot, """
            {
              "schema_version": 1,
              "bounties": [
                {
                  "backer": "Alice",
                  "mob_template_id": "mob.goblin",
                  "mob_name": "Goblin",
                  "reward": 100,
                  "posted_tick": 5
                }
              ]
            }
            """);

        try (JsonBountyRepository repo = new JsonBountyRepository(dataRoot)) {
            List<Bounty> loaded = repo.findAll();
            assertEquals(1, loaded.size());
            Bounty bounty = loaded.get(0);
            assertEquals(BountyTargetKind.MOB, bounty.targetKind());
            assertEquals("mob.goblin", bounty.targetId());
            assertEquals("Goblin", bounty.targetName());
            assertEquals(100, bounty.reward());
            assertEquals(5, bounty.postedTick());
        }
    }

    @Test
    void roundTripsMobAndPlayerBountiesAsV2(@TempDir Path dataRoot) throws Exception {
        Bounty mob = Bounty.onMob(Username.of("Alice"), "mob.goblin", "Goblin", 100, 5);
        Bounty player = Bounty.onPlayer(Username.of("Bob"), Username.of("Grimjaw"), 250, 7);

        try (JsonBountyRepository repo = new JsonBountyRepository(dataRoot)) {
            repo.save(List.of(mob, player));
        }

        try (JsonBountyRepository reopened = new JsonBountyRepository(dataRoot)) {
            List<Bounty> loaded = reopened.findAll();
            assertEquals(2, loaded.size());
            assertTrue(loaded.stream().anyMatch(b ->
                b.targetsMob() && b.targetId().equals("mob.goblin") && b.reward() == 100));
            assertTrue(loaded.stream().anyMatch(b ->
                b.targetsPlayer() && b.targetId().equals("Grimjaw") && b.reward() == 250));
        }
    }
}
