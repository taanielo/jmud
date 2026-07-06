package io.taanielo.jmud.core.messaging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded, thread-safe, in-memory ring buffer of the most recent {@code GOSSIP} lines
 * broadcast on the server.
 *
 * <p>This is ephemeral chat history only: it is never persisted to {@code data/} and is
 * lost on server restart (see the GOSSIP history feature request). It holds at most
 * {@value #MAX_ENTRIES} entries, evicting the oldest line first once full, and is safe to
 * call from any thread — recording happens alongside the existing synchronous
 * {@link MessageBroadcaster#broadcastGlobal} call, and rendering happens on a
 * newly-connected player's own reader thread during login (AGENTS.md §5). The narrow
 * {@code synchronized} scope here guards only this buffer, not any game state.
 */
public class GossipHistory {

    private static final int MAX_ENTRIES = 10;

    private final Deque<String> lines = new ArrayDeque<>(MAX_ENTRIES);

    /**
     * Records a formatted gossip line, evicting the oldest entry if the buffer is already
     * at capacity. Blank or {@code null} lines are ignored.
     *
     * @param line the fully formatted line as broadcast to other players
     *             (e.g. {@code "Alice gossips: hello"})
     */
    public synchronized void record(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (lines.size() >= MAX_ENTRIES) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    /**
     * Returns the recorded gossip lines, oldest first.
     *
     * @return an immutable snapshot of the current history; empty when no gossip has been
     *         recorded yet
     */
    public synchronized List<String> recentLines() {
        return List.copyOf(lines);
    }

    /**
     * Renders the history for display to a player right after login: a {@code "Recent
     * gossip:"} header followed by each recorded line, oldest first. Returns an empty list
     * (nothing to display) when no gossip has been recorded yet.
     *
     * @return the lines to write to the connecting player, or an empty list when history is
     *         empty
     */
    public List<String> renderForLogin() {
        List<String> history = recentLines();
        if (history.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>(history.size() + 1);
        rendered.add("Recent gossip:");
        rendered.addAll(history);
        return List.copyOf(rendered);
    }
}
