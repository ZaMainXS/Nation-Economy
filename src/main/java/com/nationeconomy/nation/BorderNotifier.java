package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        if (server.getTicks() % 4 != 0) {
            return;
        }
        NationManager manager = NationManager.get();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            String worldId = player.getWorld().getRegistryKey().getValue().toString();
            Nation nation = manager.claimAt(worldId, player.getBlockPos().getX(), player.getBlockPos().getZ());
            String current = nation == null ? WILDERNESS : nation.getKey();

            String last = LAST_SEEN.put(player.getUuid(), current);
            if (current.equals(last)) {
                continue;
            }
            if (nation == null) {
                if (last != null) {
                    player.sendMessage(Text.literal("» Wilderness").formatted(Formatting.DARK_GRAY), true);
                }
                continue;
            }

            if (nation.isMember(player.getUuid())) {
                player.sendMessage(Text.literal("» ").formatted(Formatting.GRAY)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Text.literal(" (your nation)").formatted(Formatting.GREEN)), true);
            } else {
                // Big banner for foreign land: name in nation color + claimed size.
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(
                        Text.literal(nation.claimedBlocks() + " blocks claimed")
                                .formatted(Formatting.GRAY)));
                player.networkHandler.sendPacket(new TitleS2CPacket(
                        ColorUtils.colored(nation.getName(), nation.getRgb())));
                player.sendMessage(Text.literal("» Entering ").formatted(Formatting.GRAY)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Text.literal(" — protected land").formatted(Formatting.RED)), true);
            }
        }
    }
}
