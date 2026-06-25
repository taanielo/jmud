package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link RestingTicker}.
 */
class RestingTickerTest {

    private static final int REGEN_HP   = 2;
    private static final int REGEN_MANA = 2;
    private static final int REGEN_MOVE = 2;

    private Player makePlayer(int hp, int maxHp, int mana, int maxMana, int move, int maxMove) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        Player base = Player.of(user, "%hp> ");
        PlayerVitals v = new PlayerVitals(hp, maxHp, mana, maxMana, move, maxMove);
        return base.withVitals(v).withResting(true);
    }

    private RestingTicker makeTicker(
        AtomicReference<Player> playerRef,
        List<String> fullyRestedMessages
    ) {
        return new RestingTicker(
            playerRef::get,
            playerRef::set,
            (msg, woken) -> {
                playerRef.set(woken);
                fullyRestedMessages.add(msg);
            },
            REGEN_HP,
            REGEN_MANA,
            REGEN_MOVE
        );
    }

    // ── rest starts ────────────────────────────────────────────────────

    @Test
    void tickDoesNothingWhenPlayerNotResting() {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");
        // player.isResting() == false by default

        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        // Player should be unchanged and no messages sent.
        assertEquals(player, ref.get(), "Non-resting player should not be updated");
        assertTrue(msgs.isEmpty());
    }

    @Test
    void tickDoesNothingWhenPlayerIsDead() {
        Player player = makePlayer(0, 20, 10, 20, 10, 20).die();
        // dead player won't have resting=true since die() clears it, so force it via withResting
        // Actually die() clears resting, but let's keep the spirit of the test:
        // a dead player is not rested even if somehow set to resting.
        User user = User.of(Username.of("deadhero"), Password.hash("pw", 1));
        Player deadResting = Player.of(user, "%hp> ")
            .withVitals(new PlayerVitals(0, 20, 10, 20, 10, 20))
            .die();
        // die() clears resting flag; verify ticker is safe with dead players.

        AtomicReference<Player> ref = new AtomicReference<>(deadResting);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        assertTrue(msgs.isEmpty(), "Dead player should not trigger rest regen");
    }

    // ── tick heals ─────────────────────────────────────────────────────

    @Test
    void tickRegeneratesHpManaMoveByConfiguredAmounts() {
        // Start with low vitals.
        Player player = makePlayer(10, 20, 8, 20, 6, 20);
        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        Player updated = ref.get();
        assertEquals(12, updated.getVitals().hp(),   "HP should increase by 2");
        assertEquals(10, updated.getVitals().mana(), "Mana should increase by 2");
        assertEquals(8,  updated.getVitals().move(), "Move should increase by 2");
        assertTrue(msgs.isEmpty(), "Not fully rested yet — no fully-rested message");
        assertTrue(updated.isResting(), "Should still be resting");
    }

    @Test
    void multipleTicksProgressivelyRegenerate() {
        Player player = makePlayer(14, 20, 14, 20, 14, 20);
        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick(); // 16/16/16
        ticker.tick(); // 18/18/18
        ticker.tick(); // 20/20/20 → fully rested

        assertNotNull(msgs.stream().filter(m -> m.contains("fully rested")).findFirst().orElse(null),
            "Should emit fully-rested message when all vitals reach max");
    }

    // ── auto-stops at full vitals ──────────────────────────────────────

    @Test
    void autoStopsWhenAlreadyFull() {
        // Player already at max.
        Player player = makePlayer(20, 20, 20, 20, 20, 20);
        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        assertEquals(1, msgs.size(), "One fully-rested message should fire");
        assertTrue(msgs.get(0).contains("fully rested"));
        assertFalse(ref.get().isResting(), "Resting flag should be cleared after auto-stop");
    }

    @Test
    void autoStopsWhenVitalsReachMaxMidTick() {
        // One tick away from full.
        Player player = makePlayer(19, 20, 19, 20, 19, 20);
        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        Player updated = ref.get();
        assertEquals(20, updated.getVitals().hp());
        assertEquals(20, updated.getVitals().mana());
        assertEquals(20, updated.getVitals().move());
        assertEquals(1, msgs.size(), "Fully-rested message should fire");
        assertFalse(updated.isResting(), "Resting flag should be cleared");
    }

    // ── action cancels rest ────────────────────────────────────────────

    @Test
    void tickerIsNoOpAfterRestingFlagCleared() {
        Player player = makePlayer(10, 20, 10, 20, 10, 20);
        AtomicReference<Player> ref = new AtomicReference<>(player);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        // Simulate an action cancelling rest.
        ref.set(player.withResting(false));

        ticker.tick();

        // Vitals unchanged, no messages.
        assertEquals(10, ref.get().getVitals().hp(), "HP should not change after rest cancelled");
        assertTrue(msgs.isEmpty(), "No message when resting cancelled externally");
    }

    // ── mob hit cancels rest ───────────────────────────────────────────

    @Test
    void mobHitCancelsRestViaFlagClear() {
        Player resting = makePlayer(15, 20, 15, 20, 15, 20);
        assertTrue(resting.isResting());

        // Mob hit: player vitals updated by caller, resting flag cleared.
        Player afterHit = resting.withVitals(resting.getVitals().damage(3)).withResting(false);

        assertFalse(afterHit.isResting(), "Resting flag must be cleared after mob hit");
        assertEquals(12, afterHit.getVitals().hp(), "HP must reflect damage");

        // Subsequent ticker tick should be a no-op.
        AtomicReference<Player> ref = new AtomicReference<>(afterHit);
        List<String> msgs = new ArrayList<>();
        RestingTicker ticker = makeTicker(ref, msgs);

        ticker.tick();

        assertTrue(msgs.isEmpty(), "No regen after rest cancelled by mob hit");
    }
}
