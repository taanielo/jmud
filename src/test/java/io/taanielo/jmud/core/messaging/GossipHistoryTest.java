package io.taanielo.jmud.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GossipHistory}: recording, bounded eviction, and rendering to a
 * newly logged-in player without any networking (AGENTS.md §10).
 */
class GossipHistoryTest {

    @Test
    void startsEmpty() {
        GossipHistory history = new GossipHistory();

        assertTrue(history.recentLines().isEmpty());
    }

    @Test
    void recordsLinesInOrder() {
        GossipHistory history = new GossipHistory();

        history.record("Alice gossips: hello");
        history.record("Bob gossips: hi there");

        assertEquals(
            List.of("Alice gossips: hello", "Bob gossips: hi there"),
            history.recentLines()
        );
    }

    @Test
    void evictsOldestEntryBeyondTenLines() {
        GossipHistory history = new GossipHistory();

        for (int i = 1; i <= 12; i++) {
            history.record("Player gossips: line " + i);
        }

        List<String> lines = history.recentLines();
        assertEquals(10, lines.size());
        // Lines 1 and 2 should have been evicted; oldest remaining is line 3.
        assertEquals("Player gossips: line 3", lines.get(0));
        assertEquals("Player gossips: line 12", lines.get(9));
    }

    @Test
    void ignoresBlankAndNullLines() {
        GossipHistory history = new GossipHistory();

        history.record(null);
        history.record("");
        history.record("   ");
        history.record("Alice gossips: hello");

        assertEquals(List.of("Alice gossips: hello"), history.recentLines());
    }

    @Test
    void recentLinesReturnsImmutableSnapshot() {
        GossipHistory history = new GossipHistory();
        history.record("Alice gossips: hello");

        List<String> snapshot = history.recentLines();
        history.record("Bob gossips: hi");

        assertEquals(1, snapshot.size(), "Previously returned snapshot must not change");
        assertEquals(2, history.recentLines().size());
    }

    @Test
    void renderForLoginReturnsEmptyWhenNoGossipRecorded() {
        GossipHistory history = new GossipHistory();

        assertTrue(history.renderForLogin().isEmpty());
    }

    @Test
    void renderForLoginShowsHeaderThenLinesOldestFirst() {
        GossipHistory history = new GossipHistory();
        history.record("Alice gossips: hello");
        history.record("Bob gossips: hi there");

        assertEquals(
            List.of("Recent gossip:", "Alice gossips: hello", "Bob gossips: hi there"),
            history.renderForLogin()
        );
    }
}
