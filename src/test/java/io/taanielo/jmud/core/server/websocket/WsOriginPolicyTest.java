package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WsOriginPolicyTest {

    @Test
    void permissivePolicyAllowsAnyOrigin() {
        WsOriginPolicy policy = WsOriginPolicy.permissive();

        assertTrue(policy.isAllowed("http://evil.example"));
        assertTrue(policy.isAllowed(null));
    }

    @Test
    void allowlistAcceptsConfiguredOriginCaseInsensitively() {
        WsOriginPolicy policy = WsOriginPolicy.of(List.of("https://play.example.com"));

        assertTrue(policy.isAllowed("https://play.example.com"));
        assertTrue(policy.isAllowed("HTTPS://Play.Example.com"));
    }

    @Test
    void allowlistRejectsUnknownOrigin() {
        WsOriginPolicy policy = WsOriginPolicy.of(List.of("https://play.example.com"));

        assertFalse(policy.isAllowed("http://evil.example"));
    }

    @Test
    void allowlistPermitsMissingOriginForNonBrowserClients() {
        WsOriginPolicy policy = WsOriginPolicy.of(List.of("https://play.example.com"));

        assertTrue(policy.isAllowed(null));
    }

    @Test
    void emptyAllowlistFallsBackToPermissive() {
        WsOriginPolicy policy = WsOriginPolicy.of(List.of("  ", ""));

        assertTrue(policy.isAllowed("http://anything.example"));
    }
}
