package io.taanielo.jmud.core.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class AbilityEngine {
    private final AbilityRegistry registry;

    public AbilityEngine(AbilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Ability registry is required");
    }

    public AbilityUseResult use(
        Player source,
        String input,
        AbilityTargetResolver targetResolver,
        AbilityCooldownTracker cooldowns
    ) {
        Objects.requireNonNull(source, "Source player is required");
        Objects.requireNonNull(targetResolver, "Target resolver is required");
        Objects.requireNonNull(cooldowns, "Cooldown tracker is required");

        Optional<AbilityMatch> match = registry.findBestMatch(input);
        if (match.isEmpty()) {
            return new AbilityUseResult(source, source, List.of("You don't know that ability."));
        }
        Ability ability = match.get().ability();
        String targetInput = match.get().remainingTarget();

        Player target = resolveTarget(ability, source, targetInput, targetResolver);
        if (target == null) {
            if (ability.targeting() == AbilityTargeting.HARMFUL) {
                return new AbilityUseResult(source, source, List.of("You must specify a target."));
            }
            return new AbilityUseResult(source, source, List.of("Target not found."));
        }

        if (cooldowns.isOnCooldown(ability.id())) {
            int remaining = cooldowns.remainingTicks(ability.id());
            return new AbilityUseResult(source, source, List.of("Ability is on cooldown (" + remaining + " ticks remaining)."));
        }

        if (!ability.cost().canAfford(source.getVitals())) {
            return new AbilityUseResult(source, source, List.of("You lack the resources to use that ability."));
        }

        AbilityContext context = new AbilityContext(source, target);
        if (!ability.canUse(context)) {
            return new AbilityUseResult(source, source, List.of("You cannot use that ability right now."));
        }

        PlayerVitals spent = ability.cost().apply(source.getVitals());
        context.updateSource(source.withVitals(spent));

        AbilityEffectResolver resolver = (effect, ctx) -> {
            Player currentTarget = ctx.target();
            PlayerVitals vitals = currentTarget.getVitals();
            PlayerVitals updated = switch (effect.type()) {
                case DAMAGE -> vitals.damage(effect.amount());
                case HEAL -> vitals.heal(effect.amount());
            };
            ctx.updateTarget(currentTarget.withVitals(updated));
        };

        ability.use(context, resolver);

        if (ability.cooldown().ticks() > 0) {
            cooldowns.startCooldown(ability.id(), ability.cooldown().ticks());
        }

        List<String> messages = new ArrayList<>();
        String abilityName = ability.name();
        if (context.source().getUsername().equals(context.target().getUsername())) {
            messages.add("You use " + abilityName + " on yourself.");
        } else {
            messages.add("You use " + abilityName + " on " + context.target().getUsername().getValue() + ".");
        }
        for (AbilityEffect effect : ability.effects()) {
            messages.add(formatEffect(effect, context.target()));
        }

        return new AbilityUseResult(context.source(), context.target(), messages);
    }

    private Player resolveTarget(
        Ability ability,
        Player source,
        String targetInput,
        AbilityTargetResolver targetResolver
    ) {
        if (targetInput == null || targetInput.isBlank()) {
            return ability.targeting() == AbilityTargeting.BENEFICIAL ? source : null;
        }
        return targetResolver.resolve(source, targetInput).orElse(null);
    }

    private String formatEffect(AbilityEffect effect, Player target) {
        return switch (effect.type()) {
            case DAMAGE -> target.getUsername().getValue() + " takes " + effect.amount() + " damage.";
            case HEAL -> target.getUsername().getValue() + " heals " + effect.amount() + " health.";
        };
    }
}
