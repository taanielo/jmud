package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.QuestItemRewardService.ItemRewardGrant;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link QuestItemRewardService}.
 */
class QuestItemRewardServiceTest {

    private static final QuestId QUEST_ID = QuestId.of("troll-bane");
    private static final QuestTemplate NO_ITEM_QUEST =
        new QuestTemplate(QUEST_ID, "Troll Bane", "Kill the troll.", "forest-troll", 1, 100, 350);

    private final Item charm = Item.builder(
        ItemId.of("troll-tooth-charm"), "Troll Tooth Charm", "A charm.", ItemAttributes.empty())
        .weight(1)
        .build();
    private final ItemRepository itemRepository = itemRepo(charm);
    private final Player player = Player.of(
        new User(Username.of("tester"), Password.of("pass")), PromptSettings.defaultFormat());

    @Test
    void grantAddsItemToInventoryWhenPlayerCanCarryIt() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepository, encumbrance(false));
        QuestTemplate quest = NO_ITEM_QUEST.withItemReward("troll-tooth-charm", 1);

        ItemRewardGrant grant = service.grant(player, quest);

        assertEquals(1, grant.player().getInventory().size());
        assertEquals("troll-tooth-charm", grant.player().getInventory().getFirst().getId().getValue());
        assertTrue(grant.droppedItems().isEmpty());
        assertTrue(grant.messages().isEmpty());
        assertEquals("Troll Tooth Charm", grant.description());
    }

    @Test
    void grantAddsMultipleCopiesForQuantityGreaterThanOne() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepository, encumbrance(false));
        QuestTemplate quest = NO_ITEM_QUEST.withItemReward("troll-tooth-charm", 3);

        ItemRewardGrant grant = service.grant(player, quest);

        assertEquals(3, grant.player().getInventory().size());
        assertEquals("3x Troll Tooth Charm", grant.description());
    }

    @Test
    void grantDropsItemAtFeetWhenOverweight() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepository, encumbrance(true));
        QuestTemplate quest = NO_ITEM_QUEST.withItemReward("troll-tooth-charm", 1);

        ItemRewardGrant grant = service.grant(player, quest);

        assertTrue(grant.player().getInventory().isEmpty(), "Overweight item must not be carried");
        assertEquals(1, grant.droppedItems().size());
        assertEquals("troll-tooth-charm", grant.droppedItems().getFirst().getId().getValue());
        assertTrue(grant.messages().stream().anyMatch(m -> m.contains("falls to the ground at your feet")));
        assertEquals("Troll Tooth Charm", grant.description());
    }

    @Test
    void grantIsNoOpWhenQuestHasNoItemReward() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepository, encumbrance(false));

        ItemRewardGrant grant = service.grant(player, NO_ITEM_QUEST);

        assertSame(player, grant.player());
        assertTrue(grant.droppedItems().isEmpty());
        assertTrue(grant.messages().isEmpty());
        assertTrue(grant.descriptionText().isEmpty());
    }

    @Test
    void grantIsNoOpWhenItemIdCannotBeResolved() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepo(), encumbrance(false));
        QuestTemplate quest = NO_ITEM_QUEST.withItemReward("does-not-exist", 1);

        ItemRewardGrant grant = service.grant(player, quest);

        assertSame(player, grant.player());
        assertTrue(grant.droppedItems().isEmpty());
    }

    @Test
    void describeRewardFormatsQuantity() {
        QuestItemRewardService service = new QuestItemRewardService(itemRepository, encumbrance(false));

        assertEquals(Optional.of("Troll Tooth Charm"),
            service.describeReward(NO_ITEM_QUEST.withItemReward("troll-tooth-charm", 1)));
        assertEquals(Optional.of("2x Troll Tooth Charm"),
            service.describeReward(NO_ITEM_QUEST.withItemReward("troll-tooth-charm", 2)));
        assertTrue(service.describeReward(NO_ITEM_QUEST).isEmpty());
    }

    @Test
    void receiveLineWeavesInItemReward() {
        assertEquals("You receive 30 gold and 120 experience.",
            QuestItemRewardService.receiveLine(30, 120, null));
        assertEquals("You receive 30 gold, 120 experience, and Ranger's Cloak.",
            QuestItemRewardService.receiveLine(30, 120, "Ranger's Cloak"));
    }

    // ── fakes ───────────────────────────────────────────────────────────────

    private static ItemRepository itemRepo(Item... items) {
        return new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                for (Item item : items) {
                    if (item.getId().equals(id)) {
                        return Optional.of(item);
                    }
                }
                return Optional.empty();
            }
        };
    }

    private static EncumbranceService encumbrance(boolean overburdened) {
        RaceRepository races = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of();
            }
        };
        ClassRepository classes = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new EncumbranceService(races, classes) {
            @Override
            public boolean isOverburdened(Player player) {
                return overburdened;
            }
        };
    }
}
