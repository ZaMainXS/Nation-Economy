package com.nationeconomy.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent name &lt;-&gt; UUID cache for every player that ever joined.
 * Lets commands like {@code /eco give}, {@code /nation allow access} and
 * {@code /baltop} work with offline players without depending on the vanilla
 * user cache internals.
 */
public final class KnownPlayers {

    private static final Map<UUID, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, UUID> BY_LOWER_NAME = new LinkedHashMap<>();
    private static Path file;
    private static boolean dirty;

    private KnownPlayers() {
    }

    // ------------------------------------------------------------- storage

    public static void load(Path dir) {
        file = dir.resolve("players.json");
        NAMES.clear();
        BY_LOWER_NAME.clear();
        Data data = JsonFiles.read(file, Data.class);
        if (data != null && data.players != null) {
            data.players.forEach(KnownPlayers::index);
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.players = new LinkedHashMap<>(NAMES);
        JsonFiles.write(file, data);
        dirty = false;
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    // -------------------------------------------------------------- lookup

    /** Remembers a player (called on join). */
    public static void track(ServerPlayer player) {
        index(player.getUUID(), player.getName().getString());
        dirty = true;
    }

    private static void index(UUID uuid, String name) {
        NAMES.put(uuid, name);
        BY_LOWER_NAME.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    /** Display name of a player, or {@code null} when never seen. */
    @Nullable
    public static String nameOf(UUID uuid) {
        return NAMES.get(uuid);
    }

    /** UUID of a player by name, or empty when never seen. */
    public static Optional<UUID> uuidOf(String name) {
        return Optional.ofNullable(BY_LOWER_NAME.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Resolves a command target: prefers online players, then falls back to
     * the known-player cache. Works for offline players that joined before.
     */
    public static Optional<GameProfile> resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        return uuidOf(name).map(uuid -> new GameProfile(uuid, nameOf(uuid)));
    }

    /** All known names, used for suggestion lists. */
    public static Collection<String> names() {
        return NAMES.values();
    }

    private static final class Data {
        Map<UUID, String> players = new LinkedHashMap<>();
    }
}
