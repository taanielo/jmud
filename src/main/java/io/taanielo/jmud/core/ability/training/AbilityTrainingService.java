package io.taanielo.jmud.core.ability.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.player.Player;

/**
 * Governs how a player learns abilities from the Master Trainer.
 *
 * <p>Members of a class have a fixed <em>trainable pool</em> (from the class JSON). An ability
 * in that pool can be learned when the player is at least the ability's required {@code level}
 * and has a practice point to spend; learning it deducts one practice point and adds the ability
 * to the player's learned list. This service holds those rules as pure logic so the socket layer
 * only needs to render the resulting {@link TrainingAttempt} and persist a {@link
 * TrainingAttempt.Success}. All decisions are deterministic and free of I/O.
 */
public final class AbilityTrainingService {

    private final AbilityRegistry abilityRegistry;

    /**
     * Creates a training service backed by the given ability registry.
     *
     * @param abilityRegistry registry used to resolve ability levels and display names
     */
    public AbilityTrainingService(AbilityRegistry abilityRegistry) {
        this.abilityRegistry = Objects.requireNonNull(abilityRegistry, "Ability registry is required");
    }

    /**
     * Resolves what happens when {@code player} attempts to train the ability named by
     * {@code abilityInput}, drawn from the class {@code trainablePool}.
     *
     * <p>The checks run in order: pool membership (and existence in the registry),
     * already-learned, level gate, practice points. Only the {@link TrainingAttempt.Success}
     * outcome carries a mutated player; every other outcome leaves the caller's player untouched
     * so nothing changes on rejection.
     *
     * @param player        the training player
     * @param trainablePool the ability ids this player's class may train
     * @param abilityInput  the ability identifier the player typed (matched case-insensitively)
     * @return the outcome describing success or the reason training was refused
     */
    public TrainingAttempt resolve(Player player, List<AbilityId> trainablePool, String abilityInput) {
        Objects.requireNonNull(player, "Player is required");
        String normalized = abilityInput == null ? "" : abilityInput.trim();
        AbilityId targetId = (trainablePool == null ? List.<AbilityId>of() : trainablePool).stream()
            .filter(id -> id.getValue().equalsIgnoreCase(normalized))
            .findFirst()
            .orElse(null);
        if (targetId == null) {
            return new TrainingAttempt.NotTrainable(normalized);
        }
        Ability ability = abilityRegistry.findById(targetId).orElse(null);
        if (ability == null) {
            // Misconfigured pool entry: nothing real to teach.
            return new TrainingAttempt.NotTrainable(normalized);
        }
        if (player.getLearnedAbilities().contains(targetId)) {
            return new TrainingAttempt.AlreadyLearned(ability);
        }
        if (ability.level() > player.getLevel()) {
            return new TrainingAttempt.LevelTooLow(ability, ability.level(), player.getLevel());
        }
        if (player.getPracticePoints() <= 0) {
            return new TrainingAttempt.NoPracticePoints(ability);
        }
        List<AbilityId> newAbilities = new ArrayList<>(player.getLearnedAbilities());
        newAbilities.add(targetId);
        Player updated = player
            .withPracticePoints(player.getPracticePoints() - 1)
            .withLearnedAbilities(newAbilities);
        return new TrainingAttempt.Success(updated, ability);
    }

    /**
     * Builds the annotated {@code TRAIN LIST} view of the class trainable pool for the player,
     * marking each ability learned, available now, or still level-locked. Unknown ids (absent
     * from the registry) are skipped so the listing only shows abilities that actually exist.
     *
     * @param player        the viewing player
     * @param trainablePool the ability ids this player's class may train
     * @return one status row per resolvable ability, in pool order
     */
    public List<TrainableAbilityStatus> listing(Player player, List<AbilityId> trainablePool) {
        Objects.requireNonNull(player, "Player is required");
        List<TrainableAbilityStatus> rows = new ArrayList<>();
        for (AbilityId id : trainablePool == null ? List.<AbilityId>of() : trainablePool) {
            Ability ability = abilityRegistry.findById(id).orElse(null);
            if (ability == null) {
                continue;
            }
            TrainableAbilityStatus.Status status;
            if (player.getLearnedAbilities().contains(id)) {
                status = TrainableAbilityStatus.Status.LEARNED;
            } else if (ability.level() > player.getLevel()) {
                status = TrainableAbilityStatus.Status.REQUIRES_LEVEL;
            } else {
                status = TrainableAbilityStatus.Status.AVAILABLE;
            }
            rows.add(new TrainableAbilityStatus(ability, status));
        }
        return rows;
    }
}
