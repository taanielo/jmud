package io.taanielo.jmud.core.auction.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.auction.AuctionListing;
import io.taanielo.jmud.core.auction.AuctionRepository;
import io.taanielo.jmud.core.auction.AuctionRepositoryException;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.dto.ItemMapper;

/**
 * Persists the dynamic set of active {@link AuctionListing}s to a single
 * {@code data/auctions/listings.json} file with a versioned schema (see
 * {@code docs/schemas/auction.v1.json}).
 *
 * <p>The whole file is read on {@link #findAll()} and rewritten on {@link #save(List)} — a
 * synchronous JSON write matching the rest of the codebase's persistence convention. A missing file
 * is treated as an empty set. The embedded {@link Item} of each listing is (de)serialised through the
 * shared {@link ItemMapper}, so listed items round-trip with full fidelity (rarity, affixes,
 * durability, container contents).
 */
@Slf4j
public class JsonAuctionRepository implements AuctionRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String AUCTIONS_DIR = "auctions";
    private static final String LISTINGS_FILE = "listings.json";

    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper;
    private final Path listingsPath;

    public JsonAuctionRepository() throws AuctionRepositoryException {
        this(Path.of("data"));
    }

    public JsonAuctionRepository(Path dataRoot) throws AuctionRepositoryException {
        this.objectMapper = JsonAuctionDataMapper.create();
        this.itemMapper = new ItemMapper();
        Path auctionsDir = Objects.requireNonNull(dataRoot, "Data root is required").resolve(AUCTIONS_DIR);
        ensureDirectory(auctionsDir);
        this.listingsPath = auctionsDir.resolve(LISTINGS_FILE);
    }

    @Override
    public List<AuctionListing> findAll() throws AuctionRepositoryException {
        if (!Files.exists(listingsPath)) {
            return List.of();
        }
        AuctionListingsFileDto file;
        try {
            file = objectMapper.readValue(listingsPath.toFile(), AuctionListingsFileDto.class);
        } catch (IOException e) {
            throw new AuctionRepositoryException(
                "Failed to read auction listings from " + listingsPath + ": " + e.getMessage(), e);
        }
        if (file.schemaVersion() != SCHEMA_VERSION) {
            throw new AuctionRepositoryException(
                "Unsupported auction listings schema version " + file.schemaVersion() + " in " + listingsPath);
        }
        List<AuctionListingDto> dtos = file.listings() == null ? List.of() : file.listings();
        List<AuctionListing> listings = new ArrayList<>(dtos.size());
        for (AuctionListingDto dto : dtos) {
            listings.add(toDomain(dto));
        }
        return List.copyOf(listings);
    }

    @Override
    public void save(List<AuctionListing> listings) throws AuctionRepositoryException {
        Objects.requireNonNull(listings, "listings is required");
        List<AuctionListingDto> dtos = new ArrayList<>(listings.size());
        for (AuctionListing listing : listings) {
            dtos.add(toDto(listing));
        }
        AuctionListingsFileDto file = new AuctionListingsFileDto(SCHEMA_VERSION, dtos);
        try {
            objectMapper.writeValue(listingsPath.toFile(), file);
        } catch (IOException e) {
            throw new AuctionRepositoryException(
                "Failed to write auction listings to " + listingsPath + ": " + e.getMessage(), e);
        }
    }

    // ── private helpers ───────────────────────────────────────────────

    private AuctionListingDto toDto(AuctionListing listing) {
        return new AuctionListingDto(
            listing.seller().getValue(),
            itemMapper.toDto(listing.item()),
            listing.price(),
            listing.roomId().getValue(),
            listing.createdTick(),
            listing.expiryTick());
    }

    private AuctionListing toDomain(AuctionListingDto dto) throws AuctionRepositoryException {
        try {
            Item item = itemMapper.toDomain(dto.item());
            return new AuctionListing(
                Username.of(dto.seller()),
                item,
                dto.price(),
                RoomId.of(dto.roomId()),
                dto.createdTick(),
                dto.expiryTick());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuctionRepositoryException(
                "Invalid auction listing data in " + listingsPath + ": " + e.getMessage(), e);
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
