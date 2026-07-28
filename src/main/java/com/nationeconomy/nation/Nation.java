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
 * permissions, claims, nation homes and the physical nation core.
 */
public class Nation {

    /** Base claim-block allowance: a 1000x1000 area. */
    public static final long BASE_CLAIM_BLOCKS = 1_000_000L;
    /** Extra allowance granted per netherite ingot spent on /nation upgrade. */
    public static final long BLOCKS_PER_NETHERITE = 100L;
    /** Nation home slots each nation gets. */
    public static final int MAX_HOMES = 3;
    /** Hits the nation core survives before the nation falls. */
    public static final int MAX_CORE_HITS = 10_000;

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
    /** Nation homes (max {@link #MAX_HOMES}). */
    private List<Home> homes = new ArrayList<>();

    // ------------------------------------------------------------ core data

    /** World the core lives in ("" when the nation has no core yet). */
    private String coreWorld = "";
    private double coreX;
    private double coreY;
    private double coreZ;
    /** UUID of the armor stand entity visualising the core. */
    private UUID coreEntityUuid;
    /** Remaining hits before the core (and the nation) is destroyed. */
    private int coreHits = MAX_CORE_HITS;
    /** Damage tracker: attacker uuid -> hits dealt to the core. */
    private Map<UUID, Integer> coreDamage = new LinkedHashMap<>();

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

    // ---------------------------------------------------------------- homes

    public List<Home> getHomes() {
        return homes;
    }

    public boolean addHome(Home home) {
        if (homes.size() >= MAX_HOMES) {
            return false;
        }
        homes.add(home);
        return true;
    }

    /** A nation home teleport target. */
    public static class Home {
        private String world = "minecraft:overworld";
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public Home() {
        }

        public Home(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }
    }

    // ---------------------------------------------------------------- core

    public boolean hasCore() {
        return coreWorld != null && !coreWorld.isEmpty();
    }

    public String getCoreWorld() {
        return coreWorld;
    }

    public void placeCore(String world, double x, double y, double z) {
        this.coreWorld = world;
        this.coreX = x;
        this.coreY = y;
        this.coreZ = z;
        this.coreHits = MAX_CORE_HITS;
    }

    public double getCoreX() {
        return coreX;
    }

    public double getCoreY() {
        return coreY;
    }

    public double getCoreZ() {
        return coreZ;
    }

    public UUID getCoreEntityUuid() {
        return coreEntityUuid;
    }

    public void setCoreEntityUuid(UUID coreEntityUuid) {
        this.coreEntityUuid = coreEntityUuid;
    }

    public void clearCore() {
        this.coreWorld = "";
        this.coreEntityUuid = null;
        this.coreDamage.clear();
    }

    public int getCoreHits() {
        return coreHits;
    }

    public void setCoreHits(int coreHits) {
        this.coreHits = coreHits;
    }

    public void damageCore(UUID attacker) {
        coreHits = Math.max(0, coreHits - 1);
        if (attacker != null) {
            coreDamage.merge(attacker, 1, Integer::sum);
        }
    }

    public void healCore(int amount) {
        coreHits = Math.min(MAX_CORE_HITS, coreHits + amount);
    }

    /** The player who dealt the most damage to the core, or {@code null}. */
    public UUID topCoreAttacker() {
        UUID best = null;
        int bestHits = 0;
        for (Map.Entry<UUID, Integer> entry : coreDamage.entrySet()) {
            if (entry.getValue() > bestHits) {
                bestHits = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
