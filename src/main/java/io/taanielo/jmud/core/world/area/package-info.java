/**
 * Formal world areas: named groupings of rooms with hand-drawn ASCII cartography.
 *
 * <p>An {@link io.taanielo.jmud.core.world.area.Area} owns an explicit list of room ids, the ids of
 * the adjacent areas it connects to (for the world atlas), and hand-authored {@code ascii_map} art
 * rendered when a player READs a matching map item. The {@link
 * io.taanielo.jmud.core.world.area.WorldAtlas} is a single overview document listing every area and
 * their connections. Cartography is data-driven: maps are items, never the player's live position
 * (issue #529).
 */
@NullMarked
package io.taanielo.jmud.core.world.area;

import org.jspecify.annotations.NullMarked;
