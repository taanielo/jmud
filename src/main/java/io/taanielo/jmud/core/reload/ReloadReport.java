package io.taanielo.jmud.core.reload;

/**
 * Immutable summary of a completed content reload: how many entries of each type were reloaded
 * (issue #349).
 *
 * @param rooms the number of rooms reloaded
 * @param items the number of items reloaded
 * @param mobs  the number of mob templates reloaded ({@code 0} when the mob subsystem is absent)
 */
public record ReloadReport(int rooms, int items, int mobs) {

    /**
     * Creates a report, rejecting negative counts.
     */
    public ReloadReport {
        if (rooms < 0 || items < 0 || mobs < 0) {
            throw new IllegalArgumentException("Reload counts must not be negative");
        }
    }

    /**
     * Renders the human-readable confirmation line shown to the wizard.
     *
     * @return e.g. {@code "Reloaded 42 rooms, 156 items, 8 mobs."}
     */
    public String summary() {
        return "Reloaded " + rooms + " " + plural(rooms, "room")
            + ", " + items + " " + plural(items, "item")
            + ", " + mobs + " " + plural(mobs, "mob") + ".";
    }

    private static String plural(int count, String noun) {
        return count == 1 ? noun : noun + "s";
    }
}
