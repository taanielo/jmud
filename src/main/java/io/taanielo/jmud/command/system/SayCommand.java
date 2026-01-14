package io.taanielo.jmud.command.system;

import java.util.List;

import io.taanielo.jmud.command.AbstractCommand;
import io.taanielo.jmud.command.Command;
import io.taanielo.jmud.command.CommandInput;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.server.Client;

public class SayCommand extends AbstractCommand implements Command<SayCommand.SayData> {

    public interface SayData extends CommandInput<SayData> {
        void message(Username username, String message, List<Client> targets);
    }

    public SayCommand() {
        super(SayCommand.SayData.class);
    }

    @Override
    public SayData act() {
        return (username, message, targets) -> targets.stream()
                .forEach(target -> target.sendMessage(UserSayMessage.of(message, username)));
    }
}