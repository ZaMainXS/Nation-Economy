package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

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

    public static void render(ServerPlayerEntity player, int radius) {
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        String worldId = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos center = player.getChunkPos();
        NationManager manager = NationManager.get();

        player.sendMessage(Text.literal("——— Nations Map ").formatted(Formatting.GOLD)
                .append(Text.literal("(1 square = 1 chunk)").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(" ———").formatted(Formatting.GOLD)), false);

        Set<Nation> visible = new LinkedHashSet<>();
        for (int dz = -radius; dz <= radius; dz++) {
            MutableText line = Text.empty();
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                Nation nation = manager.claimAt(worldId, chunkX * 16 + 8, chunkZ * 16 + 8);

                if (dx == 0 && dz == 0) {
                    line.append(Text.literal("+").formatted(Formatting.GOLD, Formatting.BOLD)
                            .styled(style -> style.withHoverEvent(
                                    new HoverEvent.ShowText(Text.literal("You are here")
                                            .formatted(Formatting.GOLD)))));
                } else if (nation != null) {
                    visible.add(nation);
                    line.append(cell(nation, chunkX, chunkZ));
                } else {
                    line.append(Text.literal("·").formatted(Formatting.DARK_GRAY)
                            .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(
                                    Text.literal("Wilderness — unclaimed")
                                            .formatted(Formatting.GRAY)))));
                }
            }
            player.sendMessage(line, false);
        }

        // Legend with the amounts of claimed land.
        if (visible.isEmpty()) {
            player.sendMessage(Text.literal("No nation has claimed land in this area.")
                    .formatted(Formatting.DARK_GRAY), false);
        } else {
            player.sendMessage(Text.literal("—— Nations ——").formatted(Formatting.GOLD), false);
            for (Nation nation : visible) {
                player.sendMessage(Text.empty()
                        .append(ColorUtils.colored("■ ", nation.getRgb()))
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                        .append(Text.literal(" — " + nation.claimedBlocks() + " blocks claimed ("
                                + nation.getClaims().size() + " claims)")
                                .formatted(Formatting.GRAY)), false);
            }
        }
        player.sendMessage(Text.literal("Tip: hover the squares for details, /nation info <name> for more.")
                .formatted(Formatting.DARK_GRAY), false);
    }

    private static Text cell(Nation nation, int chunkX, int chunkZ) {
        MutableText hover = Text.empty()
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal("\n" + nation.claimedBlocks() + " blocks claimed")
                        .formatted(Formatting.GRAY))
                .append(Text.literal("\nChunk " + chunkX + ", " + chunkZ).formatted(Formatting.DARK_GRAY));
        return ColorUtils.colored("■", nation.getRgb())
                .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
    }
}
