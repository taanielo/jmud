package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;

class AreaMapServiceTest {

    private static Area area(String id, String name, LevelRange range) {
        return new Area(AreaId.of(id), name, List.of(RoomId.of(id + "-room")), List.of(),
            List.of(name.toUpperCase(Locale.ROOT) + " ART"), range);
    }

    @Test
    void areaMapTitleIncludesLevelRange() {
        Area frozen = area("frozen-peaks", "Frozen Peaks", new LevelRange(14, 22));
        AreaMapService service = new AreaMapService(new StubAreaRepository(List.of(frozen), null));

        Optional<List<String>> rendered = service.render(AreaId.of("frozen-peaks"));

        assertTrue(rendered.isPresent(), "expected a rendered map");
        assertEquals("Frozen Peaks (levels 14-22)", rendered.get().getFirst());
        assertTrue(rendered.get().contains("FROZEN PEAKS ART"), "art must still be framed");
    }

    @Test
    void atlasAppendsDifficultyLegendOrderedByBand() {
        Area town = area("town", "Town", new LevelRange(1, 3));
        Area frozen = area("frozen-peaks", "Frozen Peaks", new LevelRange(14, 22));
        WorldAtlas atlas = new WorldAtlas("atlas", "World Atlas", List.of("WORLD OVERVIEW"));
        AreaMapService service =
            new AreaMapService(new StubAreaRepository(List.of(frozen, town), atlas));

        Optional<List<String>> rendered = service.render(AreaId.of("atlas"));

        assertTrue(rendered.isPresent(), "expected the atlas to render");
        List<String> lines = rendered.get();
        assertEquals("World Atlas", lines.getFirst());
        assertTrue(lines.contains("WORLD OVERVIEW"), "atlas art must still be framed");
        assertTrue(lines.contains("Recommended levels:"), "atlas must carry a difficulty legend");
        int townIdx = lines.indexOf("  Town (levels 1-3)");
        int frozenIdx = lines.indexOf("  Frozen Peaks (levels 14-22)");
        assertTrue(townIdx >= 0 && frozenIdx >= 0, "both areas must be listed: " + lines);
        assertTrue(townIdx < frozenIdx, "legend must be ordered from lowest band to highest");
    }

    @Test
    void unknownAreaRendersNothing() {
        AreaMapService service = new AreaMapService(new StubAreaRepository(List.of(), null));

        assertTrue(service.render(AreaId.of("nowhere")).isEmpty());
    }

    private record StubAreaRepository(List<Area> areas, WorldAtlas atlas) implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.copyOf(areas);
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return areas.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.ofNullable(atlas);
        }
    }
}
