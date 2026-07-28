package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows territory notifications while players move:
 * <ul>
 *     <li>a big on-screen title when entering another nation's land
 *     (nation name in its color plus how much land they own),</li>
 *     <li>a subtle action-bar message for your own land / wilderness.</li>
 * </ul>
 */
public final class BorderNotifier {

    private static final Map<UUID, String> LAST_SEEN = new ConcurrentHashMap<>();
    private static final String WILDERNESS = "\0wilderness";

    private BorderNotifier() {
    }

    public static void forget(UUID player) {
        LAST_SEEN.remove(player);
    }

    public static void tick(MinecraftServer server) {
        // Only check every 4 ticks to keep the cost negligible.
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        NationManager manager = NationManager.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String worldId = player.level().dimension().location().toString();
            Nation nation = manager.claimAt(worldId, player.getBlockPos().getX(), player.getBlockPos().getZ());
            String current = nation == null ? WILDERNESS : nation.getKey();

            String last = LAST_SEEN.put(player.getUUID(), current);
            if (current.equals(last)) {
                continue;
            }
            if (nation == null) {
                if (last != null) {
                    player.sendSystemMessage(Component.literal("» Wilderness").withStyle(ChatFormatting.DARK_GRAY), true);
                }
                continue;
            }

            if (nation.isMember(player.getUUID())) {
                player.sendSystemMessage(Component.literal("» ").withStyle(ChatFormatting.GRAY)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Component.literal(" (your nation)").withStyle(ChatFormatting.GREEN)), true);
            } else {
                // Big banner for foreign land: name in nation color + claimed size.
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal(nation.claimedBlocks() + " blocks claimed")
                                .withStyle(ChatFormatting.GRAY)));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        ColorUtils.colored(nation.getName(), nation.getRgb())));
                player.sendSystemMessage(Component.literal("» Entering ").withStyle(ChatFormatting.GRAY)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Component.literal(" — protected land").withStyle(ChatFormatting.RED)), true);
            }
        }
    }
}
