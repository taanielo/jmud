package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.EquipmentResistanceResolver;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Tick-driven source of standing environmental-hazard damage (issue #759).
 *
 * <p>Every tick the engine runs on the tick thread (AGENTS.md §5) and, for each currently occupied
 * room that declares a {@link Room#getHazard() hazard}, deals typed damage to every player
 * physically present in the room. The raw damage is a {@link CombatRandom} roll in the hazard's
 * {@code [damageMin, damageMax]} range, then mitigated by the victim's equipped elemental-resistance
 * gear through the very same {@link EquipmentResistanceResolver} and
 * {@link CombatSettings#maxResistancePercent()} cap that reduces a mob's matching typed attack — so a
 * fire-resist cloak that shrugs off a fire mob's breath also shrugs off a lava passage's fumes. A
 * resisted blow, exactly as in combat, still deals at least one point of damage.
 *
 * <p>The HP change is not applied here directly: it is routed back through the caller-supplied
 * {@code damageSink}, which funnels it through the standard player-update path so death →
 * corpse → respawn and the channeled-cast interrupt (#693) all fire with no special-case
 * (AGENTS.md §3.3). This engine therefore stays free of any transport/session dependency and is unit
 * testable without networking (AGENTS.md §10).
 *
 * <p>Occupied rooms and their occupants are visited in a deterministic order (by room id, then by
 * username) so the damage-roll RNG stream is reproducible alongside combat and weather rolls.
 */
public class HazardDamageEngine implements Tickable {

    private final RoomRepository roomRepository;
    private final PlayerLocationService playerLocationService;
    private final EquipmentResistanceResolver resistanceResolver;
    private final CombatRandom random;
    private final MessageBroadcaster messageBroadcaster;
    private final Function<Username, Optional<Player>> playerLookup;
    private final BiConsumer<Username, Player> damageSink;

    /**
     * Creates a hazard damage engine.
     *
     * @param roomRepository        repository used to resolve a room's hazard definition
     * @param playerLocationService source of which rooms are occupied and who is in each room
     * @param resistanceResolver    resolves a victim's equipped elemental resistance (shared with combat)
     * @param random                the RNG port used to roll damage (no bare {@code Random}; AGENTS.md §5)
     * @param messageBroadcaster    the sanctioned fan-out used to deliver the hazard bite message
     * @param playerLookup          resolves the live {@link Player} for a username, or empty if absent
     * @param damageSink            applies the damaged player through the standard player-update path
     */
    public HazardDamageEngine(
        RoomRepository roomRepository,
        PlayerLocationService playerLocationService,
        EquipmentResistanceResolver resistanceResolver,
        CombatRandom random,
        MessageBroadcaster messageBroadcaster,
        Function<Username, Optional<Player>> playerLookup,
        BiConsumer<Username, Player> damageSink
    ) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.playerLocationService =
            Objects.requireNonNull(playerLocationService, "Player location service is required");
        this.resistanceResolver = Objects.requireNonNull(resistanceResolver, "Resistance resolver is required");
        this.random = Objects.requireNonNull(random, "Combat random is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        this.playerLookup = Objects.requireNonNull(playerLookup, "Player lookup is required");
        this.damageSink = Objects.requireNonNull(damageSink, "Damage sink is required");
    }

    /**
     * Deals one round of hazard damage to every player standing in a hazardous room. Must only be
     * called from the tick thread.
     */
    @Override
    public void tick() {
        List<RoomId> occupied = new ArrayList<>(playerLocationService.occupiedRooms());
        occupied.sort((a, b) -> a.getValue().compareTo(b.getValue()));
        for (RoomId roomId : occupied) {
            Room room = loadRoom(roomId);
            if (room == null || !room.hasHazard()) {
                continue;
            }
            RoomHazard hazard = room.getHazard();
            List<Username> victims = new ArrayList<>(playerLocationService.getPlayersInRoom(roomId));
            victims.sort((a, b) -> a.getValue().compareTo(b.getValue()));
            for (Username victim : victims) {
                applyHazard(victim, hazard);
            }
        }
    }

    private void applyHazard(Username victim, RoomHazard hazard) {
        Optional<Player> maybePlayer = playerLookup.apply(victim);
        if (maybePlayer.isEmpty()) {
            return;
        }
        Player player = maybePlayer.get();
        if (player.isDead()) {
            return;
        }
        int raw = random.roll(hazard.damageMin(), hazard.damageMax());
        int resistPercent = clamp(
            resistanceResolver.totalResistance(player, hazard.damageType()),
            0, CombatSettings.maxResistancePercent());
        int mitigated = resistPercent > 0
            ? (int) Math.round(raw * ((100 - resistPercent) / 100.0))
            : raw;
        // A hazard always stings for at least 1, mirroring combat's landed-hit floor so resistance can
        // never grant full immunity (CombatEngine, CombatSettings.maxResistancePercent()).
        int damage = Math.max(1, mitigated);
        messageBroadcaster.sendToPlayer(victim, new PlainTextMessage(hazard.damageMessage()));
        Player damaged = player.withVitals(player.getVitals().damage(damage));
        damageSink.accept(victim, damaged);
    }

    private Room loadRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId).orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
