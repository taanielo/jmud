package io.taanielo.jmud.core.messaging;

import java.io.IOException;

import lombok.Value;

@Value(staticConstructor = "of")
public class WelcomeMessage implements Message {

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine();
        messageWriter.writeLine();
        messageWriter.writeLine("Welcome to Xolo MUD!");
        messageWriter.writeLine();
        messageWriter.write("Please enter your name: ");
    }
}
