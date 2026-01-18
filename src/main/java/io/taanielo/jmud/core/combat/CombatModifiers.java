package io.taanielo.jmud.core.combat;

public record CombatModifiers(
    CombatStatModifier attack,
    CombatStatModifier defense,
    CombatStatModifier damage,
    CombatStatModifier hitChance,
    CombatStatModifier critChance
) {
}
