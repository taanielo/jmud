package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.combat.ClassArmorBonusResolver;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.guild.GuildRepository;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.TellService;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.reload.ContentReloadService;
import io.taanielo.jmud.core.tick.TickMetricsService;
import io.taanielo.jmud.core.tick.TickThreadDispatcher;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.world.PlayerLocationService;
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
     * @param equipmentArmorResolver  resolver for AC contributed by equipped armour
     * @param raceArmorBonusResolver  resolver for AC contributed by the player's race
     * @param classArmorBonusResolver resolver for AC contributed by the player's class
     * @param classRepository         repository used by {@code HELP <class>} to render a class reference sheet
     * @param abilityRegistry         registry used by {@code HELP <class>} to resolve ability ids to display names
     * @param playerRepository        repository used to enumerate all persisted players for {@code RANK}
     * @param guildRepository         repository used to enumerate all persisted guilds for {@code RANK GUILDS}
     * @param roomService             service used to resolve room exits/occupancy for {@code SHOUT}/{@code WHISPER}
     * @param tellService             in-memory tracker of the last private-message sender per player,
     *                                backing the {@code REPLY} command
     * @param messageBroadcaster      scoped delivery service used to fan out {@code SHOUT} to nearby rooms
     * @param reputationService       faction reputation service used by the read-only {@code REPUTATION}
     *                                command to resolve faction names and standing labels
     * @param weatherEngine           weather source used to show a visibility line in {@code WHO}/{@code SCORE};
     *                                {@code null} disables the weather line
     * @param tickMetricsService      tick-loop metrics service queried by the wizard {@code STATS} command
     * @param wizardPolicy            policy deciding which players may run wizard commands
     * @param playerLocationService   service used by the wizard {@code GOTO} command to relocate the admin
     * @param mobRegistry             live mob registry used by the wizard {@code SPAWN}/{@code PURGE}
     *                                commands; {@code null} when the mob subsystem failed to load
     * @param shutdownHandle          late-bound handle used by the wizard {@code SHUTDOWN} command
     * @param contentReloadService    service used by the wizard {@code RELOAD} command to hot-reload
     *                                rooms/items/mobs from JSON
     * @param tickThreadDispatcher    bridge used by the wizard {@code RELOAD} command to apply the
     *                                reload atomically on the tick thread
     */
    public static SocketCommandRegistry createDefault(
        EquipmentArmorResolver equipmentArmorResolver,
        RaceArmorBonusResolver raceArmorBonusResolver,
        ClassArmorBonusResolver classArmorBonusResolver,
        CharacterAttributesResolver characterAttributesResolver,
        ClassRepository classRepository,
        AbilityRegistry abilityRegistry,
        PlayerRepository playerRepository,
        GuildRepository guildRepository,
        RoomService roomService,
        TellService tellService,
        MessageBroadcaster messageBroadcaster,
        ReputationService reputationService,
        @Nullable WeatherEngine weatherEngine,
        TickMetricsService tickMetricsService,
        WizardPolicy wizardPolicy,
        PlayerLocationService playerLocationService,
        @Nullable MobRegistry mobRegistry,
        ShutdownHandle shutdownHandle,
        ContentReloadService contentReloadService,
        TickThreadDispatcher tickThreadDispatcher
    ) {
        Objects.requireNonNull(equipmentArmorResolver, "Equipment armor resolver is required");
        Objects.requireNonNull(raceArmorBonusResolver, "Race armor bonus resolver is required");
        Objects.requireNonNull(classArmorBonusResolver, "Class armor bonus resolver is required");
        Objects.requireNonNull(characterAttributesResolver, "Character attributes resolver is required");
        Objects.requireNonNull(classRepository, "Class repository is required");
        Objects.requireNonNull(abilityRegistry, "Ability registry is required");
        Objects.requireNonNull(playerRepository, "Player repository is required");
        Objects.requireNonNull(guildRepository, "Guild repository is required");
        Objects.requireNonNull(roomService, "Room service is required");
        Objects.requireNonNull(tellService, "Tell service is required");
        Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        Objects.requireNonNull(reputationService, "Reputation service is required");
        Objects.requireNonNull(tickMetricsService, "Tick metrics service is required");
        Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        Objects.requireNonNull(playerLocationService, "Player location service is required");
        Objects.requireNonNull(shutdownHandle, "Shutdown handle is required");
        Objects.requireNonNull(contentReloadService, "Content reload service is required");
        Objects.requireNonNull(tickThreadDispatcher, "Tick thread dispatcher is required");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new LookCommand(registry);
        new ExamineCommand(registry);
        new CompareCommand(registry);
        new MoveCommand(registry);
        new GetCommand(registry);
        new GetFromCommand(registry);
        new PutCommand(registry);
        new DropCommand(registry);
        new GiveCommand(registry, roomService);
        new QuaffCommand(registry);
        new EatCommand(registry);
        new DrinkCommand(registry);
        new ReadCommand(registry);
        new IdentifyCommand(registry);
        new WriteCommand(registry);
        new EquipCommand(registry);
        new UnequipCommand(registry);
        new InventoryCommand(registry);
        new EquipmentCommand(registry);
        new SayCommand(registry);
        new EmoteCommand(registry);
        new TellCommand(registry, tellService);
        new WhisperCommand(registry, roomService, tellService);
        new ReplyCommand(registry, tellService);
        new ShoutCommand(registry, roomService, messageBroadcaster);
        new GossipCommand(registry);
        new WhoCommand(registry, roomService, weatherEngine);
        new RankCommand(registry, playerRepository, guildRepository);
        new ReputationCommand(registry, reputationService);
        new ScoreCommand(registry, equipmentArmorResolver, raceArmorBonusResolver, classArmorBonusResolver,
            characterAttributesResolver, roomService, weatherEngine);
        new AchievementsCommand(registry);
        new AbilityCommand(registry);
        new CastCommand(registry);
        new AbilitiesCommand(registry);
        new CooldownsCommand(registry);
        new EffectsCommand(registry);
        new AttackCommand(registry);
        new KillCommand(registry);
        new AssistCommand(registry);
        new TauntCommand(registry);
        new RangedAttackCommand(registry);
        new ConsiderCommand(registry);
        new DuelCommand(registry);
        new TradeCommand(registry);
        new MarryCommand(registry);
        new MentorCommand(registry);
        new SpouseTellCommand(registry);
        new AcceptCommand(registry);
        new FleeCommand(registry);
        new RecallCommand(registry);
        new BindCommand(registry);
        new WayfindCommand(registry);
        new AutoWalkCommand(registry);
        new CorpseCommand(registry);
        new RestCommand(registry);
        new WakeCommand(registry);
        new AnsiCommand(registry);
        new AutoLootCommand(registry);
        new AutoAssistCommand(registry);
        new BriefCommand(registry);
        new PromptCommand(registry);
        new GoldCommand(registry);
        new ListCommand(registry);
        new BuyCommand(registry);
        new SellCommand(registry);
        new RepairCommand(registry);
        new CraftCommand(registry);
        new SalvageCommand(registry);
        new BrewCommand(registry);
        new CookCommand(registry);
        new EnchantCommand(registry);
        new TanCommand(registry);
        new CutCommand(registry);
        new SewCommand(registry);
        new GatherCommand(registry);
        new QuestCommand(registry);
        new DailyQuestCommand(registry);
        new TrainCommand(registry);
        new PartyCommand(registry);
        new FollowCommand(registry);
        new GuildCommand(registry);
        new GuildQuestCommand(registry);
        new TitleCommand(registry);
        new DescribeCommand(registry);
        new DepositCommand(registry);
        new WithdrawCommand(registry);
        new StoreCommand(registry);
        new ClaimCommand(registry);
        new VaultCommand(registry);
        new AuctionCommand(registry);
        new BountyCommand(registry);
        new LockCommand(registry);
        new UnlockCommand(registry);
        new PickCommand(registry);
        new SearchCommand(registry);
        new SneakCommand(registry);
        new MountCommand(registry);
        new DismountCommand(registry);
        new StealCommand(registry);
        new TrackCommand(registry);
        new SummonCommand(registry);
        new TameCommand(registry);
        new CompanionsCommand(registry);
        new NameCommand(registry);
        new TalkCommand(registry);
        new RespondCommand(registry);
        new QuitCommand(registry);
        new HelpCommand(registry, classRepository, abilityRegistry);
        new AliasCommand(registry);
        new IgnoreCommand(registry);
        new FriendCommand(registry);
        new AfkCommand(registry);
        new LfgCommand(registry);
        new MailCommand(registry);
        new BoardCommand(registry);
        new NoteCommand(registry);
        new StatsCommand(registry, tickMetricsService, wizardPolicy);
        new GotoCommand(registry, wizardPolicy, playerLocationService, roomService, messageBroadcaster);
        new SpawnCommand(registry, wizardPolicy, mobRegistry, roomService, messageBroadcaster);
        new PurgeCommand(registry, wizardPolicy, mobRegistry, roomService, playerRepository, messageBroadcaster);
        new ShutdownCommand(registry, wizardPolicy, shutdownHandle, messageBroadcaster);
        new ReloadCommand(registry, wizardPolicy, contentReloadService, messageBroadcaster, tickThreadDispatcher);
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
