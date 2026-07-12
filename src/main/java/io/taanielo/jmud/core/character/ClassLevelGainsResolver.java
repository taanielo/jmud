package io.taanielo.jmud.core.character;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the per-level {@link LevelGains} for a character's class from a snapshot of class
 * definitions captured once at construction time.
 *
 * <p>The lookup is a plain in-memory map, so resolving gains on the tick thread (during a
 * level-up) never touches the class repository or disk (AGENTS.md §5). Build one resolver at
 * bootstrap from the already-loaded, cached class definitions and inject it into
 * {@link io.taanielo.jmud.core.player.LevelUpService}.
 *
 * <p>Unknown class ids and a {@code null} class id both resolve to {@link LevelGains#DEFAULT},
 * preserving the legacy flat gains for characters without a recognised class.
 */
public final class ClassLevelGainsResolver {

    private final Map<ClassId, LevelGains> gainsByClass;

    private ClassLevelGainsResolver(Map<ClassId, LevelGains> gainsByClass) {
        this.gainsByClass = gainsByClass;
    }

    /**
     * Builds a resolver from a snapshot of class definitions.
     *
     * @param definitions the class definitions to index; must not be null
     * @return a resolver backed by an immutable snapshot of the given definitions
     */
    public static ClassLevelGainsResolver fromDefinitions(Iterable<ClassDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions must not be null");
        Map<ClassId, LevelGains> map = new HashMap<>();
        for (ClassDefinition definition : definitions) {
            map.put(definition.id(), definition.levelGains());
        }
        return new ClassLevelGainsResolver(Map.copyOf(map));
    }

    /**
     * Returns a resolver that always yields {@link LevelGains#DEFAULT}. Intended for legacy
     * constructors and test contexts where class data is unavailable.
     *
     * @return a resolver that ignores class identity and returns the default flat gains
     */
    public static ClassLevelGainsResolver defaultGains() {
        return new ClassLevelGainsResolver(Map.of());
    }

    /**
     * Returns the per-level gains for the given class id.
     *
     * @param classId the class id to resolve, or {@code null} for an unclassed character
     * @return the class's configured {@link LevelGains}, or {@link LevelGains#DEFAULT} when the
     *         class id is {@code null} or unknown
     */
    public LevelGains gainsFor(ClassId classId) {
        if (classId == null) {
            return LevelGains.DEFAULT;
        }
        return gainsByClass.getOrDefault(classId, LevelGains.DEFAULT);
    }
}
