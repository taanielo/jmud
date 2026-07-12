package io.taanielo.jmud.core.ability.training;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a player's attempt to train a single ability at the Master Trainer.
 *
 * <p>This is a pure result value produced by {@link AbilityTrainingService}; the socket
 * layer maps each variant to a player-facing message and, for {@link Success}, persists the
 * updated player. Keeping the decision separate from I/O lets the training rules be unit
 * tested without networking (AGENTS.md §10).
 */
public sealed interface TrainingAttempt
    permits TrainingAttempt.Success,
            TrainingAttempt.NotTrainable,
            TrainingAttempt.AlreadyLearned,
            TrainingAttempt.LevelTooLow,
            TrainingAttempt.NoPracticePoints {

    /**
     * The ability was learned: {@code updatedPlayer} has one fewer practice point and the
     * ability added to its learned list.
     *
     * @param updatedPlayer the player after spending the point and learning the ability
     * @param ability       the ability that was learned
     */
    record Success(Player updatedPlayer, Ability ability) implements TrainingAttempt {}

    /**
     * The requested ability is not part of the player's class trainable pool.
     *
     * @param input the raw ability identifier the player typed
     */
    record NotTrainable(String input) implements TrainingAttempt {}

    /**
     * The player has already learned the requested ability.
     *
     * @param ability the ability already known
     */
    record AlreadyLearned(Ability ability) implements TrainingAttempt {}

    /**
     * The player's level is below the ability's required level.
     *
     * @param ability       the ability that is still locked
     * @param requiredLevel the level the ability requires
     * @param playerLevel   the player's current level
     */
    record LevelTooLow(Ability ability, int requiredLevel, int playerLevel) implements TrainingAttempt {}

    /**
     * The player meets every requirement but has no practice points to spend.
     *
     * @param ability the ability that could otherwise be learned
     */
    record NoPracticePoints(Ability ability) implements TrainingAttempt {}
}
