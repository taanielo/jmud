package io.taanielo.jmud.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code {verb}} placeholder is conjugation-aware per channel.
 */
class MessageRendererVerbTest {

    private final MessageRenderer renderer = new MessageRenderer();

    private static MessageContext withVerb() {
        return new MessageContext(
            null, null, "attacker", "goblin", null, null, "slash", 7, "MAULS", "MAUL");
    }

    @Test
    void selfChannelUsesThirdPersonVerb() {
        MessageSpec spec = new MessageSpec(MessagePhase.ATTACK_HIT, MessageChannel.SELF,
            "You slash {target}. Your strike {verb} them!");
        assertEquals("You slash goblin. Your strike MAULS them!", renderer.render(spec, withVerb()));
    }

    @Test
    void roomChannelUsesThirdPersonVerb() {
        MessageSpec spec = new MessageSpec(MessagePhase.ATTACK_HIT, MessageChannel.ROOM,
            "{source}'s strike {verb} {target}!");
        assertEquals("attacker's strike MAULS goblin!", renderer.render(spec, withVerb()));
    }

    @Test
    void targetChannelUsesSecondPersonVerb() {
        MessageSpec spec = new MessageSpec(MessagePhase.ATTACK_HIT, MessageChannel.TARGET,
            "{source} slashes you. The blows {verb} you!");
        assertEquals("attacker slashes you. The blows MAUL you!", renderer.render(spec, withVerb()));
    }

    @Test
    void verbRendersEmptyWhenAbsent() {
        MessageContext noVerb = new MessageContext(
            null, null, "attacker", "goblin", null, null, "slash", 7);
        MessageSpec spec = new MessageSpec(MessagePhase.ATTACK_HIT, MessageChannel.SELF,
            "You slash {target}.{verb}");
        assertEquals("You slash goblin.", renderer.render(spec, noVerb));
    }
}
