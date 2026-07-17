package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.shop.repository.json.JsonShopRepository;

/**
 * Content smoke-test for the traveling quartermaster vendors added in issue #669: confirms each of the
 * four mid-to-endgame quartermaster NPCs loads as a non-combatant {@code blacksmith}-tagged shopkeeper
 * spawned in its target area's waypoint room, and that a general-goods shop is bound to that same room
 * with the consumable staples (potions, a scroll, food/drink, a light source) needed to make
 * {@code REPAIR}/{@code CRAFT}/{@code SALVAGE}/{@code BUY}/{@code SELL} work there.
 */
class QuartermasterVendorContentTest {

    private static final Path DATA_ROOT = Path.of("data");

    private record Quartermaster(String mobId, String roomId) {
    }

    private static final List<Quartermaster> QUARTERMASTERS = List.of(
        new Quartermaster("frozen-peaks-quartermaster", "frozen-peaks-foothills"),
        new Quartermaster("voidscar-quartermaster", "voidscar-the-rift"),
        new Quartermaster("bonelight-quartermaster", "bonelight-choir-descent"),
        new Quartermaster("undersong-quartermaster", "undersong-descent")
    );

    private static final Set<String> CONSUMABLE_STAPLES = Set.of(
        "health-potion", "scroll-of-recall", "bread", "torch"
    );

    @Test
    void everyQuartermasterIsANonCombatantBlacksmithShopkeeperInItsWaypointRoom() throws Exception {
        List<MobTemplate> mobs = new JsonMobTemplateRepository(DATA_ROOT).findAll();

        for (Quartermaster quartermaster : QUARTERMASTERS) {
            MobTemplate mob = mobs.stream()
                .filter(m -> m.id().getValue().equals(quartermaster.mobId()))
                .findFirst()
                .orElse(null);

            assertNotNull(mob, "Quartermaster mob must load: " + quartermaster.mobId());
            assertTrue(mob.hasTag("blacksmith"),
                quartermaster.mobId() + " must be tagged blacksmith to unlock REPAIR/CRAFT/SALVAGE");
            assertTrue(mob.hasTag("shopkeeper"), quartermaster.mobId() + " must be tagged shopkeeper");
            assertTrue(mob.hasTag("npc"), quartermaster.mobId() + " must be tagged npc");
            assertEquals(quartermaster.roomId(), mob.spawnRoomId().getValue(),
                quartermaster.mobId() + " must spawn in its target area's waypoint room");
            assertEquals(0, mob.xpReward(), quartermaster.mobId() + " must be a non-combatant (xp_reward 0)");
            assertNull(mob.attackId(), quartermaster.mobId() + " must have no attack");
            assertTrue(mob.lootTable().isEmpty(), quartermaster.mobId() + " must have an empty loot table");
        }
    }

    @Test
    void everyQuartermasterRoomHasAGeneralGoodsShopWithConsumableStaples() throws Exception {
        List<Shop> shops = new JsonShopRepository(DATA_ROOT).findAll();

        for (Quartermaster quartermaster : QUARTERMASTERS) {
            Shop shop = shops.stream()
                .filter(s -> s.roomId().getValue().equals(quartermaster.roomId()))
                .findFirst()
                .orElse(null);

            assertNotNull(shop, "A shop must be bound to room " + quartermaster.roomId());
            Set<String> stockedItems = shop.stock().stream()
                .map(StockEntry::itemId)
                .map(id -> id.getValue())
                .collect(Collectors.toSet());
            assertTrue(stockedItems.containsAll(CONSUMABLE_STAPLES),
                "Shop in " + quartermaster.roomId() + " must stock the consumable staples "
                    + CONSUMABLE_STAPLES + " but had " + stockedItems);
        }
    }
}
