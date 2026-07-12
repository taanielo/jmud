package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that core attributes feed the combat pipeline: strength adds physical damage, agility
 * shifts hit/dodge/crit, and an all-baseline combatant produces the same result whether attribute
 * resolution is wired in or not (the regression guard from issue #524).
 */
class CombatEngineAttributeTest {

    private static final AttackId DAGGER = AttackId.of("attack.dagger");

    @Test
    void strengthAddsPhysicalDamageAfterWeaponRoll() throws Exception {
        // Dagger rolls 1-3; STR 16 => floor((16-10)/2) = +3 damage.
        AttackDefinition dagger = new AttackDefinition(DAGGER, "dagger", 1, 3, 0, 0, 0, List.of());
        Race strongRace = race("strong", new AttributeBonus(6, 0, 0, 0));
        // Rolls: hit=10 (hit), damage=3, crit=100 (no crit).
        CombatEngine engine = engineFor(dagger, strongRace, new FixedCombatRandom(10, 3, 100));

        Player attacker = player("attacker", RaceId.of("strong"));
        Player target = player("target", null);
        CombatResult result = engine.resolve(attacker, target, DAGGER);

        assertTrue(result.hit());
        assertEquals(6, result.damage());
    }

    @Test
    void strengthTenDealsUnmodifiedWeaponDamage() throws Exception {
        AttackDefinition dagger = new AttackDefinition(DAGGER, "dagger", 1, 3, 0, 0, 0, List.of());
        Race strongRace = race("strong", new AttributeBonus(6, 0, 0, 0));
        CombatEngine engine = engineFor(dagger, strongRace, new FixedCombatRandom(10, 3, 100));

        // A raceless (STR 10) attacker with the same weapon deals only the weapon roll.
        Player attacker = player("attacker", null);
        Player target = player("target", null);
        CombatResult result = engine.resolve(attacker, target, DAGGER);

        assertTrue(result.hit());
        assertEquals(3, result.damage());
    }

    @Test
    void agilityRaisesAttackerHitChance() throws Exception {
        AttackDefinition attack = new AttackDefinition(DAGGER, "dagger", 2, 2, 0, 0, 0, List.of());
        // Attacker AGI 16 => +6 hit; base 75 => 81. A roll of 80 now lands where it would miss at 75.
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 6));
        CombatEngine engine = engineFor(attack, nimble, new FixedCombatRandom(80, 2, 100));
        Player attacker = player("attacker", RaceId.of("nimble"));
        Player target = player("target", null);

        CombatResult result = engine.resolve(attacker, target, DAGGER);

        assertTrue(result.hit(), "AGI 16 raises hit chance to 81 so a roll of 80 lands");
    }

    @Test
    void defenderAgilityDodgeLowersAttackerHitChance() throws Exception {
        AttackDefinition attack = new AttackDefinition(DAGGER, "dagger", 2, 2, 0, 0, 0, List.of());
        // Defender AGI 16 => dodge floor(6/2)=3; base 75 => 72. A roll of 74 misses.
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 6));
        CombatEngine engine = engineFor(attack, nimble, new FixedCombatRandom(74, 2, 100));
        Player attacker = player("attacker", null);
        Player nimbleTarget = player("target", RaceId.of("nimble"));

        CombatResult result = engine.resolve(attacker, nimbleTarget, DAGGER);

        assertFalse(result.hit(), "Defender dodge lowers hit chance to 72 so a roll of 74 misses");
    }

    @Test
    void allBaselineCombatantsAreUnchangedWhetherAttributesAreWiredOrNot() throws Exception {
        AttackDefinition attack = new AttackDefinition(DAGGER, "dagger", 4, 4, 0, 0, 0, List.of());
        Player attacker = player("attacker", null);
        Player target = player("target", null);

        CombatEngine withAttributes =
            engineFor(attack, race("strong", new AttributeBonus(6, 0, 0, 0)), new FixedCombatRandom(10, 4, 100));
        CombatEngine withoutAttributes = engineFor(attack, null, new FixedCombatRandom(10, 4, 100));

        CombatResult a = withAttributes.resolve(attacker, target, DAGGER);
        CombatResult b = withoutAttributes.resolve(attacker, target, DAGGER);

        assertEquals(b.hit(), a.hit());
        assertEquals(b.damage(), a.damage());
        assertEquals(4, a.damage());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private CombatEngine engineFor(AttackDefinition attack, Race race, CombatRandom random) {
        CombatAttributeBonusResolver attributeResolver = race == null
            ? CombatAttributeBonusResolver.noOp()
            : new CombatAttributeBonusResolver(
                CharacterAttributesResolver.fromDefinitions(List.of(race), List.of()));
        return new CombatEngine(
            new StubAttackRepository(Map.of(attack.id(), attack)),
            new CombatModifierResolver(new StubEffectRepository()),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            ShieldBlockResolver.noOp(),
            OffhandAttackResolver.disabled(),
            attributeResolver,
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
    }

    private Race race(String id, AttributeBonus bonus) {
        return new Race(RaceId.of(id), id, 0, 50, 0, 0, 0, "", bonus);
    }

    private Player player(String username, RaceId race) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            List.of(),
            "prompt",
            false,
            List.of(),
            race,
            (ClassId) null
        );
    }

    private static final class FixedCombatRandom implements CombatRandom {
        private final int[] rolls;
        private int index;

        private FixedCombatRandom(int... rolls) {
            this.rolls = rolls;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (index >= rolls.length) {
                throw new IllegalStateException("No more rolls available");
            }
            return rolls[index++];
        }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks) implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubEffectRepository implements EffectRepository {
        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.empty();
        }
    }
}
