package io.taanielo.jmud.core.auction.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.auction.AuctionHouse;
import io.taanielo.jmud.core.auction.AuctionHouseRepository;
import io.taanielo.jmud.core.auction.AuctionRepositoryException;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Loads {@link AuctionHouse} definitions from {@code data/auctions/auction-house.*.json} files,
 * mirroring {@link io.taanielo.jmud.core.bank.repository.json.JsonBankRepository}.
 *
 * <p>Definitions are eagerly loaded and cached at construction time.
 */
@Slf4j
public class JsonAuctionHouseRepository implements AuctionHouseRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String AUCTIONS_DIR = "auctions";
    private static final String HOUSE_FILE_PREFIX = "auction-house.";

    private final ObjectMapper objectMapper;
    private final Path auctionsDirPath;
    private @Nullable List<AuctionHouse> cache;

    public JsonAuctionHouseRepository() throws AuctionRepositoryException {
        this(Path.of("data"));
    }

    public JsonAuctionHouseRepository(Path dataRoot) throws AuctionRepositoryException {
        this.objectMapper = JsonAuctionDataMapper.create();
        this.auctionsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(AUCTIONS_DIR);
        ensureDirectory(auctionsDirPath);
    }

    @Override
    public List<AuctionHouse> findAll() throws AuctionRepositoryException {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    @Override
    public Optional<AuctionHouse> findByRoomId(RoomId roomId) throws AuctionRepositoryException {
        Objects.requireNonNull(roomId, "roomId is required");
        return findAll().stream()
            .filter(h -> h.roomId().equals(roomId))
            .findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<AuctionHouse> load() throws AuctionRepositoryException {
        List<AuctionHouse> houses = new ArrayList<>();
        try (var stream = Files.list(auctionsDirPath)) {
            for (Path path : stream
                    .filter(p -> p.getFileName().toString().startsWith(HOUSE_FILE_PREFIX))
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList()) {
                AuctionHouseDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new AuctionRepositoryException(
                        "Unsupported auction house schema version " + dto.schemaVersion() + " in " + path);
                }
                houses.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new AuctionRepositoryException("Failed to list auction house data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} auction house(s) from {}", houses.size(), auctionsDirPath);
        return List.copyOf(houses);
    }

    private AuctionHouse toDomain(AuctionHouseDto dto, Path source) throws AuctionRepositoryException {
        try {
            return new AuctionHouse(dto.id(), dto.auctioneer(), RoomId.of(dto.roomId()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuctionRepositoryException(
                "Invalid auction house data in " + source + ": " + e.getMessage(), e);
        }
    }

    private AuctionHouseDto readDto(Path path) throws AuctionRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), AuctionHouseDto.class);
        } catch (IOException e) {
            throw new AuctionRepositoryException(
                "Failed to read auction house data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws AuctionRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new AuctionRepositoryException("Failed to create auctions directory " + path, e);
        }
    }
}
