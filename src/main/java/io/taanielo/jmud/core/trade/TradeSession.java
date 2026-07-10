package io.taanielo.jmud.core.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;

/**
 * A live trade negotiation between exactly two players.
 *
 * <p>The session tracks each side's staged offer (item references and gold) and per-side confirm
 * flag. Nothing is ever removed from a player's inventory while the session is open — offered items
 * are held by reference and only change ownership during the final atomic swap performed by
 * {@link TradeExecutionService}.
 *
 * <p>Instances are mutable and owned exclusively by {@link TradeService}, which guards every access
 * with {@code synchronized}. The proposer's proposal must be {@linkplain #isAccepted() accepted} by
 * the target before offers may be staged.
 */
public final class TradeSession {

    private final Username proposer;
    private final Username target;
    private boolean accepted;

    private final List<Item> proposerItems = new ArrayList<>();
    private final List<Item> targetItems = new ArrayList<>();
    private int proposerGold;
    private int targetGold;
    private boolean proposerConfirmed;
    private boolean targetConfirmed;

    /**
     * Creates a pending (not yet accepted) trade session.
     *
     * @param proposer the player who initiated the trade
     * @param target   the invited player
     */
    public TradeSession(Username proposer, Username target) {
        this.proposer = Objects.requireNonNull(proposer, "proposer is required");
        this.target = Objects.requireNonNull(target, "target is required");
    }

    /** @return the player who proposed the trade */
    public Username proposer() {
        return proposer;
    }

    /** @return the invited player */
    public Username target() {
        return target;
    }

    /**
     * Returns whether the given player is one of the two participants.
     *
     * @param player the player to test
     * @return {@code true} when {@code player} is the proposer or the target
     */
    public boolean involves(Username player) {
        return proposer.equals(player) || target.equals(player);
    }

    /**
     * Returns the other participant relative to the given player.
     *
     * @param player one participant
     * @return the opposing participant
     */
    public Username other(Username player) {
        return proposer.equals(player) ? target : proposer;
    }

    /** @return whether the target has accepted the proposal */
    public boolean isAccepted() {
        return accepted;
    }

    /** Marks the proposal accepted, opening the session for staging offers. */
    public void accept() {
        this.accepted = true;
    }

    /**
     * Returns an unmodifiable snapshot of the items offered by the given player.
     *
     * @param player the participant
     * @return the items on that player's side of the offer
     */
    public List<Item> itemsOf(Username player) {
        return List.copyOf(proposer.equals(player) ? proposerItems : targetItems);
    }

    /**
     * Returns the gold offered by the given player.
     *
     * @param player the participant
     * @return the gold on that player's side of the offer
     */
    public int goldOf(Username player) {
        return proposer.equals(player) ? proposerGold : targetGold;
    }

    /**
     * Returns whether the given player has locked in their offer.
     *
     * @param player the participant
     * @return that player's confirm flag
     */
    public boolean hasConfirmed(Username player) {
        return proposer.equals(player) ? proposerConfirmed : targetConfirmed;
    }

    /** @return whether both participants have confirmed matching offers */
    public boolean bothConfirmed() {
        return proposerConfirmed && targetConfirmed;
    }

    /**
     * Adds an item reference to the given player's side of the offer, clearing both confirm flags
     * (anti-scam: any offer change invalidates a prior confirm).
     *
     * @param player the participant staging the item
     * @param item   the item to stage
     */
    public void addItem(Username player, Item item) {
        Objects.requireNonNull(item, "item is required");
        sideItems(player).add(item);
        resetConfirms();
    }

    /**
     * Removes a previously-staged item (matched by reference identity) from the given player's side
     * of the offer, clearing both confirm flags.
     *
     * @param player the participant removing the item
     * @param item   the exact staged item reference to remove
     * @return {@code true} when the item was present and removed
     */
    public boolean removeItem(Username player, Item item) {
        List<Item> side = sideItems(player);
        for (int i = 0; i < side.size(); i++) {
            if (side.get(i) == item) {
                side.remove(i);
                resetConfirms();
                return true;
            }
        }
        return false;
    }

    /**
     * Increases the gold on the given player's side of the offer, clearing both confirm flags.
     *
     * @param player the participant staging gold
     * @param amount the additional gold to stage (must be positive)
     */
    public void addGold(Username player, int amount) {
        if (proposer.equals(player)) {
            proposerGold += amount;
        } else {
            targetGold += amount;
        }
        resetConfirms();
    }

    /**
     * Locks in the given player's current offer.
     *
     * @param player the confirming participant
     */
    public void confirm(Username player) {
        if (proposer.equals(player)) {
            proposerConfirmed = true;
        } else {
            targetConfirmed = true;
        }
    }

    /** Clears both participants' confirm flags. */
    public void resetConfirms() {
        proposerConfirmed = false;
        targetConfirmed = false;
    }

    private List<Item> sideItems(Username player) {
        return proposer.equals(player) ? proposerItems : targetItems;
    }
}
