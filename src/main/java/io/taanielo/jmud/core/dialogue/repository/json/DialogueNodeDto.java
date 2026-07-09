package io.taanielo.jmud.core.dialogue.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

record DialogueNodeDto(
    @Nullable String text,
    @Nullable List<DialogueResponseDto> responses
) {
}
