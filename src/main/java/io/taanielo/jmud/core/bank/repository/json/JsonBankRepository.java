package io.taanielo.jmud.core.bank.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.bank.Bank;
import io.taanielo.jmud.core.bank.BankId;
import io.taanielo.jmud.core.bank.BankRepository;
import io.taanielo.jmud.core.bank.BankRepositoryException;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Loads {@link Bank} definitions from {@code data/banks/bank.*.json} files.
 *
 * <p>All banks are eagerly loaded and cached at construction time.
 */
@Slf4j
public class JsonBankRepository implements BankRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String BANKS_DIR = "banks";

    private final ObjectMapper objectMapper;
    private final Path banksDirPath;
    private List<Bank> cache;

    public JsonBankRepository() throws BankRepositoryException {
        this(Path.of("data"));
    }

    public JsonBankRepository(Path dataRoot) throws BankRepositoryException {
        this.objectMapper = JsonBankDataMapper.create();
        this.banksDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(BANKS_DIR);
        ensureDirectory(banksDirPath);
    }

    @Override
    public List<Bank> findAll() throws BankRepositoryException {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    @Override
    public Optional<Bank> findByRoomId(RoomId roomId) throws BankRepositoryException {
        Objects.requireNonNull(roomId, "roomId is required");
        return findAll().stream()
            .filter(b -> b.roomId().equals(roomId))
            .findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<Bank> load() throws BankRepositoryException {
        List<Bank> banks = new ArrayList<>();
        try (var stream = Files.list(banksDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                BankDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new BankRepositoryException(
                        "Unsupported bank schema version " + dto.schemaVersion() + " in " + path);
                }
                banks.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new BankRepositoryException("Failed to list bank data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} bank(s) from {}", banks.size(), banksDirPath);
        return List.copyOf(banks);
    }

    private Bank toDomain(BankDto dto, Path source) throws BankRepositoryException {
        try {
            return new Bank(
                BankId.of(dto.id()),
                dto.name(),
                RoomId.of(dto.roomId())
            );
        } catch (IllegalArgumentException e) {
            throw new BankRepositoryException("Invalid bank data in " + source + ": " + e.getMessage(), e);
        }
    }

    private BankDto readDto(Path path) throws BankRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), BankDto.class);
        } catch (IOException e) {
            throw new BankRepositoryException(
                "Failed to read bank data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws BankRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new BankRepositoryException("Failed to create banks directory " + path, e);
        }
    }
}
