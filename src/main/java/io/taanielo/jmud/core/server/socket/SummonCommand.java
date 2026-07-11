package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SUMMON} command, the necromancer-style spell that conjures a temporary pet mob
 * to fight hostile mobs at the caster's side.
 *
 * <p>{@code SUMMON} spawns a pet (subject to the caster having learned the spell and meeting its
 * level and mana cost); {@code SUMMON DISMISS} releases the active pet early. Only one pet may be
 * active at a time and it auto-dismisses when its lifetime elapses or it is destroyed in combat. The
 * game logic lives in {@code MobRegistry.processSummon} / {@code MobRegistry.dismissPet} via
 * {@link SocketCommandContext#summon(String)}.
 */
public class SummonCommand extends RegistrableCommand {

    public SummonCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "summon";
    }

    @Override
    public String shortDescription() {
        return "Summon a temporary pet to fight for you, or SUMMON DISMISS to release it.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SUMMON | SUMMON DISMISS
                 Conjures a temporary pet mob that fights hostile mobs in your room and awards you
                 their experience and loot. The pet fades away after a while, or when it is
                 destroyed. You may have only one pet at a time. SUMMON DISMISS releases it early.
                 Requires having learned the summon spell and enough mana.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SUMMON".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.summon(args)));
    }
}
