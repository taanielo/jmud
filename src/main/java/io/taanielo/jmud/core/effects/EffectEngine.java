package io.taanielo.jmud.core.effects;

import java.util.Iterator;
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
        List<EffectInstance> effects = target.effects();
        EffectInstance existing = findInstance(effects, id);
        boolean applied = false;
        if (existing == null) {
            EffectInstance instance = EffectInstance.of(id, definition.durationTicks());
            effects.add(instance);
            applied = true;
        } else {
            applied = applyStacking(existing, definition);
        }
        if (applied) {
            sendMessages(target, definition, MessagePhase.APPLY, sink);
        }
        return applied;
    }

    public void tick(EffectTarget target, EffectMessageSink sink) throws EffectRepositoryException {
        Objects.requireNonNull(target, "Effect target is required");
        Objects.requireNonNull(sink, "Effect message sink is required");
        List<EffectInstance> effects = target.effects();
        Iterator<EffectInstance> iterator = effects.iterator();
        while (iterator.hasNext()) {
            EffectInstance instance = iterator.next();
            EffectDefinition definition = definitionOrThrow(instance.id());
            if (definition.isPermanent()) {
                continue;
            }
            instance.tickDown();
            if (instance.remainingTicks() <= 0) {
                iterator.remove();
                sendMessages(target, definition, MessagePhase.EXPIRE, sink);
            }
        }
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
