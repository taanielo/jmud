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
import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;
import io.taanielo.jmud.core.world.ItemSetThreshold;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.ItemSetRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class SetBonusResolverTest {

    private static final ItemSetId SET_ID = ItemSetId.of("wayfarers-leathers");
    private static final ItemId CAP = ItemId.of("leather-cap");
    private static final ItemId CHEST = ItemId.of("leather-chest");
    private static final ItemId LEGS = ItemId.of("leather-legs");

    private final Item cap = setPiece(CAP, EquipmentSlot.HEAD, 2);
    private final Item chest = setPiece(CHEST, EquipmentSlot.CHEST, 5);
    private final Item legs = setPiece(LEGS, EquipmentSlot.LEGS, 3);

    private final ItemSet set = new ItemSet(SET_ID, "Wayfarer's Leathers",
        List.of(CAP, CHEST, LEGS),
        List.of(
            new ItemSetThreshold(2, Map.of("ac", 2)),
            new ItemSetThreshold(3, Map.of("ac", 3))));

    private final SetBonusResolver resolver = new SetBonusResolver(
        stubItems(Map.of(CAP, cap, CHEST, chest, LEGS, legs)),
        stubSets(Map.of(SET_ID, set)));

    @Test
    void noPiecesEquippedGrantsNoBonusAndNoProgress() {
        Player player = playerWith(PlayerEquipment.empty());

        assertTrue(resolver.bonusStats(player).isEmpty());
        assertTrue(resolver.activeSets(player).isEmpty());
    }

    @Test
    void onePieceEquippedGrantsNoBonusButShowsProgress() {
        Player player = playerWith(PlayerEquipment.empty().equip(EquipmentSlot.HEAD, CAP));

        assertTrue(resolver.bonusStats(player).isEmpty(), "no threshold met with one piece");
        List<SetBonusResolver.SetProgress> progress = resolver.activeSets(player);
        assertEquals(1, progress.size());
        assertEquals(1, progress.get(0).wornPieces());
        assertEquals(3, progress.get(0).totalPieces());
        assertTrue(progress.get(0).activeStats().isEmpty());
        assertTrue(progress.get(0).describe().contains("(1/3)"));
        assertTrue(progress.get(0).describe().contains("next 2pc"));
    }

    @Test
    void twoPiecesEquippedGrantsFirstThreshold() {
        Player player = playerWith(PlayerEquipment.empty()
            .equip(EquipmentSlot.HEAD, CAP)
            .equip(EquipmentSlot.CHEST, CHEST));

        assertEquals(2, resolver.bonusStats(player).get("ac"));
        SetBonusResolver.SetProgress progress = resolver.activeSets(player).get(0);
        assertEquals(2, progress.wornPieces());
        assertEquals(Map.of("ac", 2), progress.activeStats());
        assertTrue(progress.describe().contains("(2/3)"));
        assertTrue(progress.describe().contains("2pc: +2 AC"));
    }

    @Test
    void fullSetStacksAllMetThresholds() {
        Player player = playerWith(PlayerEquipment.empty()
            .equip(EquipmentSlot.HEAD, CAP)
            .equip(EquipmentSlot.CHEST, CHEST)
            .equip(EquipmentSlot.LEGS, LEGS));

        // 2pc (+2) and 3pc (+3) both met and stack.
        assertEquals(5, resolver.bonusStats(player).get("ac"));
        SetBonusResolver.SetProgress progress = resolver.activeSets(player).get(0);
        assertEquals(3, progress.wornPieces());
        assertEquals(Map.of("ac", 5), progress.activeStats());
        assertTrue(progress.describe().contains("3pc: +5 AC"));
    }

    @Test
    void droppingBelowThresholdRemovesBonusOnNextRead() {
        PlayerEquipment full = PlayerEquipment.empty()
            .equip(EquipmentSlot.HEAD, CAP)
            .equip(EquipmentSlot.CHEST, CHEST)
            .equip(EquipmentSlot.LEGS, LEGS);
        Player fullPlayer = playerWith(full);
        assertEquals(5, resolver.bonusStats(fullPlayer).get("ac"));

        // Swap the chest out, dropping to a single piece: the bonus is recomputed live and vanishes.
        Player reduced = playerWith(full.unequip(EquipmentSlot.CHEST).unequip(EquipmentSlot.LEGS));
        assertTrue(resolver.bonusStats(reduced).isEmpty(), "bonus must not persist below its threshold");
    }

    @Test
    void describeSetMembershipNamesSetAndPieces() {
        List<String> lines = resolver.describeSetMembership(cap);

        assertFalse(lines.isEmpty());
        assertTrue(lines.get(0).contains("Wayfarer's Leathers"));
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Leather Cap (head)"));
        assertTrue(joined.contains("2pc bonus: +2 AC"));
    }

    @Test
    void describeSetMembershipEmptyForNonSetItem() {
        Item plain = Item.builder(ItemId.of("plain"), "Plain", "desc", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.HEAD).build();
        assertTrue(resolver.describeSetMembership(plain).isEmpty());
    }

    // --- helpers ---

    private Item setPiece(ItemId id, EquipmentSlot slot, int ac) {
        return Item.builder(id, pieceName(id), "A set piece.", new ItemAttributes(Map.of("ac", ac)))
            .equipSlot(slot)
            .setId(SET_ID)
            .build();
    }

    private static String pieceName(ItemId id) {
        return switch (id.getValue()) {
            case "leather-cap" -> "Leather Cap";
            case "leather-chest" -> "Leather Chestpiece";
            case "leather-legs" -> "Leather Leggings";
            default -> id.getValue();
        };
    }

    private Player playerWith(PlayerEquipment equipment) {
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

    private ItemRepository stubItems(Map<ItemId, Item> items) {
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

    private ItemSetRepository stubSets(Map<ItemSetId, ItemSet> sets) {
        return new ItemSetRepository() {
            @Override
            public Optional<ItemSet> findById(ItemSetId id) {
                return Optional.ofNullable(sets.get(id));
            }

            @Override
            public List<ItemSet> findAll() {
                return List.copyOf(sets.values());
            }
        };
    }
}
