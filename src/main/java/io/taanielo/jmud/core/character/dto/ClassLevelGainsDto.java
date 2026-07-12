package io.taanielo.jmud.core.character.dto;

/**
 * JSON DTO for a class's optional {@code level_gains} object, describing the per-level growth of
 * max HP, mana and move for characters of that class.
 *
 * @param hp   permanent max-HP gained per level-up
 * @param mana permanent max-mana gained per level-up
 * @param move permanent max-move gained per level-up
 */
public record ClassLevelGainsDto(int hp, int mana, int move) {
}
