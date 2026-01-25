package io.taanielo.jmud.core.audit;

public interface AuditSink {
    void write(AuditEntry entry);
}
