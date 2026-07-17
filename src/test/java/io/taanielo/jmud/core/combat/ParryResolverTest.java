package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies the agility-driven parry profile: a melee weapon in the mainhand grants a clamped parry
 * chance from AGI, while an unarmed or ranged-armed defender never parries.
 */
class ParryResolverTest {

    private static final AttackId DAGGER = AttackId.of("attack.dagger");
    private static final AttackId BOW = AttackId.of("attack.bow");

    private static final AttackDefinition MELEE_DAGGER =
        new AttackDefinition(DAGGER, "dagger", 3, 3, 0, 0, 0, List.of());
    private static final AttackDefinition RANGED_BOW =
        new AttackDefinition(BOW, "bow", 3, 3, 0, 0, 0, List.of(), WeaponType.PIERCING, null, RangeType.RANGED);

    @Test
    void baselineAgilityDefenderNeverParries() {
        // AGI 10 (no race bonus) => parry chance (10-10)=0, clamped to the floor => no parry.
        ParryResolver resolver = resolverFor(null);
        Player defender = defenderWithWeapon(null, weapon(ItemId.of("dagger"), DAGGER));

        ParryResolver.Parry parry = resolver.resolve(defender);

        assertFalse(parry.canParry());
        assertEquals(0, parry.chancePercent());
    }

    @Test
    void agilityDrivesParryChanceForMeleeWeapon() {
        // AGI 30 (race +20) => parry chance 20, within the [0, 25] band.
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 20));
        ParryResolver resolver = resolverFor(nimble);
        Player defender = defenderWithWeapon(RaceId.of("nimble"), weapon(ItemId.of("dagger"), DAGGER));

        ParryResolver.Parry parry = resolver.resolve(defender);

        assertTrue(parry.canParry());
        assertEquals(20, parry.chancePercent());
        assertSame(MELEE_DAGGER, parry.riposteAttack());
    }

    @Test
    void parryChanceIsClampedToMax() {
        // AGI 45 (race +35) => raw parry chance 35, clamped down to MAX_PARRY_CHANCE (25).
        Race blur = race("blur", new AttributeBonus(0, 0, 0, 35));
        ParryResolver resolver = resolverFor(blur);
        Player defender = defenderWithWeapon(RaceId.of("blur"), weapon(ItemId.of("dagger"), DAGGER));

        ParryResolver.Parry parry = resolver.resolve(defender);

        assertEquals(CombatSettings.MAX_PARRY_CHANCE, parry.chancePercent());
    }

    @Test
    void unarmedDefenderNeverParries() {
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 20));
        ParryResolver resolver = resolverFor(nimble);
        Player defender = player("defender", RaceId.of("nimble"));

        assertFalse(resolver.resolve(defender).canParry());
    }

    @Test
    void rangedMainhandWeaponNeverParries() {
        // Even a high-AGI defender wielding a bow cannot parry an incoming melee blow.
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 20));
        ParryResolver resolver = resolverFor(nimble);
        Player defender = defenderWithWeapon(RaceId.of("nimble"), weapon(ItemId.of("bow"), BOW));

        assertFalse(resolver.resolve(defender).canParry());
    }

    @Test
    void noOpResolverNeverParries() {
        Player defender = defenderWithWeapon(null, weapon(ItemId.of("dagger"), DAGGER));
        assertFalse(ParryResolver.noOp().resolve(defender).canParry());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ParryResolver resolverFor(Race race) {
        CombatAttributeBonusResolver attributeResolver = race == null
            ? CombatAttributeBonusResolver.noOp()
            : new CombatAttributeBonusResolver(
                CharacterAttributesResolver.fromDefinitions(List.of(race), List.of()));
        return new ParryResolver(new StubAttackRepository(Map.of(
            DAGGER, MELEE_DAGGER, BOW, RANGED_BOW)), attributeResolver);
    }

    private Item weapon(ItemId id, AttackId attackRef) {
        return Item.builder(id, "Weapon", "A test weapon.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.WEAPON).weight(1).value(0).attackRef(attackRef).build();
    }

    private Player defenderWithWeapon(RaceId race, Item weapon) {
        return player("defender", race)
            .withInventory(List.of(weapon))
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.WEAPON, weapon.getId()));
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

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks) implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }
}
