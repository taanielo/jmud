package io.taanielo.jmud.core.guild.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildId;
import io.taanielo.jmud.core.guild.GuildRank;

class JsonGuildRepositoryTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");

    @Test
    void savesAndReloadsGuild(@TempDir Path dataRoot) throws Exception {
        Path file = dataRoot.resolve("guilds").resolve("g-1.json");
        JsonGuildRepository repository = new JsonGuildRepository(dataRoot);
        try {
            Guild guild = Guild.found(GuildId.of("g-1"), "Ironclad", ALICE)
                .withMember(BOB)
                .depositTreasury(250);
            repository.save(guild);
            waitForFile(file, true);
        } finally {
            repository.close();
        }

        JsonGuildRepository reopened = new JsonGuildRepository(dataRoot);
        try {
            List<Guild> loaded = reopened.loadAll();
            assertEquals(1, loaded.size());
            Guild g = loaded.get(0);
            assertEquals("Ironclad", g.name());
            assertEquals(ALICE, g.leaderId());
            assertEquals(2, g.memberCount());
            assertEquals(GuildRank.LEADER, g.member(ALICE).orElseThrow().rank());
            assertEquals(GuildRank.MEMBER, g.member(BOB).orElseThrow().rank());
            assertEquals(1, g.member(BOB).orElseThrow().joinOrder());
            assertEquals(250, g.treasuryGold());
        } finally {
            reopened.close();
        }
    }

    @Test
    void deleteRemovesGuildFile(@TempDir Path dataRoot) throws Exception {
        Path file = dataRoot.resolve("guilds").resolve("g-2.json");
        JsonGuildRepository repository = new JsonGuildRepository(dataRoot);
        try {
            repository.save(Guild.found(GuildId.of("g-2"), "Redhand", ALICE));
            waitForFile(file, true);
            repository.delete(GuildId.of("g-2"));
            waitForFile(file, false);
            assertFalse(Files.exists(file));
        } finally {
            repository.close();
        }
    }

    private static void waitForFile(Path path, boolean shouldExist) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path) == shouldExist) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(Files.exists(path) == shouldExist,
            "Timed out waiting for file " + path + " exists=" + shouldExist);
    }
}
