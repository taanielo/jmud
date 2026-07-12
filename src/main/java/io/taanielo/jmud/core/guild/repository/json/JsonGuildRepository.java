package io.taanielo.jmud.core.guild.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildId;
import io.taanielo.jmud.core.guild.GuildMember;
import io.taanielo.jmud.core.guild.GuildRank;
import io.taanielo.jmud.core.guild.GuildRepository;
import io.taanielo.jmud.core.guild.GuildRepositoryException;
import io.taanielo.jmud.core.guild.VaultedItem;
import io.taanielo.jmud.core.world.dto.ItemMapper;

/**
 * Loads and persists guilds as {@code data/guilds/<guild-id>.json} files.
 *
 * <p>All guilds are eagerly loaded at construction. Writes and deletions are handed to a single
 * dedicated virtual thread (write-behind), so the tick thread that mutates guild state never blocks
 * on disk (AGENTS.md §5). Saves are coalesced per guild: only the latest enqueued snapshot for a
 * given guild is kept, so a burst of edits collapses into at most a couple of actual writes. Each
 * write uses a tmp file plus {@link StandardCopyOption#ATOMIC_MOVE}, mirroring player saves.
 */
@Slf4j
public class JsonGuildRepository implements GuildRepository, AutoCloseable {

    /** Latest schema written. Version 2 adds the additive shared item vault ({@code vaultedItems}). */
    private static final int SCHEMA_VERSION = 2;
    /** Oldest schema still readable; v1 files (pre-vault) load with an empty vault. */
    private static final int MIN_SCHEMA_VERSION = 1;
    private static final String GUILDS_DIR = "guilds";
    private static final long IDLE_POLL_MILLIS = 25;

    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper = new ItemMapper();
    private final Path guildsDirPath;

    /** Latest snapshot pending write for each dirty guild; a {@code null} value means "delete". */
    private final ConcurrentHashMap<GuildId, Optionalish> pending = new ConcurrentHashMap<>();
    /** Guild ids currently queued for the worker to pick up; guards against duplicate queue entries. */
    private final Set<GuildId> queued = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<GuildId> dirtyGuilds = new LinkedBlockingQueue<>();
    private final AtomicReference<@Nullable Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /** Wrapper so the concurrent map can hold a "delete" instruction distinctly from "no entry". */
    private record Optionalish(@Nullable Guild guild) {
    }

