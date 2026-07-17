package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies parry + riposte resolution in the PvP combat pipeline: a melee-armed, high-agility
 * defender fully avoids an otherwise-landing melee hit, takes zero damage, and ripostes the attacker.
 */
class CombatEngineParryTest {

    private static final AttackId SWORD = AttackId.of("attack.sword");
    private static final AttackId DAGGER = AttackId.of("attack.dagger");
    private static final AttackId ARROW = AttackId.of("attack.arrow");

    // Attacker's incoming melee blow (would otherwise deal 5).
    private static final AttackDefinition SWORD_ATTACK =
        new AttackDefinition(SWORD, "sword", 5, 5, 0, 0, 0, List.of());
    // Defender's mainhand weapon, used for the riposte damage roll (3).
    private static final AttackDefinition DAGGER_ATTACK =
        new AttackDefinition(DAGGER, "dagger", 3, 3, 0, 0, 0, List.of());
    // A ranged incoming attack (SHOOT) — never parryable.
    private static final AttackDefinition ARROW_ATTACK =
        new AttackDefinition(ARROW, "arrow", 5, 5, 0, 0, 0, List.of(), WeaponType.PIERCING, null, RangeType.RANGED);

    @Test
    void meleeHitIsParriedForZeroDamageAndRipostesTheAttacker() throws Exception {
        // Rolls: hit=10 (<=65 lands), parry=10 (<=20 parried), riposteDamage=3.
        CombatEngine engine = engine(new FixedCombatRandom(10, 10, 3));
        Player attacker = player("attacker", null);
        Player defender = nimbleDefenderWithDagger();
        int attackerHp = attacker.getVitals().hp();
        int defenderHp = defender.getVitals().hp();

        CombatResult result = engine.resolve(attacker, defender, SWORD);

        assertTrue(result.hit(), "the swing landed before it was parried");
        assertFalse(result.crit(), "a parried hit never crits");
        assertFalse(result.blocked(), "a parried hit never also blocks");
        assertEquals(0, result.damage(), "a parried hit deals zero damage");
        assertEquals(defenderHp, result.target().getVitals().hp(), "the defender takes no damage");
        assertEquals(attackerHp - 3, result.attacker().getVitals().hp(),
            "the riposte lands on the original attacker");
        assertTrue(result.targetMessage().toLowerCase(java.util.Locale.ROOT).contains("parr"),
            "the defender is told they parried");
    }

    @Test
    void parryTakesPrecedenceOverShieldBlockOnTheSameSwing() throws Exception {
        // Exactly three rolls are supplied: hit, parry, riposte-damage. If the code erroneously rolled
        // a shield block between the parry and the riposte it would exhaust the RNG and throw, so a
        // clean resolution proves the block was never rolled (no double-mitigation).
        CombatEngine engine = engine(new FixedCombatRandom(10, 10, 3));
        Player attacker = player("attacker", null);
        Player defender = nimbleDefenderWithDaggerAndShield();

        CombatResult result = engine.resolve(attacker, defender, SWORD);

        assertFalse(result.blocked());
        assertEquals(0, result.damage());
    }

    @Test
    void rangedAttackIsNeverParried() throws Exception {
        // Rolls: hit=10, damage=5, crit=100 (no crit). No parry roll is consumed for a ranged attack.
        CombatEngine engine = engine(new FixedCombatRandom(10, 5, 100));
        Player attacker = player("attacker", null);
        Player defender = nimbleDefenderWithDagger();
        int attackerHp = attacker.getVitals().hp();

        CombatResult result = engine.resolve(attacker, defender, ARROW);

        assertTrue(result.hit());
        assertEquals(5, result.damage(), "a ranged hit lands its full damage — no parry");
        assertEquals(attackerHp, result.attacker().getVitals().hp(), "no riposte on a ranged attack");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private CombatEngine engine(CombatRandom random) {
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 20));
        CombatAttributeBonusResolver attributeResolver = new CombatAttributeBonusResolver(
            CharacterAttributesResolver.fromDefinitions(List.of(nimble), List.of()));
        StubAttackRepository attacks = new StubAttackRepository(Map.of(
            SWORD, SWORD_ATTACK, DAGGER, DAGGER_ATTACK, ARROW, ARROW_ATTACK));
        ParryResolver parryResolver = new ParryResolver(attacks, attributeResolver);
        return new CombatEngine(
            attacks,
            new CombatModifierResolver(new StubEffectRepository()),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            EquipmentResistanceResolver.noOp(),
            shieldResolver(),
            OffhandAttackResolver.disabled(),
            attributeResolver,
            (tick, actorId) -> random,
            () -> 0L,
            null,
            null,
            parryResolver
        );
    }

    private ShieldBlockResolver shieldResolver() {
        ItemId shieldId = ItemId.of("shield");
        Item shield = Item.builder(shieldId, "Shield", "A shield.",
                new ItemAttributes(Map.of("block_chance", 50, "block_reduction", 50)))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).build();
        return new ShieldBlockResolver(new io.taanielo.jmud.core.world.repository.ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return shieldId.equals(id) ? Optional.of(shield) : Optional.empty();
            }
        });
    }

    private Player nimbleDefenderWithDagger() {
        Item dagger = Item.builder(ItemId.of("dagger"), "Dagger", "A dagger.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.WEAPON).weight(1).value(0).attackRef(DAGGER).build();
        return player("defender", RaceId.of("nimble"))
            .withInventory(List.of(dagger))
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.WEAPON, dagger.getId()));
    }

    private Player nimbleDefenderWithDaggerAndShield() {
        Item dagger = Item.builder(ItemId.of("dagger"), "Dagger", "A dagger.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.WEAPON).weight(1).value(0).attackRef(DAGGER).build();
        return player("defender", RaceId.of("nimble"))
            .withInventory(List.of(dagger))
            .withEquipment(PlayerEquipment.empty()
                .equip(EquipmentSlot.WEAPON, dagger.getId())
                .equip(EquipmentSlot.OFFHAND, ItemId.of("shield")));
    }

    private Player player(String username, RaceId race) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(),
            race,
            (ClassId) null
        );
    }

    private Race race(String id, AttributeBonus bonus) {
        return new Race(RaceId.of(id), id, 0, 50, 0, 0, 0, "", bonus);
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
