package io.taanielo.jmud.core.trade;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

/**
 * In-memory registry of live player-to-player trade sessions.
 *
 * <p>Sessions are keyed by both participants' {@link Username}s and never persisted — they vanish on
 * restart or disconnect. Mutating operations are {@code synchronized} so bookkeeping stays
 * consistent even when a reader thread cleans up on disconnect concurrently with the tick thread;
 * all game-state mutation itself still happens on the tick thread (AGENTS.md §5).
 *
 * <p>As a {@link Tickable}, this service scans every open session once per tick and silently
 * auto-cancels any whose participants have left the shared room, gone offline, died, or entered
 * combat, notifying the affected players through the injected notifier.
 */
public class TradeService implements Tickable {

    /**
     * Result of a trade operation, carrying a success flag and a player-facing message.
     *
     * @param success whether the operation succeeded
     * @param message the player-facing message describing the outcome
     */
    public record TradeResult(boolean success, String message) {}

    /** participant username → their live session (each session is stored under both participants). */
    private final Map<Username, TradeSession> sessions = new ConcurrentHashMap<>();

    private final Function<Username, TradeParticipantStatus> statusFn;
    private final BiConsumer<Username, String> notifier;

    /**
     * Creates a trade service.
     *
     * @param statusFn resolves a player's current world status for the auto-cancel guard
     * @param notifier delivers an auto-cancel notice to an online player (username, message)
     */
    public TradeService(
        Function<Username, TradeParticipantStatus> statusFn,
        BiConsumer<Username, String> notifier
    ) {
        this.statusFn = Objects.requireNonNull(statusFn, "statusFn is required");
        this.notifier = Objects.requireNonNull(notifier, "notifier is required");
    }

    // ── Session lifecycle ─────────────────────────────────────────────

    /**
     * Proposes a new trade from {@code proposer} to {@code target}. Both must be online, in the same
     * room, and free of any other trade session.
     *
     * @param proposer the initiating player
     * @param target   the invited player
     * @return result describing success or failure
     */
    public synchronized TradeResult propose(Username proposer, Username target) {
        Objects.requireNonNull(proposer, "proposer is required");
        Objects.requireNonNull(target, "target is required");
        if (proposer.equals(target)) {
            return new TradeResult(false, "You cannot trade with yourself.");
        }
        if (sessions.containsKey(proposer)) {
            return new TradeResult(false, "You are already in a trade. Use TRADE CANCEL first.");
        }
        TradeParticipantStatus targetStatus = statusFn.apply(target);
        if (!targetStatus.online()) {
            return new TradeResult(false, target.getValue() + " is not online.");
        }
        if (sessions.containsKey(target)) {
            return new TradeResult(false, target.getValue() + " is already in a trade.");
        }
        TradeParticipantStatus proposerStatus = statusFn.apply(proposer);
        @Nullable RoomId proposerRoom = proposerStatus.room();
        @Nullable RoomId targetRoom = targetStatus.room();
        if (proposerRoom == null || !proposerRoom.equals(targetRoom)) {
            return new TradeResult(false, target.getValue() + " is not here.");
        }
        TradeSession session = new TradeSession(proposer, target);
        sessions.put(proposer, session);
        sessions.put(target, session);
        return new TradeResult(true, "You propose a trade to " + target.getValue() + ".");
    }

    /**
     * Accepts a pending proposal on behalf of the invited player.
     *
     * @param player the invited player accepting
     * @return result describing success or failure
     */
    public synchronized TradeResult accept(Username player) {
        Objects.requireNonNull(player, "player is required");
        TradeSession session = sessions.get(player);
        if (session == null) {
            return new TradeResult(false, "You have no trade proposal to accept.");
        }
        if (session.proposer().equals(player)) {
            return new TradeResult(false,
                "You proposed this trade; wait for " + session.target().getValue() + " to accept.");
        }
        if (session.isAccepted()) {
            return new TradeResult(false, "That trade is already underway.");
        }
        session.accept();
        return new TradeResult(true,
            "You accept the trade with " + session.proposer().getValue() + ".");
    }

    /**
     * Declines or aborts a pending proposal on behalf of {@code player}.
     *
     * @param player the participant declining
     * @return result describing success or failure
     */
    public synchronized TradeResult decline(Username player) {
        Objects.requireNonNull(player, "player is required");
        TradeSession session = sessions.get(player);
        if (session == null) {
            return new TradeResult(false, "You have no trade proposal.");
        }
        remove(session);
        return new TradeResult(true, "You decline the trade.");
    }

    /**
     * Cancels an open trade session on behalf of {@code player}. Because nothing leaves inventory
     * until the atomic swap, no items or gold need restoring.
     *
     * @param player the participant cancelling
     * @return result describing success or failure
     */
    public synchronized TradeResult cancel(Username player) {
        Objects.requireNonNull(player, "player is required");
        TradeSession session = sessions.get(player);
        if (session == null) {
            return new TradeResult(false, "You are not in a trade.");
        }
        remove(session);
        return new TradeResult(true, "You cancel the trade.");
    }

    // ── Offer staging ─────────────────────────────────────────────────

