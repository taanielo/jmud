package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

/**
 * Decides whether a player holds wizard (administrator) privileges.
 *
 * <p>This is a deliberately thin, config-driven stand-in until the full role system arrives
 * (issue #44): the set of wizard usernames is supplied by the composition root from the
 * {@code jmud.wizards} configuration key. Membership uses {@link Username} equality, which is
 * case-insensitive.
 */
public final class WizardPolicy {

    private final Set<Username> wizards;

    /**
     * Creates a policy over the given set of wizard usernames.
     *
     * @param wizards the usernames granted wizard privileges; copied defensively
     */
    public WizardPolicy(Set<Username> wizards) {
        this.wizards = Set.copyOf(Objects.requireNonNull(wizards, "Wizards set is required"));
    }

    /**
     * Returns whether the given player has wizard privileges.
     *
     * @param player the player to check; may be {@code null} (an unauthenticated session)
     * @return {@code true} when the player is non-null and their username is a configured wizard
     */
    public boolean isWizard(Player player) {
        return player != null && wizards.contains(player.getUsername());
    }
}
