package io.taanielo.jmud.core.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
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

    // ── helpers ───────────────────────────────────────────────────────

    private static Player playerWithGold(int carried, int banked) {
        User user = User.of(Username.of("testplayer"), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>").withGold(carried).withBankedGold(banked);
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
