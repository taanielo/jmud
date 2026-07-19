package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class EquipmentCarryResolverTest {

    @Test
    void returnsZeroWhenNoBackpackEquipped() {
        EquipmentCarryResolver resolver = new EquipmentCarryResolver(emptyItemRepository());
        Player player = playerWithEquipment(PlayerEquipment.empty());

        assertEquals(0, resolver.totalCarry(player));
    }

    @Test
    void returnsCarryBonusOfEquippedBackpack() {
        ItemId packId = ItemId.of("leather-backpack");
        Item pack = backpackItem(packId, 18);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.BACK, packId);

        EquipmentCarryResolver resolver = new EquipmentCarryResolver(stubRepository(Map.of(packId, pack)));
        Player player = playerWithEquipment(equipment);

        assertEquals(18, resolver.totalCarry(player));
    }

    @Test
    void ignoresEquippedItemsWithoutCarryStat() {
        ItemId capId = ItemId.of("leather-cap");
        Item cap = armourItem(capId, EquipmentSlot.HEAD);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.HEAD, capId);

        EquipmentCarryResolver resolver = new EquipmentCarryResolver(stubRepository(Map.of(capId, cap)));
        Player player = playerWithEquipment(equipment);

        assertEquals(0, resolver.totalCarry(player));
    }

    @Test
    void contributesZeroWhenEquippedItemMissingFromRepository() {
        ItemId packId = ItemId.of("leather-backpack");
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.BACK, packId);

        // The repository resolves nothing for the equipped id (e.g. deleted item file).
        EquipmentCarryResolver resolver = new EquipmentCarryResolver(emptyItemRepository());
        Player player = playerWithEquipment(equipment);

        assertEquals(0, resolver.totalCarry(player));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        ItemId packId = ItemId.of("leather-backpack");
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.BACK, packId);
        EquipmentCarryResolver resolver = EquipmentCarryResolver.noOp();

        assertEquals(0, resolver.totalCarry(playerWithEquipment(equipment)));
    }

    // --- helpers ---

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

    private Item backpackItem(ItemId id, int carry) {
        return Item.builder(id, "a test pack", "A test carrying pack.", new ItemAttributes(Map.of("carry", carry)))
            .equipSlot(EquipmentSlot.BACK)
            .weight(2)
            .value(30)
            .build();
    }

    private Item armourItem(ItemId id, EquipmentSlot slot) {
        return Item.builder(id, "Test Armour", "A test armour piece.", new ItemAttributes(Map.of("ac", 2)))
            .equipSlot(slot)
            .weight(1)
            .value(0)
            .build();
    }

    private ItemRepository emptyItemRepository() {
        return new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return Optional.empty();
            }
        };
    }

    private ItemRepository stubRepository(Map<ItemId, Item> items) {
        return new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) throws RepositoryException {
                return Optional.ofNullable(items.get(id));
            }
        };
    }
}
