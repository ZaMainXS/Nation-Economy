package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The nation core: an invulnerable, invisible armor stand holding a glowing
 * nether star — the "orb levitating above a block" that every nation gets on
 * creation.
 *
 * <ul>
 *     <li>Outsiders raid it: every melee hit counts ({@link Nation#MAX_CORE_HITS}
 *     to destroy it). Reaching zero disbands the nation and announces the
 *     conqueror server-wide.</li>
 *     <li>Members heal it by right-clicking it with a Core Healer
 *     (craftable item, see the bundled recipe) — +500 hits.</li>
 *     <li>The core entity is re-bound (or respawned) on every server start.</li>
 * </ul>
 */
public final class CoreManager {

    /** Heal amount of one Core Healer use. */
    public static final int HEAL_AMOUNT = 500;

    private CoreManager() {
    }

    // ------------------------------------------------------------ spawning

    /** Spawns the core entity at the stored position of a nation. */
    @Nullable
    public static ArmorStand spawnCore(ServerLevel world, Nation nation) {
        if (!nation.hasCore()) {
            return null;
        }
        ArmorStand stand = new ArmorStand(world, nation.getCoreX(), nation.getCoreY(), nation.getCoreZ());
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setNoGravity(true);
        stand.setSmall(true);
        stand.setNoBasePlate(true);
        stand.setGlowingTag(true);
        stand.setItemSlot(EquipmentSlot.HEAD, CoreItems.coreOrb());
        updateName(stand, nation);
        stand.setCustomNameVisible(true);
        world.addFreshEntity(stand);
        nation.setCoreEntityUuid(stand.getUUID());
        return stand;
    }

    /** (Re)creates the core of a freshly founded nation at the given position. */
    public static void createCore(ServerLevel world, Nation nation, BlockPos pos) {
        removeCore(world.getServer(), nation);
        nation.placeCore(world.dimension().location().toString(),
                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        spawnCore(world, nation);
    }

    /** Removes the core entities of a nation (disband/defeat). */
    public static void removeCore(@Nullable MinecraftServer server, Nation nation) {
        if (server == null) {
            return;
        }
        ServerLevel world = coreWorld(server, nation);
        if (world != null && nation.getCoreEntityUuid() != null) {
            Entity entity = world.getEntity(nation.getCoreEntityUuid());
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** On server start: find every core entity, respawn it when it went missing. */
    public static void relinkAll(MinecraftServer server) {
        for (Nation nation : NationManager.get().nations()) {
            if (!nation.hasCore()) {
                continue;
            }
            ServerLevel world = coreWorld(server, nation);
            if (world == null) {
                continue;
            }
            Entity found = nation.getCoreEntityUuid() == null ? null : world.getEntity(nation.getCoreEntityUuid());
            if (found == null) {
                spawnCore(world, nation);
            } else {
                updateName(found, nation);
            }
        }
    }

    @Nullable
    private static ServerLevel coreWorld(MinecraftServer server, Nation nation) {
        if (!nation.hasCore()) {
            return null;
        }
        Identifier id = Identifier.tryParse(nation.getCoreWorld());
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ------------------------------------------------------------- lookups

    /** The nation owning a core entity, or {@code null}. */
    @Nullable
    public static Nation coreNation(Entity entity) {
        UUID uuid = entity.getUUID();
        for (Nation nation : NationManager.get().nations()) {
            if (uuid.equals(nation.getCoreEntityUuid())) {
                return nation;
            }
        }
        return null;
    }

    public static boolean isCore(Entity entity) {
        return coreNation(entity) != null;
    }

    // ------------------------------------------------------- attack & heal

    /**
     * Handles a hit on a core entity.
     *
     * @return {@code true} when the entity was a core (caller must cancel the
     * interaction).
     */
    public static boolean handleAttack(ServerPlayer attacker, Entity entity) {
        Nation nation = coreNation(entity);
        if (nation == null) {
            return false;
        }
        MinecraftServer server = attacker.getServer();

        if (nation.isMember(attacker.getUUID())) {
            attacker.sendSystemMessage(Component.literal("This is your nation's core (" + nation.getCoreHits() + "/"
                            + Nation.MAX_CORE_HITS + "). Heal it with a Core Healer!")
                    .withStyle(ChatFormatting.AQUA), true);
            return true;
        }

        nation.damageCore(attacker.getUUID());
        int hits = nation.getCoreHits();

        if (hits <= 0) {
            defeat(server, nation, entity);
            return true;
        }

        if (entity instanceof ArmorStand) {
            updateName(entity, nation);
        }
        if (hits % 500 == 0 || hits <= 100) {
            attacker.sendSystemMessage(Component.literal("Core: " + hits + "/" + Nation.MAX_CORE_HITS)
                    .withStyle(ChatFormatting.RED), true);
        } else if (hits % 100 == 0) {
            attacker.sendSystemMessage(Component.literal("Damaging core: " + hits + "/" + Nation.MAX_CORE_HITS)
                    .withStyle(ChatFormatting.GOLD), true);
        }
        if (entity.level() instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 0.6, entity.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        }
        NationManager.get().markDirty();
        return true;
    }

    /**
     * Handles a right-click on a core entity (Core Healer usage).
     *
     * @return {@code true} when the entity was a core (caller must cancel the
     * interaction).
     */
    public static boolean handleUse(ServerPlayer player, Entity entity, InteractionHand hand) {
        Nation nation = coreNation(entity);
        if (nation == null) {
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!CoreItems.isCoreHealer(held)) {
            player.sendSystemMessage(Component.literal(nation.getName() + "'s core: " + nation.getCoreHits() + "/"
                    + Nation.MAX_CORE_HITS + " hits.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (!nation.isMember(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only members of " + nation.getName() + " can heal its core.")
                    .withStyle(ChatFormatting.RED), false);
            return true;
        }

        held.shrink(1);
        nation.healCore(HEAL_AMOUNT);
        if (entity instanceof ArmorStand) {
            updateName(entity, nation);
        }
        player.sendSystemMessage(Component.literal("Restored " + HEAL_AMOUNT + " core hits (" + nation.getCoreHits() + "/"
                + Nation.MAX_CORE_HITS + ").").withStyle(ChatFormatting.GREEN), false);
        if (entity.level() instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX(), entity.getY() + 0.6, entity.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
        }
        NationManager.get().markDirty();
        return true;
    }

    // -------------------------------------------------------------- defeat

    /** Core destroyed: disband the nation and announce the conqueror. */
    private static void defeat(MinecraftServer server, Nation nation, Entity coreEntity) {
        NationManager manager = NationManager.get();

        UUID attackerId = nation.topCoreAttacker();
        String attackerName = attackerId == null ? "raiders"
                : Optional.ofNullable(KnownPlayers.nameOf(attackerId)).orElse("raiders");
        Nation attackerNation = attackerId == null ? null : manager.nationOf(attackerId);

        // Announce who defeated whom.
        var line = Component.empty()
                .append(Component.literal("⚔ ").withStyle(ChatFormatting.GOLD))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(" has been defeated by ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(attackerName).withStyle(ChatFormatting.RED));
        if (attackerNation != null && attackerNation != nation) {
            line.append(Component.literal(" of ").withStyle(ChatFormatting.GRAY))
                    .append(ColorUtils.colored(attackerNation.getName(), attackerNation.getRgb()));
        }
        line.append(Component.literal("!").withStyle(ChatFormatting.GRAY));
        server.getPlayerList().broadcastSystemMessage(line, false);

        // Trophy drop on the conquered core.
        if (coreEntity.level() instanceof ServerLevel world) {
            world.addFreshEntity(new ItemEntity(world,
                    coreEntity.getX(), coreEntity.getY(), coreEntity.getZ(),
                    new ItemStack(Items.NETHER_STAR)));
        }

        // Tear the nation down (same cleanup as /nation disband confirm).
        coreEntity.discard();
        nation.clearCore();
        manager.disband(nation);
        NationTeams.removeTeam(server, nation);
        for (UUID member : nation.getMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                NationTeams.applyToPlayer(server, online);
                online.sendSystemMessage(Component.literal("Your nation has fallen.").withStyle(ChatFormatting.RED), false);
            }
        }
        manager.save();
    }

    // ------------------------------------------------------------ visuals

    private static void updateName(Entity entity, Nation nation) {
        entity.setCustomName(Component.empty()
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(" — Nation Core (" + nation.getCoreHits() + "/" + Nation.MAX_CORE_HITS + ")")
                        .withStyle(ChatFormatting.GOLD)));
    }

    /** Ambient particles around all cores (called regularly). */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 6) {
            return;
        }
        for (Nation nation : NationManager.get().nations()) {
            if (!nation.hasCore()) {
                continue;
            }
            ServerLevel world = coreWorld(server, nation);
            if (world == null) {
                continue;
            }
            world.sendParticles(ParticleTypes.ENCHANT,
                    nation.getCoreX(), nation.getCoreY() + 0.5, nation.getCoreZ(), 4, 0.35, 0.3, 0.35, 0.8);
        }
    }
}
