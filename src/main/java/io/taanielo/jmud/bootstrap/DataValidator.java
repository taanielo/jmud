package io.taanielo.jmud.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.repository.json.JsonDataFileValidator;

/**
 * Validates all game-data JSON files under the {@code data/} tree and the
 * player-save directory.
 *
 * <p>Validation walks each known sub-directory, parses every {@code *.json}
 * file using the same Jackson configuration as the production repositories, and
 * collects the outcome per domain. On success each domain reports a file count;
 * on failure the broken file path and parse error are recorded, and scanning
 * continues so that <em>all</em> broken files are reported in one pass.
 *
 * <p>This class belongs in the composition root (bootstrap) because it
 * orchestrates multiple repository-layer parsers, mirroring the role of
 * {@link GameContext} during normal startup (AGENTS.md §3.3).
 */
public final class DataValidator {

    /**
     * A file that failed JSON parsing: the absolute path and the error detail.
     *
     * @param path  absolute path of the file that failed to parse
     * @param error the parse-error message
     */
    public record FileError(String path, String error) {}

    /**
     * Result for one data domain (e.g. "rooms", "players").
     *
     * @param domain    human-readable domain name
     * @param fileCount number of {@code .json} files scanned in the directory
     * @param errors    list of parse errors (empty when clean)
     */
    public record DomainResult(String domain, int fileCount, List<FileError> errors) {
        /** Returns {@code true} when no parse errors were found in this domain. */
        public boolean clean() {
            return errors.isEmpty();
        }
    }

    /**
     * Full validation report across all domains.
     *
     * @param domains one {@link DomainResult} per scanned directory
     */
    public record ValidationReport(List<DomainResult> domains) {
        /** Returns {@code true} when every domain is clean. */
        public boolean clean() {
            return domains.stream().allMatch(DomainResult::clean);
        }

        /** Total number of {@code .json} files scanned across all domains. */
        public int totalFiles() {
            return domains.stream().mapToInt(DomainResult::fileCount).sum();
        }

        /** Total number of parse errors across all domains. */
        public int totalErrors() {
            return domains.stream().mapToInt(d -> d.errors().size()).sum();
        }
    }

    /**
     * Ordered list of sub-directory names (relative to {@code dataRoot}) that are
     * validated. Directories that do not exist are silently skipped.
     */
    private static final List<String> DATA_DOMAINS = List.of(
        "rooms", "items", "mobs", "attacks", "skills", "classes",
        "races", "shops", "quests", "banks", "users", "characters"
    );

    private final JsonDataFileValidator fileValidator;

    /** Creates a validator using the project-standard JSON parser configuration. */
    public DataValidator() {
        this.fileValidator = new JsonDataFileValidator();
    }

    /**
     * Validates all game-data and player-save files.
     *
     * @param dataRoot    root of the game data tree (typically {@code data/})
     * @param playersRoot root of the player-save directory (typically {@code players/})
     * @return a {@link ValidationReport} that is never {@code null}
     */
    public ValidationReport validate(Path dataRoot, Path playersRoot) {
        List<DomainResult> results = new ArrayList<>();

        for (String domain : DATA_DOMAINS) {
            Path dir = dataRoot.resolve(domain);
            results.add(validateDirectory(domain, dir));
        }

        results.add(validateDirectory("players", playersRoot));

        return new ValidationReport(List.copyOf(results));
    }

    private DomainResult validateDirectory(String domain, Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return new DomainResult(domain, 0, List.of());
        }

        List<FileError> errors = new ArrayList<>();
        List<Path> jsonFiles;

        try (var stream = Files.list(dir)) {
            jsonFiles = stream
                .filter(p -> p.toString().endsWith(".json"))
                .toList();
        } catch (IOException e) {
            errors.add(new FileError(dir.toString(), "Failed to list directory: " + e));
            return new DomainResult(domain, 0, List.copyOf(errors));
        }

        for (Path file : jsonFiles) {
            Optional<String> error = fileValidator.validate(file);
            error.ifPresent(msg -> errors.add(new FileError(file.toAbsolutePath().toString(), msg)));
        }

        return new DomainResult(domain, jsonFiles.size(), List.copyOf(errors));
    }
}
