package io.taanielo.jmud.core.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

public final class MessageSpecMapper {
    private MessageSpecMapper() {
    }

    public static List<MessageSpec> fromDtos(List<MessageSpecDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<MessageSpec> specs = new ArrayList<>();
        for (MessageSpecDto dto : dtos) {
            if (dto == null) {
                continue;
            }
            MessagePhase phase = MessagePhase.fromString(dto.phase());
            MessageChannel channel = MessageChannel.fromString(dto.channel());
            specs.add(new MessageSpec(phase, channel, dto.text()));
        }
        return List.copyOf(specs);
    }

    public static List<MessageSpecDto> toDtos(List<MessageSpec> specs) {
        Objects.requireNonNull(specs, "Message specs are required");
        if (specs.isEmpty()) {
            return List.of();
        }
        List<MessageSpecDto> dtos = new ArrayList<>();
        for (MessageSpec spec : specs) {
            dtos.add(new MessageSpecDto(
                spec.phase().name().toLowerCase(),
                spec.channel().name().toLowerCase(),
                spec.text()
            ));
        }
        return List.copyOf(dtos);
    }
}
