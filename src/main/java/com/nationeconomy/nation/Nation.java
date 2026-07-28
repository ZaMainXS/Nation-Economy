package com.nationeconomy.nation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A nation: name, color, owner/members, trusted outsiders with granted
 * permissions, claims and the claim-block allowance.
 */
public class Nation {

    /** Base claim-block allowance: a 1000x1000 area. */
    public static final long BASE_CLAIM_BLOCKS = 1_000_000L;
    /** Extra allowance granted per netherite ingot spent on /nation upgrade. */
    public static final long BLOCKS_PER_NETHERITE = 100L;

    private String name = "Nation";
    /** Lowercase key used for lookups. */
    private String key = "nation";
    /** RGB color used for chat/tab/map. */
    private int rgb = 0x55FFFF;
    /** Scoreboard team id (max 16 chars). */
    private String teamId = "";
    private UUID owner;
    /** All members, including the owner. */
    private Set<UUID> members = new LinkedHashSet<>();
    /** Players not allowed to (re)join. */
    private Set<UUID> banished = new LinkedHashSet<>();
    /** Outsiders (or members) with explicitly granted permissions: uuid -> permissions. */
    private Map<UUID, Set<NationPermission>> trusted = new LinkedHashMap<>();
    /** Extra claim blocks bought with netherite. */
    private long bonusBlocks = 0;
    private List<Claim> claims = new ArrayList<>();

    public Nation() {
    }

    public Nation(String name, UUID owner) {
        this.name = name;
        this.key = name.toLowerCase(java.util.Locale.ROOT);
        this.owner = owner;
        this.members.add(owner);
        this.teamId = NationTeams.teamIdFor(this.key);
    }

    // --------------------------------------------------------------- basics

    public String getName() {
        return name;
    }

    public String getKey() {
        return key;
    }

    public int getRgb() {
        return rgb;
    }

    public void setRgb(int rgb) {
        this.rgb = rgb;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    // -------------------------------------------------------------- members

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isBanished(UUID uuid) {
        return banished.contains(uuid);
    }

    public Set<UUID> getBanished() {
        return banished;
    }

    // -------------------------------------------------------------- trusted

    public Map<UUID, Set<NationPermission>> getTrusted() {
        return trusted;
    }

    public void trust(UUID uuid, NationPermission permission) {
        trusted.computeIfAbsent(uuid, u -> new LinkedHashSet<>()).add(permission);
    }

    public void untrust(UUID uuid, NationPermission permission) {
        Set<NationPermission> permissions = trusted.get(uuid);
        if (permissions == null) {
            return;
        }
        if (permission == NationPermission.ALL) {
            trusted.remove(uuid);
            return;
        }
        permissions.remove(permission);
        if (permissions.isEmpty()) {
            trusted.remove(uuid);
        }
    }

    /** Checks a permission. Members can always do everything in their own nation. */
    public boolean hasPermission(UUID uuid, NationPermission permission) {
        if (isMember(uuid)) {
            return true;
        }
        Set<NationPermission> permissions = trusted.get(uuid);
        return permissions != null
                && (permissions.contains(NationPermission.ALL) || permissions.contains(permission));
    }

    // --------------------------------------------------------------- claims

    public List<Claim> getClaims() {
        return claims;
    }

    public long maxBlocks() {
        return BASE_CLAIM_BLOCKS + bonusBlocks;
    }

    public long claimedBlocks() {
        long total = 0;
        for (Claim claim : claims) {
            total += claim.area();
        }
        return total;
    }

    public long remainingBlocks() {
        return maxBlocks() - claimedBlocks();
    }

    public void addBonusBlocks(long amount) {
        bonusBlocks += amount;
    }

    /** The claim covering a position, or {@code null}. */
    public Claim claimAt(String worldId, int x, int z) {
        for (Claim claim : claims) {
            if (claim.contains(worldId, x, z)) {
                return claim;
            }
        }
        return null;
    }
}
