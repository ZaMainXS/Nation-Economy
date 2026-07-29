package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.commands.Commands;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.ChatFormatting;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The land-protection layer. Everything a player can do inside claimed land
 * is routed through {@link NationManager#canDo}:
 *
 * <ul>
 *     <li>breaking blocks -> {@link NationPermission#BREAK}</li>
 *     <li>placing blocks/fluids/boats -> {@link NationPermission#PLACE}</li>
 *     <li>opening chests & other containers -> {@link NationPermission#CHEST}</li>
 *     <li>doors, buttons, levers, entities, ... -> {@link NationPermission#USE}</li>
 * </ul>
 *
 * Players without an invite ({@code /nation allow access}) can do nothing
 * inside foreign land. Server ops (permission level 3+) bypass.
 *
 * This class also handles the claim-shovel corner selection.
 */
public final class ClaimProtection {

    /** Rate-limits "protected" messages so chat isn't spammed. */
    private static final Map<UUID, Long> LAST_DENY_MESSAGE = new ConcurrentHashMap<>();

    private ClaimProtection() {
    }

    public static void register() {
        AttackBlockCallback.EVENT.register(ClaimProtection::onAttackBlock);
        UseBlockCallback.EVENT.register(ClaimProtection::onUseBlock);
        UseItemCallback.EVENT.register(ClaimProtection::onUseItem);
        UseEntityCallback.EVENT.register(ClaimProtection::onUseEntity);
        AttackEntityCallback.EVENT.register(ClaimProtection::onAttackEntity);
    }

    // ------------------------------------------------------------ breaking

    private static InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // Claim shovel: left click selects corner 1.
        if (ClaimTool.isClaimTool(player.getMainHandItem())) {
            selectCorner(serverPlayer, world, pos, true);
            return InteractionResult.FAIL; // never break blocks with the tool
        }

        String worldId = world.dimension().identifier().toString();
        Nation nation = NationManager.get().claimAt(worldId, pos.getX(), pos.getZ());
        if (nation == null) {
            return InteractionResult.PASS; // wilderness
        }
        if (nation.hasPermission(serverPlayer.getUUID(), NationPermission.BREAK) || isOperatorBypass(serverPlayer)) {
            return InteractionResult.PASS;
        }

        // Raiding: outsiders can break in, but every block takes 1000 hits.
        return raidHit(serverPlayer, world, pos, nation);
    }

    /** Counts one raid hit against a protected block; breaks it after {@link RaidManager#RAID_HITS}. */
    private static InteractionResult raidHit(ServerPlayer player, Level world, BlockPos pos, Nation nation) {
        String worldId = world.dimension().identifier().toString();

        if (world.getBlockState(pos).getDestroySpeed(world, pos) < 0) {
            player.sendSystemMessage(Component.literal("This block cannot be raided.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        int hits = RaidManager.get().hit(worldId, pos);
        if (hits >= RaidManager.RAID_HITS) {
            RaidManager.get().clear(worldId, pos);
            world.destroyBlock(pos, true);
            player.sendSystemMessage(Component.literal("You broke through " + nation.getName() + "'s defenses!")
                    .withStyle(ChatFormatting.GOLD), false);
            return InteractionResult.FAIL;
        }

        if (hits == 1 || hits % 25 == 0 || hits >= RaidManager.RAID_HITS - 10) {
            player.sendSystemMessage(Component.literal("Raiding " + nation.getName() + ": " + hits + "/"
                    + RaidManager.RAID_HITS + " hits").withStyle(ChatFormatting.RED), true);
        }
        if (hits % 25 == 0 && world instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ParticleTypes.CRIT,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.02);
        }
        return InteractionResult.FAIL;
    }

    // -------------------------------------------------------- block usage

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        ItemStack held = player.getItemInHand(hand);

        // Claim shovel: right click selects corner 2.
        if (hand == InteractionHand.MAIN_HAND && ClaimTool.isClaimTool(held)) {
            selectCorner(serverPlayer, world, pos, false);
            return InteractionResult.FAIL;
        }

        NationPermission permission = classifyBlockUse(player, world, pos, held);
        if (permission == null) {
            return InteractionResult.PASS; // clicking a plain block with an empty hand does nothing anyway
        }
        if (allowed(serverPlayer, world, pos, permission)) {
            return InteractionResult.PASS;
        }
        deny(serverPlayer, world, pos);
        return InteractionResult.FAIL;
    }

    /**
     * Decides which permission a right-click-on-block falls under,
     * or {@code null} when the interaction is meaningless.
     */
    @Nullable
    private static NationPermission classifyBlockUse(Player player, Level world, BlockPos pos, ItemStack held) {
        Block block = world.getBlockState(pos).getBlock();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // Containers (chests, barrels, furnaces, hoppers, ...) need CHEST.
        boolean container = blockEntity instanceof Container;
        if (container) {
            // Only an actual sneak-placement (placeable in hand) bypasses the
            // "open" behaviour — sneak-clicking with an empty hand still opens
            // the container in vanilla, and would otherwise slip through here.
            if (player.isShiftKeyDown() && held.getItem() instanceof BlockItem) {
                return NationPermission.PLACE;
            }
            return NationPermission.CHEST;
        }
        // Placing a block against an existing block (also while sneaking past a usable block).
        if (held.getItem() instanceof BlockItem) {
            return NationPermission.PLACE;
        }
        if (isInteractive(block)) {
            return NationPermission.USE;
        }
        return null;
    }

    /** Blocks that "do something" on right click without being a container. */
    private static boolean isInteractive(Block block) {
        return block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof TrapDoorBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof SignBlock || block instanceof BedBlock
                || block instanceof CraftingTableBlock || block instanceof AnvilBlock
                || block instanceof StonecutterBlock || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock || block instanceof LoomBlock
                || block instanceof EnchantingTableBlock || block instanceof BellBlock
                || block instanceof NoteBlock || block instanceof JukeboxBlock
                || block instanceof BeaconBlock || block instanceof DaylightDetectorBlock
                || block instanceof DiodeBlock || block instanceof CakeBlock
                || block instanceof DragonEggBlock || block instanceof FlowerPotBlock
                || block instanceof LecternBlock || block instanceof RespawnAnchorBlock
                || block instanceof SmithingTableBlock || block instanceof CampfireBlock;
    }

    // --------------------------------------------------------- item usage

    private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Buckets and boats aimed through the air: find the destination block
        // with a raycast and require PLACE there.
        if (stack.getItem() instanceof BucketItem || stack.getItem() instanceof BoatItem) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getViewVector(1.0F).scale(5.0));
            BlockHitResult hit = world.clip(new ClipContext(start, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            if (hit.getType() == BlockHitResult.Type.BLOCK) {
                BlockPos target = hit.getBlockPos().relative(hit.getDirection());
                if (!allowed(serverPlayer, world, target, NationPermission.PLACE)) {
                    deny(serverPlayer, world, target);
                    return InteractionResult.FAIL;
                }
            }
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------ entities

    private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity,
                                            @Nullable EntityHitResult hitResult) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Nation core: right-click with a Core Healer (or peek at its health).
        if (CoreManager.handleUse(serverPlayer, entity, hand)) {
            return InteractionResult.FAIL;
        }
        if (allowed(serverPlayer, world, entity.blockPosition(), NationPermission.USE)) {
            return InteractionResult.PASS;
        }
        deny(serverPlayer, world, entity.blockPosition());
        return InteractionResult.FAIL;
    }

    private static InteractionResult onAttackEntity(Player player, Level world, InteractionHand hand, Entity entity,
                                               @Nullable EntityHitResult hitResult) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Nation core: raid hits count towards destroying it.
        if (CoreManager.handleAttack(serverPlayer, entity)) {
            return InteractionResult.FAIL;
        }
        if (allowed(serverPlayer, world, entity.blockPosition(), NationPermission.USE)) {
            return InteractionResult.PASS;
        }
        deny(serverPlayer, world, entity.blockPosition());
        return InteractionResult.FAIL;
    }

    // ------------------------------------------------------------- helpers

    /** Permission check including the operator bypass. */
    private static boolean allowed(ServerPlayer player, Level world, BlockPos pos, NationPermission permission) {
        if (isOperatorBypass(player)) {
            return true;
        }
        String worldId = world.dimension().identifier().toString();
        return NationManager.get().canDo(player.getUUID(), worldId, pos.getX(), pos.getZ(), permission);
    }

    private static boolean isOperatorBypass(ServerPlayer player) {
        return Commands.LEVEL_ADMINS.check(player.permissions());
    }

    /** Sends a rate-limited "this land is protected" message. */
    private static void deny(ServerPlayer player, Level world, BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
        if (last != null && now - last < 1000) {
            return;
        }
        LAST_DENY_MESSAGE.put(player.getUUID(), now);

        String worldId = world.dimension().identifier().toString();
        Nation nation = NationManager.get().claimAt(worldId, pos.getX(), pos.getZ());
        Component message = nation != null
                ? Component.literal("Protected by ").withStyle(ChatFormatting.RED)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(". Ask for access!").withStyle(ChatFormatting.RED))
                : Component.literal("You can't do that here.").withStyle(ChatFormatting.RED);
        player.sendSystemMessage(message, true);
    }

    // ------------------------------------------------------- corner picking

    private static void selectCorner(ServerPlayer player, Level world, BlockPos pos, boolean first) {
        NationManager.Selection selection = NationManager.get().selectionOf(player.getUUID());
        String worldId = world.dimension().identifier().toString();

        // Changing worlds invalidates the other corner.
        if (selection.worldId != null && !selection.worldId.equals(worldId)) {
            selection.cornerA = null;
            selection.cornerB = null;
        }
        selection.worldId = worldId;

        if (first) {
            selection.cornerA = pos;
        } else {
            selection.cornerB = pos;
        }

        if (world instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.35, 0.35, 0.35, 0.02);
        }

        String cornerName = first ? "Corner 1" : "Corner 2";
        player.sendSystemMessage(Component.literal(cornerName + " set at ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(pos.getX() + ", " + pos.getZ()).withStyle(ChatFormatting.AQUA)), false);

        if (selection.isComplete()) {
            long area = area(selection);
            player.sendSystemMessage(Component.literal("Selection: " + area + " blocks. ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Run /claimland confirm to claim.").withStyle(ChatFormatting.YELLOW)), false);

            if (NationManager.get().overlaps(worldId, new Claim(worldId,
                    selection.cornerA.getX(), selection.cornerA.getZ(),
                    selection.cornerB.getX(), selection.cornerB.getZ()))) {
                player.sendSystemMessage(Component.literal("Warning: your selection overlaps another nation's land!")
                        .withStyle(ChatFormatting.RED), false);
            }
        }
    }

    private static long area(NationManager.Selection selection) {
        long dx = Math.abs(selection.cornerA.getX() - selection.cornerB.getX()) + 1L;
        long dz = Math.abs(selection.cornerA.getZ() - selection.cornerB.getZ()) + 1L;
        return dx * dz;
    }
}
