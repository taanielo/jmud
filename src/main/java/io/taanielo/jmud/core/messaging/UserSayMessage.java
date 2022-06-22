package io.taanielo.jmud.core.messaging;

import java.io.IOException;

import lombok.Value;

import io.taanielo.jmud.core.authentication.Username;

@Value(staticConstructor = "of")
public class UserSayMessage implements Message {

    String value;
    Username username;

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine(username.getValue() + " said \"" + value + "\"");
    }
}
