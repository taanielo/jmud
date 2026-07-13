package io.taanielo.jmud.core.world.area;

import java.util.ArrayList;
import java.util.Comparator;
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
                String title = area.get().name() + " (" + area.get().levelRange().describe() + ")";
                return Optional.of(frame(title, area.get().asciiMap()));
            }
            Optional<WorldAtlas> atlas = areaRepository.findAtlas();
            if (atlas.isPresent() && atlas.get().id().equals(mapAreaId.getValue())) {
                List<String> art = new ArrayList<>(atlas.get().asciiMap());
                art.addAll(difficultyLegend());
                return Optional.of(frame(atlas.get().name(), art));
            }
        } catch (RepositoryException e) {
            log.warn("Failed to render map for area {}: {}", mapAreaId.getValue(), e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Builds the difficulty-band legend appended below the World Atlas art: each area with its
     * recommended level range, ordered from lowest band to highest so players can read the world as
     * a progression (issue #550).
     *
     * @return the legend lines, or an empty list when no areas are defined
     */
    private List<String> difficultyLegend() throws RepositoryException {
        List<Area> areas = new ArrayList<>(areaRepository.findAll());
        if (areas.isEmpty()) {
            return List.of();
        }
        areas.sort(Comparator
            .comparingInt((Area a) -> a.levelRange().min())
            .thenComparingInt(a -> a.levelRange().max())
            .thenComparing(Area::name));
        List<String> legend = new ArrayList<>();
        legend.add("");
        legend.add("Recommended levels:");
        for (Area area : areas) {
            legend.add("  " + area.name() + " (" + area.levelRange().describe() + ")");
        }
        return legend;
    }

    private static List<String> frame(String title, List<String> art) {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.addAll(art);
        return List.copyOf(lines);
    }
}
