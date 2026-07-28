package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
    public static ArmorStandEntity spawnCore(ServerWorld world, Nation nation) {
        if (!nation.hasCore()) {
            return null;
        }
        ArmorStandEntity stand = new ArmorStandEntity(world, nation.getCoreX(), nation.getCoreY(), nation.getCoreZ());
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setNoGravity(true);
        stand.setSmall(true);
        stand.setHideBasePlate(true);
        stand.setGlowing(true);
        stand.equipStack(EquipmentSlot.HEAD, CoreItems.coreOrb());
        updateName(stand, nation);
        stand.setCustomNameVisible(true);
        world.spawnEntity(stand);
        nation.setCoreEntityUuid(stand.getUuid());
        return stand;
    }

    /** (Re)creates the core of a freshly founded nation at the given position. */
    public static void createCore(ServerWorld world, Nation nation, BlockPos pos) {
        removeCore(world.getServer(), nation);
        nation.placeCore(world.getRegistryKey().getValue().toString(),
                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        spawnCore(world, nation);
    }

    /** Removes the core entities of a nation (disband/defeat). */
    public static void removeCore(@Nullable MinecraftServer server, Nation nation) {
        if (server == null) {
            return;
        }
        ServerWorld world = coreWorld(server, nation);
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
            ServerWorld world = coreWorld(server, nation);
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
    private static ServerWorld coreWorld(MinecraftServer server, Nation nation) {
        if (!nation.hasCore()) {
            return null;
        }
        Identifier id = Identifier.tryParse(nation.getCoreWorld());
        if (id == null) {
            return null;
        }
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    // ------------------------------------------------------------- lookups

    /** The nation owning a core entity, or {@code null}. */
    @Nullable
    public static Nation coreNation(Entity entity) {
        UUID uuid = entity.getUuid();
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
    public static boolean handleAttack(ServerPlayerEntity attacker, Entity entity) {
        Nation nation = coreNation(entity);
        if (nation == null) {
            return false;
        }
        MinecraftServer server = attacker.server;

        if (nation.isMember(attacker.getUuid())) {
            attacker.sendMessage(Text.literal("This is your nation's core (" + nation.getCoreHits() + "/"
                            + Nation.MAX_CORE_HITS + "). Heal it with a Core Healer!")
                    .formatted(Formatting.AQUA), true);
            return true;
        }

        nation.damageCore(attacker.getUuid());
        int hits = nation.getCoreHits();

        if (hits <= 0) {
            defeat(server, nation, entity);
            return true;
        }

        if (entity instanceof ArmorStandEntity) {
            updateName(entity, nation);
        }
        if (hits % 500 == 0 || hits <= 100) {
            attacker.sendMessage(Text.literal("Core: " + hits + "/" + Nation.MAX_CORE_HITS)
                    .formatted(Formatting.RED), true);
        } else if (hits % 100 == 0) {
            attacker.sendMessage(Text.literal("Damaging core: " + hits + "/" + Nation.MAX_CORE_HITS)
                    .formatted(Formatting.GOLD), true);
        }
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CRIT,
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
    public static boolean handleUse(ServerPlayerEntity player, Entity entity, Hand hand) {
        Nation nation = coreNation(entity);
        if (nation == null) {
            return false;
        }

        ItemStack held = player.getStackInHand(hand);
        if (!CoreItems.isCoreHealer(held)) {
            player.sendMessage(Text.literal(nation.getName() + "'s core: " + nation.getCoreHits() + "/"
                    + Nation.MAX_CORE_HITS + " hits.").formatted(Formatting.GRAY), true);
            return true;
        }
        if (!nation.isMember(player.getUuid())) {
            player.sendMessage(Text.literal("Only members of " + nation.getName() + " can heal its core.")
                    .formatted(Formatting.RED), false);
            return true;
        }

        held.decrement(1);
        nation.healCore(HEAL_AMOUNT);
        if (entity instanceof ArmorStandEntity) {
            updateName(entity, nation);
        }
        player.sendMessage(Text.literal("Restored " + HEAL_AMOUNT + " core hits (" + nation.getCoreHits() + "/"
                + Nation.MAX_CORE_HITS + ").").formatted(Formatting.GREEN), false);
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
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
        var line = Text.empty()
                .append(Text.literal("⚔ ").formatted(Formatting.GOLD))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(" has been defeated by ").formatted(Formatting.GRAY))
                .append(Text.literal(attackerName).formatted(Formatting.RED));
        if (attackerNation != null && attackerNation != nation) {
            line.append(Text.literal(" of ").formatted(Formatting.GRAY))
                    .append(ColorUtils.colored(attackerNation.getName(), attackerNation.getRgb()));
        }
        line.append(Text.literal("!").formatted(Formatting.GRAY));
        server.getPlayerManager().broadcast(line, false);

        // Trophy drop on the conquered core.
        if (coreEntity.getWorld() instanceof ServerWorld world) {
            world.spawnEntity(new net.minecraft.entity.ItemEntity(world,
                    coreEntity.getX(), coreEntity.getY(), coreEntity.getZ(),
                    new ItemStack(Items.NETHER_STAR)));
        }

        // Tear the nation down (same cleanup as /nation disband confirm).
        coreEntity.discard();
        nation.clearCore();
        manager.disband(nation);
        NationTeams.removeTeam(server, nation);
        for (UUID member : nation.getMembers()) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(member);
            if (online != null) {
                NationTeams.applyToPlayer(server, online);
                online.sendMessage(Text.literal("Your nation has fallen.").formatted(Formatting.RED), false);
            }
        }
        manager.save();
    }

    // ------------------------------------------------------------ visuals

    private static void updateName(Entity entity, Nation nation) {
        entity.setCustomName(Text.empty()
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(" — Nation Core (" + nation.getCoreHits() + "/" + Nation.MAX_CORE_HITS + ")")
                        .formatted(Formatting.GOLD)));
    }

    /** Ambient particles around all cores (called regularly). */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 6) {
            return;
        }
        for (Nation nation : NationManager.get().nations()) {
            if (!nation.hasCore()) {
                continue;
            }
            ServerWorld world = coreWorld(server, nation);
            if (world == null) {
                continue;
            }
            world.spawnParticles(ParticleTypes.ENCHANT,
                    nation.getCoreX(), nation.getCoreY() + 0.5, nation.getCoreZ(), 4, 0.35, 0.3, 0.35, 0.8);
        }
    }
}
