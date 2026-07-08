package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Registry for socket command handlers.
 */
public class SocketCommandRegistry {
    private final List<SocketCommandHandler> commands = new ArrayList<>();

    /**
     * Creates a registry with the default socket command set.
     *
     * <p>Repositories and resolvers are constructed by the composition root
     * ({@code GameContext}) and passed in — this method must not build
     * infrastructure itself.
     *
     * @param equipmentArmorResolver resolver for AC contributed by equipped armour
     * @param raceArmorBonusResolver resolver for AC contributed by the player's race
     * @param playerRepository       repository used to enumerate all persisted players for {@code RANK}
     * @param roomService            service used to resolve room exits/occupancy for {@code SHOUT}/{@code WHISPER}
     * @param messageBroadcaster     scoped delivery service used to fan out {@code SHOUT} to nearby rooms
     */
    public static SocketCommandRegistry createDefault(
        EquipmentArmorResolver equipmentArmorResolver,
        RaceArmorBonusResolver raceArmorBonusResolver,
        PlayerRepository playerRepository,
        RoomService roomService,
        MessageBroadcaster messageBroadcaster
    ) {
        Objects.requireNonNull(equipmentArmorResolver, "Equipment armor resolver is required");
        Objects.requireNonNull(raceArmorBonusResolver, "Race armor bonus resolver is required");
        Objects.requireNonNull(playerRepository, "Player repository is required");
        Objects.requireNonNull(roomService, "Room service is required");
        Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new LookCommand(registry);
        new ExamineCommand(registry);
        new MoveCommand(registry);
        new GetCommand(registry);
        new DropCommand(registry);
        new GiveCommand(registry, roomService);
        new QuaffCommand(registry);
        new EatCommand(registry);
        new DrinkCommand(registry);
        new ReadCommand(registry);
        new WriteCommand(registry);
        new EquipCommand(registry);
        new UnequipCommand(registry);
        new InventoryCommand(registry);
        new EquipmentCommand(registry);
        new SayCommand(registry);
        new EmoteCommand(registry);
        new TellCommand(registry);
        new WhisperCommand(registry, roomService);
        new ShoutCommand(registry, roomService, messageBroadcaster);
        new GossipCommand(registry);
        new WhoCommand(registry);
        new RankCommand(registry, playerRepository);
        new ScoreCommand(registry, equipmentArmorResolver, raceArmorBonusResolver);
        new AbilityCommand(registry);
        new CastCommand(registry);
        new AbilitiesCommand(registry);
        new AttackCommand(registry);
        new KillCommand(registry);
        new ConsiderCommand(registry);
        new FleeCommand(registry);
        new RecallCommand(registry);
        new RestCommand(registry);
        new WakeCommand(registry);
        new AnsiCommand(registry);
        new GoldCommand(registry);
        new ListCommand(registry);
        new BuyCommand(registry);
        new SellCommand(registry);
        new QuestCommand(registry);
        new TrainCommand(registry);
        new PartyCommand(registry);
        new DepositCommand(registry);
        new WithdrawCommand(registry);
        new LockCommand(registry);
        new UnlockCommand(registry);
        new QuitCommand(registry);
        new HelpCommand(registry);
        new AliasCommand(registry);
        new MailCommand(registry);
        return registry;
    }

    /**
     * Registers a command handler.
     */
    public void register(SocketCommandHandler command) {
        commands.add(Objects.requireNonNull(command, "Command is required"));
    }

    /**
     * Returns a snapshot of registered commands.
     */
    public List<SocketCommandHandler> commands() {
        return List.copyOf(commands);
    }
}
