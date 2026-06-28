package io.taanielo.jmud.core.bank;

import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Application service for bank interactions: depositing and withdrawing gold.
 *
 * <p>All operations are stateless with respect to the bank; the {@link Player}
 * passed in is never mutated. Callers receive an updated {@link Player} in the
 * returned {@link BankTransactionResult} on success.
 *
 * <p>Banked gold survives death and is never lost to mob drops or corpse decay.
 * Gold can only be moved between carried and banked balances — no gold is ever
 * created or destroyed.
 */
@Slf4j
public class BankService {

    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = Objects.requireNonNull(bankRepository, "bankRepository is required");
    }

    /**
     * Returns the bank located in the given room, if any.
     *
     * @param roomId the room to search
     * @return the bank in the room, or empty
     */
    public Optional<Bank> findBankInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        try {
            return bankRepository.findByRoomId(roomId);
        } catch (BankRepositoryException e) {
            log.warn("Failed to look up bank in room {}: {}", roomId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deposits the given amount from the player's carried gold into the bank.
     *
     * <p>Fails if {@code amount} is zero or negative, or if the player does not
     * carry enough gold.
     *
     * @param player the depositing player
     * @param amount the amount to deposit; must be positive
     * @return a result describing success or failure
     */
    public BankTransactionResult deposit(Player player, int amount) {
        Objects.requireNonNull(player, "player is required");
        if (amount <= 0) {
            return BankTransactionResult.failure("You must deposit a positive amount of gold.");
        }
        if (player.getGold() < amount) {
            return BankTransactionResult.failure(
                "You do not have enough gold. You are only carrying "
                    + player.getGold() + " gold.");
        }
        Player updated = player.addGold(-amount).addBankedGold(amount);
        return BankTransactionResult.success(
            "You deposit " + amount + " gold. "
                + "Carried: " + updated.getGold() + " gold. "
                + "Banked: " + updated.getBankedGold() + " gold.",
            updated
        );
    }

    /**
     * Withdraws the given amount from the player's banked gold into carried gold.
     *
     * <p>Fails if {@code amount} is zero or negative, or if the bank does not
     * hold enough gold for this player.
     *
     * @param player the withdrawing player
     * @param amount the amount to withdraw; must be positive
     * @return a result describing success or failure
     */
    public BankTransactionResult withdraw(Player player, int amount) {
        Objects.requireNonNull(player, "player is required");
        if (amount <= 0) {
            return BankTransactionResult.failure("You must withdraw a positive amount of gold.");
        }
        if (player.getBankedGold() < amount) {
            return BankTransactionResult.failure(
                "You do not have that much gold in the bank. Your balance is "
                    + player.getBankedGold() + " gold.");
        }
        Player updated = player.addBankedGold(-amount).addGold(amount);
        return BankTransactionResult.success(
            "You withdraw " + amount + " gold. "
                + "Carried: " + updated.getGold() + " gold. "
                + "Banked: " + updated.getBankedGold() + " gold.",
            updated
        );
    }
}
