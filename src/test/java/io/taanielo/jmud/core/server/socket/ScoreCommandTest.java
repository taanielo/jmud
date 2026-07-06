package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link ScoreCommand} AC display.
 *
 * <p>Covers: no armour (race bonus only), armour equipped (summed total), zero AC case.
 */
class ScoreCommandTest {

    @Test
    void matchesScoreToken() {
        ScoreCommand cmd = makeCommand(EquipmentArmorResolver.noOp(), RaceArmorBonusResolver.noOp());
        assertTrue(cmd.match("SCORE").isPresent());
        assertTrue(cmd.match("score").isPresent());
        assertTrue(cmd.match("SC").isPresent());
        assertTrue(cmd.match("sc").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        ScoreCommand cmd = makeCommand(EquipmentArmorResolver.noOp(), RaceArmorBonusResolver.noOp());
        assertFalse(cmd.match("LOOK").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void displaysZeroAcWhenNoArmourAndNoRacialBonus() {
        // No armour equipped, no-op race resolver → AC = 0
        EquipmentArmorResolver equipmentResolver = EquipmentArmorResolver.noOp();
        RaceArmorBonusResolver raceResolver = RaceArmorBonusResolver.noOp();

        Player player = makePlayer(PlayerEquipment.empty());
        CapturingContext context = new CapturingContext(player, true);

        ScoreCommand cmd = makeCommand(equipmentResolver, raceResolver);
        cmd.match("SCORE").orElseThrow().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("AC    : 0")),
            "Expected 'AC    : 0' in output, got: " + context.lines);
    }

    @Test
    void displaysRacialBonusOnlyWhenNoArmourEquipped() {
        // No armour equipped, race resolver returns 3 → AC = 3
        EquipmentArmorResolver equipmentResolver = EquipmentArmorResolver.noOp();
        RaceArmorBonusResolver raceResolver = stubRaceResolver(3);

        Player player = makePlayer(PlayerEquipment.empty());
        CapturingContext context = new CapturingContext(player, true);

        ScoreCommand cmd = makeCommand(equipmentResolver, raceResolver);
        cmd.match("SCORE").orElseThrow().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("AC    : 3")),
            "Expected 'AC    : 3' in output, got: " + context.lines);
    }

    @Test
    void displaysSummedAcWhenArmourEquipped() {
        // Equipment resolver returns 5, race resolver returns 2 → AC = 7
        EquipmentArmorResolver equipmentResolver = stubEquipmentResolver(5);
        RaceArmorBonusResolver raceResolver = stubRaceResolver(2);

        Player player = makePlayer(PlayerEquipment.empty());
        CapturingContext context = new CapturingContext(player, true);

        ScoreCommand cmd = makeCommand(equipmentResolver, raceResolver);
        cmd.match("SCORE").orElseThrow().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("AC    : 7")),
            "Expected 'AC    : 7' in output, got: " + context.lines);
    }

    @Test
    void displaysAcFromEquipmentOnly() {
        // Equipment resolver returns 10, no-op race resolver returns 0 → AC = 10
        EquipmentArmorResolver equipmentResolver = stubEquipmentResolver(10);
        RaceArmorBonusResolver raceResolver = RaceArmorBonusResolver.noOp();

        Player player = makePlayer(PlayerEquipment.empty());
        CapturingContext context = new CapturingContext(player, true);

        ScoreCommand cmd = makeCommand(equipmentResolver, raceResolver);
        cmd.match("SC").orElseThrow().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("AC    : 10")),
            "Expected 'AC    : 10' in output, got: " + context.lines);
    }

    @Test
    void unauthenticatedPlayerGetsErrorMessage() {
        ScoreCommand cmd = makeCommand(EquipmentArmorResolver.noOp(), RaceArmorBonusResolver.noOp());
        CapturingContext context = new CapturingContext(null, false);

        cmd.match("SCORE").orElseThrow().execute(context);

        assertTrue(context.lines.isEmpty(), "No safe-written lines expected for unauthed player");
        assertTrue(context.promptMessage != null, "Prompt error message expected");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ScoreCommand makeCommand(EquipmentArmorResolver equipmentResolver, RaceArmorBonusResolver raceResolver) {
        return new ScoreCommand(new SocketCommandRegistry(), equipmentResolver, raceResolver);
    }

    private Player makePlayer(PlayerEquipment equipment) {
        Player base = new Player(
            User.of(Username.of("hero"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(20, 20, 10, 10, 10, 10),
            List.of(),
            "prompt",
            false,
            List.of(),
            (RaceId) null,
            null
        );
        return base.withEquipment(equipment);
    }

    /** Returns an {@link EquipmentArmorResolver} that always returns {@code ac}. */
    private EquipmentArmorResolver stubEquipmentResolver(int ac) {
        ItemRepository emptyRepo = new ItemRepository() {
            @Override public void save(Item item) {}
            @Override public Optional<Item> findById(ItemId id) { return Optional.empty(); }
        };
        return new EquipmentArmorResolver(emptyRepo) {
            @Override
            public int totalAc(Player player) {
                return ac;
            }
        };
    }

    /** Returns a {@link RaceArmorBonusResolver} that always returns {@code bonus}. */
    private RaceArmorBonusResolver stubRaceResolver(int bonus) {
        RaceRepository emptyRepo = new RaceRepository() {
            @Override public Optional<Race> findById(RaceId id) { return Optional.empty(); }
            @Override public List<Race> findAll() { return List.of(); }
        };
        return new RaceArmorBonusResolver(emptyRepo) {
            @Override
            public int armorBonus(Player player) {
                return bonus;
            }
        };
    }

    // ── capturing context stub ────────────────────────────────────────────

    private static class CapturingContext implements SocketCommandContext {
        private final Player player;
        private final boolean authenticated;
        final List<String> lines = new ArrayList<>();
        String promptMessage;

        CapturingContext(Player player, boolean authenticated) {
            this.player = player;
            this.authenticated = authenticated;
        }

        @Override public boolean isAuthenticated() { return authenticated; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
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
        @Override public void sendMessage(Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
