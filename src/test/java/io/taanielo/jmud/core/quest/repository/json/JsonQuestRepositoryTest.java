package io.taanielo.jmud.core.quest.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.quest.QuestType;

/**
 * Unit tests for {@link JsonQuestRepository} exploration-quest loading (schema version 4).
 */
class JsonQuestRepositoryTest {

    @Test
    void loadsExplorationQuestWithRequiredRooms(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Path questsDir = Files.createDirectories(dataRoot.resolve("quests"));
        Files.writeString(questsDir.resolve("quest.explore-catacombs.json"), """
            {
              "schema_version": 4,
              "type": "exploration",
              "id": "explore-catacombs",
              "name": "Into the Catacombs",
              "description": "Chart every chamber of the Catacombs.",
              "required_room_ids": ["catacombs-entrance", "ossuary-hall", "burial-alcove"],
              "gold_reward": 60,
              "xp_reward": 150,
              "title_reward": "Catacomb Cartographer"
            }
            """);

        JsonQuestRepository repository = new JsonQuestRepository(dataRoot);
        QuestTemplate template = repository.findById(QuestId.of("explore-catacombs")).orElseThrow();

        assertEquals(QuestType.EXPLORATION, template.type());
        assertTrue(template.isExplorationQuest());
        assertEquals(
            java.util.List.of("catacombs-entrance", "ossuary-hall", "burial-alcove"),
            template.requiredRoomIds());
        assertEquals(60, template.goldReward());
        assertEquals(150, template.xpReward());
        assertEquals("Catacomb Cartographer", template.titleReward());
    }

    @Test
    void loadsItemRewardWithDefaultQuantity(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Path questsDir = Files.createDirectories(dataRoot.resolve("quests"));
        Files.writeString(questsDir.resolve("quest.troll-bane.json"), """
            {
              "schema_version": 1,
              "id": "troll-bane",
              "name": "Troll Bane",
              "description": "Slay the forest troll.",
              "target_mob_id": "forest-troll",
              "required_kills": 1,
              "gold_reward": 100,
              "xp_reward": 350,
              "item_reward": "troll-tooth-charm"
            }
            """);

        JsonQuestRepository repository = new JsonQuestRepository(dataRoot);
        QuestTemplate template = repository.findById(QuestId.of("troll-bane")).orElseThrow();

        assertTrue(template.hasItemReward());
        assertEquals("troll-tooth-charm", template.itemReward());
        assertEquals(1, template.itemRewardQuantity());
    }

    @Test
    void loadsReputationReward(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Path questsDir = Files.createDirectories(dataRoot.resolve("quests"));
        Files.writeString(questsDir.resolve("quest.bandit-hunter.json"), """
            {
              "schema_version": 1,
              "id": "bandit-hunter",
              "name": "Bandit Hunter",
              "description": "Cut down bandits.",
              "target_mob_id": "bandit",
              "required_kills": 6,
              "gold_reward": 90,
              "xp_reward": 220,
              "reputation_reward_faction_id": "bandits",
              "reputation_reward_delta": -25
            }
            """);

        JsonQuestRepository repository = new JsonQuestRepository(dataRoot);
        QuestTemplate template = repository.findById(QuestId.of("bandit-hunter")).orElseThrow();

        assertTrue(template.hasReputationReward());
        assertEquals("bandits", template.reputationRewardFactionId());
        assertEquals(-25, template.reputationRewardDelta());
    }

    @Test
    void loadsItemRewardWithExplicitQuantity(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Path questsDir = Files.createDirectories(dataRoot.resolve("quests"));
        Files.writeString(questsDir.resolve("quest.stock-up.json"), """
            {
              "schema_version": 1,
              "id": "stock-up",
              "name": "Stock Up",
              "description": "Slay rats.",
              "target_mob_id": "rat",
              "required_kills": 3,
              "gold_reward": 10,
              "xp_reward": 20,
              "item_reward": "health-potion",
              "item_reward_quantity": 5
            }
            """);

        JsonQuestRepository repository = new JsonQuestRepository(dataRoot);
        QuestTemplate template = repository.findById(QuestId.of("stock-up")).orElseThrow();

        assertEquals("health-potion", template.itemReward());
        assertEquals(5, template.itemRewardQuantity());
    }
}
