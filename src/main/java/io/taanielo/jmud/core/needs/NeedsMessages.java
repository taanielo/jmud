package io.taanielo.jmud.core.needs;

public final class NeedsMessages {
    private NeedsMessages() {
    }

    public static String warningMessage(NeedId needId) {
        if (NeedId.HUNGER.equals(needId)) {
            return "You feel hungry.";
        }
        if (NeedId.THIRST.equals(needId)) {
            return "You feel thirsty.";
        }
        return "You feel your " + needId.getValue() + " fading.";
    }

    public static String severeMessage(NeedId needId) {
        if (NeedId.HUNGER.equals(needId)) {
            return "You are starving.";
        }
        if (NeedId.THIRST.equals(needId)) {
            return "You are dehydrated.";
        }
        return "You are suffering from low " + needId.getValue() + ".";
    }
}
