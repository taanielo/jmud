package io.taanielo.jmud.core.needs;

import java.util.Map;

public interface Needs {
    NeedsTickResult decay();
    Map<NeedId, NeedState> snapshot();
}
