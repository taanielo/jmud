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
 * Domain service that broadcasts the server-wide announcements of rare-encounter lifecycles: a
 * <em>world boss</em> (see {@link MobTemplate#worldBoss()}) awakening on spawn/respawn and falling
 * when a player lands the killing blow, and a timed <em>world event</em> (see
 * {@link MobTemplate#worldEvent()}) whose rare-elite mob is torn into the world by the
 * {@link WorldEventScheduler} and, if left unkilled, fades away when its window closes.
 *
 * <p>A world-event mob that is also flagged {@link MobTemplate#worldBoss()} reuses
 * {@link #announceDeath} for its kill announcement, so only its spawn ({@link #announceEventSpawn})
 * and unkilled timeout ({@link #announceEventTimeout}) need event-specific flavour here.
 *
 * <p>All announcements go out through the sanctioned {@link MessageBroadcaster} global fan-out
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
     * Broadcasts the opening of a timed world event to every online player: a rare-elite mob has
     * been torn into the named room. The room name falls back to the room id when it has no
     * resolvable name.
     *
     * @param mobName the world-event mob's display name
     * @param roomId  the room the mob has spawned into
     */
    public void announceEventSpawn(String mobName, RoomId roomId) {
        Objects.requireNonNull(mobName, "mobName is required");
        Objects.requireNonNull(roomId, "roomId is required");
        String roomName = roomService.findRoom(roomId).map(Room::getName).orElse(roomId.getValue());
        broadcaster.broadcastGlobal(new PlainTextMessage(
            "A crack of unnatural energy tears open in the " + roomName + " — "
                + mobName + " has emerged!"), Set.of());
    }

    /**
     * Broadcasts the close of an unkilled world event to every online player: nobody slew the
     * rare-elite mob within its window, so the rift collapses and the mob fades away with no kill
     * credit. The room name falls back to the room id when it has no resolvable name.
     *
     * @param mobName the world-event mob's display name
     * @param roomId  the room the mob occupied
     */
    public void announceEventTimeout(String mobName, RoomId roomId) {
        Objects.requireNonNull(mobName, "mobName is required");
        Objects.requireNonNull(roomId, "roomId is required");
        String roomName = roomService.findRoom(roomId).map(Room::getName).orElse(roomId.getValue());
        broadcaster.broadcastGlobal(new PlainTextMessage(
            "The rift over the " + roomName + " collapses — " + mobName + " fades away."), Set.of());
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
