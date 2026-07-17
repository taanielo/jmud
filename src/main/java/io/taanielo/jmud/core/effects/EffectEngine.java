package io.taanielo.jmud.core.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;

public class EffectEngine {
    private final EffectRepository repository;
    private final MessageRenderer renderer = new MessageRenderer();

    public EffectEngine(EffectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Effect repository is required");
    }

    public boolean apply(EffectTarget target, EffectId id, EffectMessageSink sink) throws EffectRepositoryException {
        Objects.requireNonNull(target, "Effect target is required");
        Objects.requireNonNull(id, "Effect id is required");
        Objects.requireNonNull(sink, "Effect message sink is required");

        EffectDefinition definition = definitionOrThrow(id);
        EffectInstance existing = findInstance(target.effects(), id);
        boolean applied = false;
        if (existing == null) {
            EffectInstance instance = EffectInstance.of(id, definition.durationTicks());
            target.addEffect(instance);
            applied = true;
        } else {
            applied = applyStacking(existing, definition);
        }
        if (applied) {
            sendMessages(target, definition, MessagePhase.APPLY, sink);
        }
        return applied;
    }

    /**
     * Removes an active effect from the target on demand, before it would naturally
     * expire (e.g. a cure ability or potion). Sends the effect's {@code expire}
     * messages when an instance is actually removed. Tick-thread only (AGENTS.md §5).
     *
     * @param target the effect target to cure
     * @param id the id of the effect to remove
     * @param sink message sink for expire messages
     * @return {@code true} if a matching active effect was found and removed,
     *     {@code false} if the target had no such effect active
     */
    public boolean remove(EffectTarget target, EffectId id, EffectMessageSink sink) throws EffectRepositoryException {
        Objects.requireNonNull(target, "Effect target is required");
        Objects.requireNonNull(id, "Effect id is required");
        Objects.requireNonNull(sink, "Effect message sink is required");

        EffectInstance existing = findInstance(target.effects(), id);
        if (existing == null) {
            return false;
        }
        EffectDefinition definition = definitionOrThrow(id);
        target.removeEffect(existing);
        sendMessages(target, definition, MessagePhase.EXPIRE, sink);
        return true;
    }

    public void tick(EffectTarget target, EffectMessageSink sink) throws EffectRepositoryException {
        Objects.requireNonNull(target, "Effect target is required");
        Objects.requireNonNull(sink, "Effect message sink is required");
        for (EffectInstance instance : target.effects()) {
            EffectDefinition definition = definitionOrThrow(instance.id());
            if (definition.isPermanent()) {
                continue;
            }
            instance.tickDown();
            if (instance.remainingTicks() <= 0) {
                target.removeEffect(instance);
                sendMessages(target, definition, MessagePhase.EXPIRE, sink);
            }
        }
    }

    /**
     * Renders the {@code examine}-phase message of every effect currently active on the target,
     * in effect order, for display to a bystander looking at that player (the {@code LOOK <player>}
     * command). Each rendered line has {@code {name}} substituted with the target's display name.
     * Effects that define no {@code examine} message contribute no line.
     *
     * <p>This is a pure read: it never mutates effect durations or stacks, so it is safe to call on
     * demand from the tick thread (AGENTS.md §5).
     *
     * @param target the effect target whose visible effects to describe
     * @return the rendered examine lines, in effect order; empty when no active effect has one
     * @throws EffectRepositoryException if an active effect id cannot be resolved
     */
    public List<String> examineLines(EffectTarget target) throws EffectRepositoryException {
        Objects.requireNonNull(target, "Effect target is required");
        List<String> lines = new ArrayList<>();
        for (EffectInstance instance : target.effects()) {
            EffectDefinition definition = definitionOrThrow(instance.id());
            MessageContext context = new MessageContext(
                target.username(),
                target.username(),
                target.displayName(),
                target.displayName(),
                null,
                definition.name(),
                null,
                null
            );
            for (MessageSpec spec : definition.messages()) {
                if (spec.phase() != MessagePhase.EXAMINE) {
                    continue;
                }
                String rendered = renderer.render(spec, context);
                if (rendered == null || rendered.isBlank()) {
                    continue;
                }
                lines.add(rendered);
            }
        }
        return lines;
    }

    private EffectDefinition definitionOrThrow(EffectId id) throws EffectRepositoryException {
        Optional<EffectDefinition> definition = repository.findById(id);
        if (definition.isEmpty()) {
            throw new EffectRepositoryException("Unknown effect id " + id.getValue());
        }
        return definition.get();
    }

    private EffectInstance findInstance(List<EffectInstance> effects, EffectId id) {
        for (EffectInstance instance : effects) {
            if (instance.id().equals(id)) {
                return instance;
            }
        }
        return null;
    }

    private boolean applyStacking(EffectInstance instance, EffectDefinition definition) {
        switch (definition.stacking()) {
            case REFRESH -> instance.refresh(definition.durationTicks());
            case STACK -> instance.stack(definition.durationTicks());
            case IGNORE -> {
                return false;
            }
            case REPLACE -> instance.replace(definition.durationTicks());
        }
        return true;
    }

    private void sendMessages(
        EffectTarget target,
        EffectDefinition definition,
        MessagePhase phase,
        EffectMessageSink sink
    ) {
        List<MessageSpec> messages = definition.messages();
        if (messages.isEmpty()) {
            return;
        }
        MessageContext context = new MessageContext(
            target.username(),
            target.username(),
            target.displayName(),
            target.displayName(),
            null,
            definition.name(),
            null,
            null
        );
        for (MessageSpec spec : messages) {
            if (spec.phase() != phase) {
                continue;
            }
            String rendered = renderer.render(spec, context);
            if (rendered == null || rendered.isBlank()) {
                continue;
            }
            if (spec.channel() == MessageChannel.ROOM) {
                sink.sendToRoom(rendered);
            } else {
                sink.sendToTarget(rendered);
            }
        }
    }
}
