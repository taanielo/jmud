package io.taanielo.jmud.core.needs;

import java.util.List;

public record NeedsTickResult(Needs needs, List<NeedEvent> events, int damage) {
}
