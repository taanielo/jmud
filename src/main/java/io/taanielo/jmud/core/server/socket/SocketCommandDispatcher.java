package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.ThreadContext;

import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;

/**
 * Dispatches socket command input to registered command handlers.
 */
public class SocketCommandDispatcher {
    /** MDC key used to propagate the per-command correlation id into Log4j2's {@link ThreadContext}. */
    public static final String MDC_CORRELATION_ID = "correlationId";

    private final SocketCommandRegistry registry;
    private final AuditService auditService;

    /**
     * Creates a dispatcher that reads commands from the provided registry.
     */
    public SocketCommandDispatcher(SocketCommandRegistry registry, AuditService auditService) {
        this.registry = Objects.requireNonNull(registry, "Command registry is required");
        this.auditService = Objects.requireNonNull(auditService, "Audit service is required");
    }

    /**
     * Returns the correlation id of the command currently being executed on this
     * (tick) thread, or {@code null} when no command dispatch is in progress.
     *
     * <p>Written by {@link #dispatch} into Log4j2's {@link ThreadContext} (MDC) for
     * the duration of a single command execution so that both server log lines and
     * audit events share the same id and can be correlated in post-processing.
     */
    public String currentCorrelationId() {
        return ThreadContext.get(MDC_CORRELATION_ID);
    }

    /**
     * Parses the incoming line and executes the appropriate command.
     *
     * <p>The {@code correlationId} is placed into Log4j2's {@link ThreadContext} under
     * the key {@value #MDC_CORRELATION_ID} before dispatch and removed in a
     * {@code finally} block, so server log lines emitted during execution carry the
     * id and the tick thread is never left with a stale value.
     */
    public void dispatch(SocketCommandContext context, String clientInput, String correlationId) {
        Objects.requireNonNull(context, "Command context is required");
        if (clientInput == null) {
            return;
        }
        ThreadContext.put(MDC_CORRELATION_ID, correlationId);
        try {
            dispatchInternal(context, clientInput, correlationId);
        } finally {
            ThreadContext.remove(MDC_CORRELATION_ID);
        }
    }

    private void dispatchInternal(SocketCommandContext context, String clientInput, String correlationId) {
        String trimmed = clientInput.trim();
        if (trimmed.isEmpty()) {
            context.sendPrompt();
            return;
        }
        AuditSubject actor = resolveActor(context);
        auditService.emit(new AuditEvent(
            "command.received",
            actor,
            null,
            null,
            "received",
            correlationId,
            Map.of("input", trimmed)
        ));
        List<SocketCommandMatch> matches = new ArrayList<>();
        for (SocketCommandHandler command : registry.commands()) {
            command.match(trimmed).ifPresent(matches::add);
        }
        if (matches.isEmpty()) {
            auditService.emit(new AuditEvent(
                "command.unknown",
                actor,
                null,
                null,
                "unknown",
                correlationId,
                Map.of("input", trimmed)
            ));
            context.writeLineWithPrompt("Unknown command");
            return;
        }
        if (matches.size() > 1) {
            String options = matches.stream()
                .map(match -> match.command().name())
                .distinct()
                .collect(Collectors.joining(", "));
            auditService.emit(new AuditEvent(
                "command.ambiguous",
                actor,
                null,
                null,
                "ambiguous",
                correlationId,
                Map.of("input", trimmed, "options", options)
            ));
            context.writeLineWithPrompt("Ambiguous command. Specify: " + options);
            return;
        }
        SocketCommandMatch match = matches.getFirst();
        if (context.getPlayer() != null && context.getPlayer().isDead()) {
            if (!"quit".equalsIgnoreCase(match.command().name())) {
                auditService.emit(new AuditEvent(
                    "command.blocked",
                    actor,
                    null,
                    null,
                    "dead",
                    correlationId,
                    Map.of("command", match.command().name())
                ));
                context.writeLineWithPrompt("You cannot act while dead.");
                return;
            }
        }
        auditService.emit(new AuditEvent(
            "command.execute",
            actor,
            null,
            null,
            "executing",
            correlationId,
            Map.of("command", match.command().name())
        ));
        match.execute(context);
    }

    private AuditSubject resolveActor(SocketCommandContext context) {
        if (context.getPlayer() == null) {
            return null;
        }
        return AuditSubject.player(context.getPlayer().getUsername());
    }
}
