package io.taanielo.jmud.core.needs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.taanielo.jmud.core.authentication.Username;

public class NeedsSession {

    private final Username username;
    private final Needs needs;
    private final Map<NeedId, NeedSeverity> lastSeverities;
    private final int health;

    private NeedsSession(Username username, Needs needs, Map<NeedId, NeedSeverity> lastSeverities, int health) {
        this.username = username;
        this.needs = needs;
        this.lastSeverities = Map.copyOf(lastSeverities);
        this.health = health;
    }

    public static NeedsSession forPlayer(Username username) {
        return new NeedsSession(username, BasicNeeds.defaultNeeds(), new java.util.HashMap<>(), NeedsSettings.startingHealth());
    }

    public static NeedsSession withNeeds(Username username, Needs needs, int health) {
        return new NeedsSession(username, needs, new java.util.HashMap<>(), health);
    }

    public NeedsTickOutcome tick() {
        NeedsTickResult result = needs.decay();
        List<String> messages = new ArrayList<>();
        Map<NeedId, NeedSeverity> nextSeverities = new java.util.HashMap<>(lastSeverities);

        for (NeedEvent event : result.events()) {
            NeedSeverity previous = lastSeverities.getOrDefault(event.needId(), NeedSeverity.NORMAL);
            if (event.severity() != previous) {
                if (event.severity() == NeedSeverity.WARNING) {
                    messages.add(NeedsMessages.warningMessage(event.needId()));
                } else if (event.severity() == NeedSeverity.SEVERE) {
                    messages.add(NeedsMessages.severeMessage(event.needId()));
                }
            }
            nextSeverities.put(event.needId(), event.severity());
        }

        NeedsSession nextSession = new NeedsSession(username, result.needs(), nextSeverities, health);
        return new NeedsTickOutcome(nextSession, List.copyOf(messages));
    }

    public Username username() {
        return username;
    }

    public Needs needs() {
        return needs;
    }

    public int health() {
        return health;
    }
}
