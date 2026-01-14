package io.taanielo.jmud.core.command;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientContext;

public class SayCommand implements Command {

    private final ClientContext context;

    public SayCommand(ClientContext context) {
        this.context = context;
    }

    @Override
    public void execute(Client client, String... args) {
        if (args.length > 0) {
            String text = String.join(" ", args);
            Message say = UserSayMessage.of(text, context.getPlayer().getUsername());
            context.getMessageBroadcaster().broadcast(client, say);
        }
    }
}
