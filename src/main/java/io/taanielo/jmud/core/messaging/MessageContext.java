package io.taanielo.jmud.core.messaging;

import io.taanielo.jmud.core.authentication.Username;

public record MessageContext(
    Username sourceUser,
    Username targetUser,
    String sourceName,
    String targetName,
    String itemName,
    String effectName,
    String abilityName,
    Integer damage
) {
}
