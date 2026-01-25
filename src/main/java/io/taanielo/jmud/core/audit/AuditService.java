package io.taanielo.jmud.core.audit;

import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class AuditService {
    public static final int SCHEMA_VERSION = 1;

    private final AuditSink sink;
    private final Clock clock;
    private final LongSupplier tickSupplier;
    private final Supplier<String> correlationSupplier;

    public static AuditService create(LongSupplier tickSupplier) {
        Clock clock = Clock.systemDefaultZone();
        if (!AuditSettings.enabled()) {
            return new AuditService(new NoOpAuditSink(), clock, tickSupplier, AuditService::newCorrelationId);
        }
        AuditSink fileSink = new JsonlFileAuditSink(Path.of(AuditSettings.path()), clock);
        AuditSink asyncSink = new AsyncAuditSink(fileSink, AuditSettings.queueSize());
        return new AuditService(asyncSink, clock, tickSupplier, AuditService::newCorrelationId);
    }

    public AuditService(
        AuditSink sink,
        Clock clock,
        LongSupplier tickSupplier,
        Supplier<String> correlationSupplier
    ) {
        this.sink = Objects.requireNonNull(sink, "Audit sink is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "Tick supplier is required");
        this.correlationSupplier = Objects.requireNonNull(correlationSupplier, "Correlation supplier is required");
    }

    public void emit(AuditEvent event) {
        if (event == null) {
            return;
        }
        long timestamp = clock.millis();
        long tick = tickSupplier.getAsLong();
        String correlationId = normalizeCorrelationId(event.correlationId());
        Map<String, Object> metadata = sanitizeMetadata(event.metadata());
        AuditEntry entry = new AuditEntry(
            SCHEMA_VERSION,
            timestamp,
            event.eventType(),
            correlationId,
            tick,
            event.actor(),
            event.target(),
            event.roomId(),
            event.result(),
            metadata
        );
        sink.write(entry);
    }

    public String newCorrelationId() {
        return correlationSupplier.get();
    }

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return correlationSupplier.get();
        }
        return correlationId;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            cleaned.put(entry.getKey(), entry.getValue());
        }
        return cleaned.isEmpty() ? Map.of() : Map.copyOf(cleaned);
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
