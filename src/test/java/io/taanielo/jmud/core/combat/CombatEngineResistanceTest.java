package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies elemental-resistance mitigation in {@link CombatEngine}: physical/untyped attacks are
 * unaffected, zero resistance leaves damage unchanged, partial resistance scales damage down, and
 * excessive resistance is capped so a blow always lands for a floor of damage.
 */
class CombatEngineResistanceTest {

    private static final AttackId FIRE_ATTACK = AttackId.of("attack.fire");
    private static final AttackId PHYSICAL_ATTACK = AttackId.of("attack.physical");
    private static final ItemId WARD_ID = ItemId.of("ward");

    @Test
    void physicalAttackIsNeverResistedEvenWithResistGear() throws Exception {
        // 40% "fire_resist" gear must NOT reduce a physical blow.
        CombatResult result = resolveFireOrPhysical(PHYSICAL_ATTACK, "fire_resist", 40);
        assertTrue(result.hit());
        assertEquals(40, result.damage());
    }

    @Test
    void typedAttackWithZeroResistDealsFullDamage() throws Exception {
        CombatResult result = resolveFireOrPhysical(FIRE_ATTACK, "cold_resist", 40);
        // Gear resists cold, not fire, so the fire attack lands at full strength.
        assertTrue(result.hit());
        assertEquals(40, result.damage());
    }

    @Test
    void typedAttackIsReducedByMatchingResistance() throws Exception {
        CombatResult result = resolveFireOrPhysical(FIRE_ATTACK, "fire_resist", 25);
        // 40 * (1 - 0.25) = 30.
        assertTrue(result.hit());
        assertEquals(30, result.damage());
    }

    @Test
    void resistanceIsCappedSoDamageNeverFullyNegated() throws Exception {
        // 200% raw resist is capped at CombatSettings.maxResistancePercent() (default 75).
        CombatResult result = resolveFireOrPhysical(FIRE_ATTACK, "fire_resist", 200);
        int expectedFloor = (int) Math.round(40 * ((100 - CombatSettings.maxResistancePercent()) / 100.0));
        assertTrue(result.hit());
        assertEquals(expectedFloor, result.damage());
        assertTrue(result.damage() > 0, "Capped resistance must never fully negate damage");
    }

    private CombatResult resolveFireOrPhysical(AttackId attackId, String resistStat, int resistValue)
        throws RepositoryException, EffectRepositoryException {
        AttackDefinition fire = new AttackDefinition(
            FIRE_ATTACK, "fire", 40, 40, 0, 0, 0, List.of(),
            WeaponType.SLASHING, null, RangeType.MELEE, DamageType.FIRE);
        AttackDefinition physical = new AttackDefinition(
            PHYSICAL_ATTACK, "physical", 40, 40, 0, 0, 0, List.of(),
            WeaponType.SLASHING, null, RangeType.MELEE, DamageType.PHYSICAL);

        Item ward = Item.builder(WARD_ID, "Ward", "A resist ward.",
                new ItemAttributes(Map.of(resistStat, resistValue)))
            .equipSlot(EquipmentSlot.CHEST)
            .weight(1)
            .value(0)
            .build();
        ItemRepository itemRepository = new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return WARD_ID.equals(id) ? Optional.of(ward) : Optional.empty();
            }
        };

        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(FIRE_ATTACK, fire, PHYSICAL_ATTACK, physical)),
            new CombatModifierResolver(new StubEffectRepository()),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            new EquipmentResistanceResolver(itemRepository),
            ShieldBlockResolver.noOp(),
            OffhandAttackResolver.disabled(),
            CombatAttributeBonusResolver.noOp(),
            (tick, actorId) -> new FixedCombatRandom(10, 40, 100),
            () -> 0L,
            null,
            null
        );

        Player attacker = player("attacker");
        Player target = player("target").withEquipment(
            PlayerEquipment.empty().equip(EquipmentSlot.CHEST, WARD_ID));

        return engine.resolve(attacker, target, attackId);
    }

    private Player player(String username) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(200, 200, 100, 100, 100, 100),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(),
            null,
            null
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

    private static final class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        private StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubEffectRepository implements EffectRepository {
        @Override
        public Optional<io.taanielo.jmud.core.effects.EffectDefinition> findById(
            io.taanielo.jmud.core.effects.EffectId id) {
            return Optional.empty();
        }
    }
}
