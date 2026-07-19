package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PlayerAuctionWatchListTest {

    @Test
    void emptyListHasNoWatches() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty();
        assertTrue(watches.isEmpty());
        assertFalse(watches.isFull());
        assertEquals(0, watches.size());
    }

    @Test
    void withNormalisesKeywordToLowerCaseTrimmed() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty().with("  Flaming Sword  ");
        assertTrue(watches.has("flaming sword"));
        assertTrue(watches.has("FLAMING SWORD"));
        assertEquals(List.of("flaming sword"), List.copyOf(watches.keywords()));
    }

    @Test
    void withPreservesInsertionOrder() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty()
            .with("sword").with("potion").with("shield");
        assertEquals(List.of("sword", "potion", "shield"), List.copyOf(watches.keywords()));
    }

    @Test
    void watchingSameKeywordTwiceIsANoOp() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty().with("sword");
        PlayerAuctionWatchList again = watches.with("SWORD");
        assertSame(watches, again);
        assertEquals(1, again.size());
    }

    @Test
    void listIsCappedAtMaxWatches() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty();
        for (int i = 0; i < PlayerAuctionWatchList.MAX_WATCHES; i++) {
            watches = watches.with("keyword" + i);
        }
        assertTrue(watches.isFull());
        assertEquals(PlayerAuctionWatchList.MAX_WATCHES, watches.size());
        PlayerAuctionWatchList full = watches;
        assertThrows(IllegalStateException.class, () -> full.with("one-too-many"));
    }

    @Test
    void constructorTruncatesSurplusKeywordsDefensively() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < PlayerAuctionWatchList.MAX_WATCHES + 5; i++) {
            tooMany.add("keyword" + i);
        }
        PlayerAuctionWatchList watches = new PlayerAuctionWatchList(tooMany);
        assertEquals(PlayerAuctionWatchList.MAX_WATCHES, watches.size());
    }

    @Test
    void unwatchingKeywordNotBeingWatchedIsANoOp() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty().with("sword");
        PlayerAuctionWatchList after = watches.without("potion");
        assertSame(watches, after);
    }

    @Test
    void withoutRemovesTheKeyword() {
        PlayerAuctionWatchList watches = PlayerAuctionWatchList.empty().with("sword").with("potion");
        PlayerAuctionWatchList after = watches.without("SWORD");
        assertFalse(after.has("sword"));
        assertTrue(after.has("potion"));
    }

    @Test
    void constructorIgnoresNullAndBlankAndDuplicates() {
        List<String> input = new ArrayList<>();
        input.add("sword");
        input.add("  ");
        input.add(null);
        input.add("SWORD");
        PlayerAuctionWatchList watches = new PlayerAuctionWatchList(input);
        assertEquals(1, watches.size());
        assertTrue(watches.has("sword"));
    }
}
