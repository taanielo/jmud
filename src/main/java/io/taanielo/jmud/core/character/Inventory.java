package io.taanielo.jmud.core.character;

import java.util.List;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

public interface Inventory {
    void add(Item item);
    boolean remove(ItemId itemId);
    List<Item> items();
    int size();
}
