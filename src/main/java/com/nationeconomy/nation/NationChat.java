package com.nationeconomy.nation;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
            Nation nation = NationManager.get().nationOf(sender.getUUID());

            MutableComponent line = Component.empty();
            if (nation != null) {
                line.append(NationTeams.chatPrefix(nation));
            }
            line.append(Component.literal("<" + sender.getName().getString() + "> ").withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(message.decoratedContent().getString()).withStyle(ChatFormatting.WHITE));

            sender.level().getServer().getPlayerList().broadcastSystemMessage(line, false);
            return false;
        });
    }
}
