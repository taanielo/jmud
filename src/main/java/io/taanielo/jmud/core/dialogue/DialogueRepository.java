package io.taanielo.jmud.core.dialogue;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository for NPC dialogue trees. Implementations live in the infrastructure layer and
 * load trees from data files at start-up.
 */
public interface DialogueRepository {

    /**
     * Returns all known dialogue trees.
     *
     * @return every loaded dialogue tree (never null)
     * @throws DialogueRepositoryException when the underlying data cannot be read
     */
    List<DialogueTree> findAll() throws DialogueRepositoryException;

    /**
     * Returns the dialogue tree with the given id, if present.
     *
     * @param id the dialogue tree id
     * @return the matching tree, or empty when none exists
     * @throws DialogueRepositoryException when the underlying data cannot be read
     */
    Optional<DialogueTree> findById(DialogueId id) throws DialogueRepositoryException;
}
