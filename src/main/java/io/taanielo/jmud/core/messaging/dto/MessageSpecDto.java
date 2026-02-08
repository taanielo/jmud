package io.taanielo.jmud.core.messaging.dto;

public record MessageSpecDto(
    String phase,
    String channel,
    String text
) {
}
