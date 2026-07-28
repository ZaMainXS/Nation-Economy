package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
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

    private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        // Claim shovel: left click selects corner 1.
        if (ClaimTool.isClaimTool(player.getMainHandStack())) {
            selectCorner(serverPlayer, world, pos, true);
            return ActionResult.FAIL; // never break blocks with the tool
        }

        String worldId = world.getRegistryKey().getValue().toString();
        Nation nation = NationManager.get().claimAt(worldId, pos.getX(), pos.getZ());
        if (nation == null) {
            return ActionResult.PASS; // wilderness
        }
        if (nation.hasPermission(serverPlayer.getUuid(), NationPermission.BREAK) || isOperatorBypass(serverPlayer)) {
            return ActionResult.PASS;
        }

        // Raiding: outsiders can break in, but every block takes 1000 hits.
        return raidHit(serverPlayer, world, pos, nation);
    }

    /** Counts one raid hit against a protected block; breaks it after {@link RaidManager#RAID_HITS}. */
    private static ActionResult raidHit(ServerPlayerEntity player, World world, BlockPos pos, Nation nation) {
        String worldId = world.getRegistryKey().getValue().toString();

        if (world.getBlockState(pos).getHardness(world, pos) < 0) {
            player.sendMessage(Text.literal("This block cannot be raided.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        int hits = RaidManager.get().hit(worldId, pos);
        if (hits >= RaidManager.RAID_HITS) {
            RaidManager.get().clear(worldId, pos);
            world.breakBlock(pos, true, player);
            player.sendMessage(Text.literal("You broke through " + nation.getName() + "'s defenses!")
                    .formatted(Formatting.GOLD), false);
            return ActionResult.FAIL;
        }

        if (hits == 1 || hits % 25 == 0 || hits >= RaidManager.RAID_HITS - 10) {
            player.sendMessage(Text.literal("Raiding " + nation.getName() + ": " + hits + "/"
                    + RaidManager.RAID_HITS + " hits").formatted(Formatting.RED), true);
        }
        if (hits % 25 == 0 && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.02);
        }
        return ActionResult.FAIL;
    }

    // -------------------------------------------------------- block usage

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        ItemStack held = player.getStackInHand(hand);

        // Claim shovel: right click selects corner 2.
        if (hand == Hand.MAIN_HAND && ClaimTool.isClaimTool(held)) {
            selectCorner(serverPlayer, world, pos, false);
            return ActionResult.FAIL;
        }

        NationPermission permission = classifyBlockUse(player, world, pos, held);
        if (permission == null) {
            return ActionResult.PASS; // clicking a plain block with an empty hand does nothing anyway
        }
        if (allowed(serverPlayer, world, pos, permission)) {
            return ActionResult.PASS;
        }
        deny(serverPlayer, world, pos);
        return ActionResult.FAIL;
    }

    /**
     * Decides which permission a right-click-on-block falls under,
     * or {@code null} when the interaction is meaningless.
     */
    @Nullable
    private static NationPermission classifyBlockUse(PlayerEntity player, World world, BlockPos pos, ItemStack held) {
        Block block = world.getBlockState(pos).getBlock();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // Containers (chests, barrels, furnaces, hoppers, ...) need CHEST.
        boolean container = blockEntity instanceof Inventory;
        if (container && !player.isSneaking()) {
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
        return block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof TrapdoorBlock
                || block instanceof AbstractButtonBlock || block instanceof LeverBlock
                || block instanceof AbstractSignBlock || block instanceof BedBlock
                || block instanceof CraftingTableBlock || block instanceof AnvilBlock
                || block instanceof StonecutterBlock || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock || block instanceof LoomBlock
                || block instanceof EnchantingTableBlock || block instanceof BellBlock
                || block instanceof NoteBlock || block instanceof JukeboxBlock
                || block instanceof BeaconBlock || block instanceof DaylightDetectorBlock
                || block instanceof AbstractRedstoneGateBlock || block instanceof CakeBlock
                || block instanceof DragonEggBlock || block instanceof FlowerPotBlock
                || block instanceof LecternBlock || block instanceof RespawnAnchorBlock
                || block instanceof SmithingTableBlock || block instanceof CampfireBlock;
    }

    // --------------------------------------------------------- item usage

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }
        // Buckets and boats aimed through the air: find the destination block
        // with a raycast and require PLACE there.
        if (stack.getItem() instanceof BucketItem || stack.getItem() instanceof BoatItem) {
            Vec3d start = player.getCameraPosVec(1.0F);
            Vec3d end = start.add(player.getRotationVec(1.0F).multiply(5.0));
            BlockHitResult hit = world.raycast(new RaycastContext(start, end,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, player));
            if (hit.getType() == BlockHitResult.Type.BLOCK) {
                BlockPos target = hit.getBlockPos().offset(hit.getSide());
                if (!allowed(serverPlayer, world, target, NationPermission.PLACE)) {
                    deny(serverPlayer, world, target);
                    return TypedActionResult.fail(stack);
                }
            }
        }
        return TypedActionResult.pass(stack);
    }

    // ------------------------------------------------------------ entities

    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity,
                                            @Nullable EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        // Nation core: right-click with a Core Healer (or peek at its health).
        if (CoreManager.handleUse(serverPlayer, entity, hand)) {
            return ActionResult.FAIL;
        }
        if (allowed(serverPlayer, world, entity.getBlockPos(), NationPermission.USE)) {
            return ActionResult.PASS;
        }
        deny(serverPlayer, world, entity.getBlockPos());
        return ActionResult.FAIL;
    }

    private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity,
                                               @Nullable EntityHitResult hitResult) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        // Nation core: raid hits count towards destroying it.
        if (CoreManager.handleAttack(serverPlayer, entity)) {
            return ActionResult.FAIL;
        }
        if (allowed(serverPlayer, world, entity.getBlockPos(), NationPermission.USE)) {
            return ActionResult.PASS;
        }
        deny(serverPlayer, world, entity.getBlockPos());
        return ActionResult.FAIL;
    }

    // ------------------------------------------------------------- helpers

    /** Permission check including the operator bypass. */
    private static boolean allowed(ServerPlayerEntity player, World world, BlockPos pos, NationPermission permission) {
        if (isOperatorBypass(player)) {
            return true;
        }
        String worldId = world.getRegistryKey().getValue().toString();
        return NationManager.get().canDo(player.getUuid(), worldId, pos.getX(), pos.getZ(), permission);
    }

    private static boolean isOperatorBypass(ServerPlayerEntity player) {
        return player.server.getPermissionLevel(player.getGameProfile()) >= 3;
    }

    /** Sends a rate-limited "this land is protected" message. */
    private static void deny(ServerPlayerEntity player, World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MESSAGE.get(player.getUuid());
        if (last != null && now - last < 1000) {
            return;
        }
        LAST_DENY_MESSAGE.put(player.getUuid(), now);

        String worldId = world.getRegistryKey().getValue().toString();
        Nation nation = NationManager.get().claimAt(worldId, pos.getX(), pos.getZ());
        Text message = nation != null
                ? Text.literal("Protected by ").formatted(Formatting.RED)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(". Ask for access!").formatted(Formatting.RED))
                : Text.literal("You can't do that here.").formatted(Formatting.RED);
        player.sendMessage(message, true);
    }

    // ------------------------------------------------------- corner picking

    private static void selectCorner(ServerPlayerEntity player, World world, BlockPos pos, boolean first) {
        NationManager.Selection selection = NationManager.get().selectionOf(player.getUuid());
        String worldId = world.getRegistryKey().getValue().toString();

        // Changing worlds invalidates the other corner.
        if (selection.worldId != null && !selection.worldId.equals(worldId)) {
            selection.cornerA = null;
            selection.cornerB = null;
        }
        selection.worldId = worldId;

        if (first) {
            selection.cornerA = pos.toImmutable();
        } else {
            selection.cornerB = pos.toImmutable();
        }

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.35, 0.35, 0.35, 0.02);
        }

        String cornerName = first ? "Corner 1" : "Corner 2";
        player.sendMessage(Text.literal(cornerName + " set at ").formatted(Formatting.GREEN)
                .append(Text.literal(pos.getX() + ", " + pos.getZ()).formatted(Formatting.AQUA)), false);

        if (selection.isComplete()) {
            long area = area(selection);
            player.sendMessage(Text.literal("Selection: " + area + " blocks. ").formatted(Formatting.GRAY)
                    .append(Text.literal("Run /claimland confirm to claim.").formatted(Formatting.YELLOW)), false);

            if (NationManager.get().overlaps(worldId, new Claim(worldId,
                    selection.cornerA.getX(), selection.cornerA.getZ(),
                    selection.cornerB.getX(), selection.cornerB.getZ()))) {
                player.sendMessage(Text.literal("Warning: your selection overlaps another nation's land!")
                        .formatted(Formatting.RED), false);
            }
        }
    }

    private static long area(NationManager.Selection selection) {
        long dx = Math.abs(selection.cornerA.getX() - selection.cornerB.getX()) + 1L;
        long dz = Math.abs(selection.cornerA.getZ() - selection.cornerB.getZ()) + 1L;
        return dx * dz;
    }
}
