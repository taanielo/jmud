package io.taanielo.jmud.core.bounty.repository.json;

/**
 * JSON transfer object for a single persisted open bounty.
 *
 * @param backer        the posting player's username
 * @param mobTemplateId the template id of the mob type targeted
 * @param mobName       the mob type's display name at post time
 * @param reward        the escrowed gold stake
 * @param postedTick    the game tick the bounty was posted
 */
record BountyDto(
    String backer,
    String mobTemplateId,
    String mobName,
    int reward,
    long postedTick
) {
}
