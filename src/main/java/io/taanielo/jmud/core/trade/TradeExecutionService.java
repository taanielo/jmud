package io.taanielo.jmud.core.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;

/**
 * Performs the pure, deterministic atomic swap that completes a confirmed {@link TradeSession}.
 *
 * <p>Validation happens at execution time (not merely when items/gold were staged) so a concurrent
 * spend or item loss between confirm and swap is caught: each side must still carry every offered
 * item and enough gold. Offered items move inventory-to-inventory (unequipping worn items first,
 * mirroring {@code GameActionService.giveItem}) and gold moves ledger-to-ledger, all within a single
 * result so the caller applies both players' new state on one tick.
 */
public class TradeExecutionService {

    /**
     * Outcome of an attempted swap.
     *
     * @param success         whether the swap succeeded
     * @param error           the failure reason, or {@code null} on success
     * @param updatedProposer the proposer's post-swap state, or {@code null} on failure
     * @param updatedTarget   the target's post-swap state, or {@code null} on failure
     * @param proposerSummary a summary line for the proposer, or {@code null} on failure
     * @param targetSummary   a summary line for the target, or {@code null} on failure
     */
    public record TradeExecutionResult(
        boolean success,
        @Nullable String error,
        @Nullable Player updatedProposer,
        @Nullable Player updatedTarget,
        @Nullable String proposerSummary,
        @Nullable String targetSummary
    ) {
        static TradeExecutionResult failure(String error) {
            return new TradeExecutionResult(false, error, null, null, null, null);
        }
    }

    /**
     * Executes the swap for the two participants of {@code session}.
     *
     * @param proposer     the live proposer player (must match {@link TradeSession#proposer()})
     * @param target       the live target player (must match {@link TradeSession#target()})
     * @param session      the confirmed session holding both offers
     * @param overburdened predicate reporting whether a player would be over their carry weight
     * @return the swap outcome
     */
    public TradeExecutionResult execute(
        Player proposer,
        Player target,
        TradeSession session,
        Predicate<Player> overburdened
    ) {
        List<Item> proposerOffer = session.itemsOf(proposer.getUsername());
        List<Item> targetOffer = session.itemsOf(target.getUsername());
        int proposerGold = session.goldOf(proposer.getUsername());
        int targetGold = session.goldOf(target.getUsername());

        if (proposer.getGold() < proposerGold) {
            return TradeExecutionResult.failure(
                proposer.getUsername().getValue() + " no longer has enough gold.");
        }
        if (target.getGold() < targetGold) {
            return TradeExecutionResult.failure(
                target.getUsername().getValue() + " no longer has enough gold.");
        }

        List<Item> proposerRemaining = new ArrayList<>(proposer.getInventory());
        if (!removeByIdentity(proposerRemaining, proposerOffer)) {
            return TradeExecutionResult.failure(
                proposer.getUsername().getValue() + " no longer holds every offered item.");
        }
        List<Item> targetRemaining = new ArrayList<>(target.getInventory());
        if (!removeByIdentity(targetRemaining, targetOffer)) {
            return TradeExecutionResult.failure(
                target.getUsername().getValue() + " no longer holds every offered item.");
        }

        proposerRemaining.addAll(targetOffer);
        targetRemaining.addAll(proposerOffer);

        PlayerEquipment proposerEquipment = unequipOffered(proposer.getEquipment(), proposerOffer);
        PlayerEquipment targetEquipment = unequipOffered(target.getEquipment(), targetOffer);

        int proposerNewGold = proposer.getGold() - proposerGold + targetGold;
        int targetNewGold = target.getGold() - targetGold + proposerGold;

        Player updatedProposer = proposer
            .withInventory(proposerRemaining)
            .withEquipment(proposerEquipment)
            .withGold(proposerNewGold);
        Player updatedTarget = target
            .withInventory(targetRemaining)
            .withEquipment(targetEquipment)
            .withGold(targetNewGold);

        if (overburdened.test(updatedProposer)) {
            return TradeExecutionResult.failure(
                proposer.getUsername().getValue() + " cannot carry that much.");
        }
        if (overburdened.test(updatedTarget)) {
            return TradeExecutionResult.failure(
                target.getUsername().getValue() + " cannot carry that much.");
        }

        String proposerSummary = "Trade complete. You gave " + describe(proposerOffer, proposerGold)
            + " and received " + describe(targetOffer, targetGold) + ".";
        String targetSummary = "Trade complete. You gave " + describe(targetOffer, targetGold)
            + " and received " + describe(proposerOffer, proposerGold) + ".";

        return new TradeExecutionResult(
            true, null, updatedProposer, updatedTarget, proposerSummary, targetSummary);
    }

    /**
     * Removes, by reference identity, exactly one working-list entry per offered item.
     *
     * @param working the mutable working copy of a player's inventory
     * @param offered the offered item references
     * @return {@code true} when every offered item was found and removed
     */
    private boolean removeByIdentity(List<Item> working, List<Item> offered) {
        for (Item item : offered) {
            boolean removed = false;
            for (int i = 0; i < working.size(); i++) {
                if (working.get(i) == item) {
                    working.remove(i);
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return false;
            }
        }
        return true;
    }

    private PlayerEquipment unequipOffered(PlayerEquipment equipment, List<Item> offered) {
        PlayerEquipment result = equipment;
        for (Item item : offered) {
            if (result.isEquipped(item.getId())) {
                EquipmentSlot slot = result.equippedSlot(item.getId());
                if (slot != null) {
                    result = result.unequip(slot);
                }
            }
        }
        return result;
    }

    private String describe(List<Item> items, int gold) {
        List<String> parts = new ArrayList<>();
        for (Item item : items) {
            parts.add(item.getName());
        }
        if (gold > 0) {
            parts.add(gold + " gold");
        }
        if (parts.isEmpty()) {
            return "nothing";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1))
            + " and " + parts.get(parts.size() - 1);
    }
}
