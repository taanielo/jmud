package io.taanielo.jmud.core.mob;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildService;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Domain service that broadcasts the two server-wide announcements of a world-boss encounter
 * (see {@link MobTemplate#worldBoss()}): the boss awakening on spawn/respawn, and its fall when a
 * player lands the killing blow.
 *
 * <p>Both announcements go out through the sanctioned {@link MessageBroadcaster} global fan-out
 * (AGENTS.md §3.3), mirroring the tick-driven broadcast pattern used by the arena and daily-quest
 * tickers. The death announcement enriches the killer's name with their guild (preferred) or party
 * affiliation when they belong to one, resolved from the injected {@link GuildService}/
 * {@link PartyService}. All calls run on the tick thread via {@link MobRegistry} (AGENTS.md §5); the
 * service holds no mutable state of its own.
 */
public class WorldBossAnnouncer {

    private final MessageBroadcaster broadcaster;
    private final RoomService roomService;
    @Nullable
    private final GuildService guildService;
    @Nullable
    private final PartyService partyService;

    /**
     * Creates a world-boss announcer.
     *
     * @param broadcaster  the sanctioned global message fan-out; must not be null
     * @param roomService  resolves a boss's current room to its display name for spawn announcements
     * @param guildService the guild service used to name the killer's guild; may be null to disable
     * @param partyService the party service used to detect the killer's party; may be null to disable
     */
    public WorldBossAnnouncer(
        MessageBroadcaster broadcaster,
        RoomService roomService,
        @Nullable GuildService guildService,
        @Nullable PartyService partyService
    ) {
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster is required");
        this.roomService = Objects.requireNonNull(roomService, "roomService is required");
        this.guildService = guildService;
        this.partyService = partyService;
    }

    /**
     * Broadcasts the awakening of a world boss to every online player, naming the boss and the room
     * it has spawned into (falling back to the room id when the room has no resolvable name).
     *
     * @param bossName the world boss's display name
     * @param roomId   the room the boss instance currently occupies
     */
    public void announceSpawn(String bossName, RoomId roomId) {
        Objects.requireNonNull(bossName, "bossName is required");
        Objects.requireNonNull(roomId, "roomId is required");
        String roomName = roomService.findRoom(roomId).map(Room::getName).orElse(roomId.getValue());
        broadcaster.broadcastGlobal(new PlainTextMessage(
            "The earth trembles — " + bossName + " has awoken in the " + roomName + "!"), Set.of());
    }

    /**
     * Broadcasts the fall of a world boss to every online player, naming the killer and — when they
     * belong to one — their guild (preferred) or party.
     *
     * @param bossName the slain world boss's display name
     * @param killer   the player who landed the killing blow
     */
    public void announceDeath(String bossName, Username killer) {
        Objects.requireNonNull(bossName, "bossName is required");
        Objects.requireNonNull(killer, "killer is required");
        broadcaster.broadcastGlobal(new PlainTextMessage(
            bossName + " has fallen to " + killer.getValue() + affiliation(killer) + "!"), Set.of());
    }

    /**
     * Builds the killer's affiliation suffix: {@code " of the <Guild>"} when the killer is in a
     * guild, otherwise {@code " and their party"} when they are grouped, otherwise an empty string.
     *
     * @param killer the killing player
     * @return the affiliation suffix, never null
     */
    private String affiliation(Username killer) {
        if (guildService != null) {
            String guildName = guildService.guildOf(killer).map(Guild::name).orElse(null);
            if (guildName != null) {
                return " of the " + guildName;
            }
        }
        if (partyService != null && partyService.findParty(killer).isPresent()) {
            return " and their party";
        }
        return "";
    }
}
