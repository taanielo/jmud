package io.taanielo.jmud.core.world.area;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Renders the hand-drawn cartography a player sees when they READ a map item.
 *
 * <p>A map item carries a {@code map_area_id}: when it names a world {@link Area} this service
 * returns that area's {@code ascii_map} art framed with a title; when it names the {@link
 * WorldAtlas} it returns the atlas overview. No player position is ever drawn — map items are the
 * only cartography and they show fixed, authored art (issue #529).
 */
@Slf4j
public class AreaMapService {

    private final AreaRepository areaRepository;

    /**
     * Creates a map-rendering service backed by the given area repository.
     *
     * @param areaRepository the source of area and atlas definitions
     */
    public AreaMapService(AreaRepository areaRepository) {
        this.areaRepository = Objects.requireNonNull(areaRepository, "Area repository is required");
    }

    /**
     * Renders the cartography for the given {@code map_area_id}, framed for display.
     *
     * <p>The id is matched first against world areas and then against the atlas, so a single
     * item field can drive both area maps and the World Atlas.
     *
     * @param mapAreaId the {@code map_area_id} carried by the map item
     * @return the framed map lines, or {@link Optional#empty()} when no area or atlas matches
     */
    public Optional<List<String>> render(@Nullable AreaId mapAreaId) {
        if (mapAreaId == null) {
            return Optional.empty();
        }
        try {
            Optional<Area> area = areaRepository.findById(mapAreaId);
            if (area.isPresent()) {
                return Optional.of(frame(area.get().name(), area.get().asciiMap()));
            }
            Optional<WorldAtlas> atlas = areaRepository.findAtlas();
            if (atlas.isPresent() && atlas.get().id().equals(mapAreaId.getValue())) {
                return Optional.of(frame(atlas.get().name(), atlas.get().asciiMap()));
            }
        } catch (RepositoryException e) {
            log.warn("Failed to render map for area {}: {}", mapAreaId.getValue(), e.getMessage());
        }
        return Optional.empty();
    }

    private static List<String> frame(String title, List<String> art) {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.addAll(art);
        return List.copyOf(lines);
    }
}
