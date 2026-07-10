package io.taanielo.jmud.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class ContentReloadServiceTest {

    private static Item item(String id) {
        return Item.builder(ItemId.of(id), id, "desc", ItemAttributes.empty()).weight(1).value(1).build();
    }

    @Test
    void commitsAllContentAndReportsCounts() throws Exception {
        List<String> committed = new ArrayList<>();
        FakePreparedItems items = new FakePreparedItems(2, committed);
        FakePrepared rooms = new FakePrepared("rooms", 3, committed);
        FakePrepared mobs = new FakePrepared("mobs", 4, committed);

        ContentReloadService service = new ContentReloadService(
            () -> items,
            lookup -> rooms,
            () -> mobs,
            id -> Optional.empty());

        PreparedContentReload prepared = service.prepare();
        // Nothing committed until the tick-thread commit runs.
        assertTrue(committed.isEmpty());

        ReloadReport report = prepared.commit();

        assertEquals(new ReloadReport(3, 2, 4), report);
        // Items must commit before rooms so room state stays consistent with the item cache.
        assertEquals(List.of("items", "rooms", "mobs"), committed);
    }

    @Test
    void combinedLookupPrefersPreparedItemsThenFallsBackToLive() throws Exception {
        Item preparedApple = item("apple");
        Item liveSword = item("sword");
        FakePreparedItems items = new FakePreparedItems(1, new ArrayList<>());
        items.add(preparedApple);

        List<ItemLookup> capturedLookup = new ArrayList<>();
        ContentReloadService service = new ContentReloadService(
            () -> items,
            lookup -> {
                capturedLookup.add(lookup);
                return new FakePrepared("rooms", 0, new ArrayList<>());
            },
            null,
            id -> id.equals(ItemId.of("sword")) ? Optional.of(liveSword) : Optional.empty());

        service.prepare();

        ItemLookup lookup = capturedLookup.getFirst();
        assertSame(preparedApple, lookup.find(ItemId.of("apple")).orElseThrow());
        assertSame(liveSword, lookup.find(ItemId.of("sword")).orElseThrow());
        assertTrue(lookup.find(ItemId.of("missing")).isEmpty());
    }

    @Test
    void missingMobReloaderReportsZeroMobs() throws Exception {
        FakePreparedItems items = new FakePreparedItems(5, new ArrayList<>());
        FakePrepared rooms = new FakePrepared("rooms", 7, new ArrayList<>());

        ContentReloadService service = new ContentReloadService(
            () -> items,
            lookup -> rooms,
            null,
            id -> Optional.empty());

        ReloadReport report = service.prepare().commit();

        assertEquals(new ReloadReport(7, 5, 0), report);
    }

    @Test
    void parseErrorDuringPrepareAppliesNoCommit() {
        List<String> committed = new ArrayList<>();
        FakePreparedItems items = new FakePreparedItems(2, committed);

        ContentReloadService service = new ContentReloadService(
            () -> items,
            lookup -> {
                throw new RepositoryException("broken room file");
            },
            () -> new FakePrepared("mobs", 1, committed),
            id -> Optional.empty());

        assertThrows(RepositoryException.class, service::prepare);
        assertTrue(committed.isEmpty(), "no content may be committed when prepare fails");
    }

    @Test
    void itemPrepareErrorPropagates() {
        ContentReloadService service = new ContentReloadService(
            () -> {
                throw new RepositoryException("broken item file");
            },
            lookup -> new FakePrepared("rooms", 0, new ArrayList<>()),
            null,
            id -> Optional.empty());

        RepositoryException error = assertThrows(RepositoryException.class, service::prepare);
        assertTrue(error.getMessage().contains("broken item file"));
    }

    private static final class FakePrepared implements PreparedReload {
        private final String contentType;
        private final int count;
        private final List<String> committedSink;

        FakePrepared(String contentType, int count, List<String> committedSink) {
            this.contentType = contentType;
            this.count = count;
            this.committedSink = committedSink;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void commit() {
            committedSink.add(contentType);
        }
    }

    private static final class FakePreparedItems implements PreparedItemReload {
        private final int count;
        private final List<String> committedSink;
        private final List<Item> items = new ArrayList<>();

        FakePreparedItems(int count, List<String> committedSink) {
            this.count = count;
            this.committedSink = committedSink;
        }

        void add(Item item) {
            items.add(item);
        }

        @Override
        public Optional<Item> find(ItemId id) {
            return items.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public String contentType() {
            return "items";
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void commit() {
            committedSink.add("items");
        }
    }
}
