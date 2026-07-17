package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;

/**
 * Renders the {@code EFFECTS} command listing: a player's active status effects split into a
 * beneficial and a harmful group, each line showing the effect's display name, remaining duration
 * (in ticks, or {@code permanent}), and stack count when stacked.
 *
 * <p>This is a pure, read-only formatter over the supplied effect snapshot and repository; it never
 * mutates effect durations or stacks, so it is safe to call from the tick thread (AGENTS.md §5).
 */
final class EffectListingFormatter {

    private EffectListingFormatter() {
    }

    /**
     * Builds the {@code EFFECTS} listing lines for the given active effects.
     *
     * <p>Effects whose id cannot be resolved in the repository are silently skipped. When no effect
     * resolves, an empty list is returned so the caller can print a dedicated "no active effects"
     * message.
     *
     * @param effects    the player's live active effect instances
     * @param repository  repository used to resolve each effect's display name and harmful/beneficial
     *                    classification
     * @return the formatted listing lines, or an empty list when there are no resolvable effects
     */
    static List<String> format(List<EffectInstance> effects, EffectRepository repository) {
        Objects.requireNonNull(effects, "Effects are required");
        Objects.requireNonNull(repository, "Effect repository is required");
        List<String> beneficial = new ArrayList<>();
        List<String> harmful = new ArrayList<>();
        for (EffectInstance instance : effects) {
            EffectDefinition definition = resolve(instance, repository);
            if (definition == null) {
                continue;
            }
            String line = formatLine(definition, instance);
            if (definition.isHarmful()) {
                harmful.add(line);
            } else {
                beneficial.add(line);
            }
        }
        if (beneficial.isEmpty() && harmful.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("Active effects:");
        if (!beneficial.isEmpty()) {
            lines.add("  Beneficial:");
            lines.addAll(beneficial);
        }
        if (!harmful.isEmpty()) {
            lines.add("  Harmful:");
            lines.addAll(harmful);
        }
        return lines;
    }

    private static @Nullable EffectDefinition resolve(EffectInstance instance, EffectRepository repository) {
        try {
            return repository.findById(instance.id()).orElse(null);
        } catch (EffectRepositoryException e) {
            return null;
        }
    }

    private static String formatLine(EffectDefinition definition, EffectInstance instance) {
        StringBuilder detail = new StringBuilder();
        if (definition.isPermanent()) {
            detail.append("permanent");
        } else {
            int ticks = instance.remainingTicks();
            detail.append(ticks).append(ticks == 1 ? " tick" : " ticks");
        }
        if (instance.stacks() > 1) {
            detail.append(", x").append(instance.stacks());
        }
        return String.format("    %-20s (%s)", definition.name(), detail);
    }
}
