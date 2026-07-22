package io.taanielo.jmud.core.bounty.repository.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single persisted open bounty.
 *
 * <p>Schema v2 writes the generic target fields ({@code target_kind}, {@code target_id},
 * {@code target_name}). The legacy v1 mob-only fields ({@code mob_template_id}, {@code mob_name}) are
 * retained as nullable so pre-v2 files still deserialize (see {@code JsonBountyRepository}); on write
 * they are always null and omitted via {@link JsonInclude.Include#NON_NULL}.
 *
 * @param backer        the posting player's username
 * @param targetKind    the target discriminator ("MOB" or "PLAYER"); absent in v1 files
 * @param targetId      the target's stable id (mob template id or player username); absent in v1 files
 * @param targetName    the target's display name; absent in v1 files
 * @param mobTemplateId legacy v1 field: the mob template id targeted; null in v2 files
 * @param mobName       legacy v1 field: the mob display name; null in v2 files
 * @param reward        the escrowed gold stake
 * @param postedTick    the game tick the bounty was posted
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record BountyDto(
    String backer,
    @Nullable String targetKind,
    @Nullable String targetId,
    @Nullable String targetName,
    @Nullable String mobTemplateId,
    @Nullable String mobName,
    int reward,
    long postedTick
) {
}
