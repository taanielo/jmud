package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.AbilityId;

public class PlayerAbilities {
    private final List<AbilityId> learned;

    public PlayerAbilities(List<AbilityId> learned) {
        this.learned = List.copyOf(Objects.requireNonNullElse(learned, List.of()));
    }

    public List<AbilityId> learned() {
        return learned;
    }

    public PlayerAbilities withLearned(List<AbilityId> nextLearned) {
        return new PlayerAbilities(nextLearned);
    }
}
