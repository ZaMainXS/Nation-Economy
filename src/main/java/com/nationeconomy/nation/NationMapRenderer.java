package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Renders the territory map in chat ({@code /nation map}).
 *
 * <p>A server-side mod has no way to draw a real full-screen minimap on a
 * vanilla client, so the map is rendered as a colored grid of squares in
 * chat — every square is one chunk, colored in the owning nation's color.
 * Hovering a square shows the nation name, its total claimed land and the
 * chunk coordinates; the legend below lists every visible nation with its
 * total claimed blocks.
 */
public final class NationMapRenderer {

    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 14;

    private NationMapRenderer() {
    }

    public static void render(ServerPlayer player, int radius) {
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        String worldId = player.level().dimension().location().toString();
        ChunkPos center = player.chunkPosition();
        NationManager manager = NationManager.get();

        player.sendSystemMessage(Component.literal("——— Nations Map ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("(1 square = 1 chunk)").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" ———").withStyle(ChatFormatting.GOLD)), false);

        Set<Nation> visible = new LinkedHashSet<>();
        for (int dz = -radius; dz <= radius; dz++) {
            MutableComponent line = Component.empty();
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                Nation nation = manager.claimAt(worldId, chunkX * 16 + 8, chunkZ * 16 + 8);

                if (dx == 0 && dz == 0) {
                    line.append(Component.literal("+").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .withStyle(style -> style.withHoverEvent(
                                    new HoverEvent.ShowText(Component.literal("You are here")
                                            .withStyle(ChatFormatting.GOLD)))));
                } else if (nation != null) {
                    visible.add(nation);
                    line.append(cell(nation, chunkX, chunkZ));
                } else {
                    line.append(Component.literal("·").withStyle(ChatFormatting.DARK_GRAY)
                            .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Wilderness — unclaimed")
                                            .withStyle(ChatFormatting.GRAY)))));
                }
            }
            player.sendSystemMessage(line, false);
        }

        // Legend with the amounts of claimed land.
        if (visible.isEmpty()) {
            player.sendSystemMessage(Component.literal("No nation has claimed land in this area.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            player.sendSystemMessage(Component.literal("—— Nations ——").withStyle(ChatFormatting.GOLD), false);
            for (Nation nation : visible) {
                player.sendSystemMessage(Component.empty()
                        .append(ColorUtils.colored("■ ", nation.getRgb()))
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Component.literal(" — " + nation.claimedBlocks() + " blocks claimed ("
                                + nation.getClaims().size() + " claims)")
                                .withStyle(ChatFormatting.GRAY)), false);
            }
        }
        player.sendSystemMessage(Component.literal("Tip: hover the squares for details, /nation info <name> for more.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static Component cell(Nation nation, int chunkX, int chunkZ) {
        MutableComponent hover = Component.empty()
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal("\n" + nation.claimedBlocks() + " blocks claimed")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nChunk " + chunkX + ", " + chunkZ).withStyle(ChatFormatting.DARK_GRAY));
        return ColorUtils.colored("■", nation.getRgb())
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
    }
}
