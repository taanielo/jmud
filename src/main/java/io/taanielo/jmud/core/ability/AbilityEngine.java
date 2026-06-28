package io.taanielo.jmud.core.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;

public class AbilityEngine {
    private final AbilityRegistry registry;
    private final AbilityCostResolver costResolver;
    private final AbilityEffectResolver effectResolver;
    private final AbilityMessageSink messageSink;
    private final MessageRenderer renderer = new MessageRenderer();

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

    /**
     * Uses an ability identified by {@code input}.
     *
     * <p>Equivalent to calling
     * {@link #use(Player, String, List, AbilityTargetResolver, AbilityCooldownTracker, Predicate)}
     * with an {@code inCombatCheck} that always returns {@code false} (never in combat).
     * Prefer the overload that accepts an explicit in-combat predicate when combat state
     * is available to the caller.
     *
     * @param source            the player using the ability
     * @param input             raw ability input (name and optional target)
     * @param learnedAbilityIds abilities the player has learned
     * @param targetResolver    resolves target names to player objects
     * @param cooldowns         tracks per-ability cooldowns
     * @return result of the ability use
     */
    public AbilityUseResult use(
        Player source,
        String input,
        List<AbilityId> learnedAbilityIds,
        AbilityTargetResolver targetResolver,
        AbilityCooldownTracker cooldowns
    ) {
        return use(source, input, learnedAbilityIds, targetResolver, cooldowns, _ -> false);
    }

    /**
     * Uses an ability identified by {@code input}, enforcing first-strike restrictions for
     * {@link AbilityTargeting#HARMFUL_OPENER} abilities via the supplied predicate.
     *
     * @param source            the player using the ability
     * @param input             raw ability input (name and optional target)
     * @param learnedAbilityIds abilities the player has learned
     * @param targetResolver    resolves target names to player objects
     * @param cooldowns         tracks per-ability cooldowns
     * @param inCombatCheck     returns {@code true} when the source is already engaged in combat;
     *                          used to block opener abilities mid-combat
     * @return result of the ability use
     */
    public AbilityUseResult use(
        Player source,
        String input,
        List<AbilityId> learnedAbilityIds,
        AbilityTargetResolver targetResolver,
        AbilityCooldownTracker cooldowns,
        Predicate<Player> inCombatCheck
    ) {
        Objects.requireNonNull(source, "Source player is required");
        Objects.requireNonNull(targetResolver, "Target resolver is required");
        Objects.requireNonNull(cooldowns, "Cooldown tracker is required");
        Objects.requireNonNull(inCombatCheck, "In-combat check is required");

        Optional<AbilityMatch> match = registry.findBestMatch(input, learnedAbilityIds);
        if (match.isEmpty()) {
            return new AbilityUseResult(source, source, List.of("You don't know that ability."));
        }
        Ability ability = match.get().ability();
        String targetInput = match.get().remainingTarget();

        if (ability.targeting() == AbilityTargeting.HARMFUL_OPENER && inCombatCheck.test(source)) {
            return new AbilityUseResult(source, source,
                List.of("You can only backstab as an opener — you are already in combat."));
        }

        Player target = resolveTarget(ability, source, targetInput, targetResolver);
        if (target == null) {
            if (isHarmfulTargeting(ability.targeting())) {
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

        emitMessages(ability, context);

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

    /** Returns {@code true} for targeting modes that require an explicit hostile target. */
    private static boolean isHarmfulTargeting(AbilityTargeting targeting) {
        return targeting == AbilityTargeting.HARMFUL || targeting == AbilityTargeting.HARMFUL_OPENER;
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

    private void emitMessages(Ability ability, AbilityContext context) {
        List<MessageSpec> specs = ability.messages();
        if (specs.isEmpty()) {
            return;
        }
        MessageContext messageContext = new MessageContext(
            context.source().getUsername(),
            context.target().getUsername(),
            context.source().getUsername().getValue(),
            context.target().getUsername().getValue(),
            null,
            null,
            ability.name(),
            null
        );
        for (MessageSpec spec : specs) {
            if (spec.phase() != MessagePhase.USE) {
                continue;
            }
            String rendered = renderer.render(spec, messageContext);
            if (rendered == null || rendered.isBlank()) {
                continue;
            }
            MessageChannel channel = spec.channel();
            switch (channel) {
                case SELF -> messageSink.sendToSource(context.source(), rendered);
                case TARGET -> {
                    if (!context.source().getUsername().equals(context.target().getUsername())) {
                        messageSink.sendToTarget(context.target(), rendered);
                    }
                }
                case ROOM -> messageSink.sendToRoom(context.source(), context.target(), rendered);
            }
        }
    }
}
