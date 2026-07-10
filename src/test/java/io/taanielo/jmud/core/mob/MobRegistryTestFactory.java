package io.taanielo.jmud.core.mob;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Public factory that assembles a live {@link MobRegistry} from a supplied {@link RoomService} and a
 * fixed set of templates, for tests outside the {@code core.mob} package (e.g. the wizard SPAWN/PURGE
 * command tests) that need a real registry without repeating the repository-stub boilerplate.
 */
public final class MobRegistryTestFactory {

    private MobRegistryTestFactory() {
    }

    /**
     * Builds and initialises a {@link MobRegistry} backed by in-memory stub repositories.
     *
     * @param roomService the room service the registry resolves occupancy and exits through
     * @param templates   the mob templates to load
     * @return an initialised registry (its {@link MobRegistry#init()} has been called)
     */
    public static MobRegistry create(RoomService roomService, List<MobTemplate> templates) {
        PlayerRepository playerRepo = new StubPlayerRepository();
        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(templates),
            new StubItemRepository(),
            new StubAttackRepository(),
            roomService,
            playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo),
            new PlayerEventBus(),
            MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    private record StubMobTemplateRepository(List<MobTemplate> templates) implements MobTemplateRepository {
        @Override public List<MobTemplate> findAll() { return templates; }
    }

    private record StubAttackRepository() implements AttackRepository {
        @Override public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.empty();
        }
    }

    private record StubItemRepository() implements ItemRepository {
        @Override public void save(Item item) throws RepositoryException {}

        @Override public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.empty();
        }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }

        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }
}
