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
