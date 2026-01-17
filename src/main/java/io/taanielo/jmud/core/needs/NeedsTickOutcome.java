package io.taanielo.jmud.core.needs;

import java.util.List;

public record NeedsTickOutcome(NeedsSession session, List<String> messages) {
}
