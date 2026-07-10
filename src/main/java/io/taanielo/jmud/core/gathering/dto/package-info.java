/**
 * JSON transfer objects and mappers for resource-node content files
 * ({@code data/resource-nodes/*.json}).
 *
 * <p>These types exist only to bridge on-disk JSON into the immutable
 * {@link io.taanielo.jmud.core.gathering.ResourceNode} domain value objects; they carry no game
 * logic. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.gathering.dto;

import org.jspecify.annotations.NullMarked;
