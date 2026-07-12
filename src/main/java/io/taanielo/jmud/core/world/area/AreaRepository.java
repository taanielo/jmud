package io.taanielo.jmud.core.world.area;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Data-access contract for world {@link Area} definitions and the {@link WorldAtlas}.
 */
public interface AreaRepository {

    /**
     * Returns every area definition.
     *
     * @return all areas (never {@code null})
     * @throws RepositoryException when the area data cannot be read
     */
    List<Area> findAll() throws RepositoryException;

    /**
     * Returns the area with the given id, or empty when none matches.
     *
     * @param id the area id to look up
     * @return the matching area, or {@link Optional#empty()}
     * @throws RepositoryException when the area data cannot be read
     */
    Optional<Area> findById(AreaId id) throws RepositoryException;

    /**
     * Returns the world atlas overview document, or empty when it is not present.
     *
     * @return the atlas, or {@link Optional#empty()}
     * @throws RepositoryException when the atlas data cannot be read
     */
    Optional<WorldAtlas> findAtlas() throws RepositoryException;
}
