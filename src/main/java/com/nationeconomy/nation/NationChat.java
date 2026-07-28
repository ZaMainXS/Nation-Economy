package com.nationeconomy.nation;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Replaces vanilla chat formatting with
 * {@code [Nation] <Player> message}.
 * The nation tag uses the nation's custom color — any of the 16 million
 * RGB colors work here.
 */
public final class NationChat {

    private NationChat() {
    }

    public static void register() {
        // Cancel vanilla chat and re-broadcast our own format.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Nation nation = NationManager.get().nationOf(sender.getUuid());

            MutableText line = Text.empty();
            if (nation != null) {
                line.append(NationTeams.chatPrefix(nation));
            }
            line.append(Text.literal("<" + sender.getName().getString() + "> ").formatted(Formatting.WHITE));
            line.append(Text.literal(message.getContent().getString()).formatted(Formatting.WHITE));

            sender.getServer().getPlayerManager().broadcast(line, false);
            return false;
        });
    }
}
