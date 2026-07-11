package io.taanielo.jmud.core.player;

import java.util.Objects;

/**
 * Computes and applies the move-point cost of walking between rooms, and decides when a player has
 * run out of stamina to travel.
 *
 * <p>Movement spends the {@code move} vital that {@link PlayerVitals} already tracks and that
 * {@link RestingTicker} regenerates: each successful step costs {@link MovementSettings#stepCost()}
 * move points, plus {@link MovementSettings#overburdenedSurcharge()} extra while the player is
 * {@linkplain EncumbranceService#isOverburdened(Player) overburdened}. This graduated surcharge
 * replaces the previous hard "carrying too much" movement gate: an overburdened player can still
 * move, but pays more per step and runs out of stamina sooner.
 *
 * <p>All methods are pure with respect to game state — {@link #spend(Player)} returns a new
 * immutable {@link Player} snapshot rather than mutating in place — so callers on the tick thread
 * are responsible for replacing and persisting the returned player (AGENTS.md §5).
 */
public class MovementCostService {

    /** Message shown to a player who has no move points left and cannot take another step. */
    public static final String EXHAUSTED_MESSAGE = "You are too exhausted to go on. REST to recover.";

    private final EncumbranceService encumbranceService;
    private final int baseCost;
    private final int overburdenedSurcharge;

    /**
     * Creates a movement cost service reading its per-step cost and overburden surcharge from
     * {@link MovementSettings} (backed by {@code jmud.properties}).
     *
     * @param encumbranceService the encumbrance service used to detect overburdened players
     */
    public MovementCostService(EncumbranceService encumbranceService) {
        this(encumbranceService, MovementSettings.stepCost(), MovementSettings.overburdenedSurcharge());
    }

    /**
     * Creates a movement cost service with explicit costs, primarily for deterministic unit tests.
     *
     * @param encumbranceService   the encumbrance service used to detect overburdened players
     * @param baseCost             the baseline move-point cost of one step (non-negative)
     * @param overburdenedSurcharge the extra move-point cost per step while overburdened (non-negative)
     */
    public MovementCostService(EncumbranceService encumbranceService, int baseCost, int overburdenedSurcharge) {
        this.encumbranceService = Objects.requireNonNull(encumbranceService, "Encumbrance service is required");
        if (baseCost < 0) {
            throw new IllegalArgumentException("Base move cost must be non-negative");
        }
        if (overburdenedSurcharge < 0) {
            throw new IllegalArgumentException("Overburdened move surcharge must be non-negative");
        }
        this.baseCost = baseCost;
        this.overburdenedSurcharge = overburdenedSurcharge;
    }

    /**
     * Returns the move-point cost of the player's next step, including the overburden surcharge when
     * the player is currently carrying too much.
     *
     * @param player the player about to move
     * @return the move points the step will consume
     */
    public int stepCost(Player player) {
        Objects.requireNonNull(player, "Player is required");
        int cost = baseCost;
        if (encumbranceService.isOverburdened(player)) {
            cost += overburdenedSurcharge;
        }
        return cost;
    }

    /**
     * Returns {@code true} when the player has no move points left and must rest before travelling.
     *
     * @param player the player about to move
     * @return {@code true} if the player is out of move points
     */
    public boolean isExhausted(Player player) {
        Objects.requireNonNull(player, "Player is required");
        return player.getVitals().move() <= 0;
    }

    /**
     * Returns a copy of the player with move points reduced by {@link #stepCost(Player)}, floored at
     * zero. Callers must replace and persist the returned snapshot on the tick thread.
     *
     * @param player the player who just moved
     * @return the player with move points spent for the step
     */
    public Player spend(Player player) {
        Objects.requireNonNull(player, "Player is required");
        return player.withVitals(player.getVitals().consumeMove(stepCost(player)));
    }
}
