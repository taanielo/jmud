package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code PROMPT} command, letting players customize and colorize their in-game prompt.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code PROMPT}                          — show the current prompt format and available tokens</li>
 *   <li>{@code PROMPT SET <format>}             — set and persist the prompt format string</li>
 *   <li>{@code PROMPT COLOR [on|off|toggle|status]} — control ANSI colorization of the prompt</li>
 * </ul>
 *
 * <p>Format strings may contain the percent tokens {@code %h}/{@code %H} (hit points),
 * {@code %m}/{@code %M} (mana), {@code %v} (moves), {@code %x} (experience), {@code %l} (level),
 * and {@code %%} for a literal percent sign, in addition to the historic brace tokens such as
 * {@code {hp}} and {@code {partyHp}}.
 */
public class PromptCommand extends RegistrableCommand {

    /**
     * Creates a {@code PromptCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public PromptCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "prompt";
    }

    @Override
    public String shortDescription() {
        return "Customize your in-game prompt. Use PROMPT SET <format> or PROMPT COLOR on.";
    }

    @Override
    public String longDescription() {
        return "Usage: PROMPT [sub-command] [args]\n"
             + "  PROMPT                          — show your current prompt format and tokens\n"
             + "  PROMPT SET <format>             — set and save your prompt format string\n"
             + "  PROMPT COLOR [on|off|toggle|status] — control ANSI color in your prompt\n"
             + "\n"
             + "Format tokens:\n"
             + "  %h/%H hit points   %m/%M mana   %v moves   %x experience   %l level   %% literal %\n"
             + "  {hp} {maxHp} {mana} {maxMana} {move} {maxMove} {exp} {partyHp} are also supported.\n"
             + "\n"
             + "Example: PROMPT SET [%h/%Hhp %m/%Mmn %vmv] >";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"PROMPT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.updatePrompt(args)));
    }
}
