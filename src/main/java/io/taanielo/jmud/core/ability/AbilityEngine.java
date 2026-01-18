package io.taanielo.jmud.core.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

public class AbilityEngine {
    private final AbilityRegistry registry;
    private final AbilityCostResolver costResolver;
    private final AbilityEffectResolver effectResolver;
    private final AbilityMessageSink messageSink;

    public AbilityEngine(
        AbilityRegistry registry,
        AbilityCostResolver costResolver,
        AbilityEffectResolver effectResolver,
        AbilityMessageSink messageSink
    ) {
        this.registry = Objects.requireNonNull(registry, "Ability registry is required");
        this.costResolver = Objects.requireNonNull(costResolver, "Ability cost resolver is required");
        this.effectResolver = Objects.requireNonNull(effectResolver, "Ability effect resolver is required");
        this.messageSink = Objects.requireNonNull(messageSink, "Ability message sink is required");
    }

    public AbilityUseResult use(
        Player source,
        String input,
        List<AbilityId> learnedAbilityIds,
        AbilityTargetResolver targetResolver,
        AbilityCooldownTracker cooldowns
    ) {
        Objects.requireNonNull(source, "Source player is required");
        Objects.requireNonNull(targetResolver, "Target resolver is required");
        Objects.requireNonNull(cooldowns, "Cooldown tracker is required");

        Optional<AbilityMatch> match = registry.findBestMatch(input, learnedAbilityIds);
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

        if (!costResolver.canAfford(source, ability.cost())) {
            return new AbilityUseResult(source, source, List.of("You lack the resources to use that ability."));
        }

        AbilityContext context = new AbilityContext(source, target);
        if (!ability.canUse(context)) {
            return new AbilityUseResult(source, source, List.of("You cannot use that ability right now."));
        }

        Player updatedSource = costResolver.applyCost(source, ability.cost());
        context.updateSource(updatedSource);
        if (source.getUsername().equals(target.getUsername())) {
            context.updateTarget(updatedSource);
        }

        ability.use(context, effectResolver);

        if (ability.cooldown().ticks() > 0) {
            cooldowns.startCooldown(ability.id(), ability.cooldown().ticks());
        }

        AbilityMessages resolved = resolveMessages(ability, context);
        messageSink.sendToSource(context.source(), resolved.self());
        if (!context.source().getUsername().equals(context.target().getUsername())) {
            messageSink.sendToTarget(context.target(), resolved.target());
        }
        messageSink.sendToRoom(context.source(), context.target(), resolved.room());

        List<String> messages = new ArrayList<>();
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
        return switch (effect.kind()) {
            case VITALS -> {
                String stat = effect.stat().name().toLowerCase();
                String verb = effect.operation() == AbilityOperation.INCREASE ? "gains" : "loses";
                yield target.getUsername().getValue() + " " + verb + " " + effect.amount() + " " + stat + ".";
            }
            case EFFECT -> target.getUsername().getValue() + " is affected by " + effect.effectId() + ".";
        };
    }

    private AbilityMessages resolveMessages(Ability ability, AbilityContext context) {
        AbilityMessages messages = ability.messages();
        String sourceName = context.source().getUsername().getValue();
        String targetName = context.target().getUsername().getValue();
        String abilityName = ability.name();

        String selfTemplate;
        String targetTemplate;
        String roomTemplate;

        if (messages == null) {
            if (sourceName.equals(targetName)) {
                selfTemplate = "You use {ability} on yourself.";
                targetTemplate = "{source} uses {ability} on themselves.";
                roomTemplate = "{source} uses {ability} on themselves.";
            } else {
                selfTemplate = "You use {ability} on {target}.";
                targetTemplate = "{source} uses {ability} on you.";
                roomTemplate = "{source} uses {ability} on {target}.";
            }
        } else {
            selfTemplate = normalize(messages.self(), "You use {ability} on {target}.");
            targetTemplate = normalize(messages.target(), "{source} uses {ability} on you.");
            roomTemplate = normalize(messages.room(), "{source} uses {ability} on {target}.");
        }

        return new AbilityMessages(
            format(selfTemplate, sourceName, targetName, abilityName),
            format(targetTemplate, sourceName, targetName, abilityName),
            format(roomTemplate, sourceName, targetName, abilityName)
        );
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String format(String template, String sourceName, String targetName, String abilityName) {
        return template
            .replace("{source}", sourceName)
            .replace("{target}", targetName)
            .replace("{ability}", abilityName);
    }
}
