package io.taanielo.jmud.core.dialogue.repository.json;

import org.jspecify.annotations.Nullable;

record DialogueResponseDto(
    @Nullable String text,
    @Nullable String target,
    @Nullable String grantQuestId
) {
}
