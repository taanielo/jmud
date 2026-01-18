package io.taanielo.jmud.core.ability;

import java.util.Locale;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

public class RoomAbilityTargetResolver implements AbilityTargetResolver {
    private final RoomService roomService;
    private final PlayerRepository playerRepository;

    public RoomAbilityTargetResolver(RoomService roomService, PlayerRepository playerRepository) {
        this.roomService = roomService;
        this.playerRepository = playerRepository;
    }

    @Override
    public Optional<Player> resolve(Player source, String targetInput) {
        if (source == null || targetInput == null || targetInput.isBlank()) {
            return Optional.empty();
        }
        RoomService.LookResult look = roomService.look(source.getUsername());
        Room room = look.room();
        if (room == null) {
            return Optional.empty();
        }
        String normalized = targetInput.trim().toLowerCase(Locale.ROOT);
        for (Username occupant : room.getOccupants()) {
            String name = occupant.getValue();
            if (name.equalsIgnoreCase(source.getUsername().getValue())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                    return playerRepository.loadPlayer(occupant);
                }
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                return playerRepository.loadPlayer(occupant);
            }
        }
        return Optional.empty();
    }
}
