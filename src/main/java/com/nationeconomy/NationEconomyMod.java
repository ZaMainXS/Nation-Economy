package com.nationeconomy;

import com.nationeconomy.combat.CombatManager;
import com.nationeconomy.economy.EconomyCommands;
import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.gui.GuideGui;
import com.nationeconomy.nation.BorderNotifier;
import com.nationeconomy.nation.ClaimProtection;
import com.nationeconomy.nation.CoreManager;
import com.nationeconomy.nation.NationChat;
import com.nationeconomy.nation.NationCommands;
import com.nationeconomy.nation.NationManager;
import com.nationeconomy.nation.NationTeams;
import com.nationeconomy.nation.RaidManager;
import com.nationeconomy.shop.ShopCommands;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.util.KnownPlayers;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
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
 *     {@code /sellall}, {@code /sellhelditem}) with an op admin GUI
 *     ({@code /shopadmin}), quick admin commands ({@code /economycategory},
 *     {@code /economyhanditem}, {@code /sreload}),</li>
 *     <li>a nations plugin: create nations, claim land with a golden shovel,
 *     per-player land permissions, nation colors in chat/tab, a territory
 *     map, nation homes,</li>
 *     <li>a combat-tag system (no shop/home while tagged, death on
 *     combat-logging, persistent timers), and</li>
 *     <li>raiding: 1,000 hits per protected block and destroyable nation
 *     cores (10,000 hits) with the craftable Core Healer.</li>
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
            GuideGui.register(dispatcher);
        });

        // ------------------------------------------------------- data files
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            dataDir = server.getWorldPath(new LevelResource(MOD_ID));
            try {
                Files.createDirectories(dataDir);
            } catch (IOException e) {
                LOGGER.error("Could not create data directory {}", dataDir, e);
            }
            KnownPlayers.load(dataDir);
            EconomyManager.get().load(dataDir);
            ShopManager.get().load(dataDir);
            NationManager.get().load(dataDir);
            RaidManager.get().load(dataDir);
            CombatManager.load(dataDir);
            NationTeams.rebuild(server);
            CoreManager.relinkAll(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveAll());

        // --------------------------------------------------------- players
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            KnownPlayers.track(player);
            EconomyManager.get().ensureAccount(player.getUUID());
            NationTeams.applyToPlayer(server, player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BorderNotifier.forget(handler.player.getUUID()));

        // ------------------------------------------------------------ tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BorderNotifier.tick(server);
            CoreManager.tick(server);
            if (server.getTickCount() % AUTOSAVE_INTERVAL == 0) {
                saveDirty();
            }
        });

        // --------------------------------------------------- events & chat
        NationChat.register();
        ClaimProtection.register();
        CombatManager.register();
    }

    private static void saveDirty() {
        KnownPlayers.saveIfDirty();
        EconomyManager.get().saveIfDirty();
        ShopManager.get().saveIfDirty();
        NationManager.get().saveIfDirty();
        RaidManager.get().saveIfDirty();
        CombatManager.saveIfDirty();
    }

    private static void saveAll() {
        KnownPlayers.save();
        EconomyManager.get().save();
        ShopManager.get().save();
        NationManager.get().save();
        RaidManager.get().save();
        CombatManager.save();
        LOGGER.info("Saved Nation & Economy data.");
    }
}
