package io.taanielo.jmud.core.needs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BasicNeeds implements Needs {

    private final Map<NeedId, NeedState> needs;

    public BasicNeeds(Map<NeedId, NeedState> needs) {
        if (needs == null || needs.isEmpty()) {
            throw new IllegalArgumentException("Needs map must not be empty");
        }
        this.needs = Map.copyOf(needs);
    }

    public static BasicNeeds of(Map<NeedId, NeedState> needs) {
        return new BasicNeeds(needs);
    }

    public static BasicNeeds defaultNeeds() {
        int warning = NeedsSettings.warningThreshold();
        int severe = NeedsSettings.severeThreshold();
        NeedState hunger = NeedState.of(
            NeedsSettings.maxHunger(),
            NeedsSettings.maxHunger(),
            NeedsSettings.hungerDecay(),
            warning,
            severe
        );
        NeedState thirst = NeedState.of(
            NeedsSettings.maxThirst(),
            NeedsSettings.maxThirst(),
            NeedsSettings.thirstDecay(),
            warning,
            severe
        );
        return new BasicNeeds(Map.of(
            NeedId.HUNGER, hunger,
            NeedId.THIRST, thirst
        ));
    }

    @Override
    public NeedsTickResult decay() {
        List<NeedEvent> events = new ArrayList<>();
        Map<NeedId, NeedState> next = needs.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    NeedState decayed = entry.getValue().decay();
                    NeedSeverity severity = decayed.severity();
                    if (severity != NeedSeverity.NORMAL) {
                        events.add(new NeedEvent(entry.getKey(), severity));
                    }
                    return decayed;
                }
            ));
        return new NeedsTickResult(new BasicNeeds(next), List.copyOf(events), 0);
    }

    @Override
    public Map<NeedId, NeedState> snapshot() {
        return Map.copyOf(needs);
    }
}
