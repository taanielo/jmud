package io.taanielo.jmud.core.server;

import java.util.List;

public interface ClientPool {

    void add(Client client);

    void remove(Client client);

    int getNextId();

    List<Client> clients();
}
