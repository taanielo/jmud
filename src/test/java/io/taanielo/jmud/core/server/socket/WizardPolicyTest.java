package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

/**
 * Unit tests for {@link WizardPolicy}.
 */
class WizardPolicyTest {

    private static Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("secret")), "%h/%H hp>");
    }

    @Test
    void configuredWizardIsRecognised() {
        WizardPolicy policy = new WizardPolicy(Set.of(Username.of("Gandalf")));
        assertTrue(policy.isWizard(player("Gandalf")));
    }

    @Test
    void membershipIsCaseInsensitive() {
        WizardPolicy policy = new WizardPolicy(Set.of(Username.of("Gandalf")));
        assertTrue(policy.isWizard(player("gandalf")));
    }

    @Test
    void nonWizardIsRejected() {
        WizardPolicy policy = new WizardPolicy(Set.of(Username.of("Gandalf")));
        assertFalse(policy.isWizard(player("Frodo")));
    }

    @Test
    void emptyPolicyGrantsNobody() {
        WizardPolicy policy = new WizardPolicy(Set.of());
        assertFalse(policy.isWizard(player("Gandalf")));
    }

    @Test
    void nullPlayerIsNotWizard() {
        WizardPolicy policy = new WizardPolicy(Set.of(Username.of("Gandalf")));
        assertFalse(policy.isWizard(null));
    }
}
