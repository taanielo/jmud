package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link QuestCommand} token matching and argument forwarding.
 *
 * <p>Quest logic (accept, status, complete, abandon) is tested separately via
 * {@link QuestKillServiceTest} and integration-level tests that use
 * {@link QuestContextStub} directly.
 */
class QuestCommandTest {

    private static final QuestId RAT_CATCHER_ID = QuestId.of("rat-catcher");
    private static final QuestTemplate RAT_CATCHER = new QuestTemplate(
        RAT_CATCHER_ID, "Rat Catcher", "Kill 5 rats.", "rat", 5, 30, 75);

    private QuestCommand command;

    @BeforeEach
    void setUp() {
        command = new QuestCommand(new SocketCommandRegistry());
    }

    // ── Token matching ──────────────────────────────────────────────────

    @Test
    void matchesQuestToken() {
        assertTrue(command.match("QUEST LIST").isPresent());
        assertTrue(command.match("quest list").isPresent());
        assertTrue(command.match("Quest Status").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("BUY sword").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("QUE").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        var ctx = new QuestContextStub(null, null) {
            @Override
            public void executeQuest(String args) { captured.set(args); }
        };
        command.match("QUEST ACCEPT rat-catcher").orElseThrow().execute(ctx);
        assertEquals("ACCEPT rat-catcher", captured.get());
    }

    @Test
    void passesBlankArgsForBareQuestToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        var ctx = new QuestContextStub(null, null) {
            @Override
            public void executeQuest(String args) { captured.set(args); }
        };
        command.match("QUEST").orElseThrow().execute(ctx);
        assertEquals("", captured.get());
    }

    // ── Metadata ────────────────────────────────────────────────────────

    @Test
    void nameIsQuest() {
        assertEquals("quest", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionCoversAllSubCommands() {
        String desc = command.longDescription().toUpperCase(Locale.ROOT);
        assertTrue(desc.contains("LIST"));
        assertTrue(desc.contains("ACCEPT"));
        assertTrue(desc.contains("STATUS"));
        assertTrue(desc.contains("COMPLETE"));
        assertTrue(desc.contains("ABANDON"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry reg = new SocketCommandRegistry();
        new QuestCommand(reg);
        assertTrue(reg.commands().stream().anyMatch(c -> c instanceof QuestCommand));
    }

    // ── Accept / Status / Complete / Abandon integration via stub ───────

    @Test
    void acceptSetsActiveQuestOnPlayer() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat());
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));
        ctx.setRoomId(RoomId.of("courtyard"));

        command.match("QUEST ACCEPT rat-catcher").orElseThrow().execute(ctx);

        assertNotNull(ctx.savedPlayer, "Player should have been saved after ACCEPT");
        assertNotNull(ctx.savedPlayer.getActiveQuest());
        assertEquals(RAT_CATCHER_ID, ctx.savedPlayer.getActiveQuest().templateId());
        assertEquals(5, ctx.savedPlayer.getActiveQuest().killsRemaining());
    }

