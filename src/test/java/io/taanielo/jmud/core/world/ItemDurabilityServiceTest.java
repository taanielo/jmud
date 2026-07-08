package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;

/**
 * Unit tests for {@link ItemDurabilityService}.
 */
class ItemDurabilityServiceTest {

    private final ItemDurabilityService service = new ItemDurabilityService(1);

    private static Item sword(int maxDurability, Integer durability) {
        return Item.builder(ItemId.of("iron-sword"), "Iron Sword", "A blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .durability(Durability.of(maxDurability, durability))
            .build();
    }

    private static Item unbreakable() {
        return Item.builder(ItemId.of("torch"), "a torch", "A torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
    }

    private static Player playerWithEquippedSword(Item swordItem, int gold) {
        Player player = Player.of(
            User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt");
        player = player.addItem(swordItem).withGold(gold);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.WEAPON, swordItem.getId());
        return player.withEquipment(equipment);
    }

    @Test
    void degradeReducesDurabilityByPoints() {
        Item worn = service.degrade(sword(50, 50), 3);
        assertEquals(47, worn.getDurability());
    }

    @Test
    void degradeStopsAtZeroAndMarksBroken() {
        Item worn = service.degrade(sword(2, 1), 5);
        assertEquals(0, worn.getDurability());
        assertTrue(worn.isBroken());
    }

    @Test
    void degradeLeavesUnbreakableItemsUnchanged() {
        Item torch = unbreakable();
        assertEquals(torch, service.degrade(torch, 5));
    }

    @Test
    void brokenItemIsNotUsableInCombat() {
        assertFalse(service.isUsableInCombat(sword(50, 0)));
        assertTrue(service.isUsableInCombat(sword(50, 1)));
        assertTrue(service.isUsableInCombat(unbreakable()));
    }

    @Test
    void repairRestoresFullDurability() {
        Item repaired = service.repair(sword(50, 3));
        assertEquals(50, repaired.getDurability());
        assertFalse(repaired.isBroken());
    }

    @Test
    void repairCostIsProportionalToValueAndDamage() {
        // value 100, missing 25 of 50 => 100 * 25/50 * 0.1 = 5
        assertEquals(5, service.calculateRepairCost(sword(50, 25)));
        // fully repaired => no cost
        assertEquals(0, service.calculateRepairCost(sword(50, 50)));
        // any wear costs at least 1 gold
        assertEquals(1, service.calculateRepairCost(sword(50, 49)));
        // unbreakable => no cost
        assertEquals(0, service.calculateRepairCost(unbreakable()));
    }

    @Test
    void constructorRejectsNonPositiveDegradeRate() {
        assertThrows(IllegalArgumentException.class, () -> new ItemDurabilityService(0));
    }

    @Test
    void degradeEquippedWearsDownWornGear() {
        Player player = playerWithEquippedSword(sword(50, 50), 0);
        ItemDurabilityService.DegradeResult result = service.degradeEquipped(player);
        Item worn = result.player().getInventory().get(0);
        assertEquals(49, worn.getDurability());
        assertTrue(result.messages().isEmpty(), "no message until the item breaks");
    }

    @Test
    void degradeEquippedAnnouncesBreak() {
        Player player = playerWithEquippedSword(sword(50, 1), 0);
        ItemDurabilityService.DegradeResult result = service.degradeEquipped(player);
        Item worn = result.player().getInventory().get(0);
        assertTrue(worn.isBroken());
        assertEquals(1, result.messages().size());
        assertTrue(result.messages().get(0).contains("breaks"));
    }

    @Test
    void degradeEquippedIgnoresUnequippedItems() {
        Player player = Player.of(
            User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt")
            .addItem(sword(50, 50));
        // Not equipped => untouched.
        ItemDurabilityService.DegradeResult result = service.degradeEquipped(player);
        assertEquals(50, result.player().getInventory().get(0).getDurability());
    }

    @Test
    void repairPlayerItemChargesGoldAndRestores() {
        Player player = playerWithEquippedSword(sword(50, 25), 100);
        ItemDurabilityService.RepairOutcome outcome = service.repair(player, "iron sword");
        assertTrue(outcome.success());
        assertEquals(95, outcome.updatedPlayer().getGold());
        assertEquals(50, outcome.updatedPlayer().getInventory().get(0).getDurability());
    }

    @Test
    void repairPlayerItemFailsWhenTooPoor() {
        Player player = playerWithEquippedSword(sword(50, 0), 1);
        ItemDurabilityService.RepairOutcome outcome = service.repair(player, "iron sword");
        assertFalse(outcome.success());
        assertTrue(outcome.message().contains("gold"));
    }

    @Test
    void repairPlayerItemFailsForUnbreakableItem() {
        Player player = Player.of(
            User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt")
            .addItem(unbreakable()).withGold(100);
        ItemDurabilityService.RepairOutcome outcome = service.repair(player, "torch");
        assertFalse(outcome.success());
        assertTrue(outcome.message().contains("cannot be repaired"));
    }

    @Test
    void repairPlayerItemFailsWhenAlreadyPerfect() {
        Player player = playerWithEquippedSword(sword(50, 50), 100);
        ItemDurabilityService.RepairOutcome outcome = service.repair(player, "iron sword");
        assertFalse(outcome.success());
        assertTrue(outcome.message().contains("perfect"));
    }

    @Test
    void repairPlayerItemFailsWhenNotCarried() {
        Player player = Player.of(
            User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt").withGold(100);
        ItemDurabilityService.RepairOutcome outcome = service.repair(player, "iron sword");
        assertFalse(outcome.success());
        assertTrue(outcome.message().contains("not carrying"));
    }
}
