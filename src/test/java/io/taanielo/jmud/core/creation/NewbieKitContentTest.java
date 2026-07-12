package io.taanielo.jmud.core.creation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.creation.json.JsonNewbieKitRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content integration test for the newbie starting kit (issue #519). Loads the real
 * {@code data/newbie-kit.json} plus the real item definitions and verifies the acceptance criteria:
 * a brand-new character can eat and drink at least twice and afford one purchased meal without having
 * killed anything.
 */
class NewbieKitContentTest {

    private static final Path DATA_ROOT = Path.of("data");

    @Test
    void shippedKitLetsANewCharacterEatDrinkTwiceAndBuyAMeal() throws Exception {
        ItemRepository itemRepository = new JsonItemRepository(DATA_ROOT);
        NewbieKit kit = new JsonNewbieKitRepository(DATA_ROOT).load();
        NewbieKitService service = new NewbieKitService(kit, itemRepository);

        Player kitted = service.applyTo(newPlayer());

        List<Item> inventory = kitted.getInventory();
        long edible = inventory.stream().filter(NewbieKitContentTest::isEdible).count();
        long drinkable = inventory.stream().filter(NewbieKitContentTest::isDrinkable).count();

        assertTrue(edible >= 2, "kit must let a new character eat at least twice, was " + edible);
        assertTrue(drinkable >= 2, "kit must let a new character drink at least twice, was " + drinkable);

        int cheapestMeal = inventory.stream()
            .filter(NewbieKitContentTest::isEdible)
            .mapToInt(Item::getValue)
            .min()
            .orElseThrow();
        assertTrue(kitted.getGold() >= cheapestMeal,
            "kit gold (" + kitted.getGold() + ") must afford at least one more meal (" + cheapestMeal + ")");
    }

    private static boolean isEdible(Item item) {
        return item.getAttributes().getStats().getOrDefault("hunger", 0) > 0;
    }

    private static boolean isDrinkable(Item item) {
        return item.getAttributes().getStats().getOrDefault("thirst", 0) > 0;
    }

    private static Player newPlayer() {
        User user = User.of(Username.of("sparky"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }
}
