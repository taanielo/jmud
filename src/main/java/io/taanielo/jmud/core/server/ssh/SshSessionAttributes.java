package io.taanielo.jmud.core.server.ssh;

import org.apache.sshd.common.AttributeRepository.AttributeKey;

import io.taanielo.jmud.core.authentication.User;

/**
 * SSH session attribute keys for authenticated users.
 */
public final class SshSessionAttributes {

    public static final AttributeKey<User> AUTHENTICATED_USER = new AttributeKey<>();
    public static final AttributeKey<Boolean> NEW_USER = new AttributeKey<>();

    private SshSessionAttributes() {
    }
}
