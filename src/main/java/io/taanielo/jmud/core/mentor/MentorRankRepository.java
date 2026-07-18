package io.taanielo.jmud.core.mentor;

/**
 * Loads the Mentors' Guild {@link MentorRankLadder} definition.
 *
 * <p>The ladder is game content stored as versioned JSON under {@code data/}; the concrete
 * implementation lives in the infrastructure layer (AGENTS.md §3.2, §11).
 */
public interface MentorRankRepository {

    /**
     * Loads the configured mentor rank ladder.
     *
     * @return the mentor rank ladder
     * @throws MentorRankException if the ladder data is missing, unreadable, or invalid
     */
    MentorRankLadder load() throws MentorRankException;
}
