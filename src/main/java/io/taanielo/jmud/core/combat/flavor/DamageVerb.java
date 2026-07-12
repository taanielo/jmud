package io.taanielo.jmud.core.combat.flavor;

import java.util.Objects;

/**
 * A damage-severity verb in its two conjugations.
 *
 * <p>The {@code thirdPerson} form ("mauls", "MASSACRES") agrees with a third-person-singular subject
 * and is used for the attacker's own line and the room line ("Your strike MAULS them!"). The
 * {@code secondPerson} base form ("maul", "MASSACRE") agrees with a second-person / plural subject
 * and is used for the victim's line ("The blows MAUL you!").
 *
 * @param thirdPerson  the third-person-singular conjugation; must not be blank
 * @param secondPerson the second-person / base conjugation; must not be blank
 */
public record DamageVerb(String thirdPerson, String secondPerson) {
    public DamageVerb {
        Objects.requireNonNull(thirdPerson, "Third-person verb is required");
        Objects.requireNonNull(secondPerson, "Second-person verb is required");
        if (thirdPerson.isBlank()) {
            throw new IllegalArgumentException("Third-person verb must not be blank");
        }
        if (secondPerson.isBlank()) {
            throw new IllegalArgumentException("Second-person verb must not be blank");
        }
    }
}
