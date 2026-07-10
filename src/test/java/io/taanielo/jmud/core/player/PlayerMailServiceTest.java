package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link PlayerMailService}.
 */
class PlayerMailServiceTest {

    private final PlayerMailService service = new PlayerMailService();

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    @Test
    void sendDeliversMessageToRecipientMailbox() {
        Player recipient = player("bob");
        MailResult result = service.send(recipient, "alice", 100, "Welcome to the game!");
        assertTrue(result.success());
        assertEquals("You leave a message for bob.", result.message());
        assertEquals(1, result.updatedPlayer().mailbox().messages().size());
        PlayerMailMessage message = result.updatedPlayer().mailbox().messages().get(0);
        assertEquals("alice", message.sender());
        assertEquals("Welcome to the game!", message.body());
        assertFalse(message.read());
    }

    @Test
    void sendRejectsBlankBody() {
        Player recipient = player("bob");
        MailResult result = service.send(recipient, "alice", 100, "   ");
        assertFalse(result.success());
    }

    @Test
    void sendFailsWhenMailboxIsFull() {
        List<PlayerMailMessage> full = new ArrayList<>();
        for (int i = 0; i < PlayerMailbox.CAPACITY; i++) {
            full.add(new PlayerMailMessage("someone", i, "msg " + i, false));
        }
        Player recipient = player("bob").withMailbox(new PlayerMailbox(full));

        MailResult result = service.send(recipient, "alice", 100, "one more");

        assertFalse(result.success());
        assertTrue(result.message().contains("full"));
    }

    @Test
    void listFailsWhenMailboxIsEmpty() {
        MailResult result = service.list(player("bob"), 100);
        assertFalse(result.success());
        assertEquals("You have no mail.", result.message());
    }

    @Test
    void listReturnsFormattedLinesAndMarksAllRead() {
        Player recipient = player("bob");
        MailResult sent = service.send(recipient, "alice", 0, "Hello there, welcome to the realm of adventure!");
        Player withMail = sent.updatedPlayer();

        MailResult result = service.list(withMail, 3600);

        assertTrue(result.success());
        assertEquals(1, result.lines().size());
        assertTrue(result.lines().get(0).contains("alice"));
        assertEquals(0, result.updatedPlayer().mailbox().unreadCount());
    }

    @Test
    void readShowsFullMessageAndMarksItRead() {
        Player recipient = player("bob");
        Player withMail = service.send(recipient, "alice", 0, "Full message body").updatedPlayer();

        MailResult result = service.read(withMail, 1);

        assertTrue(result.success());
        assertEquals(2, result.lines().size());
        assertTrue(result.lines().get(0).contains("alice"));
        assertEquals("Full message body", result.lines().get(1));
        assertTrue(result.updatedPlayer().mailbox().messages().get(0).read());
    }

    @Test
    void readFailsForInvalidIndex() {
        Player recipient = player("bob");
        MailResult result = service.read(recipient, 1);
        assertFalse(result.success());
    }

    @Test
    void deleteRemovesMessage() {
        Player recipient = player("bob");
        Player withMail = service.send(recipient, "alice", 0, "delete me").updatedPlayer();

        MailResult result = service.delete(withMail, 1);

        assertTrue(result.success());
        assertTrue(result.updatedPlayer().mailbox().isEmpty());
    }

    @Test
    void deleteFailsForInvalidIndex() {
        Player recipient = player("bob");
        MailResult result = service.delete(recipient, 5);
        assertFalse(result.success());
    }

    @Test
    void sendGoldDeductsFromSenderAndAttachesToRecipient() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");

        MailResult result = service.sendGold(sender, recipient, 100, "Here, take this.", 50);

        assertTrue(result.success());
        assertTrue(result.message().contains("50 gold"));
        assertEquals(50, result.updatedSender().getGold());
        PlayerMailMessage message = result.updatedPlayer().mailbox().messages().get(0);
        assertEquals("alice", message.sender());
        assertEquals(50, message.attachedGold());
        assertTrue(message.hasAttachment());
    }

    @Test
    void sendGoldRejectsNonPositiveAmount() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");

        MailResult result = service.sendGold(sender, recipient, 100, "nope", 0);

        assertFalse(result.success());
    }

    @Test
    void sendGoldFailsWhenSenderCannotAfford() {
        Player sender = player("alice").addGold(10);
        Player recipient = player("bob");

        MailResult result = service.sendGold(sender, recipient, 100, "too much", 50);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("gold"));
        assertEquals(null, result.updatedSender());
        assertEquals(null, result.updatedPlayer());
    }

    @Test
    void sendGoldFailsWhenMailboxFullWithoutDeducting() {
        List<PlayerMailMessage> full = new ArrayList<>();
        for (int i = 0; i < PlayerMailbox.CAPACITY; i++) {
            full.add(new PlayerMailMessage("someone", i, "msg " + i, false));
        }
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob").withMailbox(new PlayerMailbox(full));

        MailResult result = service.sendGold(sender, recipient, 100, "one more", 50);

        assertFalse(result.success());
        assertTrue(result.message().contains("full"));
        assertEquals(null, result.updatedSender());
    }

    @Test
    void sendGoldRejectsBlankBody() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");

        MailResult result = service.sendGold(sender, recipient, 100, "  ", 50);

        assertFalse(result.success());
    }

    @Test
    void readCreditsAttachedGoldAndClearsAttachment() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        MailResult result = service.read(withMail, 1);

        assertTrue(result.success());
        assertEquals(50, result.updatedPlayer().getGold());
        assertFalse(result.updatedPlayer().mailbox().messages().get(0).hasAttachment());
        assertTrue(result.lines().stream().anyMatch(l -> l.contains("50 gold")));
    }

    @Test
    void readIsIdempotentForGoldCredit() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        Player afterFirstRead = service.read(withMail, 1).updatedPlayer();
        MailResult secondRead = service.read(afterFirstRead, 1);

        assertTrue(secondRead.success());
        assertEquals(50, secondRead.updatedPlayer().getGold());
    }

    @Test
    void deleteCreditsUnclaimedGoldBeforeRemoving() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        MailResult result = service.delete(withMail, 1);

        assertTrue(result.success());
        assertTrue(result.updatedPlayer().mailbox().isEmpty());
        assertEquals(50, result.updatedPlayer().getGold());
        assertTrue(result.message().contains("50 gold"));
    }

    @Test
    void deleteAfterReadDoesNotDoubleCreditGold() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        Player afterRead = service.read(withMail, 1).updatedPlayer();
        MailResult result = service.delete(afterRead, 1);

        assertTrue(result.success());
        assertEquals(50, result.updatedPlayer().getGold());
    }

    @Test
    void listMarksEntriesWithGoldAttachment() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        MailResult result = service.list(withMail, 0);

        assertTrue(result.success());
        assertTrue(result.lines().get(0).contains("[50 gold]"));
    }
}
