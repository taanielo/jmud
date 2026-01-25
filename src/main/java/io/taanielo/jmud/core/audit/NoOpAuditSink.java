package io.taanielo.jmud.core.audit;

public class NoOpAuditSink implements AuditSink {
    @Override
    public void write(AuditEntry entry) {
    }
}
