package io.taanielo.jmud.core.salvage;

import java.util.List;

/**
 * Data-access contract for {@link SalvageTier} definitions.
 */
public interface SalvageTierRepository {

    /**
     * Returns all salvage tier definitions (one per rarity tier that can be salvaged).
     *
     * @return every known salvage tier; never {@code null}
     * @throws SalvageTierRepositoryException when data cannot be read
     */
    List<SalvageTier> findAll() throws SalvageTierRepositoryException;
}
