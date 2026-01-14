package io.taanielo.jmud.command.system;

import io.taanielo.jmud.command.Command;
import io.taanielo.jmud.command.CommandInput;
import io.taanielo.jmud.core.server.Client;

public class QuitCommand implements Command<QuitCommand.QuitInput> {

    @Override
    public QuitInput act() {
        return Client::close;
    }

    public interface QuitInput extends CommandInput<QuitInput> {
        void input(Client client);
    }
}