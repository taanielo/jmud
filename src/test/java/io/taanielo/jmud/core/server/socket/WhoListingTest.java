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
}
