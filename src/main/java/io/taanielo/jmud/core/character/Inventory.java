package io.taanielo.jmud.core.character;

import java.util.List;

public interface Inventory {
    Inventory add(String itemId);
    Inventory remove(String itemId);
    List<String> items();
    int size();
    boolean contains(String itemId);
}
