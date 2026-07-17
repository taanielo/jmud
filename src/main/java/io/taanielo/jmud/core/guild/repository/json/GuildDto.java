package io.taanielo.jmud.core.guild.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.dto.ItemDto;

/**
 * JSON transfer object for a single guild file ({@code guilds/<id>.json}).
 */
record GuildDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String leaderId,
    @Nullable List<GuildMemberDto> members,
    int treasuryGold,
    @Nullable List<VaultedItemDto> vaultedItems,
    int lifetimeDepositedGold
) {

    /** JSON transfer object for one roster entry. */
    record GuildMemberDto(
        @Nullable String username,
        @Nullable String rank,
        int joinOrder
    ) {
    }

    /** JSON transfer object for one shared-vault entry: the stored item and its depositor. */
    record VaultedItemDto(
        @Nullable ItemDto item,
        @Nullable String depositor
    ) {
    }
}
