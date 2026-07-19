package io.taanielo.jmud.core.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link BankService}.
 *
 * <p>All tests run without networking or file I/O — the repository is stubbed in-memory.
 */
class BankServiceTest {

    private static final RoomId BANK_ROOM = RoomId.of("courtyard");
    private static final RoomId OTHER_ROOM = RoomId.of("training-yard");

    private static final Bank BANK = new Bank(
        BankId.of("town-bank"), "Aldric the Banker", BANK_ROOM);

    private BankService bankService;

    @BeforeEach
    void setUp() {
        bankService = new BankService(new StubBankRepository(BANK));
    }

    // ── findBankInRoom ─────────────────────────────────────────────────

    @Test
    void findBankInRoom_returnsBank_whenPresent() {
        Optional<Bank> result = bankService.findBankInRoom(BANK_ROOM);
        assertTrue(result.isPresent());
        assertEquals("Aldric the Banker", result.get().name());
    }

    @Test
    void findBankInRoom_returnsEmpty_whenNoBankInRoom() {
        Optional<Bank> result = bankService.findBankInRoom(OTHER_ROOM);
        assertTrue(result.isEmpty());
    }

    // ── deposit ───────────────────────────────────────────────────────

    @Test
    void deposit_success_movesGoldFromCarriedToBanked() {
        Player player = playerWithGold(100, 0);
        BankTransactionResult result = bankService.deposit(player, 60);

        assertTrue(result.success());
        assertNotNull(result.updatedPlayer());
        assertEquals(40, result.updatedPlayer().getGold(), "carried gold should decrease by deposit amount");
        assertEquals(60, result.updatedPlayer().getBankedGold(), "banked gold should increase by deposit amount");
    }

    @Test
    void deposit_success_addsToExistingBankedGold() {
        Player player = playerWithGold(50, 200);
        BankTransactionResult result = bankService.deposit(player, 50);

        assertTrue(result.success());
        assertEquals(0, result.updatedPlayer().getGold());
        assertEquals(250, result.updatedPlayer().getBankedGold());
    }

