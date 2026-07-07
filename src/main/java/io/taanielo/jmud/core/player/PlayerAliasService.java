package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Domain service implementing the {@code ALIAS} command's business rules: defining,
 * removing, and listing a player's custom command aliases.
 *
 * <p>Kept separate from {@code SocketCommandContextImpl} so the validation rules
 * (self-reference guard, built-in command shadowing warning) are unit-testable
 * without sockets (AGENTS.md §10).
 */
public class PlayerAliasService {

    /**
     * Defines or overwrites an alias for the given player.
     *
     * <p>Rejects an alias whose expansion's first word is the alias name itself (direct
     * self-reference). If the name collides with a built-in command name, the alias is
     * still defined (per-player only, never affecting other players), but the returned
     * message includes a warning.
     *
     * @param player               the player defining the alias
     * @param name                 the alias name; must not be blank
     * @param expansion            the command line the alias expands to; must not be blank
     * @param builtinCommandNames  the names of all registered built-in commands, used only
     *                             to warn on shadowing; case-insensitive comparison
     * @return the result, with {@link AliasResult#updatedPlayer()} set on success
     */
    public AliasResult define(Player player, String name, String expansion, Set<String> builtinCommandNames) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(expansion, "expansion is required");
        Objects.requireNonNull(builtinCommandNames, "builtinCommandNames is required");
        String trimmedName = name.trim();
        String trimmedExpansion = expansion.trim();
        if (trimmedName.isEmpty() || trimmedExpansion.isEmpty()) {
            return AliasResult.failure("Usage: ALIAS <name> <expansion>");
        }
        String firstExpansionWord = trimmedExpansion.split("\\s+", 2)[0];
        if (firstExpansionWord.equalsIgnoreCase(trimmedName)) {
            return AliasResult.failure("An alias cannot expand into itself.");
        }
        boolean shadowsBuiltin = builtinCommandNames.stream().anyMatch(trimmedName::equalsIgnoreCase);
        Player updated = player.defineAlias(trimmedName, trimmedExpansion);
        String lowerName = trimmedName.toLowerCase(Locale.ROOT);
        String message = shadowsBuiltin
            ? "Alias '" + lowerName + "' defined (warning: shadows a built-in command for you only)."
            : "Alias '" + lowerName + "' defined: " + trimmedExpansion;
        return AliasResult.success(message, updated);
    }

    /**
     * Removes an alias from the given player.
     *
     * @param player the player removing the alias
     * @param name   the alias name to remove; must not be blank
     * @return the result, with {@link AliasResult#updatedPlayer()} set on success
     */
    public AliasResult remove(Player player, String name) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(name, "name is required");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            return AliasResult.failure("Usage: ALIAS -d <name>");
        }
        if (!player.aliases().has(trimmedName)) {
            return AliasResult.failure("You have no alias named '" + trimmedName + "'.");
        }
        Player updated = player.removeAlias(trimmedName);
        return AliasResult.success("Alias '" + trimmedName.toLowerCase(Locale.ROOT) + "' removed.", updated);
    }

    /**
     * Lists the given player's aliases, one per line, formatted as {@code name -> expansion}.
     *
     * @param player the player whose aliases to list
     * @return a listing result; {@link AliasResult#lines()} is empty when no aliases are defined
     */
    public AliasResult list(Player player) {
        Objects.requireNonNull(player, "player is required");
        Map<String, String> expansions = player.aliases().expansions();
        if (expansions.isEmpty()) {
            return AliasResult.failure("You have no aliases defined. Usage: ALIAS <name> <expansion>");
        }
        List<String> lines = expansions.entrySet().stream()
            .map(entry -> "  " + entry.getKey() + " -> " + entry.getValue())
            .toList();
        return AliasResult.listing(lines);
    }
}
