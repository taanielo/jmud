package io.taanielo.jmud.core.effects;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EffectEngine {
    private final EffectRepository repository;

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
            sendApplyMessages(definition, sink);
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
                sendExpireMessages(definition, sink);
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

    private void sendApplyMessages(EffectDefinition definition, EffectMessageSink sink) {
        EffectMessages messages = definition.messages();
        if (messages == null) {
            return;
        }
        if (messages.applySelf() != null) {
            sink.sendToTarget(messages.applySelf());
        }
        if (messages.applyRoom() != null) {
            sink.sendToRoom(messages.applyRoom());
        }
    }

    private void sendExpireMessages(EffectDefinition definition, EffectMessageSink sink) {
        EffectMessages messages = definition.messages();
        if (messages == null) {
            return;
        }
        if (messages.expireSelf() != null) {
            sink.sendToTarget(messages.expireSelf());
        }
        if (messages.expireRoom() != null) {
            sink.sendToRoom(messages.expireRoom());
        }
    }
}