    @Test
    void deposit_failure_insufficientCarriedGold() {
        Player player = playerWithGold(30, 0);
        BankTransactionResult result = bankService.deposit(player, 50);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().contains("30"), "message should mention current balance");
    }

    @Test
    void deposit_failure_zeroAmount() {
        Player player = playerWithGold(100, 0);
        BankTransactionResult result = bankService.deposit(player, 0);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    @Test
    void deposit_failure_negativeAmount() {
        Player player = playerWithGold(100, 0);
        BankTransactionResult result = bankService.deposit(player, -10);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    // ── withdraw ──────────────────────────────────────────────────────

    @Test
    void withdraw_success_movesGoldFromBankedToCarried() {
        Player player = playerWithGold(0, 200);
        BankTransactionResult result = bankService.withdraw(player, 75);

        assertTrue(result.success());
        assertNotNull(result.updatedPlayer());
        assertEquals(75, result.updatedPlayer().getGold(), "carried gold should increase by withdraw amount");
        assertEquals(125, result.updatedPlayer().getBankedGold(), "banked gold should decrease by withdraw amount");
    }

    @Test
    void withdraw_success_addsToExistingCarriedGold() {
        Player player = playerWithGold(50, 100);
        BankTransactionResult result = bankService.withdraw(player, 100);

        assertTrue(result.success());
        assertEquals(150, result.updatedPlayer().getGold());
        assertEquals(0, result.updatedPlayer().getBankedGold());
    }

    @Test
    void withdraw_failure_insufficientBankedGold() {
        Player player = playerWithGold(0, 20);
        BankTransactionResult result = bankService.withdraw(player, 50);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().contains("20"), "message should mention current balance");
    }

    @Test
    void withdraw_failure_zeroAmount() {
        Player player = playerWithGold(0, 100);
        BankTransactionResult result = bankService.withdraw(player, 0);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    @Test
    void withdraw_failure_negativeAmount() {
        Player player = playerWithGold(0, 100);
        BankTransactionResult result = bankService.withdraw(player, -5);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    // ── gold conservation ─────────────────────────────────────────────

    @Test
    void deposit_goldTotalIsConserved() {
        Player player = playerWithGold(100, 50);
        int totalBefore = player.getGold() + player.getBankedGold();

        BankTransactionResult result = bankService.deposit(player, 40);

        assertTrue(result.success());
        int totalAfter = result.updatedPlayer().getGold() + result.updatedPlayer().getBankedGold();
        assertEquals(totalBefore, totalAfter, "no gold should be created or destroyed on deposit");
    }

    @Test
    void withdraw_goldTotalIsConserved() {
        Player player = playerWithGold(20, 80);
        int totalBefore = player.getGold() + player.getBankedGold();

        BankTransactionResult result = bankService.withdraw(player, 30);

        assertTrue(result.success());
        int totalAfter = result.updatedPlayer().getGold() + result.updatedPlayer().getBankedGold();
        assertEquals(totalBefore, totalAfter, "no gold should be created or destroyed on withdraw");
    }

    // ── storeItem ─────────────────────────────────────────────────────

    @Test
    void storeItem_success_movesItemFromInventoryToVault() {
        Item sword = item("sword", "a sword", 5);
        Player player = playerCarrying(sword);

        BankTransactionResult result = bankService.storeItem(player, "a sword");

        assertTrue(result.success());
        Player updated = result.updatedPlayer();
        assertTrue(updated.getInventory().isEmpty(), "item should leave inventory");
        assertEquals(1, updated.getBankedItems().size(), "item should be in the vault");
        assertEquals("a sword", updated.getBankedItems().get(0).getName());
    }

    @Test
    void storeItem_failure_itemNotFound() {
        Player player = playerCarrying(item("sword", "a sword", 5));

        BankTransactionResult result = bankService.storeItem(player, "a shield");

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    @Test
    void storeItem_failure_vaultFull() {
        BankService smallVault = new BankService(new StubBankRepository(BANK), 1);
        Player player = playerCarrying(item("sword", "a sword", 5), item("shield", "a shield", 8));
        // Fill the single vault slot.
        Player afterFirst = smallVault.storeItem(player, "a sword").updatedPlayer();

        BankTransactionResult result = smallVault.storeItem(afterFirst, "a shield");

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().contains("full"), "message should mention the vault is full");
    }

    // ── claimItem ─────────────────────────────────────────────────────

    @Test
    void claimItem_success_movesItemFromVaultToInventory() {
        Item sword = item("sword", "a sword", 5);
        Player player = Player.of(user(), "{hp}hp>").addBankedItem(sword);

        BankTransactionResult result = bankService.claimItem(player, "a sword", 100);

        assertTrue(result.success());
        Player updated = result.updatedPlayer();
        assertTrue(updated.getBankedItems().isEmpty(), "item should leave the vault");
        assertEquals(1, updated.getInventory().size(), "item should return to inventory");
    }

    @Test
    void claimItem_failure_notInVault() {
        Player player = Player.of(user(), "{hp}hp>").addBankedItem(item("sword", "a sword", 5));

        BankTransactionResult result = bankService.claimItem(player, "a shield", 100);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    @Test
    void claimItem_failure_wouldExceedCarryWeight() {
        Item heavy = item("anvil", "an anvil", 40);
        Player player = playerCarrying(item("sword", "a sword", 5)).addBankedItem(heavy);

        BankTransactionResult result = bankService.claimItem(player, "an anvil", 20);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("lighten"),
            "message should tell the player to lighten their load");
    }

    // ── vault upgrade ─────────────────────────────────────────────────

    @Test
    void effectiveVaultCapacity_defaultsToBaseForTierZero() {
        Player player = Player.of(user(), "{hp}hp>");

        assertEquals(BankSettings.DEFAULT_VAULT_CAPACITY, bankService.effectiveVaultCapacity(player));
    }

    @Test
    void upgradeVault_success_deductsGoldAndRaisesTier() {
        Player player = playerWithGold(6_000, 0);

        BankTransactionResult result = bankService.upgradeVault(player);

        assertTrue(result.success());
        Player updated = result.updatedPlayer();
        assertEquals(1_000, updated.getGold(), "cost should come from carried gold only");
        assertEquals(1, updated.vault().capacityTier());
        assertEquals(BankSettings.DEFAULT_VAULT_CAPACITY + 10, bankService.effectiveVaultCapacity(updated));
    }

    @Test
    void upgradeVault_failure_insufficientGoldLeavesStateUnchanged() {
        Player player = playerWithGold(100, 0);

        BankTransactionResult result = bankService.upgradeVault(player);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertEquals(0, player.vault().capacityTier(), "tier must not change on failure");
    }

    @Test
    void upgradeVault_failure_alreadyAtMaxTier() {
        Player base = Player.of(user(), "{hp}hp>").withGold(1_000_000);
        Player player = base.withVault(base.vault().withCapacityTier(VaultUpgradeTier.TIER_THREE.rank()));

        BankTransactionResult result = bankService.upgradeVault(player);

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("maximum"));
    }

    @Test
    void upgradeVault_thenStoreItem_respectsNewCapacity() {
        BankService smallVault = new BankService(new StubBankRepository(BANK), 1);
        Player player = playerWithGold(6_000, 0)
            .withInventory(List.of(item("sword", "a sword", 5), item("shield", "a shield", 8)));

        // Base capacity 1 → filling one slot then storing another fails.
        Player afterFirst = smallVault.storeItem(player, "a sword").updatedPlayer();
        assertFalse(smallVault.storeItem(afterFirst, "a shield").success(), "base vault should be full");

        // Buying tier 1 (+10) lifts the cap, so the second item now fits.
        Player upgraded = smallVault.upgradeVault(afterFirst).updatedPlayer();
        assertEquals(11, smallVault.effectiveVaultCapacity(upgraded));
        BankTransactionResult stored = smallVault.storeItem(upgraded, "a shield");
        assertTrue(stored.success());
        assertEquals(2, stored.updatedPlayer().getBankedItems().size());
    }

    @Test
    void purchasedVaultTier_surviveDeath() {
        Player upgraded = bankService.upgradeVault(playerWithGold(6_000, 0)).updatedPlayer();

        Player dead = upgraded.die();

        assertEquals(1, dead.vault().capacityTier(), "purchased tier should survive death");
    }

    // ── death survival ────────────────────────────────────────────────

    @Test
    void vaultedItems_surviveDeath() {
        Item trophy = item("trophy", "a dragon trophy", 3);
        Player player = playerCarrying(item("junk", "some junk", 1));
        Player stored = bankService.storeItem(player, "some junk").updatedPlayer();
        // Also stash a trophy directly.
        stored = stored.addBankedItem(trophy);

        Player dead = stored.die();

        assertEquals(2, dead.getBankedItems().size(), "vault contents should survive death");
        assertTrue(dead.getBankedItems().stream().anyMatch(i -> i.getName().equals("a dragon trophy")));
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static Player playerWithGold(int carried, int banked) {
        User user = User.of(Username.of("testplayer"), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>").withGold(carried).withBankedGold(banked);
    }

    private static User user() {
        return User.of(Username.of("testplayer"), Password.hash("pass", 1));
    }

    private static Player playerCarrying(Item... items) {
        return Player.of(user(), "{hp}hp>").withInventory(List.of(items));
    }

    private static Item item(String id, String name, int weight) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .weight(weight)
            .build();
    }

    // ── stub repository ───────────────────────────────────────────────

    private record StubBankRepository(Bank bank) implements BankRepository {
        @Override
        public List<Bank> findAll() {
            return List.of(bank);
        }

        @Override
        public Optional<Bank> findByRoomId(RoomId roomId) {
            if (bank.roomId().equals(roomId)) {
                return Optional.of(bank);
            }
            return Optional.empty();
        }
    }
}
