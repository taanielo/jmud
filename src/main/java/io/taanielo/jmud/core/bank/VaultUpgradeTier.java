package io.taanielo.jmud.core.bank;

import java.util.Optional;

/**
 * A purchasable expansion tier for a player's personal bank vault.
 *
 * <p>Every player starts at {@link #BASE} (tier {@code 0}), which grants no bonus over the
 * configured base capacity ({@link BankSettings#vaultCapacity()}). Paying gold at a bank via
 * {@code VAULT UPGRADE} advances the player one tier at a time, and each tier permanently adds
 * {@value #SLOTS_PER_TIER} slots on top of the base capacity. Progression is capped at
 * {@link #TIER_THREE}, so there is no infinite grind, and a purchased tier is never lost.
 *
 * <p>Tiers are a pure, deterministic table (no wall-clock or randomness); the effective slot count
 * is derived by {@code BankService} as base capacity plus {@link #slotBonus()}.
 */
public enum VaultUpgradeTier {

    /** Default tier: no gold spent, no bonus over the base capacity. */
    BASE(0, 0, 0),
    /** First upgrade: +10 slots for 5,000 gold. */
    TIER_ONE(1, 10, 5_000),
    /** Second upgrade: +20 slots total for a further 15,000 gold. */
    TIER_TWO(2, 20, 15_000),
    /** Top upgrade: +30 slots total for a further 40,000 gold. */
    TIER_THREE(3, 30, 40_000);

    /** Number of vault slots each tier above {@link #BASE} adds relative to the previous tier. */
    public static final int SLOTS_PER_TIER = 10;

    private final int rank;
    private final int slotBonus;
    private final int upgradeCost;

    VaultUpgradeTier(int rank, int slotBonus, int upgradeCost) {
        this.rank = rank;
        this.slotBonus = slotBonus;
        this.upgradeCost = upgradeCost;
    }

    /**
     * Returns the tier for the given persisted rank, clamped into the valid range.
     *
     * <p>Ranks below {@code 0} resolve to {@link #BASE}; ranks above the maximum resolve to the top
     * tier, so a player can never end up outside the known table.
     *
     * @param rank the persisted capacity-tier value
     * @return the matching tier, never {@code null}
     */
    public static VaultUpgradeTier forRank(int rank) {
        VaultUpgradeTier result = BASE;
        for (VaultUpgradeTier tier : values()) {
            if (rank >= tier.rank) {
                result = tier;
            }
        }
        return result;
    }

    /** Returns this tier's numeric rank, matching the value persisted on the player. */
    public int rank() {
        return rank;
    }

    /** Returns the total number of extra slots this tier grants over the base capacity. */
    public int slotBonus() {
        return slotBonus;
    }

    /** Returns the gold cost to advance from the previous tier to this one; {@code 0} for {@link #BASE}. */
    public int upgradeCost() {
        return upgradeCost;
    }

    /** Returns {@code true} when this is the top tier and no further upgrade is possible. */
    public boolean isMax() {
        return this == TIER_THREE;
    }

    /**
     * Returns the next tier up, or empty when this is already the top tier.
     *
     * @return the next {@link VaultUpgradeTier}, or empty at {@link #TIER_THREE}
     */
    public Optional<VaultUpgradeTier> next() {
        return switch (this) {
            case BASE -> Optional.of(TIER_ONE);
            case TIER_ONE -> Optional.of(TIER_TWO);
            case TIER_TWO -> Optional.of(TIER_THREE);
            case TIER_THREE -> Optional.empty();
        };
    }
}
