package io.taanielo.jmud.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;

public class MessageEmitter {
    private final MessageRenderer renderer = new MessageRenderer();

    public List<GameMessage> emit(List<MessageSpec> specs, MessagePhase phase, MessageContext context) {
        Objects.requireNonNull(specs, "Message specs are required");
        Objects.requireNonNull(phase, "Message phase is required");
        Objects.requireNonNull(context, "Message context is required");
        if (specs.isEmpty()) {
            return List.of();
        }
        List<GameMessage> messages = new ArrayList<>();
        for (MessageSpec spec : specs) {
            if (spec.phase() != phase) {
                continue;
            }
            String text = renderer.render(spec, context);
            if (text == null || text.isBlank()) {
                continue;
            }
            MessageChannel channel = spec.channel();
            switch (channel) {
                case SELF -> {
                    if (context.sourceUser() != null) {
                        messages.add(GameMessage.toSource(text));
                    }
                }
                case TARGET -> {
                    if (context.targetUser() != null) {
                        messages.add(GameMessage.toPlayer(context.targetUser(), text));
                    }
                }
                case ROOM -> messages.add(GameMessage.toRoom(context.sourceUser(), context.targetUser(), text));
            }
        }
        return List.copyOf(messages);
    }
}
