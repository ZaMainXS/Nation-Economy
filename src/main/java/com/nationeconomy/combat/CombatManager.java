package com.nationeconomy.combat;

import com.nationeconomy.util.JsonFiles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The combat-tag system. When two players fight, both are tagged for
 * {@value #TAG_SECONDS} seconds. While tagged a player:
 * <ul>
 *     <li>cannot buy anything from the shop,</li>
 *     <li>cannot use {@code /nation home},</li>
 *     <li>dies and drops their items when intentionally logging out.</li>
 * </ul>
 *
 * Tags are persisted to {@code <world>/nationeconomy/combattags.json}, so a
 * player whose game crashes keeps the timer running while they reconnect.
 * (A graceful quit vs. a network lag-out cannot be told apart on the server,
 * so the timer — and the logout penalty — treat them the same; server
 * shutdowns never kill players.)
 */
public final class CombatManager {

    public static final long TAG_SECONDS = 30;
    private static final long TAG_MILLIS = TAG_SECONDS * 1000L;

    private static final Map<UUID, Long> TAGGED_UNTIL = new LinkedHashMap<>();
    private static volatile boolean serverStopping = false;
    private static Path file;
    private static boolean dirty;

    private CombatManager() {
    }

    // ------------------------------------------------------------- events

    public static void register() {
        serverStopping = false;

        // Tag both players on PvP damage (melee, arrows, anything).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim
                    && source.getAttacker() instanceof ServerPlayer attacker
                    && !attacker.getUUID().equals(victim.getUUID())) {
                tag(attacker);
                tag(victim);
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (isTagged(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You are still in combat for " + secondsLeft(player.getUUID())
                        + "s. Logging out means death!").withStyle(ChatFormatting.RED), false);
            }
        });

        // Combat logging: die and drop your stuff.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (!isTagged(player.getUUID()) || serverStopping) {
                return;
            }
            untag(player.getUUID());
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            LivingEntity living = player;
            ServerLevel world = (ServerLevel) player.level();
            world.getServer().getPlayerList().broadcastSystemMessage(Component.empty()
                    .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" logged out during combat and died!").withStyle(ChatFormatting.GRAY)), false);
            living.hurt(world, living.damageSources().generic(), Float.MAX_VALUE);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> serverStopping = true);

        // Action bar countdown + expiry.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                long left = taggedMillisLeft(player.getUUID());
                if (left < 0) {
                    sendFeedbackIfExpires(player);
                } else if (left > 0) {
                    player.sendSystemMessage(Component.literal("⚔ In combat: " + ((left + 999) / 1000) + "s")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        });
    }

    /** Notifies a player the moment their tag runs out. */
    private static void sendFeedbackIfExpires(ServerPlayer player) {
        if (TAGGED_UNTIL.remove(player.getUUID()) != null) {
            dirty = true;
            player.sendSystemMessage(Component.literal("You are no longer in combat.").withStyle(ChatFormatting.GREEN), false);
        }
    }

    // --------------------------------------------------------------- logic

    public static void tag(ServerPlayer player) {
        TAGGED_UNTIL.put(player.getUUID(), System.currentTimeMillis() + TAG_MILLIS);
        dirty = true;
    }

    public static void untag(UUID uuid) {
        if (TAGGED_UNTIL.remove(uuid) != null) {
            dirty = true;
        }
    }

    public static boolean isTagged(UUID uuid) {
        return taggedMillisLeft(uuid) > 0;
    }

    public static long taggedMillisLeft(UUID uuid) {
        Long until = TAGGED_UNTIL.get(uuid);
        if (until == null) {
            return -1;
        }
        return until - System.currentTimeMillis();
    }

    public static long secondsLeft(UUID uuid) {
        return Math.max(0, (taggedMillisLeft(uuid) + 999) / 1000);
    }

    /** Denies an action during combat, telling the player why. */
    public static boolean denyIfTagged(ServerPlayer player, String action) {
        if (!isTagged(player.getUUID())) {
            return false;
        }
        player.sendSystemMessage(Component.literal("You can't " + action + " while in combat! (" +
                secondsLeft(player.getUUID()) + "s left)").withStyle(ChatFormatting.RED), false);
        return true;
    }

    // ------------------------------------------------------------- storage

    public static void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        long now = System.currentTimeMillis();
        TAGGED_UNTIL.forEach((uuid, until) -> {
            if (until > now) {
                data.tags.put(uuid, until);
            }
        });
        JsonFiles.write(file, data);
        dirty = false;
    }

    public static void load(Path dataDir) {
        file = dataDir.resolve("combattags.json");
        TAGGED_UNTIL.clear();
        Data data = JsonFiles.read(file, Data.class);
        if (data != null && data.tags != null) {
            long now = System.currentTimeMillis();
            data.tags.forEach((uuid, until) -> {
                if (until > now) {
                    TAGGED_UNTIL.put(uuid, until);
                }
            });
        }
        dirty = false;
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private static final class Data {
        Map<UUID, Long> tags = new LinkedHashMap<>();
    }
}
