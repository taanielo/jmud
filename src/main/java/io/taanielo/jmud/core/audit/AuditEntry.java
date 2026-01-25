package io.taanielo.jmud.core.audit;

import java.util.Map;

public record AuditEntry(
    int schemaVersion,
    long timestampMs,
    String eventType,
    String correlationId,
    long tick,
    AuditSubject actor,
    AuditSubject target,
    String roomId,
    String result,
    Map<String, Object> metadata
) {
}
