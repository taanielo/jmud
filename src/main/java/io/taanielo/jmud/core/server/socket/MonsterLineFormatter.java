package io.taanielo.jmud.core.server.socket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.mob.MobInstance;

/**
 * Formats the {@code Monsters:} summary line shown after {@code look}/{@code move}, shared by
 * the socket command context and the respawn tick callback.
 */
final class MonsterLineFormatter {

    private MonsterLineFormatter() {
    }

    /**
     * Formats a {@code Monsters:} summary line for the given live mob instances.
     *
     * <p>Mobs of the same template name are grouped and counted; e.g.
     * {@code "Monsters: 2x Goblin, 1x Troll"}. Returns {@code "Monsters: none"}
     * when the list is empty.
     *
     * @param mobs live mob instances currently in the room
     * @return formatted Monsters line
     */
    static String format(List<MobInstance> mobs) {
        if (mobs == null || mobs.isEmpty()) {
            return "Monsters: none";
        }
        // Group by display name (a tamed companion's custom name when set, else its template name),
        // preserving encounter order for determinism.
        LinkedHashMap<String, Long> counts = mobs.stream()
            .collect(Collectors.groupingBy(
                MobInstance::displayName,
                LinkedHashMap::new,
                Collectors.counting()
            ));
        String body = counts.entrySet().stream()
            .map(e -> e.getValue() + "x " + e.getKey())
            .collect(Collectors.joining(", "));
        return "Monsters: " + body;
    }
}
