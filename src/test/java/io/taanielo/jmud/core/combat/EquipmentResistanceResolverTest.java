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

class EquipmentResistanceResolverTest {

    @Test
    void physicalDamageIsNeverResisted() {
        ItemId cloakId = ItemId.of("resist-cloak");
        Item cloak = resistItem(cloakId, EquipmentSlot.CHEST, "fire_resist", 25);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, cloakId);

        EquipmentResistanceResolver resolver =
            new EquipmentResistanceResolver(stubRepository(Map.of(cloakId, cloak)));

        assertEquals(0, resolver.totalResistance(playerWithEquipment(equipment), DamageType.PHYSICAL));
    }

    @Test
    void returnsSinglePieceResistance() {
        ItemId cloakId = ItemId.of("fire-cloak");
        Item cloak = resistItem(cloakId, EquipmentSlot.CHEST, "fire_resist", 25);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, cloakId);

        EquipmentResistanceResolver resolver =
            new EquipmentResistanceResolver(stubRepository(Map.of(cloakId, cloak)));

        assertEquals(25, resolver.totalResistance(playerWithEquipment(equipment), DamageType.FIRE));
    }

    @Test
    void stacksMatchingResistanceAcrossSlots() {
        ItemId neckId = ItemId.of("cold-amulet");
        ItemId ringId = ItemId.of("cold-ring");
        Item amulet = resistItem(neckId, EquipmentSlot.NECK, "cold_resist", 20);
        Item ring = resistItem(ringId, EquipmentSlot.FINGER, "cold_resist", 15);
        PlayerEquipment equipment = PlayerEquipment.empty()
            .equip(EquipmentSlot.NECK, neckId)
            .equip(EquipmentSlot.FINGER, ringId);

        EquipmentResistanceResolver resolver =
            new EquipmentResistanceResolver(stubRepository(Map.of(neckId, amulet, ringId, ring)));

        assertEquals(35, resolver.totalResistance(playerWithEquipment(equipment), DamageType.COLD));
    }

    @Test
    void ignoresNonMatchingResistanceType() {
        ItemId cloakId = ItemId.of("fire-cloak");
        Item cloak = resistItem(cloakId, EquipmentSlot.CHEST, "fire_resist", 25);
        PlayerEquipment equipment = PlayerEquipment.empty().equip(EquipmentSlot.CHEST, cloakId);

        EquipmentResistanceResolver resolver =
            new EquipmentResistanceResolver(stubRepository(Map.of(cloakId, cloak)));

        assertEquals(0, resolver.totalResistance(playerWithEquipment(equipment), DamageType.COLD));
    }

    @Test
    void resolverDoesNotApplyTheCapItself() {
        ItemId a = ItemId.of("fire-a");
        ItemId b = ItemId.of("fire-b");
        Item first = resistItem(a, EquipmentSlot.CHEST, "fire_resist", 60);
        Item second = resistItem(b, EquipmentSlot.HEAD, "fire_resist", 60);
        PlayerEquipment equipment = PlayerEquipment.empty()
            .equip(EquipmentSlot.CHEST, a)
            .equip(EquipmentSlot.HEAD, b);

        EquipmentResistanceResolver resolver =
            new EquipmentResistanceResolver(stubRepository(Map.of(a, first, b, second)));

        // The uncapped sum is returned; CombatEngine applies the cap at the point of mitigation.
        assertEquals(120, resolver.totalResistance(playerWithEquipment(equipment), DamageType.FIRE));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        EquipmentResistanceResolver resolver = EquipmentResistanceResolver.noOp();
        assertEquals(0, resolver.totalResistance(playerWithEquipment(PlayerEquipment.empty()), DamageType.FIRE));
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

    private Item resistItem(ItemId id, EquipmentSlot slot, String statKey, int value) {
        return Item.builder(id, "Test Ward", "A test ward.", new ItemAttributes(Map.of(statKey, value)))
            .equipSlot(slot)
            .weight(1)
            .value(0)
            .build();
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
