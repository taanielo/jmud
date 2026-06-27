package io.taanielo.jmud.core.mob.dto;

/**
 * JSON transfer object for the optional {@code gold_drop} block inside a mob template.
 */
public record GoldDropDto(int min, int max) {
}
