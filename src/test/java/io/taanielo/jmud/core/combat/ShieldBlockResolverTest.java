package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

class ShieldBlockResolverTest {

    @Test
    void returnsNoneWhenOffhandEmpty() {
        ShieldBlockResolver resolver = new ShieldBlockResolver(stubRepository(Map.of()));
        Player player = playerWithEquipment(PlayerEquipment.empty());

        ShieldBlockResolver.ShieldBlock block = resolver.resolve(player);

        assertFalse(block.canBlock());
        assertEquals(0, block.chancePercent());
    }

    @Test
    void returnsNoneWhenOffhandItemHasNoBlockStat() {
        ItemId charmId = ItemId.of("charm");
        Item charm = offhandItem(charmId, Map.of("strength", 1));
        ShieldBlockResolver resolver = new ShieldBlockResolver(stubRepository(Map.of(charmId, charm)));
        Player player = playerWithEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, charmId));

        assertFalse(resolver.resolve(player).canBlock());
    }

    @Test
    void resolvesBlockChanceAndReductionFromOffhandShield() {
        ItemId shieldId = ItemId.of("shield");
        Item shield = offhandItem(shieldId, Map.of("block_chance", 30, "block_reduction", 40));
        ShieldBlockResolver resolver = new ShieldBlockResolver(stubRepository(Map.of(shieldId, shield)));
        Player player = playerWithEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, shieldId));

        ShieldBlockResolver.ShieldBlock block = resolver.resolve(player);

        assertTrue(block.canBlock());
        assertEquals(30, block.chancePercent());
        assertEquals(40, block.reductionPercent());
    }

    @Test
    void usesDefaultReductionWhenReductionStatAbsent() {
        ItemId shieldId = ItemId.of("shield");
        Item shield = offhandItem(shieldId, Map.of("block_chance", 20));
        ShieldBlockResolver resolver = new ShieldBlockResolver(stubRepository(Map.of(shieldId, shield)));
        Player player = playerWithEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, shieldId));

        ShieldBlockResolver.ShieldBlock block = resolver.resolve(player);

        assertTrue(block.canBlock());
        assertEquals(CombatSettings.defaultBlockReductionPercent(), block.reductionPercent());
    }

    @Test
    void ignoresBlockStatOnNonOffhandSlots() {
        // A block_chance stat on a chest piece must not grant a block; only the off-hand slot counts.
        ItemId chestId = ItemId.of("chest");
        Item chest = Item.builder(chestId, "Odd Chest", "A chest piece.",
                new ItemAttributes(Map.of("block_chance", 90)))
            .equipSlot(EquipmentSlot.CHEST).weight(1).value(0).build();
        ShieldBlockResolver resolver = new ShieldBlockResolver(stubRepository(Map.of(chestId, chest)));
        Player player = playerWithEquipment(PlayerEquipment.empty().equip(EquipmentSlot.CHEST, chestId));

        assertFalse(resolver.resolve(player).canBlock());
    }

    @Test
    void noOpResolverNeverBlocks() {
        Player player = playerWithEquipment(PlayerEquipment.empty());
        assertFalse(ShieldBlockResolver.noOp().resolve(player).canBlock());
    }

    private Player playerWithEquipment(PlayerEquipment equipment) {
        Player base = new Player(
            User.of(Username.of("hero"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(),
            (RaceId) null,
            null
        );
        return base.withEquipment(equipment);
    }

    private Item offhandItem(ItemId id, Map<String, Integer> stats) {
        return Item.builder(id, "Off-hand", "An off-hand item.", new ItemAttributes(stats))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).build();
    }

    private ItemRepository stubRepository(Map<ItemId, Item> items) {
        return new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return Optional.ofNullable(items.get(id));
            }
        };
    }
}
