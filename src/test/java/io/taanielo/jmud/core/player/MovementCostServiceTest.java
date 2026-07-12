package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

class MovementCostServiceTest {

    private static final RaceId RACE_ID = RaceId.of("human");
    private static final int MAX_CARRY = 10;
    private static final int BASE_COST = 1;
    private static final int SURCHARGE = 1;

    private final MovementCostService service =
        new MovementCostService(encumbranceService(), BASE_COST, SURCHARGE);

    @Test
    void normalMoveDecrementsByBaseCost() {
        Player player = player(withMove(10), List.of());

        Player moved = service.spend(player);

        assertEquals(BASE_COST, service.stepCost(player));
        assertEquals(9, moved.getVitals().move());
    }

    @Test
    void overburdenedMovePaysSurcharge() {
        Player player = player(withMove(10), List.of(heavyRock()));

        assertEquals(BASE_COST + SURCHARGE, service.stepCost(player));
        assertEquals(8, service.spend(player).getVitals().move());
    }

    @Test
    void playerWithNoMovePointsIsExhausted() {
        assertTrue(service.isExhausted(player(withMove(0), List.of())));
        assertFalse(service.isExhausted(player(withMove(1), List.of())));
    }

    @Test
    void spendFloorsMoveAtZero() {
        // Overburdened cost (2) exceeds the single remaining move point; it floors at zero.
        Player player = player(withMove(1), List.of(heavyRock()));

        assertEquals(0, service.spend(player).getVitals().move());
    }

    @Test
    void mountedMoveReducesStepCostByDiscount() {
        // A higher base cost so the mount discount is visible without flooring at zero.
        MovementCostService pricey = new MovementCostService(encumbranceService(), 4, SURCHARGE);
        Player onFoot = player(withMove(10), List.of());
        Player mounted = onFoot.withMount(PlayerMount.riding("a swift warhorse", 2));

        assertEquals(4, pricey.stepCost(onFoot));
        assertEquals(2, pricey.stepCost(mounted));
        assertEquals(8, pricey.spend(mounted).getVitals().move());
    }

    @Test
    void distinctMountsGrantDistinctDiscounts() {
        MovementCostService pricey = new MovementCostService(encumbranceService(), 4, SURCHARGE);
        Player pony = player(withMove(10), List.of()).withMount(PlayerMount.riding("a sturdy pony", 1));
        Player warhorse = player(withMove(10), List.of()).withMount(PlayerMount.riding("a swift warhorse", 2));

        assertEquals(3, pricey.stepCost(pony));
        assertEquals(2, pricey.stepCost(warhorse));
    }

    @Test
    void mountDiscountFloorsStepCostAtZero() {
        // Base cost 1, discount 2: the step is free, never negative.
        Player mounted = player(withMove(5), List.of()).withMount(PlayerMount.riding("a swift warhorse", 2));

        assertEquals(0, service.stepCost(mounted));
        assertEquals(5, service.spend(mounted).getVitals().move());
    }

    @Test
    void mountDiscountOffsetsOverburdenSurcharge() {
        // Overburdened base+surcharge = 2, warhorse discount 2 → free steps while overloaded.
        Player mounted = player(withMove(5), List.of(heavyRock()))
            .withMount(PlayerMount.riding("a swift warhorse", 2));

        assertEquals(0, service.stepCost(mounted));
    }

    @Test
    void dismountedPlayerPaysFullCost() {
        Player mounted = player(withMove(10), List.of()).withMount(PlayerMount.riding("a sturdy pony", 1));
        Player dismounted = mounted.withMount(PlayerMount.dismounted());

        assertEquals(BASE_COST, service.stepCost(dismounted));
    }

    @Test
    void restingRegenComposesWithSpend() {
        Player player = player(withMove(10), List.of());

        Player afterStep = service.spend(player);
        assertEquals(9, afterStep.getVitals().move());

        // A rest tick restores move exactly as RestingTicker does, on top of the spent value.
        PlayerVitals rested = afterStep.getVitals().regenRest(0, 0, 2);
        assertEquals(11, rested.move());
    }

    private static PlayerVitals withMove(int move) {
        return new PlayerVitals(20, 20, 20, 20, move, 20);
    }

    private static Player player(PlayerVitals vitals, List<Item> inventory) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1000));
        Player base = new Player(user, 1, 0, vitals, List.of(), "prompt", false, List.of(), RACE_ID, null);
        return inventory.isEmpty() ? base : base.withInventory(inventory);
    }

    private static Item heavyRock() {
        return Item.builder(ItemId.of("rock"), "Boulder", "A heavy boulder.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(MAX_CARRY + 5)
            .value(0)
            .build();
    }

    private static EncumbranceService encumbranceService() {
        RaceRepository races = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId lookup) {
                return lookup.equals(RACE_ID)
                    ? Optional.of(new Race(RACE_ID, "Human", 0, MAX_CARRY))
                    : Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of(new Race(RACE_ID, "Human", 0, MAX_CARRY));
            }
        };
        ClassRepository classes = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId lookup) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new EncumbranceService(races, classes);
    }
}