    /**
     * Stages an item on {@code player}'s side of the offer, clearing both confirm flags.
     *
     * @param player the participant staging the item
     * @param item   the item reference to stage
     * @return result describing success or failure
     */
    public synchronized TradeResult addItem(Username player, Item item) {
        Objects.requireNonNull(item, "item is required");
        TradeSession session = activeSession(player);
        if (session == null) {
            return notActive();
        }
        session.addItem(player, item);
        return new TradeResult(true, "You add " + item.getName() + " to the trade.");
    }

    /**
     * Stages gold on {@code player}'s side of the offer, clearing both confirm flags.
     *
     * @param player the participant staging gold
     * @param amount the additional gold to stage (must be positive)
     * @return result describing success or failure
     */
    public synchronized TradeResult addGold(Username player, int amount) {
        TradeSession session = activeSession(player);
        if (session == null) {
            return notActive();
        }
        if (amount <= 0) {
            return new TradeResult(false, "Add how much gold?");
        }
        session.addGold(player, amount);
        return new TradeResult(true,
            "You add " + amount + " gold to the trade (" + session.goldOf(player) + " offered).");
    }

    /**
     * Removes a previously-staged item from {@code player}'s side of the offer (matched by name or
     * id), clearing both confirm flags.
     *
     * @param player    the participant removing an item
     * @param itemInput the item name or id to remove from the offer
     * @return result describing success or failure
     */
    public synchronized TradeResult removeItem(Username player, String itemInput) {
        TradeSession session = activeSession(player);
        if (session == null) {
            return notActive();
        }
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return new TradeResult(false, "Remove what?");
        }
        Item match = null;
        for (Item item : session.itemsOf(player)) {
            if (item.getName().equalsIgnoreCase(normalized) || item.getId().getValue().equalsIgnoreCase(normalized)) {
                match = item;
                break;
            }
        }
        if (match == null) {
            for (Item item : session.itemsOf(player)) {
                if (item.getName().toLowerCase(Locale.ROOT).startsWith(normalized.toLowerCase(Locale.ROOT))) {
                    match = item;
                    break;
                }
            }
        }
        if (match == null || !session.removeItem(player, match)) {
            return new TradeResult(false, "That item is not in your offer.");
        }
        return new TradeResult(true, "You remove " + match.getName() + " from the trade.");
    }

    /**
     * Locks in {@code player}'s current offer.
     *
     * @param player the confirming participant
     * @return result describing success or failure
     */
    public synchronized TradeResult confirm(Username player) {
        TradeSession session = activeSession(player);
        if (session == null) {
            return notActive();
        }
        if (session.hasConfirmed(player)) {
            return new TradeResult(false, "You have already confirmed. Waiting on the other party.");
        }
        session.confirm(player);
        return new TradeResult(true, "You confirm the trade.");
    }

    /** Clears both confirm flags on {@code player}'s session, if any (used after a failed swap). */
    public synchronized void resetConfirms(Username player) {
        TradeSession session = sessions.get(player);
        if (session != null) {
            session.resetConfirms();
        }
    }

    /** Removes {@code player}'s session silently (used after a successful swap). */
    public synchronized void complete(Username player) {
        TradeSession session = sessions.get(player);
        if (session != null) {
            remove(session);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────

    /**
     * Returns {@code player}'s current session, if any (accepted or still pending).
     *
     * @param player the participant to look up
     * @return the session, or empty
     */
    public synchronized Optional<TradeSession> session(Username player) {
        return Optional.ofNullable(sessions.get(player));
    }

    /**
     * Returns whether {@code player} is currently involved in any trade session.
     *
     * @param player the participant to check
     * @return whether a session exists for the player
     */
    public synchronized boolean isTrading(Username player) {
        return sessions.containsKey(player);
    }

    // ── Auto-cancel guard ─────────────────────────────────────────────

    @Override
    public synchronized void tick() {
        Set<TradeSession> live = new LinkedHashSet<>(sessions.values());
        for (TradeSession session : live) {
            Username proposer = session.proposer();
            Username target = session.target();
            TradeParticipantStatus ps = statusFn.apply(proposer);
            TradeParticipantStatus ts = statusFn.apply(target);

            if (!ps.online() || !ts.online()) {
                remove(session);
                Username offline = !ps.online() ? proposer : target;
                Username onlineParty = offline.equals(proposer) ? target : proposer;
                if (statusFn.apply(onlineParty).online()) {
                    notifier.accept(onlineParty,
                        "Your trade with " + offline.getValue() + " was cancelled; they are no longer available.");
                }
                continue;
            }
            String reason = null;
            if (ps.dead() || ts.dead()) {
                reason = "a participant has died";
            } else if (ps.inCombat() || ts.inCombat()) {
                reason = "a participant entered combat";
            } else {
                @Nullable RoomId pr = ps.room();
                @Nullable RoomId tr = ts.room();
                if (pr == null || !pr.equals(tr)) {
                    reason = "you are no longer in the same room";
                }
            }
            if (reason != null) {
                remove(session);
                String message = "Your trade was cancelled because " + reason + ".";
                notifier.accept(proposer, message);
                notifier.accept(target, message);
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────

    private @Nullable TradeSession activeSession(Username player) {
        TradeSession session = sessions.get(player);
        return session != null && session.isAccepted() ? session : null;
    }

    private TradeResult notActive() {
        return new TradeResult(false, "You are not in an active trade.");
    }

    private void remove(TradeSession session) {
        sessions.remove(session.proposer());
        sessions.remove(session.target());
    }
}
