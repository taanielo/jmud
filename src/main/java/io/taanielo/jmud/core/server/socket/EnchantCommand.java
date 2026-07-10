package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ENCHANT} command. With no arguments it lists the enchantments an Enchanter can
 * apply, with each affix's stat bonus, live {@code have/need} material counts and gold cost; with an
 * {@code <item> <enchantment>} argument it permanently imbues that carried item with the named affix,
 * consuming the required materials and gold. Requires an Enchanter to be present in the current room,
 * mirroring {@link BrewCommand} and {@link CookCommand}.
 */
public class EnchantCommand extends RegistrableCommand {

    public EnchantCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "enchant";
    }

    @Override
    public String shortDescription() {
        return "Imbue carried gear with a permanent affix at an enchanter.";
    }

    @Override
    public String longDescription() {
        return "Usage: ENCHANT [<item> <enchantment>]\n"
             + "  With no argument, lists the enchantments the enchanter in this room can apply,\n"
             + "  showing each affix's stat bonus, the materials it needs (and how many you are\n"
             + "  carrying) and the gold cost.\n"
             + "  With an item and enchantment name, permanently adds that affix to the item you are\n"
             + "  carrying: the listed materials and gold are consumed and the item's new stats are\n"
             + "  reported. Only equippable weapons and armour can be enchanted, and each item can\n"
             + "  bear a single rune. You must be standing near an enchanter.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"ENCHANT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.enchant(args)));
    }
}
