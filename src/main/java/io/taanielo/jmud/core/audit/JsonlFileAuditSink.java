package io.taanielo.jmud.core.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonlFileAuditSink implements AuditSink {
    private final Path basePath;
    private final Clock clock;
    private final ObjectMapper mapper;
    private LocalDate currentDate;
    private BufferedWriter writer;

    public JsonlFileAuditSink(Path basePath, Clock clock) {
        this.basePath = Objects.requireNonNull(basePath, "Base path is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public synchronized void write(AuditEntry entry) {
        Objects.requireNonNull(entry, "Audit entry is required");
        LocalDate now = LocalDate.now(clock);
        try {
            ensureWriter(now);
            String line = mapper.writeValueAsString(entry);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.warn("Failed to write audit entry", e);
        }
    }

    private void ensureWriter(LocalDate date) throws IOException {
        if (writer != null && date.equals(currentDate)) {
            return;
        }
        closeWriter();
        currentDate = date;
        Path resolved = resolvePathForDate(date);
        Path parent = resolved.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(
            resolved,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private Path resolvePathForDate(LocalDate date) {
        String fileName = basePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String prefix = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String suffix = dot >= 0 ? fileName.substring(dot) : "";
        String dated = prefix + "-" + date + suffix;
        Path parent = basePath.getParent();
        return parent == null ? Path.of(dated) : parent.resolve(dated);
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            log.warn("Failed to close audit log writer", e);
        } finally {
            writer = null;
        }
    }
}
