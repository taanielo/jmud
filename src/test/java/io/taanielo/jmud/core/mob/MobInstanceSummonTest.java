package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link MobInstance}'s summoned-pet tracking: the {@link MobInstance#summoned}
 * factory, summoner ownership, and lifetime decay via {@link MobInstance#tickSummonLifetime()}.
 */
class MobInstanceSummonTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final RoomId SUMMON_ROOM = RoomId.of("crypt");
    private static final Username SUMMONER = Username.of("necro");
    private static final int OWNER_LEVEL = 1;

    private MobTemplate petTemplate() {
        return new MobTemplate(
            MobId.of("spectral-servant"),
            "Spectral Servant",
            25,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            0,
            0,
            null,
            List.of("undead", "pet"),
            false,
            null,
            15);
    }

    @Test
    void ordinaryMobIsNotSummoned() {
        MobInstance mob = new MobInstance(petTemplate());

        assertFalse(mob.isSummoned(), "A mob built via the normal constructor is not a pet");
        assertEquals(SPAWN_ROOM, mob.roomId(), "It spawns at the template spawn room");
    }

    @Test
    void summonedPetTracksSummonerRoomAndLifetime() {
        MobInstance pet = MobInstance.summoned(petTemplate(), SUMMON_ROOM, SUMMONER, OWNER_LEVEL, 3);

        assertTrue(pet.isSummoned(), "The factory produces a summoned pet");
        assertEquals(SUMMONER, pet.summoner(), "The pet remembers its summoner");
        assertEquals(SUMMON_ROOM, pet.roomId(), "The pet spawns in the summon room, not the template room");
        assertEquals(3, pet.summonTicksRemaining(), "The pet starts with its full lifetime");
    }

    @Test
    void tickSummonLifetimeDecaysAndReportsExpiry() {
        MobInstance pet = MobInstance.summoned(petTemplate(), SUMMON_ROOM, SUMMONER, OWNER_LEVEL, 2);

        assertFalse(pet.tickSummonLifetime(), "First tick decays to 1, not yet expired");
        assertEquals(1, pet.summonTicksRemaining());
        assertTrue(pet.tickSummonLifetime(), "Second tick decays to 0 and reports expiry");
    }

    @Test
    void summonedRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class,
            () -> MobInstance.summoned(petTemplate(), SUMMON_ROOM, SUMMONER, OWNER_LEVEL, 0));
    }
}
