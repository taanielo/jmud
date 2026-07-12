package io.taanielo.jmud.core.messaging;

import io.taanielo.jmud.core.authentication.Username;

/**
 * The substitution values available when rendering a {@link MessageSpec} template.
 *
 * <p>{@code verbThirdPerson} and {@code verbSecondPerson} carry a pre-resolved worded-damage verb in
 * its two conjugations (see {@code core.combat.flavor.DamageVerb}); {@link MessageRenderer} selects
 * the conjugation per channel when it substitutes {@code {verb}}. Both may be {@code null} for
 * non-damage messages, in which case {@code {verb}} renders as an empty string.
 */
public record MessageContext(
    Username sourceUser,
    Username targetUser,
    String sourceName,
    String targetName,
    String itemName,
    String effectName,
    String abilityName,
    Integer damage,
    String verbThirdPerson,
    String verbSecondPerson
) {
    /**
     * Creates a context with no worded-damage verb (both conjugations {@code null}). Used by every
     * message source that does not surface tiered damage.
     */
    public MessageContext(
        Username sourceUser,
        Username targetUser,
        String sourceName,
        String targetName,
        String itemName,
        String effectName,
        String abilityName,
        Integer damage
    ) {
        this(sourceUser, targetUser, sourceName, targetName, itemName, effectName, abilityName, damage, null, null);
    }
}
