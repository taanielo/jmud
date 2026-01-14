package io.taanielo.jmud.core.server;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ClientContext {
    private final User user;
    private final MessageBroadcaster messageBroadcaster;
}
