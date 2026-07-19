package io.taanielo.jmud.core.mentor;

/**
 * Thrown when the Mentors' Guild rank ladder definition cannot be loaded or is invalid.
 */
public class MentorRankException extends Exception {

    public MentorRankException(String message) {
        super(message);
    }

    public MentorRankException(String message, Throwable cause) {
        super(message, cause);
    }
}
