package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.AttributeBonus;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies that harmful spell damage scales with the caster's intellect and healing scales with the
 * caster's wisdom, while a caster with no configured attributes resolver applies base amounts.
 */
class AbilityAttributeScalingTest {

    private final EffectEngine effectEngine = new EffectEngine(id -> Optional.empty());
    private final NoopMessageSink messageSink = new NoopMessageSink();

    @Test
    void intellectAmplifiesHarmfulSpellDamage() {
        // INT 16 => +6 modifier => 100 + 30 = 130% of a 20-damage bolt = 26.
        CharacterAttributesResolver resolver = resolverFor(new AttributeBonus(0, 6, 0, 0));
        DefaultAbilityEffectResolver effectResolver =
            new DefaultAbilityEffectResolver(effectEngine, messageSink, AbilityEffectListener.noop(), resolver);
        Player caster = player("mage", RaceId.of("arcane"), 100);
        Player target = player("victim", null, 100);
        AbilityContext context = new AbilityContext(caster, target);
        AbilityEffect firebolt =
            new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 20, null);

        effectResolver.apply(firebolt, context);

        assertEquals(74, context.target().getVitals().hp());
    }

    @Test
    void wisdomAmplifiesHealing() {
        // WIS 16 => +6 modifier => 130% of a 20-point heal = 26.
        CharacterAttributesResolver resolver = resolverFor(new AttributeBonus(0, 0, 6, 0));
        DefaultAbilityEffectResolver effectResolver =
            new DefaultAbilityEffectResolver(effectEngine, messageSink, AbilityEffectListener.noop(), resolver);
        Player caster = player("cleric", RaceId.of("arcane"), 50);
        AbilityContext context = new AbilityContext(caster, caster);
        AbilityEffect heal =
            new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.INCREASE, 20, null);

        effectResolver.apply(heal, context);

        assertEquals(76, context.target().getVitals().hp());
    }

    @Test
    void withoutResolverAmountsAreUnchanged() {
        DefaultAbilityEffectResolver effectResolver =
            new DefaultAbilityEffectResolver(effectEngine, messageSink, AbilityEffectListener.noop());
        Player caster = player("mage", RaceId.of("arcane"), 100);
        Player target = player("victim", null, 100);
        AbilityContext context = new AbilityContext(caster, target);
        AbilityEffect firebolt =
            new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 20, null);

        effectResolver.apply(firebolt, context);

        assertEquals(80, context.target().getVitals().hp());
    }

    private CharacterAttributesResolver resolverFor(AttributeBonus raceBonus) {
        Race race = new Race(RaceId.of("arcane"), "Arcane", 0, 50, 0, 0, 0, "", raceBonus);
        return CharacterAttributesResolver.fromDefinitions(List.of(race), List.of());
    }

    private Player player(String username, RaceId race, int hp) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(hp, 100, 50, 50, 50, 50),
            List.of(),
            "prompt",
            false,
            List.of(),
            race,
            (ClassId) null
        );
    }

    private static final class NoopMessageSink implements AbilityMessageSink {
        @Override
        public void sendToSource(Player source, String message) {
        }

        @Override
        public void sendToTarget(Player target, String message) {
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
        }
    }
}
