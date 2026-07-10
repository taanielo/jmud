package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Unit tests for {@link PlayerMailService}.
 */
class PlayerMailServiceTest {

    private final PlayerMailService service = new PlayerMailService();
    private final EncumbranceService neverOverburdened = encumbrance(false);

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private static Item item(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A test item.", ItemAttributes.empty()).weight(3).build();
    }

    private static EncumbranceService encumbrance(boolean overburdened) {
        RaceRepository races = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of();
            }
        };
        ClassRepository classes = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new EncumbranceService(races, classes) {
            @Override
            public boolean isOverburdened(Player player) {
                return overburdened;
            }
        };
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

        MailResult result = service.read(withMail, 1, neverOverburdened);

        assertTrue(result.success());
        assertEquals(2, result.lines().size());
        assertTrue(result.lines().get(0).contains("alice"));
        assertEquals("Full message body", result.lines().get(1));
        assertTrue(result.updatedPlayer().mailbox().messages().get(0).read());
    }

    @Test
    void readFailsForInvalidIndex() {
        Player recipient = player("bob");
        MailResult result = service.read(recipient, 1, neverOverburdened);
        assertFalse(result.success());
    }

    @Test
    void deleteRemovesMessage() {
        Player recipient = player("bob");
        Player withMail = service.send(recipient, "alice", 0, "delete me").updatedPlayer();

        MailResult result = service.delete(withMail, 1, neverOverburdened);

        assertTrue(result.success());
        assertTrue(result.updatedPlayer().mailbox().isEmpty());
    }

    @Test
    void deleteFailsForInvalidIndex() {
        Player recipient = player("bob");
        MailResult result = service.delete(recipient, 5, neverOverburdened);
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

        MailResult result = service.read(withMail, 1, neverOverburdened);

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

        Player afterFirstRead = service.read(withMail, 1, neverOverburdened).updatedPlayer();
        MailResult secondRead = service.read(afterFirstRead, 1, neverOverburdened);

        assertTrue(secondRead.success());
        assertEquals(50, secondRead.updatedPlayer().getGold());
    }

    @Test
    void deleteCreditsUnclaimedGoldBeforeRemoving() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        MailResult result = service.delete(withMail, 1, neverOverburdened);

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

        Player afterRead = service.read(withMail, 1, neverOverburdened).updatedPlayer();
        MailResult result = service.delete(afterRead, 1, neverOverburdened);

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

    @Test
    void sendItemRemovesItemFromSenderAndAttachesToRecipient() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");

        MailResult result = service.sendItem(sender, recipient, 100, "for you", sword);

        assertTrue(result.success());
        assertTrue(result.message().contains("a runed longsword"));
        assertTrue(result.updatedSender().getInventory().isEmpty());
        PlayerMailMessage message = result.updatedPlayer().mailbox().messages().get(0);
        assertEquals("alice", message.sender());
        assertTrue(message.hasItemAttachment());
        assertEquals("a runed longsword", message.resolveAttachedItem().getName());
    }

    @Test
    void sendItemRejectsBlankBody() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");

        MailResult result = service.sendItem(sender, recipient, 100, "  ", sword);

        assertFalse(result.success());
    }

    @Test
    void sendItemFailsWhenMailboxFullWithoutRemovingItem() {
        List<PlayerMailMessage> full = new ArrayList<>();
        for (int i = 0; i < PlayerMailbox.CAPACITY; i++) {
            full.add(new PlayerMailMessage("someone", i, "msg " + i, false));
        }
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob").withMailbox(new PlayerMailbox(full));

        MailResult result = service.sendItem(sender, recipient, 100, "one more", sword);

        assertFalse(result.success());
        assertTrue(result.message().contains("full"));
        assertEquals(null, result.updatedSender());
    }

    @Test
    void listMarksEntriesWithItemAttachment() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        MailResult result = service.list(withMail, 0);

        assertTrue(result.success());
        assertTrue(result.lines().get(0).contains("[item: a runed longsword]"));
    }

    @Test
    void readCreditsAttachedItemAndClearsAttachment() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        MailResult result = service.read(withMail, 1, neverOverburdened);

        assertTrue(result.success());
        assertEquals(1, result.updatedPlayer().getInventory().size());
        assertEquals("a runed longsword", result.updatedPlayer().getInventory().get(0).getName());
        assertFalse(result.updatedPlayer().mailbox().messages().get(0).hasItemAttachment());
        assertTrue(result.lines().stream().anyMatch(l -> l.contains("a runed longsword")));
    }

    @Test
    void readIsIdempotentForItemCredit() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        Player afterFirstRead = service.read(withMail, 1, neverOverburdened).updatedPlayer();
        MailResult secondRead = service.read(afterFirstRead, 1, neverOverburdened);

        assertTrue(secondRead.success());
        assertEquals(1, secondRead.updatedPlayer().getInventory().size());
    }

    @Test
    void deleteCreditsUnclaimedItemBeforeRemoving() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        MailResult result = service.delete(withMail, 1, neverOverburdened);

        assertTrue(result.success());
        assertTrue(result.updatedPlayer().mailbox().isEmpty());
        assertEquals(1, result.updatedPlayer().getInventory().size());
        assertTrue(result.message().contains("a runed longsword"));
    }

    @Test
    void deleteAfterReadDoesNotDoubleCreditItem() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        Player afterRead = service.read(withMail, 1, neverOverburdened).updatedPlayer();
        MailResult result = service.delete(afterRead, 1, neverOverburdened);

        assertTrue(result.success());
        assertEquals(1, result.updatedPlayer().getInventory().size());
    }

    @Test
    void readLeavesItemAttachedWhenRecipientOverburdened() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        MailResult result = service.read(withMail, 1, encumbrance(true));

        assertTrue(result.success());
        assertTrue(result.updatedPlayer().getInventory().isEmpty());
        assertTrue(result.updatedPlayer().mailbox().messages().get(0).hasItemAttachment());
        assertTrue(result.lines().stream().anyMatch(l -> l.contains("too much")));

        // Retry after freeing space succeeds and claims the item exactly once.
        MailResult retry = service.read(result.updatedPlayer(), 1, neverOverburdened);
        assertTrue(retry.success());
        assertEquals(1, retry.updatedPlayer().getInventory().size());
        assertFalse(retry.updatedPlayer().mailbox().messages().get(0).hasItemAttachment());
    }

    @Test
    void deleteFailsWithoutStateChangeWhenRecipientOverburdened() {
        Item sword = item("sword", "a runed longsword");
        Player sender = player("alice").addItem(sword);
        Player recipient = player("bob");
        Player withMail = service.sendItem(sender, recipient, 0, "for you", sword).updatedPlayer();

        MailResult result = service.delete(withMail, 1, encumbrance(true));

        assertFalse(result.success());
        assertTrue(result.message().contains("too much"));
        // Message is left intact for a later retry.
        assertEquals(1, withMail.mailbox().messages().size());
        assertTrue(withMail.mailbox().messages().get(0).hasItemAttachment());
    }

    @Test
    void goldOnlyClaimIsUnaffectedByOverburdenCheck() {
        Player sender = player("alice").addGold(100);
        Player recipient = player("bob");
        Player withMail = service.sendGold(sender, recipient, 0, "a gift", 50).updatedPlayer();

        MailResult result = service.read(withMail, 1, encumbrance(true));

        assertTrue(result.success());
        assertEquals(50, result.updatedPlayer().getGold());
        assertFalse(result.updatedPlayer().mailbox().messages().get(0).hasAttachment());
    }

    @Test
    void messageCanCarryBothGoldAndItemClaimedIndependently() {
        Item sword = item("sword", "a runed longsword");
        Player recipient = player("bob").withMailbox(PlayerMailbox.empty().add(
            PlayerMailMessage.withAttachments("alice", 0, "both", false, 40, sword)));

        MailResult result = service.read(recipient, 1, neverOverburdened);

        assertTrue(result.success());
        assertEquals(40, result.updatedPlayer().getGold());
        assertEquals(1, result.updatedPlayer().getInventory().size());
        PlayerMailMessage message = result.updatedPlayer().mailbox().messages().get(0);
        assertFalse(message.hasAttachment());
        assertFalse(message.hasItemAttachment());
    }
}