    /**
     * Creates a repository rooted at {@code data}, loading existing guilds and starting the writer.
     *
     * @throws GuildRepositoryException if the guilds directory cannot be created
     */
    public JsonGuildRepository() throws GuildRepositoryException {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading existing guilds and starting
     * the write-behind worker thread.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     * @throws GuildRepositoryException if the guilds directory cannot be created
     */
    public JsonGuildRepository(Path dataRoot) throws GuildRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.guildsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(GUILDS_DIR);
        ensureDirectory(guildsDirPath);
        Thread thread = Thread.ofVirtual().name("guild-repository-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    @Override
    public List<Guild> loadAll() throws GuildRepositoryException {
        List<Guild> guilds = new ArrayList<>();
        try (var stream = Files.list(guildsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                guilds.add(readGuild(path));
            }
        } catch (IOException e) {
            throw new GuildRepositoryException("Failed to list guild data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} guild(s) from {}", guilds.size(), guildsDirPath);
        return List.copyOf(guilds);
    }

    @Override
    public void save(Guild guild) {
        Objects.requireNonNull(guild, "guild is required");
        pending.put(guild.id(), new Optionalish(guild));
        enqueue(guild.id());
    }

    @Override
    public void delete(GuildId guildId) {
        Objects.requireNonNull(guildId, "guildId is required");
        pending.put(guildId, new Optionalish(null));
        enqueue(guildId);
    }

    /**
     * Stops the write-behind worker thread. Any pending writes still queued are best-effort flushed
     * before the thread exits.
     */
    @Override
    public void close() {
        running = false;
        @Nullable Thread thread = workerThread.get();
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── private helpers ───────────────────────────────────────────────

    private void enqueue(GuildId guildId) {
        if (queued.add(guildId)) {
            dirtyGuilds.add(guildId);
        }
    }

    private Guild readGuild(Path path) throws GuildRepositoryException {
        GuildDto dto = readDto(path);
        if (dto.schemaVersion() < MIN_SCHEMA_VERSION || dto.schemaVersion() > SCHEMA_VERSION) {
            throw new GuildRepositoryException(
                "Unsupported guild schema version " + dto.schemaVersion() + " in " + path);
        }
        try {
            @Nullable List<GuildDto.GuildMemberDto> declaredMembers = dto.members();
            List<GuildDto.GuildMemberDto> rawMembers =
                declaredMembers == null ? List.of() : declaredMembers;
            List<GuildMember> members = new ArrayList<>();
            for (GuildDto.GuildMemberDto m : rawMembers) {
                members.add(new GuildMember(
                    Username.of(Objects.requireNonNull(m.username(), "Guild member username is required")),
                    parseRank(m.rank()),
                    m.joinOrder()));
            }
            @Nullable List<GuildDto.VaultedItemDto> declaredVault = dto.vaultedItems();
            List<GuildDto.VaultedItemDto> rawVault = declaredVault == null ? List.of() : declaredVault;
            List<VaultedItem> vaultedItems = new ArrayList<>();
            for (GuildDto.VaultedItemDto v : rawVault) {
                vaultedItems.add(new VaultedItem(
                    itemMapper.toDomain(Objects.requireNonNull(v.item(), "Vaulted item is required")),
                    Username.of(Objects.requireNonNull(v.depositor(), "Vaulted item depositor is required"))));
            }
            return new Guild(
                GuildId.of(Objects.requireNonNull(dto.id(), "Guild id is required")),
                Objects.requireNonNull(dto.name(), "Guild name is required"),
                Username.of(Objects.requireNonNull(dto.leaderId(), "Guild leaderId is required")),
                members,
                Math.max(0, dto.treasuryGold()),
                vaultedItems);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new GuildRepositoryException("Invalid guild data in " + path + ": " + e.getMessage(), e);
        }
    }

    private static GuildRank parseRank(@Nullable String rank) {
        if (rank == null) {
            return GuildRank.MEMBER;
        }
        return GuildRank.valueOf(rank.trim().toUpperCase(Locale.ROOT));
    }

    private GuildDto readDto(Path path) throws GuildRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), GuildDto.class);
        } catch (IOException e) {
            throw new GuildRepositoryException("Failed to read guild data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws GuildRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new GuildRepositoryException("Failed to create guilds directory " + path, e);
        }
    }

    private void runWorker() {
        while (running) {
            @Nullable GuildId guildId;
            try {
                guildId = dirtyGuilds.poll(IDLE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
                continue;
            }
            if (guildId == null) {
                continue;
            }
            queued.remove(guildId);
            @Nullable Optionalish snapshot = pending.remove(guildId);
            if (snapshot != null) {
                @Nullable Guild guild = snapshot.guild();
                if (guild == null) {
                    deleteFile(guildId);
                } else {
                    writeGuild(guild);
                }
            }
        }
    }

    private void writeGuild(Guild guild) {
        Path file = guildsDirPath.resolve(fileName(guild.id()));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            List<GuildDto.GuildMemberDto> memberDtos = guild.members().stream()
                .map(m -> new GuildDto.GuildMemberDto(
                    m.username().getValue(), m.rank().name(), m.joinOrder()))
                .toList();
            List<GuildDto.VaultedItemDto> vaultDtos = guild.vaultedItems().stream()
                .map(v -> new GuildDto.VaultedItemDto(
                    itemMapper.toDto(v.item()), v.depositor().getValue()))
                .toList();
            GuildDto dto = new GuildDto(
                SCHEMA_VERSION, guild.id().value(), guild.name(), guild.leaderId().getValue(),
                memberDtos, guild.treasuryGold(), vaultDtos);
            objectMapper.writeValue(tmp.toFile(), dto);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to persist guild {} to {}", guild.id().value(), file, e);
        }
    }

    private void deleteFile(GuildId guildId) {
        Path file = guildsDirPath.resolve(fileName(guildId));
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete guild file {}", file, e);
        }
    }

    /** Derives a filesystem-safe guild file name from the guild id. */
    private static String fileName(GuildId guildId) {
        String sanitized = guildId.value().replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized + ".json";
    }
}