    @Test
    void acceptWhileHoldingActiveQuestPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat())
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 3));
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));

        command.match("QUEST ACCEPT rat-catcher").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved when already holding a quest");
        assertFalse(ctx.messages.isEmpty());
        assertTrue(ctx.messages.stream().anyMatch(m -> m.toLowerCase().contains("already")),
            "Expected 'already' in error: " + ctx.messages);
    }

    @Test
    void statusShowsNoContractWhenNoneHeld() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat());
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));

        command.match("QUEST STATUS").orElseThrow().execute(ctx);

        assertFalse(ctx.messages.isEmpty());
        assertTrue(ctx.messages.stream().anyMatch(m -> m.contains("No active contract")),
            "Expected 'No active contract' in: " + ctx.messages);
    }

    @Test
    void completePrintsErrorOutsideCourtyard() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat())
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));
        ctx.setRoomId(RoomId.of("training-yard"));

        command.match("QUEST COMPLETE").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved on out-of-room COMPLETE");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.contains("Guild Clerk")),
            "Expected Guild Clerk error: " + ctx.messages);
    }

    @Test
    void completeInCourtyardGrantsRewardAndClearsQuest() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat())
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));
        ctx.setRoomId(RoomId.of("courtyard"));

        command.match("QUEST COMPLETE").orElseThrow().execute(ctx);

        assertNotNull(ctx.savedPlayer, "Player should be saved after COMPLETE");
        assertNull(ctx.savedPlayer.getActiveQuest(), "Active quest should be cleared after COMPLETE");
        assertEquals(30, ctx.savedPlayer.getGold(), "Gold reward should be 30");
        assertTrue(ctx.savedPlayer.getExperience() >= 75, "XP reward should be granted");
    }

    @Test
    void abandonClearsActiveQuest() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat())
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 2));
        QuestContextStub ctx = new QuestContextStub(player, List.of(RAT_CATCHER));

        command.match("QUEST ABANDON").orElseThrow().execute(ctx);

        assertNotNull(ctx.savedPlayer, "Player should be saved after ABANDON");
        assertNull(ctx.savedPlayer.getActiveQuest(), "Active quest should be null after ABANDON");
        assertEquals(0, ctx.savedPlayer.getGold(), "No gold should be awarded on abandon");
    }

    @Test
    void killDecrementsThenCompleteGrantsReward() {
        // End-to-end kill count decrement test via QuestKillService
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = Player.of(user, PromptSettings.defaultFormat())
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 1));
        QuestRepository repo = new ListQuestRepo(List.of(RAT_CATCHER));
        QuestKillService svc = new QuestKillService(repo);

        var result = svc.recordKill(player, "rat");
        assertTrue(result.isPresent());
        Player afterKill = result.get().player();
        assertTrue(afterKill.getActiveQuest().isComplete());
        String msg = result.get().messages().getFirst();
        assertTrue(msg.contains("Guild Clerk"), "Expected Guild Clerk notification: " + msg);

        // Simulate COMPLETE in Courtyard
        QuestContextStub ctx = new QuestContextStub(afterKill, List.of(RAT_CATCHER));
        ctx.setRoomId(RoomId.of("courtyard"));
        command.match("QUEST COMPLETE").orElseThrow().execute(ctx);
        assertNull(ctx.savedPlayer.getActiveQuest());
        assertEquals(30, ctx.savedPlayer.getGold());
    }

    // ── Stub context ────────────────────────────────────────────────────

    /**
     * Test-only context that captures saved players and output messages.
     * Implements only the minimum required for quest-related tests.
     */
    static class QuestContextStub implements SocketCommandContext {

        private Player player;
        private final List<QuestTemplate> questTemplates;
        final List<String> messages = new ArrayList<>();
        Player savedPlayer = null;
        private RoomId roomId = RoomId.of("courtyard");

        QuestContextStub(Player player, List<QuestTemplate> questTemplates) {
            this.player = player;
            this.questTemplates = questTemplates == null ? List.of() : questTemplates;
        }

        void setRoomId(RoomId roomId) {
            this.roomId = roomId;
        }

        @Override
        public void executeQuest(String args) {
            if (player == null) {
                writeLineWithPrompt("You must be logged in to manage quests.");
                return;
            }
            QuestRepository repo = new ListQuestRepo(questTemplates);
            String[] parts = args == null
                ? new String[]{"", ""}
                : SocketCommandParsing.splitInput(args);
            String sub = parts[0];
            String subArgs = parts[1];

            switch (sub) {
                case "LIST" -> {
                    try {
                        for (QuestTemplate t : repo.findAll()) {
                            messages.add(t.id().getValue() + " — " + t.description());
                        }
                    } catch (QuestRepositoryException e) {
                        messages.add("error");
                    }
                }
                case "ACCEPT" -> {
                    if (player.getActiveQuest() != null) {
                        messages.add("You already hold an active contract.");
                        return;
                    }
                    String norm = subArgs.trim().toLowerCase(Locale.ROOT);
                    try {
                        QuestTemplate template = repo.findAll().stream()
                            .filter(t -> t.id().getValue().equalsIgnoreCase(norm))
                            .findFirst().orElse(null);
                        if (template == null) {
                            messages.add("Unknown contract.");
                            return;
                        }
                        player = player.withActiveQuest(new ActiveQuest(template.id(), template.requiredKills()));
                        savedPlayer = player;
                        messages.add("Contract accepted: " + template.name());
                    } catch (QuestRepositoryException e) {
                        messages.add("error");
                    }
                }
                case "STATUS" -> {
                    ActiveQuest active = player.getActiveQuest();
                    if (active == null) {
                        messages.add("No active contract.");
                        return;
                    }
                    try {
                        QuestTemplate template = repo.findById(active.templateId()).orElse(null);
                        if (template == null) {
                            messages.add("Unknown quest.");
                            return;
                        }
                        int done = template.requiredKills() - active.killsRemaining();
                        messages.add(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
                    } catch (QuestRepositoryException e) {
                        messages.add("error");
                    }
                }
                case "COMPLETE" -> {
                    if (!"courtyard".equals(roomId.getValue())) {
                        messages.add("The Guild Clerk is not here. Find them in the Courtyard.");
                        return;
                    }
                    ActiveQuest active = player.getActiveQuest();
                    if (active == null) {
                        messages.add("No active contract.");
                        return;
                    }
                    if (!active.isComplete()) {
                        messages.add("Not yet complete (" + active.killsRemaining() + " remaining).");
                        return;
                    }
                    try {
                        QuestTemplate template = repo.findById(active.templateId()).orElse(null);
                        if (template == null) {
                            messages.add("Unknown template.");
                            return;
                        }
                        player = player.withActiveQuest(null).addGold(template.goldReward());
                        io.taanielo.jmud.core.player.LevelUpService svc = new io.taanielo.jmud.core.player.LevelUpService();
                        io.taanielo.jmud.core.player.LevelUpService.LevelUpResult lvResult = svc.awardXp(player, template.xpReward());
                        player = lvResult.player();
                        savedPlayer = player;
                        messages.add("Contract complete. +" + template.goldReward() + "g, +" + template.xpReward() + "xp.");
                    } catch (QuestRepositoryException e) {
                        messages.add("error");
                    }
                }
                case "ABANDON" -> {
                    if (player.getActiveQuest() == null) {
                        messages.add("No active contract to abandon.");
                        return;
                    }
                    player = player.withActiveQuest(null);
                    savedPlayer = player;
                    messages.add("Contract abandoned.");
                }
                default -> messages.add("Usage: QUEST [LIST|ACCEPT <id>|STATUS|COMPLETE|ABANDON]");
            }
        }

        // ── minimal SocketCommandContext stubs ──────────────────────────

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<io.taanielo.jmud.core.authentication.Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { messages.add(m); }
        @Override public void writeLineSafe(String m) { messages.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(io.taanielo.jmud.core.authentication.Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }

    // ── List-backed quest repository stub ───────────────────────────────

    static class ListQuestRepo implements QuestRepository {
        private final List<QuestTemplate> templates;
        ListQuestRepo(List<QuestTemplate> templates) { this.templates = templates; }
        @Override public List<QuestTemplate> findAll() { return templates; }
        @Override public Optional<QuestTemplate> findById(QuestId id) {
            return templates.stream().filter(t -> t.id().equals(id)).findFirst();
        }
    }
}
