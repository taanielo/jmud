package io.taanielo.jmud.core.quest;

/**
 * Discriminates the three supported quest contracts.
 *
 * <ul>
 *   <li>{@link #KILL} — slay a number of a target mob, then claim the reward from the Guild Clerk.</li>
 *   <li>{@link #DELIVERY_ITEM} — collect a number of a drop item and turn them in to the Guild Clerk.</li>
 *   <li>{@link #DELIVERY_NPC} — carry a package handed over by one NPC to a second NPC in another zone.</li>
 * </ul>
 */
public enum QuestType {
    KILL,
    DELIVERY_ITEM,
    DELIVERY_NPC
}
