package io.taanielo.jmud.core.server;

import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.player.Player;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ClientContext {
    private final Player player;
    private final MessageBroadcaster messageBroadcaster;
}
