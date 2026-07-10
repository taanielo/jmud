package io.taanielo.jmud.core.gathering;

import java.util.List;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Read-only repository of static {@link ResourceNode} definitions.
 *
 * <p>Implementations load node content from data files once at startup; the composition root wires
 * the result into {@link ResourceGatheringService} (AGENTS.md §3.3), so no I/O reaches the tick loop.
 */
public interface ResourceNodeRepository {

    /**
     * Returns every configured resource node.
     *
     * @return an immutable list of node definitions, possibly empty
     * @throws RepositoryException if the underlying data cannot be read
     */
    List<ResourceNode> findAll() throws RepositoryException;
}
