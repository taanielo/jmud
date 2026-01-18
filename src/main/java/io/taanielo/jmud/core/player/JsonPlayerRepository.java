package io.taanielo.jmud.core.player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;

@Slf4j
public class JsonPlayerRepository implements PlayerRepository {

    private static final String PLAYERS_DIR = "players";
    private final ObjectMapper objectMapper;
    private final Path playersDirPath;

    public JsonPlayerRepository() {
        this(Path.of("."));
    }

    public JsonPlayerRepository(Path dataRoot) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.playersDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(PLAYERS_DIR);
        if (!Files.exists(playersDirPath)) {
            try {
                Files.createDirectories(playersDirPath);
            } catch (IOException e) {
                log.error("Failed to create players directory: {}", playersDirPath, e);
                throw new RuntimeException("Failed to create players directory", e);
            }
        }
    }

    @Override
    public void savePlayer(Player player) {
        Path playerFilePath = getPlayerFilePath(player.getUsername());
        Path tempFilePath = playerFilePath.getParent().resolve(playerFilePath.getFileName() + ".tmp");

        try {
            // Write to a temporary file first for atomic write
            objectMapper.writeValue(tempFilePath.toFile(), player);
            // Replace the original file with the temporary file
            Files.move(tempFilePath, playerFilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Player {} saved successfully to {}", player.getUsername(), playerFilePath);
        } catch (IOException e) {
            log.error("Failed to save player {}", player.getUsername(), e);
        }
    }

    @Override
    public Optional<Player> loadPlayer(Username username) {
        Path playerFilePath = getPlayerFilePath(username);
        if (!Files.exists(playerFilePath)) {
            log.debug("Player file not found for {}", username);
            return Optional.empty();
        }

        try {
            Player player = objectMapper.readValue(playerFilePath.toFile(), Player.class);
            log.info("Player {} loaded successfully from {}", username, playerFilePath);
            return Optional.of(player);
        } catch (IOException e) {
            log.error("Failed to load player {}", username, e);
            return Optional.empty();
        }
    }

    private Path getPlayerFilePath(Username username) {
        return playersDirPath.resolve(username.getValue() + ".json");
    }
}
