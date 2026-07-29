package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.UUID;

/**
 * Maintains one scoreboard team per nation. The team prefix
 * ({@code [Nation] }) shows up in the tab list and above players' heads,
 * colored with the nation color.
 */
public final class NationTeams {

    public static final String ID_PREFIX = "ne_";

    private NationTeams() {
    }

    /** Deterministic, short (<= 16 chars) team id for a nation key. */
    public static String teamIdFor(String nationKey) {
        return ID_PREFIX + Integer.toString(nationKey.hashCode() & 0x7fffffff, 36);
    }

    /** Recreates/updates all nation teams and (re)assigns known members. Called on server start. */
    public static void rebuild(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        // Remove teams of nations that no longer exist.
        for (PlayerTeam team : scoreboard.getPlayerTeams().toArray(PlayerTeam[]::new)) {
            if (!team.getName().startsWith(ID_PREFIX)) {
                continue;
            }
            boolean stillExists = false;
            for (Nation nation : NationManager.get().nations()) {
                if (team.getName().equals(nation.getTeamId())) {
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                scoreboard.removePlayerTeam(team);
            }
        }

        // Ensure all nation teams exist with the right style.
        for (Nation nation : NationManager.get().nations()) {
            PlayerTeam team = ensureTeam(server, nation);
            for (UUID member : nation.getMembers()) {
                String name = KnownPlayers.nameOf(member);
                if (name != null) {
                    scoreboard.addPlayerToTeam(name, team);
                }
            }
        }
    }

    /** Creates (or restyles) the team of a nation. */
    public static PlayerTeam ensureTeam(MinecraftServer server, Nation nation) {
        Scoreboard scoreboard = server.getScoreboard();
        String teamId = nation.getTeamId();
        PlayerTeam team = scoreboard.getPlayerTeam(teamId);
        if (team == null) {
            // Extremely unlikely id collision -> extend the id deterministically.
            int suffix = 2;
            String candidate = teamId;
            while (scoreboard.getPlayerTeam(candidate) != null && candidate.length() < 16) {
                candidate = teamId + suffix++;
            }
            teamId = candidate;
            nation.setTeamId(teamId);
            team = scoreboard.addPlayerTeam(teamId);
        }
        team.setPlayerPrefix(nationTag(nation).append(Component.literal(" ")));
        team.setColor(ColorUtils.nearestFormatting(nation.getRgb()));
        return team;
    }

    /** Removes a nation's team (used when a nation is disbanded). */
    public static void removeTeam(MinecraftServer server, Nation nation) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(nation.getTeamId());
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    /** The {@code [Name]} tag styled in the nation color. */
    public static MutableComponent nationTag(Nation nation) {
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Puts a player into the right nation team and removes them from any
     * stale nation team. Called on join and whenever membership changes.
     */
    public static void applyToPlayer(MinecraftServer server, ServerPlayer player) {
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getName().getString();

        Nation nation = NationManager.get().nationOf(player.getUUID());
        PlayerTeam desired = nation == null ? null : ensureTeam(server, nation);

        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && current.getName().startsWith(ID_PREFIX) && current != desired) {
            scoreboard.removePlayerFromTeam(playerName, current);
        }
        if (desired != null) {
            scoreboard.addPlayerToTeam(playerName, desired);
        }
    }

    /** Prefix for chat messages. */
    public static Component chatPrefix(Nation nation) {
        return nationTag(nation).append(Component.literal(" "));
    }
}
