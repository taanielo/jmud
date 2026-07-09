package io.taanielo.jmud.core.dialogue.repository.json;

import java.util.Map;

import org.jspecify.annotations.Nullable;

record DialogueTreeDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String npcId,
    @Nullable String startNode,
    @Nullable Map<String, DialogueNodeDto> nodes
) {
}
