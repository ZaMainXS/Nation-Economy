package com.nationeconomy.nation;

import com.nationeconomy.util.JsonFiles;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks raid progress: every block inside claimed land needs
 * {@value #RAID_HITS} hits before it actually breaks for a raider.
 * Persisted to {@code <world>/nationeconomy/raids.json}.
 */
public final class RaidManager {

    public static final int RAID_HITS = 1000;

    private static final RaidManager INSTANCE = new RaidManager();

    /** world id -> (packed block pos -> hits). All attackers contribute to the same counter. */
    private final Map<String, Map<Long, Integer>> hits = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    private RaidManager() {
    }

    public static RaidManager get() {
        return INSTANCE;
    }

    public void load(Path dir) {
        file = dir.resolve("raids.json");
        hits.clear();
        Data data = JsonFiles.read(file, Data.class);
        if (data != null && data.hits != null) {
            data.hits.forEach((world, map) -> {
                Map<Long, Integer> packed = new HashMap<>();
                map.forEach((pos, count) -> packed.put(pos, count));
                hits.put(world, packed);
            });
        }
        dirty = false;
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        hits.forEach((world, map) -> data.hits.put(world, new LinkedHashMap<>(map)));
        JsonFiles.write(file, data);
        dirty = false;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** Adds one raid hit and returns the new total for that block. */
    public int hit(String worldId, BlockPos pos) {
        Map<Long, Integer> worldHits = hits.computeIfAbsent(worldId, w -> new HashMap<>());
        long packed = pos.asLong();
        int total = worldHits.getOrDefault(packed, 0) + 1;
        worldHits.put(packed, total);
        dirty = true;
        return total;
    }

    public int hitsAt(String worldId, BlockPos pos) {
        Map<Long, Integer> worldHits = hits.get(worldId);
        return worldHits == null ? 0 : worldHits.getOrDefault(pos.asLong(), 0);
    }

    public void clear(String worldId, BlockPos pos) {
        Map<Long, Integer> worldHits = hits.get(worldId);
        if (worldHits != null && worldHits.remove(pos.asLong()) != null) {
            dirty = true;
        }
    }

    /** Number of raided blocks in a world (for stats/debug). */
    public int size(String worldId) {
        Map<Long, Integer> worldHits = hits.get(worldId);
        return worldHits == null ? 0 : worldHits.size();
    }

    private static final class Data {
        Map<String, Map<Long, Integer>> hits = new LinkedHashMap<>();
    }
}
