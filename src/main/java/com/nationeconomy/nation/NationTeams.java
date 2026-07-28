package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        for (Team team : scoreboard.getTeams().toArray(Team[]::new)) {
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
                scoreboard.removeTeam(team);
            }
        }

        // Ensure all nation teams exist with the right style.
        for (Nation nation : NationManager.get().nations()) {
            Team team = ensureTeam(server, nation);
            for (UUID member : nation.getMembers()) {
                String name = KnownPlayers.nameOf(member);
                if (name != null) {
                    scoreboard.addScoreHolderToTeam(name, team);
                }
            }
        }
    }

    /** Creates (or restyles) the team of a nation. */
    public static Team ensureTeam(MinecraftServer server, Nation nation) {
        Scoreboard scoreboard = server.getScoreboard();
        String teamId = nation.getTeamId();
        Team team = scoreboard.getTeam(teamId);
        if (team == null) {
            // Extremely unlikely id collision -> extend the id deterministically.
            int suffix = 2;
            String candidate = teamId;
            while (scoreboard.getTeam(candidate) != null && candidate.length() < 16) {
                candidate = teamId + suffix++;
            }
            teamId = candidate;
            nation.setTeamId(teamId);
            team = scoreboard.addTeam(teamId);
        }
        team.setPrefix(nationTag(nation).append(Text.literal(" ")));
        team.setColor(ColorUtils.nearestFormatting(nation.getRgb()));
        return team;
    }

    /** Removes a nation's team (used when a nation is disbanded). */
    public static void removeTeam(MinecraftServer server, Nation nation) {
        Scoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(nation.getTeamId());
        if (team != null) {
            scoreboard.removeTeam(team);
        }
    }

    /** The {@code [Name]} tag styled in the nation color. */
    public static Text nationTag(Nation nation) {
        return Text.literal("[")
                .formatted(Formatting.DARK_GRAY)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal("]").formatted(Formatting.DARK_GRAY));
    }

    /**
     * Puts a player into the right nation team and removes them from any
     * stale nation team. Called on join and whenever membership changes.
     */
    public static void applyToPlayer(MinecraftServer server, ServerPlayerEntity player) {
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getName().getString();

        Nation nation = NationManager.get().nationOf(player.getUuid());
        Team desired = nation == null ? null : ensureTeam(server, nation);

        Team current = scoreboard.getScoreHolderTeam(playerName);
        if (current != null && current.getName().startsWith(ID_PREFIX) && current != desired) {
            scoreboard.removeScoreHolderFromTeam(playerName, current);
        }
        if (desired != null) {
            scoreboard.addScoreHolderToTeam(playerName, desired);
        }
    }

    /** Prefix for chat messages. */
    public static Text chatPrefix(Nation nation) {
        return nationTag(nation).append(Text.literal(" "));
    }
}
