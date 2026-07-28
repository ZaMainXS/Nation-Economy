package com.nationeconomy;

import com.nationeconomy.economy.EconomyCommands;
import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.nation.BorderNotifier;
import com.nationeconomy.nation.ClaimProtection;
import com.nationeconomy.nation.NationChat;
import com.nationeconomy.nation.NationCommands;
import com.nationeconomy.nation.NationManager;
import com.nationeconomy.nation.NationTeams;
import com.nationeconomy.shop.ShopCommands;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.util.KnownPlayers;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Nation &amp; Economy — a server-side mod for Fabric 1.21.11 combining
 *
 * <ul>
 *     <li>an EconomyShopGUI-style shop ({@code /shop}, {@code /sell},
 *     {@code /sellall}, {@code /sellhelditem}) with op-managed categories, and</li>
 *     <li>a nations plugin: create nations, claim land with a golden shovel,
 *     per-player land permissions, nation colors shown in chat/tab, and a
 *     territory map.</li>
 * </ul>
 *
 * Everything runs on the server; vanilla clients can connect.
 */
public class NationEconomyMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "nationeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger("Nation & Economy");

    /** Save data every 5 minutes (in ticks) when something changed. */
    private static final int AUTOSAVE_INTERVAL = 20 * 60 * 5;

    private static Path dataDir;

    public static Path dataDir() {
        return dataDir;
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("Loading Nation & Economy...");

        // ---------------------------------------------------------- commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher, registryAccess);
            ShopCommands.register(dispatcher, registryAccess);
            NationCommands.register(dispatcher, registryAccess);
        });

        // ------------------------------------------------------- data files
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            dataDir = server.getSavePath(WorldSavePath.ROOT).resolve(MOD_ID);
            try {
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                LOGGER.error("Could not create data directory {}", dataDir, e);
            }
            KnownPlayers.load(dataDir);
            EconomyManager.get().load(dataDir);
            ShopManager.get().load(dataDir);
            NationManager.get().load(dataDir);
            NationTeams.rebuild(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveAll());

        // --------------------------------------------------------- players
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            KnownPlayers.track(player);
            EconomyManager.get().ensureAccount(player.getUuid());
            NationTeams.applyToPlayer(server, player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BorderNotifier.forget(handler.getPlayer().getUuid()));

        // ------------------------------------------------------------ tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BorderNotifier.tick(server);
            if (server.getTicks() % AUTOSAVE_INTERVAL == 0) {
                saveDirty();
            }
        });

        // --------------------------------------------------- events & chat
        NationChat.register();
        ClaimProtection.register();
    }

    private static void saveDirty() {
        KnownPlayers.saveIfDirty();
        EconomyManager.get().saveIfDirty();
        ShopManager.get().saveIfDirty();
        NationManager.get().saveIfDirty();
    }

    private static void saveAll() {
        KnownPlayers.save();
        EconomyManager.get().save();
        ShopManager.get().save();
        NationManager.get().save();
        LOGGER.info("Saved Nation & Economy data.");
    }
}
