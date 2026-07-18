package io.taanielo.jmud.core.world.area;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.Corpse;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Read-only domain service backing the {@code CORPSE} command: it answers "where is my stuff and how
 * do I get to it?" for a fallen player's tracked corpse.
 *
 * <p>Full-loot-on-death drops a dying player's gold and unequipped items into a lootable corpse that
 * decays after a configured window. This service turns the transiently-tracked {@link Corpse} into
 * player-facing guidance: it confirms when the corpse is underfoot, and otherwise names the room it
 * lies in, how much gold it holds, how long remains before it decays, and — reusing the exact same
 * ferry-aware routing {@code WAYFIND} uses via {@link WayfindService#describeRoute} — turn-by-turn
 * directions back to it (or a friendly "no known route" when the room is unreachable on foot).
 *
 * <p>A corpse whose decay window has already elapsed (but which has not yet been swept by the decay
 * ticker) is treated as gone, so a decayed corpse reports identically to no corpse at all. Every
 * lookup is an in-memory read with no mutation, safe to run on the tick thread (AGENTS.md §5).
 */
public class CorpseLocatorService {

    private final WayfindService wayfindService;
    private final Function<RoomId, String> roomNames;
    private final IntSupplier corpseDecaySeconds;
    private final Supplier<Instant> clock;

    /**
     * Creates a corpse-locating service.
     *
     * @param wayfindService     the shared ferry-aware routing service used to render directions to
     *                           the corpse's room
     * @param roomNames          a function returning a room's display name, used to name the room the
     *                           corpse lies in
     * @param corpseDecaySeconds supplies the configured corpse decay window, in seconds, used to
     *                           compute the remaining time and to discard already-decayed corpses
     * @param clock              supplies the current instant, used to measure how long a corpse has
     *                           existed (injectable for deterministic tests)
     */
    public CorpseLocatorService(
            WayfindService wayfindService,
            Function<RoomId, String> roomNames,
            IntSupplier corpseDecaySeconds,
            Supplier<Instant> clock) {
        this.wayfindService = Objects.requireNonNull(wayfindService, "Wayfind service is required");
        this.roomNames = Objects.requireNonNull(roomNames, "Room name resolver is required");
        this.corpseDecaySeconds = Objects.requireNonNull(corpseDecaySeconds, "Corpse decay seconds supplier is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * Produces the player-facing lines for a {@code CORPSE} invocation.
     *
     * <p>Returns a single "no corpse" line when the player has no tracked corpse or their most recent
     * one has already outlived its decay window. When the corpse is in {@code currentRoom} it confirms
     * they are standing on it (no directions). Otherwise it names the room, reports carried gold and
     * remaining time, and appends ferry-aware turn-by-turn directions (or a "no known route" line).
     *
     * @param currentRoom the room the player is standing in
     * @param corpse      the player's tracked corpse, or {@code null} when none is tracked
     * @return one or more lines to send to the player (never empty, never {@code null})
     */
    public List<String> locate(RoomId currentRoom, @Nullable Corpse corpse) {
        Objects.requireNonNull(currentRoom, "Current room is required");
        if (corpse == null) {
            return List.of("You have no corpse in the world.");
        }
        long remainingSeconds = remainingSeconds(corpse);
        if (remainingSeconds <= 0) {
            return List.of("You have no corpse in the world.");
        }
        if (corpse.roomId().equals(currentRoom)) {
            return List.of("Your corpse lies here" + goldClause(corpse.gold())
                + ". Loot it before it decays!");
        }
        String roomName = roomNames.apply(corpse.roomId());
        String summary = "Your corpse lies in " + roomName + goldClause(corpse.gold())
            + ". It will decay in " + friendlyRemaining(remainingSeconds) + ".";
        String directions = wayfindService.describeRoute(roomName, currentRoom, corpse.roomId())
            .orElse("No known route to " + roomName + ".");
        return List.of(summary, directions);
    }

    private long remainingSeconds(Corpse corpse) {
        long elapsed = Duration.between(corpse.spawnedAt(), clock.get()).toSeconds();
        return corpseDecaySeconds.getAsInt() - elapsed;
    }

    /**
     * Renders the trailing gold clause of a corpse summary: {@code ", holding 214 gold"} when the
     * corpse carries any, or an empty string otherwise (so a goldless corpse reads cleanly).
     */
    private static String goldClause(int gold) {
        return gold > 0 ? ", holding " + gold + " gold" : "";
    }

    /**
     * Rounds a remaining-seconds count to a friendly, human-readable duration: "less than a minute",
     * "about a minute", or "about N minutes".
     */
    private static String friendlyRemaining(long seconds) {
        if (seconds < 60) {
            return "less than a minute";
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes <= 1) {
            return "about a minute";
        }
        return "about " + minutes + " minutes";
    }
}
