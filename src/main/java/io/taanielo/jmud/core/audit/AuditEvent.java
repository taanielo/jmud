package io.taanielo.jmud.core.audit;

import java.util.Map;

public record AuditEvent(
    String eventType,
    AuditSubject actor,
    AuditSubject target,
    String roomId,
    String result,
    String correlationId,
    Map<String, Object> metadata
) {
}
