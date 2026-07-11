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
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.Durability;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

class OffhandAttackResolverTest {

    private final OffhandAttackResolver resolver = new OffhandAttackResolver();

    @Test
    void resolvesOffhandWeaponWithAttackRef() {
        ItemId id = ItemId.of("parrying-dagger");
        AttackId attackRef = AttackId.of("attack.parrying-dagger");
        Player attacker = attackerWithOffhand(weapon(id, "Parrying Dagger", attackRef, Map.of()));

        Optional<OffhandAttackResolver.OffhandWeapon> weapon = resolver.resolve(attacker);

        assertTrue(weapon.isPresent());
        assertEquals(attackRef, weapon.get().attackId());
        assertEquals("Parrying Dagger", weapon.get().weaponName());
    }

    @Test
    void treatsShieldAsBlockNotWeapon() {
        // A shield (block_chance) is resolved by ShieldBlockResolver, never as a dual-wield weapon,
        // even if it somehow also carried an attackRef — the two mechanics are mutually exclusive.
        ItemId id = ItemId.of("training-shield");
        Item shield = weapon(id, "Training Shield", AttackId.of("attack.dagger"),
            Map.of("block_chance", 25, "block_reduction", 50));
        Player attacker = attackerWithOffhand(shield);

        assertTrue(resolver.resolve(attacker).isEmpty());
    }

    @Test
    void ignoresNonWeaponOffhandItem() {
        ItemId id = ItemId.of("trinket");
        Item trinket = Item.builder(id, "Lucky Charm", "A trinket.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).build();
        Player attacker = attackerWithOffhand(trinket);

        assertTrue(resolver.resolve(attacker).isEmpty());
    }

    @Test
    void ignoresBrokenOffhandWeapon() {
        ItemId id = ItemId.of("broken-dagger");
        Item broken = Item.builder(id, "Cracked Dagger", "A broken blade.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0)
            .attackRef(AttackId.of("attack.dagger"))
            .durability(Durability.of(10, 0))
            .build();
        Player attacker = attackerWithOffhand(broken);

        assertTrue(resolver.resolve(attacker).isEmpty());
    }

    @Test
    void emptyOffhandSlotYieldsNoWeapon() {
        Player attacker = player("attacker");

        assertTrue(resolver.resolve(attacker).isEmpty());
    }

    @Test
    void disabledResolverNeverGrantsOffhandAttack() {
        ItemId id = ItemId.of("parrying-dagger");
        Player attacker = attackerWithOffhand(
            weapon(id, "Parrying Dagger", AttackId.of("attack.parrying-dagger"), Map.of()));

        assertFalse(OffhandAttackResolver.disabled().resolve(attacker).isPresent());
    }

    private Item weapon(ItemId id, String name, AttackId attackRef, Map<String, Integer> stats) {
        return Item.builder(id, name, "A test off-hand item.", new ItemAttributes(stats))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).attackRef(attackRef).build();
    }

    private Player attackerWithOffhand(Item offhand) {
        return player("attacker")
            .withInventory(List.of(offhand))
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, offhand.getId()));
    }

    private Player player(String username) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<EffectInstance>(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );
    }
}
