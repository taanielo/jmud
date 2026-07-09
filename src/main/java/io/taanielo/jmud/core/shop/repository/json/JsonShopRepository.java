package io.taanielo.jmud.core.shop.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopId;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.ShopRepositoryException;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Loads {@link Shop} definitions from {@code data/shops/shop.*.json} files.
 *
 * <p>All shops are eagerly loaded and cached at construction time.
 */
@Slf4j
public class JsonShopRepository implements ShopRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final double DEFAULT_SELL_RATIO = 0.5;
    private static final String SHOPS_DIR = "shops";

    private final ObjectMapper objectMapper;
    private final Path shopsDirPath;
    private List<Shop> cache;

    public JsonShopRepository() throws ShopRepositoryException {
        this(Path.of("data"));
    }

    public JsonShopRepository(Path dataRoot) throws ShopRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.shopsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(SHOPS_DIR);
        ensureDirectory(shopsDirPath);
    }

    @Override
    public List<Shop> findAll() throws ShopRepositoryException {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    @Override
    public Optional<Shop> findByRoomId(RoomId roomId) throws ShopRepositoryException {
        Objects.requireNonNull(roomId, "roomId is required");
        return findAll().stream()
            .filter(s -> s.roomId().equals(roomId))
            .findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<Shop> load() throws ShopRepositoryException {
        List<Shop> shops = new ArrayList<>();
        try (var stream = Files.list(shopsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                ShopDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new ShopRepositoryException(
                        "Unsupported shop schema version " + dto.schemaVersion() + " in " + path);
                }
                shops.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new ShopRepositoryException("Failed to list shop data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} shop(s) from {}", shops.size(), shopsDirPath);
        return List.copyOf(shops);
    }

    private Shop toDomain(ShopDto dto, Path source) throws ShopRepositoryException {
        try {
            List<StockEntry> stock = dto.stock() == null ? List.of() : dto.stock().stream()
                .map(e -> new StockEntry(ItemId.of(e.itemId()), e.price()))
                .toList();
            double sellRatio = dto.sellRatio() != null ? dto.sellRatio() : DEFAULT_SELL_RATIO;
            FactionId factionId = dto.factionId() != null ? FactionId.of(dto.factionId()) : null;
            return new Shop(
                ShopId.of(dto.id()),
                dto.name(),
                RoomId.of(dto.roomId()),
                stock,
                sellRatio,
                factionId
            );
        } catch (IllegalArgumentException e) {
            throw new ShopRepositoryException("Invalid shop data in " + source + ": " + e.getMessage(), e);
        }
    }

    private ShopDto readDto(Path path) throws ShopRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), ShopDto.class);
        } catch (IOException e) {
            throw new ShopRepositoryException(
                "Failed to read shop data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws ShopRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new ShopRepositoryException("Failed to create shops directory " + path, e);
        }
    }
}
