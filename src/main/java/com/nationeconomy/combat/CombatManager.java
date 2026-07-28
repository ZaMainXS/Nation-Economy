package com.nationeconomy.combat;

import com.nationeconomy.util.JsonFiles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
            if (entity instanceof ServerPlayerEntity victim
                    && source.getAttacker() instanceof ServerPlayerEntity attacker
                    && !attacker.getUuid().equals(victim.getUuid())) {
                tag(attacker);
                tag(victim);
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (isTagged(player.getUuid())) {
                player.sendMessage(Text.literal("You are still in combat for " + secondsLeft(player.getUuid())
                        + "s. Logging out means death!").formatted(Formatting.RED), false);
            }
        });

        // Combat logging: die and drop your stuff.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (!isTagged(player.getUuid()) || serverStopping) {
                return;
            }
            untag(player.getUuid());
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            LivingEntity living = player;
            ServerWorld world = (ServerWorld) player.getWorld();
            world.getServer().getPlayerManager().broadcast(Text.empty()
                    .append(Text.literal(player.getName().getString()).formatted(Formatting.RED))
                    .append(Text.literal(" logged out during combat and died!").formatted(Formatting.GRAY)), false);
            living.damage(world, living.getDamageSources().generic(), Float.MAX_VALUE);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> serverStopping = true);

        // Action bar countdown + expiry.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                long left = taggedMillisLeft(player.getUuid());
                if (left < 0) {
                    sendFeedbackIfExpires(player);
                } else if (left > 0) {
                    player.sendMessage(Text.literal("⚔ In combat: " + ((left + 999) / 1000) + "s")
                            .formatted(Formatting.RED), true);
                }
            }
        });
    }

    /** Notifies a player the moment their tag runs out. */
    private static void sendFeedbackIfExpires(ServerPlayerEntity player) {
        if (TAGGED_UNTIL.remove(player.getUuid()) != null) {
            dirty = true;
            player.sendMessage(Text.literal("You are no longer in combat.").formatted(Formatting.GREEN), false);
        }
    }

    // --------------------------------------------------------------- logic

    public static void tag(ServerPlayerEntity player) {
        TAGGED_UNTIL.put(player.getUuid(), System.currentTimeMillis() + TAG_MILLIS);
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
    public static boolean denyIfTagged(ServerPlayerEntity player, String action) {
        if (!isTagged(player.getUuid())) {
            return false;
        }
        player.sendMessage(Text.literal("You can't " + action + " while in combat! (" +
                secondsLeft(player.getUuid()) + "s left)").formatted(Formatting.RED), false);
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
