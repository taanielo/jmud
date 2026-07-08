package io.taanielo.jmud.core.ability;

public enum AbilityTargeting {
    /** Targets a hostile entity; usable at any time during or outside combat. */
    HARMFUL,
    /** Targets a friendly entity (defaults to self when no target is provided). */
    BENEFICIAL,
    /**
     * Targets a hostile entity but may only be used as an opening strike —
     * i.e. the source must not already be engaged in combat with any target.
     */
    HARMFUL_OPENER,
    /**
     * Applies a beneficial effect to every player in the caster's room,
     * including the caster themselves. No explicit target is required.
     */
    BENEFICIAL_GROUP,
    /**
     * Targets a hostile entity that must carry the {@code "undead"} mob tag.
     * If the target lacks the tag, the ability has no effect.
     */
    HARMFUL_UNDEAD,
    /**
     * Command-only utility ability with no generic target and no generic effects.
     *
     * <p>Such an ability is invoked exclusively through its own dedicated command (for example
     * the rogue {@code PICK} skill via {@code PickCommand} / {@code GameActionService.pickLock});
     * its mechanic is custom logic that does not flow through the generic effect pipeline. It is
     * still listed in the ability catalog so it can be learned and shown by {@code ABILITIES},
     * but {@link AbilityEngine} refuses to activate it through the generic {@code USE}/{@code CAST}
     * path. This closes the exploit where a non-inert opener effect could be applied to another
     * player via {@code USE}; a {@code NONE} ability can never reach effect resolution generically.
     */
    NONE
}
