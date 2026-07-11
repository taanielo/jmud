package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;

class EncumbranceServiceTest {

    private static Player playerWith(RaceId race, ClassId classId) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1000));
        PlayerVitals vitals = PlayerVitals.defaults();
        return new Player(user, 1, 0, vitals, List.of(), "prompt", false, List.of(), race, classId);
    }

    @Test
    void maxCarrySumsRaceBaseAndClassBonus() {
        RaceId raceId = RaceId.of("human");
        ClassId classId = ClassId.of("warrior");
        RaceRepository races = fixedRace(raceId, new Race(raceId, "Human", 0, 100));
        ClassRepository classes = fixedClass(classId, new ClassDefinition(classId, "Warrior", 0, 25, List.of()));
        EncumbranceService service = new EncumbranceService(races, classes);

        assertEquals(125, service.maxCarry(playerWith(raceId, classId)));
    }

    @Test
    void maxCarryDegradesToZeroBaseWhenRaceLookupFails() {
        RaceId raceId = RaceId.of("broken");
        ClassId classId = ClassId.of("warrior");
        RaceRepository races = throwingRace();
        ClassRepository classes = fixedClass(classId, new ClassDefinition(classId, "Warrior", 0, 25, List.of()));
        EncumbranceService service = new EncumbranceService(races, classes);
        Player player = playerWith(raceId, classId);

        // The lookup failure must not propagate; the class bonus still applies (base degrades to 0).
        int result = assertDoesNotThrow(() -> service.maxCarry(player));
        assertEquals(25, result);
    }

    @Test
    void maxCarryDegradesToZeroBonusWhenClassLookupFails() {
        RaceId raceId = RaceId.of("human");
        ClassId classId = ClassId.of("broken");
        RaceRepository races = fixedRace(raceId, new Race(raceId, "Human", 0, 100));
        ClassRepository classes = throwingClass();
        EncumbranceService service = new EncumbranceService(races, classes);
        Player player = playerWith(raceId, classId);

        int result = assertDoesNotThrow(() -> service.maxCarry(player));
        assertEquals(100, result);
    }

    private static RaceRepository fixedRace(RaceId id, Race race) {
        return new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId lookup) {
                return lookup.equals(id) ? Optional.of(race) : Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of(race);
            }
        };
    }

    private static ClassRepository fixedClass(ClassId id, ClassDefinition definition) {
        return new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId lookup) {
                return lookup.equals(id) ? Optional.of(definition) : Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of(definition);
            }
        };
    }

    private static RaceRepository throwingRace() {
        return new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId lookup) throws RaceRepositoryException {
                throw new RaceRepositoryException("boom");
            }

            @Override
            public List<Race> findAll() throws RaceRepositoryException {
                throw new RaceRepositoryException("boom");
            }
        };
    }

    private static ClassRepository throwingClass() {
        return new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId lookup) throws ClassRepositoryException {
                throw new ClassRepositoryException("boom");
            }

            @Override
            public List<ClassDefinition> findAll() throws ClassRepositoryException {
                throw new ClassRepositoryException("boom");
            }
        };
    }
}
