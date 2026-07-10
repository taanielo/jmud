package io.taanielo.jmud.core.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.faction.repository.json.JsonFactionRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestReputationRewardService;
import io.taanielo.jmud.core.quest.QuestReputationRewardService.ReputationRewardGrant;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopService;
import io.taanielo.jmud.core.shop.ShopTransactionResult;
import io.taanielo.jmud.core.shop.repository.json.JsonShopRepository;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content integration test for the Kingsreach Militia faction (issue #403). Loads the real
 * {@code data/} files and verifies the militia faction exists, that several mainstream kill quests
 * grant positive militia standing, and that the Armory shop rewards that standing with cheaper prices
 * and unlocked gated stock — while a never-questing (neutral) player sees the gates closed and base
 * prices.
 */
class MilitiaFactionContentTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final FactionId MILITIA = FactionId.of("militia");
    private static final RoomId ARMORY = RoomId.of("armory");

    private static Player newPlayer() {
        User user = new User(Username.of("recruit"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void militiaFaction_isDefinedWithAPriceModifier() throws Exception {
        FactionRepository factions = new JsonFactionRepository(DATA_ROOT);

        Faction militia = factions.findById(MILITIA).orElse(null);

        assertNotNull(militia, "militia faction must be defined under data/factions/");
        assertEquals("the Kingsreach Militia", militia.name());
        assertEquals(0, militia.killReputationDelta(), "no mob belongs to the militia, so kill delta is 0");
        assertTrue(militia.priceModifierPerPoint() > 0.0,
            "militia must shift shop prices with standing, was " + militia.priceModifierPerPoint());
    }

    @Test
    void severalMainstreamQuests_grantPositiveMilitiaReputation() throws Exception {
        QuestRepositoryLike quests = quests();

        List<QuestTemplate> militiaQuests = quests.all().stream()
            .filter(q -> MILITIA.getValue().equals(q.reputationRewardFactionId()))
            .toList();

        assertTrue(militiaQuests.size() >= 6,
            "at least 6 quests should reward militia standing, found " + militiaQuests.size());
        assertTrue(militiaQuests.stream().allMatch(q -> q.reputationRewardDelta() > 0),
            "every militia quest reward must be a positive standing gain");
    }

    @Test
    void armoryShop_isMilitiaAlignedWithGatedStock() throws Exception {
        Shop armory = armory();

        assertEquals(MILITIA, armory.factionId(), "Armory must be aligned with the militia");
        long gated = armory.stock().stream().filter(e -> e.minReputation() != null).count();
        assertTrue(gated >= 3, "at least 3 Armory stock entries should be reputation-gated, found " + gated);
    }

    @Test
    void questingRaisesStanding_unlocksGatedGear_andImprovesPrices() throws Exception {
        QuestRepositoryLike quests = quests();
        Shop armory = armory();
        ReputationService reputation = new ReputationService(new JsonFactionRepository(DATA_ROOT));
        ShopService shopService = new ShopService(
            new JsonShopRepository(DATA_ROOT), new JsonItemRepository(DATA_ROOT), reputation);

        // A never-questing player: gated gear is locked, prices are at base.
        Player neutral = newPlayer().withGold(1000);
        assertEquals(0, neutral.reputation().standing(MILITIA));
        String neutralListing = String.join("\n", shopService.formatListing(armory, neutral));
        assertTrue(neutralListing.contains("[locked"),
            "gated gear must read as locked for a neutral player: " + neutralListing);
        ShopTransactionResult lockedBuy = shopService.buy(neutral, armory, "long bow");
        assertFalse(lockedBuy.success(), "a neutral player must not be able to buy gated gear");

        // Complete a handful of militia quests to build standing well past every gate.
        Player veteran = grantQuest(reputation, quests, neutral, "goblin-thrasher");
        veteran = grantQuest(reputation, quests, veteran, "spider-slayer");
        veteran = grantQuest(reputation, quests, veteran, "crypt-clearer");
        int standing = veteran.reputation().standing(MILITIA);
        assertTrue(standing >= 20,
            "three militia quests should push standing to at least 20, was " + standing);

        // Gated gear now lists unlocked and is purchasable.
        String veteranListing = String.join("\n", shopService.formatListing(armory, veteran));
        assertFalse(veteranListing.contains("[locked"),
            "no gear should be locked once standing clears every gate: " + veteranListing);
        ShopTransactionResult unlockedBuy = shopService.buy(veteran, armory, "long bow");
        assertTrue(unlockedBuy.success(), "gated gear must be purchasable at high standing: "
            + unlockedBuy.message());

        // Prices improve with standing: the veteran pays less than a neutral buyer for the same item.
        int neutralPrice = neutral.getGold() - shopService.buy(neutral, armory, "iron sword")
            .updatedPlayer().getGold();
        int veteranPrice = veteran.getGold() - shopService.buy(veteran, armory, "iron sword")
            .updatedPlayer().getGold();
        assertTrue(veteranPrice < neutralPrice,
            "friendly militia standing should discount prices: veteran " + veteranPrice
                + " vs neutral " + neutralPrice);
    }

    private static Player grantQuest(
        ReputationService reputation, QuestRepositoryLike quests, Player player, String questId) {
        QuestReputationRewardService rewards = new QuestReputationRewardService(reputation);
        QuestTemplate template = quests.byId(questId);
        ReputationRewardGrant grant = rewards.grant(player, template);
        return grant.player();
    }

    private static QuestRepositoryLike quests() throws Exception {
        JsonQuestRepository repo = new JsonQuestRepository(DATA_ROOT);
        List<QuestTemplate> all = repo.findAll();
        return new QuestRepositoryLike(all);
    }

    private static Shop armory() throws Exception {
        return new JsonShopRepository(DATA_ROOT).findByRoomId(ARMORY)
            .orElseThrow(() -> new AssertionError("Armory shop must exist in room " + ARMORY.getValue()));
    }

    private record QuestRepositoryLike(List<QuestTemplate> all) {
        QuestTemplate byId(String id) {
            return all.stream()
                .filter(q -> q.id().equals(QuestId.of(id)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("quest not found: " + id));
        }
    }
}
