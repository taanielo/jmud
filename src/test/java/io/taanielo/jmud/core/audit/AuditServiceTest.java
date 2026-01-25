package io.taanielo.jmud.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

class AuditServiceTest {

    @Test
    void buildsEntriesWithSchemaTimestampTickAndCorrelation() {
        RecordingAuditSink sink = new RecordingAuditSink();
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuditService auditService = new AuditService(sink, clock, () -> 42L, () -> "corr-1");

        AuditEvent event = new AuditEvent(
            "player.login",
            new AuditSubject("player", "ana"),
            null,
            "training-yard",
            "success",
            null,
            Map.of("newPlayer", true)
        );

        auditService.emit(event);

        assertEquals(1, sink.entries.size());
        AuditEntry entry = sink.entries.getFirst();
        assertEquals(AuditService.SCHEMA_VERSION, entry.schemaVersion());
        assertEquals(clock.millis(), entry.timestampMs());
        assertEquals(42L, entry.tick());
        assertEquals("corr-1", entry.correlationId());
        assertEquals("player.login", entry.eventType());
        assertEquals("training-yard", entry.roomId());
    }

    private static class RecordingAuditSink implements AuditSink {
        private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void write(AuditEntry entry) {
            entries.add(entry);
        }
    }
}
