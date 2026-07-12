package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

class WhoListingTest {

    @Test
    void listsMultiplePlayersWithHeaderAndCount() {
        List<String> lines = WhoListing.format(List.of(
            Username.of("Aragorn"),
            Username.of("Gandalf"),
            Username.of("Legolas")
        ));

        assertEquals(List.of(
            "Players online:",
            "  Aragorn",
            "  Gandalf",
            "  Legolas",
            "3 players online."
        ), lines);
    }

    @Test
    void preservesInputOrder() {
        List<String> lines = WhoListing.format(List.of(
            Username.of("Zoe"),
            Username.of("Alice")
        ));

        assertEquals("  Zoe", lines.get(1));
        assertEquals("  Alice", lines.get(2));
    }

    @Test
    void singlePlayerUsesSingularNoun() {
        List<String> lines = WhoListing.format(List.of(Username.of("Solo")));

        assertEquals(List.of(
            "Players online:",
            "  Solo",
            "1 player online."
        ), lines);
    }

    @Test
    void noPlayersStillRendersHeaderAndZeroCount() {
        List<String> lines = WhoListing.format(List.of());

        assertEquals(List.of(
            "Players online:",
            "0 players online."
        ), lines);
    }

    @Test
    void footerPluralisesByCount() {
        assertEquals("0 players online.", WhoListing.footer(0));
        assertEquals("1 player online.", WhoListing.footer(1));
        assertEquals("5 players online.", WhoListing.footer(5));
    }

    @Test
    void appendsGuildTagWhenPresent() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Alice"), Username.of("Bob")),
            name -> name.getValue().equals("Alice") ? " [Ironclad]" : "");

        assertEquals(List.of(
            "Players online:",
            "  Alice [Ironclad]",
            "  Bob",
            "2 players online."
        ), lines);
    }

    @Test
    void appendsActiveTitleAfterGuildTag() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Sparky"), Username.of("Bob")),
            name -> name.getValue().equals("Sparky") ? " [Ironclad]" : "",
            name -> name.getValue().equals("Sparky") ? " the Centurion" : "");

        assertEquals(List.of(
            "Players online:",
            "  Sparky [Ironclad] the Centurion",
            "  Bob",
            "2 players online."
        ), lines);
    }

    @Test
    void appendsActiveTitleWithoutGuildTag() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Solo")),
            name -> "",
            name -> " the Wanderer");

        assertEquals(List.of(
            "Players online:",
            "  Solo the Wanderer",
            "1 player online."
        ), lines);
    }

    @Test
    void marksFriendsWithStarPrefix() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Alice"), Username.of("Bob")),
            name -> "",
            name -> "",
            name -> name.getValue().equals("Alice"));

        assertEquals(List.of(
            "Players online:",
            "* Alice",
            "  Bob",
            "2 players online."
        ), lines);
    }

    @Test
    void friendPrefixCombinesWithGuildTagAndTitle() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Sparky")),
            name -> " [Ironclad]",
            name -> " the Centurion",
            name -> true);

        assertEquals(List.of(
            "Players online:",
            "* Sparky [Ironclad] the Centurion",
            "1 player online."
        ), lines);
    }

    @Test
    void tagsAwayPlayersWithAfkMarker() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Alice"), Username.of("Bob")),
            name -> "",
            name -> "",
            name -> false,
            name -> name.getValue().equals("Alice"));

        assertEquals(List.of(
            "Players online:",
            "  Alice [AFK]",
            "  Bob",
            "2 players online."
        ), lines);
    }

    @Test
    void afkMarkerCombinesWithFriendPrefixGuildTagAndTitle() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Sparky")),
            name -> " [Ironclad]",
            name -> " the Centurion",
            name -> true,
            name -> true);

        assertEquals(List.of(
            "Players online:",
            "* Sparky [Ironclad] the Centurion [AFK]",
            "1 player online."
        ), lines);
    }

    @Test
    void nonAwayPlayersAreUnaffectedByAfkPredicate() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Alice")),
            name -> "",
            name -> "",
            name -> false,
            name -> false);

        assertEquals(List.of(
            "Players online:",
            "  Alice",
            "1 player online."
        ), lines);
    }

    @Test
    void insertsLevelClassAfterNameAndLfgTagLast() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Sparky"), Username.of("Bob")),
            name -> "",
            name -> "",
            name -> false,
            name -> false,
            name -> name.getValue().equals("Sparky") ? " [12 Warrior]" : " [3 Adventurer]",
            name -> name.getValue().equals("Sparky") ? " [LFG: tank for Catacombs]" : "");

        assertEquals(List.of(
            "Players online:",
            "  Sparky [12 Warrior] [LFG: tank for Catacombs]",
            "  Bob [3 Adventurer]",
            "2 players online."
        ), lines);
    }

    @Test
    void fullyDecoratedLineRendersEveryMarkerInOrder() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Sparky")),
            name -> " [Ironclad]",
            name -> " the Centurion",
            name -> true,
            name -> true,
            name -> " [30 Paladin]",
            name -> " [LFG]");

        assertEquals(List.of(
            "Players online:",
            "* Sparky [30 Paladin] [Ironclad] the Centurion [AFK] [LFG]",
            "1 player online."
        ), lines);
    }

    @Test
    void emptyLevelClassAndLfgResolversRenderBareName() {
        List<String> lines = WhoListing.format(
            List.of(Username.of("Solo")),
            name -> "",
            name -> "",
            name -> false,
            name -> false,
            name -> "",
            name -> "");

        assertEquals(List.of(
            "Players online:",
            "  Solo",
            "1 player online."
        ), lines);
    }
}
