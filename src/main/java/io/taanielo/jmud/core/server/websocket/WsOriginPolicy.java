package io.taanielo.jmud.core.server.websocket;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Config-backed {@code Origin} allowlist for the WebSocket handshake. A browser attaches an {@code
 * Origin} header to every WebSocket upgrade; checking it against an allowlist makes cross-site
 * WebSocket hijacking harder (issue #526 §5). Non-browser clients (curl, websocat, tests) that send
 * no {@code Origin} are always allowed, since the check exists to constrain browsers.
 *
 * <p>The default is {@link #permissive()} — appropriate for the loopback-bound default deployment;
 * a public deployment should configure an explicit allowlist.
 */
public final class WsOriginPolicy {

    private final boolean allowAll;
    private final Set<String> allowedOrigins;

    private WsOriginPolicy(boolean allowAll, Set<String> allowedOrigins) {
        this.allowAll = allowAll;
        this.allowedOrigins = allowedOrigins;
    }

    /** A policy that accepts any origin. */
    public static WsOriginPolicy permissive() {
        return new WsOriginPolicy(true, Set.of());
    }

    /**
     * A policy that accepts only the given origins (case-insensitive). An empty list yields a
     * permissive policy, so an unset configuration keeps the default loopback behaviour.
     */
    public static WsOriginPolicy of(List<String> origins) {
        Objects.requireNonNull(origins, "Origins are required");
        Set<String> normalized = origins.stream()
            .filter(Objects::nonNull)
            .map(o -> o.trim().toLowerCase(Locale.ROOT))
            .filter(o -> !o.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        return normalized.isEmpty() ? permissive() : new WsOriginPolicy(false, normalized);
    }

    /**
     * Returns whether a connection presenting the given {@code Origin} header may proceed. A missing
     * ({@code null}) origin is always allowed; a present origin must be on the allowlist unless the
     * policy is permissive.
     */
    public boolean isAllowed(@Nullable String origin) {
        if (allowAll || origin == null) {
            return true;
        }
        return allowedOrigins.contains(origin.trim().toLowerCase(Locale.ROOT));
    }
}
