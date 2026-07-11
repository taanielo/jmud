package io.taanielo.jmud.core.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AbilityRegistry {
    private final List<Ability> abilities;
    private final Map<AbilityId, Ability> abilityById;

    public AbilityRegistry(List<Ability> abilities) {
        this.abilities = List.copyOf(abilities);
        Map<AbilityId, Ability> map = new HashMap<>();
        for (Ability ability : this.abilities) {
            map.put(ability.id(), ability);
        }
        this.abilityById = Map.copyOf(map);
    }

    public Optional<AbilityMatch> findBestMatch(String input, List<AbilityId> allowedAbilityIds) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        if (allowedAbilityIds == null || allowedAbilityIds.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        String[] tokens = trimmed.split("\\s+", -1);

        List<Candidate> candidates = new ArrayList<>();
        for (AbilityId abilityId : allowedAbilityIds) {
            Ability ability = abilityById.get(abilityId);
            if (ability == null) {
                continue;
            }
            List<String> aliases = new ArrayList<>();
            aliases.add(ability.name());
            aliases.addAll(ability.aliases());
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                String aliasLower = alias.toLowerCase(Locale.ROOT);
                for (int i = 1; i <= tokens.length; i++) {
                    String candidate = String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, i));
                    String candidateLower = candidate.toLowerCase(Locale.ROOT);
                    if (aliasLower.startsWith(candidateLower)) {
                        String remaining = String.join(" ", java.util.Arrays.copyOfRange(tokens, i, tokens.length));
                        candidates.add(new Candidate(ability, aliasLower.length(), candidateLower.length(), remaining));
                    }
                }
            }
        }
        return candidates.stream()
            .max(Comparator
                .comparingInt((Candidate candidate) -> candidate.ability.level())
                .thenComparingInt(candidate -> candidate.aliasLength)
                .thenComparingInt(candidate -> candidate.matchLength)
            )
            .map(candidate -> new AbilityMatch(candidate.ability, candidate.remainingTarget.trim()));
    }

    /**
     * Looks up an ability by its id.
     *
     * @param id the ability id to look up
     * @return the ability, or empty if not found
     */
    public Optional<Ability> findById(AbilityId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(abilityById.get(id));
    }

    public List<AbilityId> abilityIds() {
        return abilities.stream()
            .map(Ability::id)
            .toList();
    }

    private static class Candidate {
        private final Ability ability;
        private final int aliasLength;
        private final int matchLength;
        private final String remainingTarget;

        private Candidate(Ability ability, int aliasLength, int matchLength, String remainingTarget) {
            this.ability = ability;
            this.aliasLength = aliasLength;
            this.matchLength = matchLength;
            this.remainingTarget = remainingTarget;
        }
    }
}
