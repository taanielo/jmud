package io.taanielo.jmud.core.auction;

import java.util.Locale;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Predicate describing which active {@link AuctionListing}s an {@code AUCTION LIST} view should
 * include. A filter never affects the numbering of listings — {@link AuctionService} assigns each
 * surviving row its number from the full server-wide active-listing order before filtering, so
 * {@code AUCTION BUY <#>} / {@code AUCTION CANCEL <#>} keep resolving against the same index.
 */
public sealed interface AuctionFilter permits AuctionFilter.All, AuctionFilter.Mine, AuctionFilter.Keyword {

    /**
     * Returns whether the given listing should appear in the filtered view.
     *
     * @param listing the listing to test
     * @return {@code true} to include the listing
     */
    boolean matches(AuctionListing listing);

    /**
     * Returns a filter that includes every active listing.
     *
     * @return the identity filter
     */
    static AuctionFilter all() {
        return All.INSTANCE;
    }

    /**
     * Returns a filter that includes only listings owned by the given seller.
     *
     * @param seller the seller whose listings should be shown
     * @return a "mine" filter
     */
    static AuctionFilter mine(Username seller) {
        return new Mine(seller);
    }

    /**
     * Returns a filter that includes only listings whose item display name contains the given
     * keyword (case-insensitive substring match).
     *
     * @param keyword the keyword to match against item names
     * @return a keyword filter
     */
    static AuctionFilter keyword(String keyword) {
        return new Keyword(keyword);
    }

    /** Filter that includes every active listing. */
    record All() implements AuctionFilter {
        private static final All INSTANCE = new All();

        @Override
        public boolean matches(AuctionListing listing) {
            return true;
        }
    }

    /** Filter that includes only a single seller's own listings. */
    record Mine(Username seller) implements AuctionFilter {
        public Mine {
            Objects.requireNonNull(seller, "seller is required");
        }

        @Override
        public boolean matches(AuctionListing listing) {
            return listing.seller().equals(seller);
        }
    }

    /** Filter that matches listings whose item name contains a keyword, case-insensitively. */
    record Keyword(String keyword) implements AuctionFilter {
        public Keyword {
            Objects.requireNonNull(keyword, "keyword is required");
            keyword = keyword.trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean matches(AuctionListing listing) {
            if (keyword.isEmpty()) {
                return true;
            }
            return listing.item().getName().toLowerCase(Locale.ROOT).contains(keyword);
        }
    }
}
