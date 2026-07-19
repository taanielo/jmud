package io.taanielo.jmud.core.combat;

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
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class EquipmentArmorResolverTest {

    @Test
    void returnsZeroWhenNoArmourEquipped() throws Exception {
        EquipmentArmorResolver resolver = new EquipmentArmorResolver(emptyItemRepository());
        Player player = playerWithEquipment(PlayerEquipment.empty());

        assertEquals(0, resolver.totalAc(player));
    }

    @Test
    void returnsSinglePieceAc() throws Exception {
        ItemId capId = ItemId.of("leather-cap");
        Item cap = armourItem(capId, EquipmentSlot.HEAD, 2);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.HEAD, capId);

        EquipmentArmorResolver resolver = new EquipmentArmorResolver(stubRepository(Map.of(capId, cap)));
        Player player = playerWithEquipment(equipment);

        assertEquals(2, resolver.totalAc(player));
    }

    @Test
    void stacksMultiplePiecesAc() throws Exception {
        ItemId capId = ItemId.of("leather-cap");
        ItemId chestId = ItemId.of("leather-chest");
        ItemId legsId = ItemId.of("leather-legs");
        Item cap = armourItem(capId, EquipmentSlot.HEAD, 2);
        Item chest = armourItem(chestId, EquipmentSlot.CHEST, 5);
        Item legs = armourItem(legsId, EquipmentSlot.LEGS, 3);

        PlayerEquipment equipment = PlayerEquipment.empty()
            .equip(EquipmentSlot.HEAD, capId)
            .equip(EquipmentSlot.CHEST, chestId)
            .equip(EquipmentSlot.LEGS, legsId);

        EquipmentArmorResolver resolver = new EquipmentArmorResolver(
            stubRepository(Map.of(capId, cap, chestId, chest, legsId, legs))
        );
        Player player = playerWithEquipment(equipment);

        assertEquals(10, resolver.totalAc(player));
    }

    @Test
    void ignoresItemsWithoutAcStat() throws Exception {
        ItemId swordId = ItemId.of("iron-sword");
        Item sword = armourItem(swordId, EquipmentSlot.WEAPON, 0); // no ac stat
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.WEAPON, swordId);

        EquipmentArmorResolver resolver = new EquipmentArmorResolver(stubRepository(Map.of(swordId, sword)));
        Player player = playerWithEquipment(equipment);

        assertEquals(0, resolver.totalAc(player));
    }

    @Test
    void stacksOnTopOfRacialBonus() throws Exception {
        // Racial bonus of 3 is handled externally; equipment contributes an additional 5.
        ItemId chestId = ItemId.of("leather-chest");
        Item chest = armourItem(chestId, EquipmentSlot.CHEST, 5);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, chestId);

        EquipmentArmorResolver equipmentResolver = new EquipmentArmorResolver(
            stubRepository(Map.of(chestId, chest))
        );
        Player player = playerWithEquipment(equipment);

        int racialBonus = 3;
        int totalBonus = racialBonus + equipmentResolver.totalAc(player);

        assertEquals(8, totalBonus);
    }

    @Test
    void foldsItemSetAcBonusIntoTotal() throws Exception {
        ItemId capId = ItemId.of("leather-cap");
        ItemId chestId = ItemId.of("leather-chest");
        io.taanielo.jmud.core.world.ItemSetId setId =
            io.taanielo.jmud.core.world.ItemSetId.of("leathers");
        Item cap = Item.builder(capId, "Cap", "d", new ItemAttributes(Map.of("ac", 2)))
            .equipSlot(EquipmentSlot.HEAD).setId(setId).build();
        Item chest = Item.builder(chestId, "Chest", "d", new ItemAttributes(Map.of("ac", 5)))
            .equipSlot(EquipmentSlot.CHEST).setId(setId).build();
        io.taanielo.jmud.core.world.ItemSet set = new io.taanielo.jmud.core.world.ItemSet(
            setId, "Leathers", List.of(capId, chestId),
            List.of(new io.taanielo.jmud.core.world.ItemSetThreshold(2, Map.of("ac", 4))));

        ItemRepository items = stubRepository(Map.of(capId, cap, chestId, chest));
        SetBonusResolver setBonus = new SetBonusResolver(items, new io.taanielo.jmud.core.world.repository
            .ItemSetRepository() {
            @Override
            public Optional<io.taanielo.jmud.core.world.ItemSet> findById(
                io.taanielo.jmud.core.world.ItemSetId id) {
                return id.equals(setId) ? Optional.of(set) : Optional.empty();
            }

            @Override
            public List<io.taanielo.jmud.core.world.ItemSet> findAll() {
                return List.of(set);
            }
        });
        EquipmentArmorResolver resolver = new EquipmentArmorResolver(items, setBonus);
        Player player = playerWithEquipment(PlayerEquipment.empty()
            .equip(EquipmentSlot.HEAD, capId)
            .equip(EquipmentSlot.CHEST, chestId));

        // 2 (cap) + 5 (chest) piece AC + 4 (2-piece set bonus) = 11.
        assertEquals(11, resolver.totalAc(player));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        EquipmentArmorResolver resolver = EquipmentArmorResolver.noOp();
        Player player = playerWithEquipment(PlayerEquipment.empty());

        assertEquals(0, resolver.totalAc(player));
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

    private Item armourItem(ItemId id, EquipmentSlot slot, int ac) {
        Map<String, Integer> stats = ac > 0 ? Map.of("ac", ac) : Map.of();
        return Item.builder(id, "Test Armour", "A test armour piece.", new ItemAttributes(stats))
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
