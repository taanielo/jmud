package io.taanielo.jmud.core.player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the custom command aliases a player has defined, e.g. via {@code ALIAS k kill}.
 *
 * <p>Alias names are case-insensitive and stored normalised to lower case. The insertion
 * order is preserved so listing a player's aliases is stable and predictable.
 */
public class PlayerAliases {
    private final Map<String, String> expansions;

    public PlayerAliases(Map<String, String> expansions) {
        Map<String, String> normalised = new LinkedHashMap<>();
        if (expansions != null) {
            for (Map.Entry<String, String> entry : expansions.entrySet()) {
                normalised.put(normalize(entry.getKey()), entry.getValue());
            }
        }
        this.expansions = Map.copyOf(normalised);
    }

    /**
     * Returns an empty {@link PlayerAliases} instance.
     */
    public static PlayerAliases empty() {
        return new PlayerAliases(Map.of());
    }

    /**
     * Returns the alias name -> expansion map, in the order aliases were defined.
     */
    public Map<String, String> expansions() {
        return expansions;
    }

    /**
     * Returns the expansion for the given alias name, or {@code null} if no alias with
     * that name is defined.
     *
     * @param name the alias name to look up; case-insensitive
     */
    public String expansionOf(String name) {
        Objects.requireNonNull(name, "name is required");
        return expansions.get(normalize(name));
    }

    /**
     * Returns {@code true} when an alias with the given name is defined.
     *
     * @param name the alias name to check; case-insensitive
     */
    public boolean has(String name) {
        Objects.requireNonNull(name, "name is required");
        return expansions.containsKey(normalize(name));
    }

    /**
     * Returns a copy of this component with the given alias defined or overwritten.
     *
     * @param name       the alias name; case-insensitive, must not be blank
     * @param expansion  the command line the alias expands to; must not be blank
     */
    public PlayerAliases define(String name, String expansion) {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(expansion, "expansion is required");
        Map<String, String> next = new LinkedHashMap<>(expansions);
        next.put(normalize(name), expansion);
        return new PlayerAliases(next);
    }

    /**
     * Returns a copy of this component with the given alias removed, or this instance
     * unchanged if no such alias exists.
     *
     * @param name the alias name to remove; case-insensitive
     */
    public PlayerAliases remove(String name) {
        Objects.requireNonNull(name, "name is required");
        String key = normalize(name);
        if (!expansions.containsKey(key)) {
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(expansions);
        next.remove(key);
        return new PlayerAliases(next);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
