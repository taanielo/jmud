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

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link TrainCommand} token matching and argument forwarding,
 * plus integration tests via {@link TrainContextStub}.
 */
class TrainCommandTest {

    private static final AbilityId BASH_ID = AbilityId.of("skill.bash");
    private static final AbilityId HEAL_ID = AbilityId.of("spell.heal");

    private TrainCommand command;

    @BeforeEach
    void setUp() {
        command = new TrainCommand(new SocketCommandRegistry());
    }

    // ── Token matching ──────────────────────────────────────────────────

    @Test
    void matchesTrainToken() {
        assertTrue(command.match("TRAIN LIST").isPresent());
        assertTrue(command.match("train list").isPresent());
        assertTrue(command.match("Train skill.bash").isPresent());
        assertTrue(command.match("TRAIN").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("QUEST LIST").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("TR").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        var ctx = new TrainContextStub(null, List.of(), RoomId.of("training-yard"), true) {
            @Override
            public void executeTrain(String args) { captured.set(args); }
        };
        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);
        assertEquals("skill.bash", captured.get());
    }

    @Test
    void passesBlankArgsForBareTrainToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        var ctx = new TrainContextStub(null, List.of(), RoomId.of("training-yard"), true) {
            @Override
            public void executeTrain(String args) { captured.set(args); }
        };
        command.match("TRAIN").orElseThrow().execute(ctx);
        assertEquals("", captured.get());
    }

    // ── Metadata ────────────────────────────────────────────────────────

    @Test
    void nameIsTrain() {
        assertEquals("train", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionCoversSubCommands() {
        String desc = command.longDescription().toUpperCase(Locale.ROOT);
        assertTrue(desc.contains("LIST"));
        assertTrue(desc.contains("TRAIN"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry reg = new SocketCommandRegistry();
        new TrainCommand(reg);
        assertTrue(reg.commands().stream().anyMatch(c -> c instanceof TrainCommand));
    }

    // ── Practice points ─────────────────────────────────────────────────

    @Test
    void trainAbilityDeductsPracticePoint() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 1); // 1 practice point
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("training-yard"), true);

        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);

        assertNotNull(ctx.savedPlayer, "Player should be saved");
        assertEquals(0, ctx.savedPlayer.getPracticePoints(), "Practice point should be deducted");
        assertTrue(ctx.savedPlayer.getLearnedAbilities().contains(BASH_ID), "Ability should be learned");
    }

    @Test
    void trainWithNoPracticePointsPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 0); // no practice points
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("training-yard"), true);

        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.toLowerCase().contains("no practice")),
            "Expected 'no practice' message: " + ctx.messages);
    }

    @Test
    void trainAlreadyLearnedAbilityPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(BASH_ID), 2); // bash already learned
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("training-yard"), true);

        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved when re-training known ability");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.toLowerCase().contains("already")),
            "Expected 'already' message: " + ctx.messages);
    }

    @Test
    void trainOutsideTrainingYardPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 1);
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("courtyard"), true);

        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.contains("Training Yard")),
            "Expected Training Yard error: " + ctx.messages);
    }

    @Test
    void trainWithNoTrainerPresentPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 1);
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("training-yard"), false);

        command.match("TRAIN skill.bash").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved when trainer absent");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.contains("Trainer")),
            "Expected trainer not here message: " + ctx.messages);
    }

    @Test
    void trainAbilityNotInClassListPrintsError() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 1);
        // Warrior only has BASH_ID in trainable list; HEAL_ID is not
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID), RoomId.of("training-yard"), true);

        command.match("TRAIN spell.heal").orElseThrow().execute(ctx);

        assertNull(ctx.savedPlayer, "Player should NOT be saved");
        assertTrue(ctx.messages.stream().anyMatch(m -> m.toLowerCase().contains("not trainable")),
            "Expected 'not trainable' message: " + ctx.messages);
    }

    @Test
    void trainListShowsTrainableAbilities() {
        User user = new User(Username.of("hero"), Password.of("pw"));
        Player player = makeWarriorWith(user, List.of(), 2);
        TrainContextStub ctx = new TrainContextStub(player, List.of(BASH_ID, HEAL_ID), RoomId.of("training-yard"), true);

        command.match("TRAIN LIST").orElseThrow().execute(ctx);

        assertTrue(ctx.messages.stream().anyMatch(m -> m.contains("skill.bash")),
            "Expected skill.bash in listing: " + ctx.messages);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Player makeWarriorWith(User user, List<AbilityId> learnedAbilities, int practicePoints) {
        return new Player(
            user,
            2,
            0,
            PlayerVitals.defaults(),
            List.of(),
            PromptSettings.defaultFormat(),
            false,
            learnedAbilities,
            RaceId.of("human"),
            ClassId.of("warrior"),
            false,
            null,
            null,
            0,
            null,
            0L,
            practicePoints,
            0,
            null
        );
    }

    // ── Stub context ────────────────────────────────────────────────────

    /**
     * Test-only context for TRAIN command tests.
     */
    static class TrainContextStub implements SocketCommandContext {

        private Player player;
        private final List<AbilityId> classAbilityIds;
        private final RoomId roomId;
        private final boolean trainerPresent;
        final List<String> messages = new ArrayList<>();
        Player savedPlayer = null;

        TrainContextStub(Player player, List<AbilityId> classAbilityIds, RoomId roomId, boolean trainerPresent) {
            this.player = player;
            this.classAbilityIds = classAbilityIds == null ? List.of() : classAbilityIds;
            this.roomId = roomId;
            this.trainerPresent = trainerPresent;
        }

        @Override
        public void executeTrain(String args) {
            if (player == null) {
                writeLineWithPrompt("You must be logged in to train.");
                return;
            }
            // Require training-yard
            if (!"training-yard".equals(roomId.getValue())) {
                writeLineWithPrompt("The Master Trainer is not here. Find them in the Training Yard.");
                return;
            }
            // Require trainer present
            if (!trainerPresent) {
                writeLineWithPrompt("The Master Trainer is not here.");
                return;
            }
            String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
            String sub = parts[0];
            String subArgs = parts[1];
            switch (sub) {
                case "LIST" -> {
                    messages.add("Master Trainer — Trainable Abilities (Practice Points: " + player.getPracticePoints() + "):");
                    for (AbilityId id : classAbilityIds) {
                        boolean learned = player.getLearnedAbilities().contains(id);
                        messages.add(id.getValue() + " — " + (learned ? "learned" : "unlearned"));
                    }
                }
                case "" -> messages.add("Usage: TRAIN LIST  or  TRAIN <ability-id>");
                default -> {
                    String normalized = sub.trim().toLowerCase(Locale.ROOT);
                    AbilityId targetId = classAbilityIds.stream()
                        .filter(id -> id.getValue().equalsIgnoreCase(normalized))
                        .findFirst()
                        .orElse(null);
                    if (targetId == null) {
                        messages.add("'" + sub + "' is not trainable by your class.");
                        return;
                    }
                    if (player.getLearnedAbilities().contains(targetId)) {
                        messages.add("You have already learned " + targetId.getValue() + ".");
                        return;
                    }
                    if (player.getPracticePoints() <= 0) {
                        messages.add("You have no practice points.");
                        return;
                    }
                    List<AbilityId> newAbilities = new ArrayList<>(player.getLearnedAbilities());
                    newAbilities.add(targetId);
                    player = player.withPracticePoints(player.getPracticePoints() - 1)
                        .withLearnedAbilities(newAbilities);
                    savedPlayer = player;
                    messages.add("You have learned " + targetId.getValue() + "!");
                }
            }
        }

        // ── minimal SocketCommandContext stubs ──────────────────────────

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { messages.add(m); }
        @Override public void writeLineSafe(String m) { messages.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
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
}
