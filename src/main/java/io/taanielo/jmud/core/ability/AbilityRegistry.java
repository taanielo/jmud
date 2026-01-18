package io.taanielo.jmud.core.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class AbilityRegistry {
    private final List<Ability> abilities;

    public AbilityRegistry(List<Ability> abilities) {
        this.abilities = List.copyOf(abilities);
    }

    public Optional<AbilityMatch> findBestMatch(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        String[] tokens = trimmed.split("\\s+");

        List<Candidate> candidates = new ArrayList<>();
        for (Ability ability : abilities) {
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
