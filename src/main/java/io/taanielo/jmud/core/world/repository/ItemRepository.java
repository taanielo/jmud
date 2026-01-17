package io.taanielo.jmud.core.world.repository;

import java.util.Optional;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

public interface ItemRepository {
    void save(Item item) throws RepositoryException;
    Optional<Item> findById(ItemId id) throws RepositoryException;
}
