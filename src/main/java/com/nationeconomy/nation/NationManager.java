package com.nationeconomy.nation;

import com.nationeconomy.util.JsonFiles;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores all nations, member lookups and claims.
 * Persisted to {@code <world>/nationeconomy/nations.json}.
 */
public final class NationManager {

    private static final NationManager INSTANCE = new NationManager();

    private final Map<String, Nation> nations = new LinkedHashMap<>();
    private final Map<UUID, String> playerNations = new LinkedHashMap<>();
    /** Transient shovel selections: player -> two corners. */
    private final Map<UUID, Selection> selections = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    private NationManager() {
    }

    public static NationManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------- storage

    public void load(Path dir) {
        file = dir.resolve("nations.json");
        nations.clear();
        playerNations.clear();
        selections.clear();
        Data data = JsonFiles.read(file, Data.class);
        if (data != null) {
            if (data.nations != null) {
                data.nations.forEach((key, nation) -> nations.put(key, nation));
            }
            if (data.playerNations != null) {
                playerNations.putAll(data.playerNations);
            }
        }
        // Drop dangling player -> nation references.
        playerNations.entrySet().removeIf(entry -> !nations.containsKey(entry.getValue()));
        dirty = false;
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.nations = new LinkedHashMap<>(nations);
        data.playerNations = new LinkedHashMap<>(playerNations);
        JsonFiles.write(file, data);
        dirty = false;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void markDirty() {
        dirty = true;
    }

    // -------------------------------------------------------------- nations

    public Iterable<Nation> nations() {
        return new ArrayList<>(nations.values());
    }

    public int nationCount() {
        return nations.size();
    }

    public Optional<Nation> find(String name) {
        return Optional.ofNullable(nations.get(name.toLowerCase(Locale.ROOT)));
    }

    @Nullable
    public Nation nationOf(UUID player) {
        String key = playerNations.get(player);
        return key == null ? null : nations.get(key);
    }

    public String keyOf(UUID player) {
        return playerNations.get(player);
    }

    public void createNation(Nation nation) {
        nations.put(nation.getKey(), nation);
        setPlayerNation(nation.getOwner(), nation.getKey());
        markDirty();
    }

    public void disband(Nation nation) {
        nations.remove(nation.getKey());
        playerNations.values().removeIf(key -> key.equals(nation.getKey()));
        markDirty();
    }

    public void setPlayerNation(UUID player, @Nullable String nationKey) {
        if (nationKey == null) {
            playerNations.remove(player);
        } else {
            playerNations.put(player, nationKey);
        }
        markDirty();
    }

    // --------------------------------------------------------------- claims

    /** The nation owning the column at (x, z), or {@code null} for wilderness. */
    @Nullable
    public Nation claimAt(String worldId, int x, int z) {
        for (Nation nation : nations.values()) {
            if (nation.getClaims().isEmpty()) {
                continue;
            }
            if (nation.claimAt(worldId, x, z) != null) {
                return nation;
            }
        }
        return null;
    }

    /** Checks whether a proposed claim overlaps any existing claim. */
    public boolean overlaps(String worldId, Claim proposed) {
        for (Nation nation : nations.values()) {
            for (Claim claim : nation.getClaims()) {
                if (claim.intersects(worldId, proposed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The permission check used by every interaction event.
     *
     * @return {@code true} when the action is allowed.
     */
    public boolean canDo(UUID player, String worldId, int x, int z, NationPermission permission) {
        Nation nation = claimAt(worldId, x, z);
        if (nation == null) {
            return true; // wilderness
        }
        return nation.hasPermission(player, permission);
    }

    // ------------------------------------------------------------ selections

    public static final class Selection {
        public String worldId;
        public BlockPos cornerA;
        public BlockPos cornerB;

        public boolean isComplete() {
            return worldId != null && cornerA != null && cornerB != null;
        }
    }

    public Selection selectionOf(UUID player) {
        return selections.computeIfAbsent(player, uuid -> new Selection());
    }

    public void clearSelection(UUID player) {
        selections.remove(player);
    }

    private static final class Data {
        Map<String, Nation> nations = new LinkedHashMap<>();
        Map<UUID, String> playerNations = new LinkedHashMap<>();
    }
}
