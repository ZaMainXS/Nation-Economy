package com.nationeconomy.nation;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.nationeconomy.combat.CombatManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * All nation commands — founding, membership, land permissions, colors,
 * map, homes, upgrades, the disband confirmation flow and the claim shovel.
 */
public final class NationCommands {

    private static final String NAME_PATTERN = "[A-Za-z0-9_]{3,16}";

    /** Pending disband confirmations: owner uuid -> timestamp. */
    private static final Map<UUID, Long> PENDING_DISBAND = new ConcurrentHashMap<>();
    private static final long DISBAND_CONFIRM_MILLIS = 15_000;

    private NationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        var nation = Commands.literal("nation").executes(NationCommands::help);

        nation.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(NationCommands::create)));
        nation.then(Commands.literal("join")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(NationCommands::suggestNations)
                        .executes(NationCommands::join)));
        nation.then(Commands.literal("leave").executes(NationCommands::leave));
        nation.then(Commands.literal("disband")
                .executes(NationCommands::disbandRequest)
                .then(Commands.literal("confirm").executes(NationCommands::disbandConfirm)));
        nation.then(Commands.literal("banish")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .executes(ctx -> banish(ctx, true))));
        nation.then(Commands.literal("unbanish")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .executes(ctx -> banish(ctx, false))));
        nation.then(Commands.literal("allow").then(Commands.literal("access")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .suggests(NationCommands::suggestPermissions)
                                .executes(ctx -> access(ctx, true))))));
        nation.then(Commands.literal("deny").then(Commands.literal("access")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .suggests(NationCommands::suggestPermissions)
                                .executes(ctx -> access(ctx, false))))));
        nation.then(Commands.literal("trusted").executes(NationCommands::trusted));
        nation.then(Commands.literal("color")
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestMatching(ColorUtils.namedColors().keySet(), builder))
                        .executes(NationCommands::color)));
        nation.then(Commands.literal("colors").executes(NationCommands::colors));
        nation.then(Commands.literal("sethome")
                .executes(ctx -> setHome(ctx, -1))
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(ctx -> setHome(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1))));
        nation.then(Commands.literal("home")
                .executes(ctx -> home(ctx, 0))
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(ctx -> home(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1))));
        nation.then(Commands.literal("delhome")
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(NationCommands::delHome)));
        nation.then(Commands.literal("homes").executes(NationCommands::homes));
        nation.then(Commands.literal("info")
                .executes(NationCommands::infoSelf)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(NationCommands::suggestNations)
                        .executes(NationCommands::infoOther)));
        nation.then(Commands.literal("list").executes(NationCommands::list));
        nation.then(Commands.literal("members").executes(NationCommands::members));
        nation.then(Commands.literal("map")
                .executes(ctx -> map(ctx, NationMapRenderer.DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, NationMapRenderer.MAX_RADIUS))
                        .executes(ctx -> map(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
        nation.then(Commands.literal("upgrade").executes(NationCommands::upgrade));
        nation.then(Commands.literal("unclaim").executes(NationCommands::unclaim));

        dispatcher.register(nation);

        // /claimland
        var claimland = Commands.literal("claimland").executes(NationCommands::claimTool);
        claimland.then(Commands.literal("confirm").executes(NationCommands::claimConfirm));
        claimland.then(Commands.literal("cancel").executes(NationCommands::claimCancel));
        claimland.then(Commands.literal("info").executes(NationCommands::claimInfo));
        dispatcher.register(claimland);
    }

    // ----------------------------------------------------------- suggestions

    private static CompletableFuture<Suggestions> suggestNations(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder builder) {
        java.util.List<String> names = new java.util.ArrayList<>();
        NationManager.get().nations().forEach(n -> names.add(n.getName()));
        return SharedSuggestionProvider.suggestMatching(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestMatching(KnownPlayers.names(), builder);
    }

    private static CompletableFuture<Suggestions> suggestPermissions(CommandContext<CommandSourceStack> ctx,
                                                                     SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestMatching(
                List.of("break", "place", "chest", "use", "all"), builder);
    }

    // ------------------------------------------------------------------ help

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("—— Nation commands ——").withStyle(ChatFormatting.GOLD), false);
        String[][] lines = {
                {"/nation create <name>", "found a nation (core spawns at your feet)"},
                {"/nation join <name>", "join a nation"},
                {"/nation leave", "leave your nation"},
                {"/claimland", "get the land claim shovel"},
                {"/claimland confirm", "claim the selected land"},
                {"/nation color <color>", "change nation color (name or #RRGGBB)"},
                {"/nation allow access <player> <perm>", "grant land access (leader)"},
                {"/nation banish <player>", "kick & ban a player (leader)"},
                {"/nation sethome / home / delhome", "up to 3 nation homes"},
                {"/nation map", "view the territory map"},
                {"/nation upgrade", "1 netherite ingot = +100 claim blocks"},
                {"/nation disband", "delete your nation (asks to confirm)"},
                {"/nationalexplain", "open the full guide GUI"},
        };
        for (String[] line : lines) {
            source.sendSuccess(() -> Component.literal(" " + line[0]).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" — " + line[1]).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- create

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        NationManager manager = NationManager.get();

        // One nation per player until theirs is destroyed/lost.
        if (manager.nationOf(player.getUUID()) != null) {
            ctx.getSource().sendFailure(Component.literal("You are already in a nation. You can only be in one — "
                    + "leave it or wait until it is destroyed."));
            return 0;
        }
        if (!name.matches(NAME_PATTERN)) {
            ctx.getSource().sendFailure(Component.literal("Names must be 3-16 characters: letters, numbers, underscores."));
            return 0;
        }
        if (!NameFilter.isClean(name)) {
            ctx.getSource().sendFailure(Component.literal("That name is not allowed. Pick a respectful name."));
            return 0;
        }
        if (manager.find(name).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("A nation with that name already exists."));
            return 0;
        }

        Nation nation = new Nation(name, player.getUUID());
        manager.createNation(nation);
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);

        // The nation core spawns where you stand — guard it with your life.
        ServerLevel world = (ServerLevel) player.level();
        CoreManager.createCore(world, nation, player.blockPosition());
        manager.save();

        player.sendSystemMessage(Component.literal("You founded the nation ").withStyle(ChatFormatting.GREEN)
                .append(ColorUtils.colored(name, nation.getRgb()))
                .append(Component.literal("!").withStyle(ChatFormatting.GREEN)), false);
        player.sendSystemMessage(Component.literal("Your Nation Core spawned here (10,000 hits). If raiders destroy it, "
                        + "your nation falls — build defenses around it!")
                .withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.empty()
                .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" founded the nation ").withStyle(ChatFormatting.GRAY))
                .append(ColorUtils.colored(name, nation.getRgb())), false);
        return 1;
    }

    // ------------------------------------------------------------------ join

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        NationManager manager = NationManager.get();

        if (manager.nationOf(player.getUUID()) != null) {
            ctx.getSource().sendFailure(Component.literal("You are already in a nation. /nation leave first."));
            return 0;
        }
        Optional<Nation> target = manager.find(name);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("There is no nation called '" + name + "'."));
            return 0;
        }
        Nation nation = target.get();
        if (nation.isBanished(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You have been banished from " + nation.getName() + "."));
            return 0;
        }
        nation.addMember(player.getUUID());
        manager.setPlayerNation(player.getUUID(), nation.getKey());
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Component.literal(player.getName().getString())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" joined the nation!").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("Welcome to ").withStyle(ChatFormatting.GREEN)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
        return 1;
    }

    // ----------------------------------------------------------------- leave

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        if (nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Leaders can't leave. Use /nation disband instead."));
            return 0;
        }
        nation.removeMember(player.getUUID());
        manager.setPlayerNation(player.getUUID(), null);
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);
        manager.save();

        player.sendSystemMessage(Component.literal("You left ").withStyle(ChatFormatting.GRAY)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
        broadcastToNation(ctx.getSource().getServer(), nation, Component.literal(player.getName().getString())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" left the nation.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    // --------------------------------------------------------------- disband

    private static int disbandRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can disband the nation."));
            return 0;
        }
        PENDING_DISBAND.put(player.getUUID(), System.currentTimeMillis());
        player.sendSystemMessage(Component.literal("⚠ Are you sure you want to disband ").withStyle(ChatFormatting.RED)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal("? All land and the core will be lost forever.").withStyle(ChatFormatting.RED)), false);
        player.sendSystemMessage(Component.literal("Type /nation disband confirm within 15 seconds to proceed.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int disbandConfirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You have no nation to disband."));
            return 0;
        }
        Long requested = PENDING_DISBAND.remove(player.getUUID());
        if (requested == null || System.currentTimeMillis() - requested > DISBAND_CONFIRM_MILLIS) {
            player.sendSystemMessage(Component.literal("No pending disband. Run /nation disband first.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        disbandNow(ctx.getSource().getServer(), manager, nation,
                Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    /** Shared teardown used by the disband command. */
    private static void disbandNow(net.minecraft.server.MinecraftServer server, NationManager manager,
                                   Nation nation, Component who) {
        String name = nation.getName();
        int rgb = nation.getRgb();
        CoreManager.removeCore(server, nation);
        nation.clearCore();
        manager.disband(nation);
        NationTeams.removeTeam(server, nation);
        for (UUID member : nation.getMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                NationTeams.applyToPlayer(server, online);
                online.sendSystemMessage(Component.literal("Your nation " + name + " was disbanded.").withStyle(ChatFormatting.RED), false);
            }
        }
        manager.save();
        server.getPlayerList().broadcastSystemMessage(Component.empty()
                .append(who)
                .append(Component.literal(" disbanded the nation ").withStyle(ChatFormatting.GRAY))
                .append(ColorUtils.colored(name, rgb))
                .append(Component.literal(". Its land is wilderness again.").withStyle(ChatFormatting.GRAY)), false);
    }

    // ---------------------------------------------------------------- banish

    private static int banish(CommandContext<CommandSourceStack> ctx, boolean ban) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "player");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can do that."));
            return 0;
        }
        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        UUID targetId = target.get().id();
        if (targetId.equals(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You can't (un)banish yourself."));
            return 0;
        }

        if (ban) {
            nation.removeMember(targetId);
            nation.getTrusted().remove(targetId);
            nation.getBanished().add(targetId);
            if (nation.getKey().equals(manager.keyOf(targetId))) {
                manager.setPlayerNation(targetId, null);
            }
            player.sendSystemMessage(Component.literal("Banished ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(target.get().name()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" from the nation.").withStyle(ChatFormatting.GRAY)), false);
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(targetId);
            if (online != null) {
                NationTeams.applyToPlayer(ctx.getSource().getServer(), online);
                online.sendSystemMessage(Component.literal("You were banished from ").withStyle(ChatFormatting.RED)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
            }
        } else {
            if (!nation.getBanished().remove(targetId)) {
                ctx.getSource().sendFailure(Component.literal(target.get().name() + " is not banished."));
                return 0;
            }
            player.sendSystemMessage(Component.literal("Lifted the ban of ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(target.get().name()).withStyle(ChatFormatting.GREEN)), false);
        }
        manager.save();
        return 1;
    }

    // ---------------------------------------------------- allow / deny access

    private static int access(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "player");
        String permissionName = StringArgumentType.getString(ctx, "permission");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can manage land permissions."));
            return 0;
        }
        Optional<NationPermission> permission = NationPermission.parse(permissionName);
        if (permission.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown permission. Use: break, place, chest, use or all."));
            return 0;
        }
        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown player '" + targetName + "'."));
            return 0;
        }

        if (grant) {
            nation.trust(target.get().id(), permission.get());
        } else {
            nation.untrust(target.get().id(), permission.get());
        }
        manager.save();

        String action = grant ? "Granted " : "Revoked ";
        String preposition = grant ? " to " : " from ";
        player.sendSystemMessage(Component.literal(action + permission.get().displayName() + " access" + preposition)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(target.get().name()).withStyle(ChatFormatting.AQUA)), false);
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(target.get().id());
        if (online != null && grant) {
            online.sendSystemMessage(NationTeams.chatPrefix(nation)
                    .copy()
                    .append(Component.literal("You now have " + permission.get().displayName()
                            + " access in this land.").withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    // --------------------------------------------------------------- trusted

    private static int trusted(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("—— Trusted players of " + nation.getName() + " ——").withStyle(ChatFormatting.GOLD), false);
        if (nation.getTrusted().isEmpty()) {
            player.sendSystemMessage(Component.literal("Nobody has been granted access yet.").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        nation.getTrusted().forEach((uuid, permissions) -> {
            String name = Optional.ofNullable(KnownPlayers.nameOf(uuid)).orElse(uuid.toString());
            StringBuilder perms = new StringBuilder();
            permissions.forEach(p -> perms.append(p.displayName()).append(' '));
            String permList = perms.toString().trim();
            player.sendSystemMessage(Component.literal(" " + name).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" — " + permList).withStyle(ChatFormatting.GRAY)), false);
        });
        return 1;
    }

    // ----------------------------------------------------------------- color

    private static int color(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorInput = StringArgumentType.getString(ctx, "color");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can change the nation color."));
            return 0;
        }
        Integer rgb = ColorUtils.parseColor(colorInput);
        if (rgb == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown color. Use a name (/nation colors) or hex like #FF8800."));
            return 0;
        }
        nation.setRgb(rgb);
        NationTeams.ensureTeam(ctx.getSource().getServer(), nation);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Component.literal("The nation color is now ")
                .withStyle(ChatFormatting.GRAY)
                .append(ColorUtils.colored("this color!", rgb)));
        return 1;
    }

    private static int colors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("—— Nation colors ——").withStyle(ChatFormatting.GOLD), false);
        var line = Component.literal("");
        int[] count = {0};
        ColorUtils.namedColors().forEach((name, rgb) -> {
            line.append(ColorUtils.colored(name + "  ", rgb));
            if (++count[0] % 5 == 0) {
                ctx.getSource().sendSuccess(() -> line.copy(), false);
            }
        });
        ctx.getSource().sendSuccess(() -> line.copy(), false);
        ctx.getSource().sendSuccess(() -> Component.literal("…or any hex color, e.g. /nation color #ff8800")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    // ----------------------------------------------------------------- homes

    private static Nation.Home currentHome(ServerPlayer player) {
        return new Nation.Home(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static int setHome(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can set nation homes."));
            return 0;
        }
        List<Nation.Home> homes = nation.getHomes();
        if (slot >= 0) {
            // Explicit slot: replace (or append when it's the next free slot).
            if (slot < homes.size()) {
                homes.set(slot, currentHome(player));
            } else if (slot == homes.size() && homes.size() < Nation.MAX_HOMES) {
                homes.add(currentHome(player));
            } else {
                ctx.getSource().sendFailure(Component.literal("Slot " + (slot + 1) + " is not available. "
                        + "Delete one with /nation delhome first."));
                return 0;
            }
            manager.save();
            player.sendSystemMessage(Component.literal("Nation home " + (slot + 1) + " set here.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        if (homes.size() >= Nation.MAX_HOMES) {
            ctx.getSource().sendFailure(Component.literal("All 3 nation homes are set. /nation delhome <1-3> to free one."));
            return 0;
        }
        homes.add(currentHome(player));
        manager.save();
        player.sendSystemMessage(Component.literal("Nation home " + homes.size() + " set here. Teleport with /nation home "
                + homes.size() + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        if (CombatManager.denyIfTagged(player, "teleport home")) {
            return 0;
        }
        List<Nation.Home> homes = nation.getHomes();
        if (homes.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Your nation has no home yet. A leader can set one with /nation sethome."));
            return 0;
        }
        if (slot >= homes.size()) {
            ctx.getSource().sendFailure(Component.literal("Home " + (slot + 1) + " isn't set. Your nation has "
                    + homes.size() + " home(s) (/nation homes)."));
            return 0;
        }
        Nation.Home target = homes.get(slot);
        Identifier id = Identifier.tryParse(target.getWorld());
        ServerLevel world = id == null ? null
                : ctx.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (world == null) {
            ctx.getSource().sendFailure(Component.literal("The home's world no longer exists."));
            return 0;
        }
        player.teleport(new TeleportTransition(world, new Vec3(target.getX(), target.getY(), target.getZ()),
                Vec3.ZERO, target.getYaw(), target.getPitch(), TeleportTransition.DO_NOTHING));
        player.sendSystemMessage(Component.literal("Welcome home ").withStyle(ChatFormatting.GREEN)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(" member! (home " + (slot + 1) + ")").withStyle(ChatFormatting.GRAY)), false);
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can delete nation homes."));
            return 0;
        }
        if (slot < 0 || slot >= nation.getHomes().size()) {
            ctx.getSource().sendFailure(Component.literal("There is no home in slot " + (slot + 1) + "."));
            return 0;
        }
        nation.getHomes().remove(slot);
        manager.save();
        player.sendSystemMessage(Component.literal("Deleted nation home " + (slot + 1) + ".").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int homes(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("—— " + nation.getName() + " homes (" + nation.getHomes().size() + "/"
                + Nation.MAX_HOMES + ") ——").withStyle(ChatFormatting.GOLD), false);
        int i = 1;
        for (Nation.Home home : nation.getHomes()) {
            int index = i++;
            player.sendSystemMessage(Component.literal(" #" + index + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(home.getWorld()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  " + (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ())
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        if (nation.getHomes().isEmpty()) {
            player.sendSystemMessage(Component.literal("None set. Leader: /nation sethome").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ info

    private static int infoSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        showInfo(ctx.getSource(), nation);
        return 1;
    }

    private static int infoOther(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Nation> nation = NationManager.get().find(name);
        if (nation.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("There is no nation called '" + name + "'."));
            return 0;
        }
        showInfo(ctx.getSource(), nation.get());
        return 1;
    }

    private static void showInfo(CommandSourceStack source, Nation nation) {
        String ownerName = Optional.ofNullable(KnownPlayers.nameOf(nation.getOwner())).orElse("?");
        source.sendSuccess(() -> Component.empty()
                .append(ColorUtils.colored("■ ", nation.getRgb()))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()).withStyle(ChatFormatting.BOLD))
                .append(Component.literal("  leader: " + ownerName).withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> Component.literal(" Members: " + nation.getMembers().size()).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("    Land: " + nation.claimedBlocks() + " / " + nation.maxBlocks()
                        + " blocks (" + nation.getClaims().size() + " claims)").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("    Homes: " + nation.getHomes().size() + "/" + Nation.MAX_HOMES)
                        .withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal(" Color: ").withStyle(ChatFormatting.GRAY)
                .append(ColorUtils.colored(String.format("#%06X", nation.getRgb()), nation.getRgb()))
                .append(Component.literal("    Core: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(nation.hasCore()
                        ? nation.getCoreHits() + "/" + Nation.MAX_CORE_HITS + " hits left"
                        : "no core").withStyle(nation.hasCore() ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)), false);
        if (!nation.getClaims().isEmpty()) {
            source.sendSuccess(() -> Component.literal(" Claims:").withStyle(ChatFormatting.DARK_GRAY), false);
            for (Claim claim : nation.getClaims()) {
                source.sendSuccess(() -> Component.literal("  " + claim.getWorld() + " " + claim)
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
    }

    // ------------------------------------------------------------------ list

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("—— Nations (" + NationManager.get().nationCount() + ") ——")
                .withStyle(ChatFormatting.GOLD), false);
        NationManager.get().nations().forEach(nation -> ctx.getSource().sendSuccess(() -> Component.empty()
                .append(ColorUtils.colored("■ ", nation.getRgb()))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(" — " + nation.getMembers().size() + " members, "
                        + nation.claimedBlocks() + " blocks claimed").withStyle(ChatFormatting.GRAY)), false));
        if (NationManager.get().nationCount() == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("None yet. Create one with /nation create <name>!")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // --------------------------------------------------------------- members

    private static int members(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var server = ctx.getSource().getServer();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in a nation."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("—— Members of " + nation.getName() + " ——").withStyle(ChatFormatting.GOLD), false);
        for (UUID member : nation.getMembers()) {
            String name = Optional.ofNullable(KnownPlayers.nameOf(member)).orElse(member.toString());
            boolean online = server.getPlayerList().getPlayer(member) != null;
            String role = nation.isOwner(member) ? " (leader)" : "";
            player.sendSystemMessage(Component.literal(" " + name).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                    .append(Component.literal(role).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(online ? "  • online" : "  • offline").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------- map

    private static int map(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        NationMapRenderer.render(ctx.getSource().getPlayerOrException(), radius);
        return 1;
    }

    // --------------------------------------------------------------- upgrade

    private static int upgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("You need to be in a nation to upgrade its land limit."));
            return 0;
        }
        if (!consume(player, new ItemStack(Items.NETHERITE_INGOT))) {
            ctx.getSource().sendFailure(Component.literal("You need 1 netherite ingot in your inventory to buy +"
                    + Nation.BLOCKS_PER_NETHERITE + " claim blocks."));
            return 0;
        }
        nation.addBonusBlocks(Nation.BLOCKS_PER_NETHERITE);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Component.literal(player.getName().getString())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" upgraded the land limit to " + nation.maxBlocks() + " blocks!")
                        .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static boolean consume(ServerPlayer player, ItemStack wanted) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(wanted.getItem()) && stack.getCount() >= wanted.getCount()) {
                stack.shrink(wanted.getCount());
                if (stack.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------- unclaim

    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the nation leader can unclaim land."));
            return 0;
        }
        String worldId = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        Claim claim = nation.claimAt(worldId, pos.getX(), pos.getZ());
        if (claim == null) {
            ctx.getSource().sendFailure(Component.literal("You are not standing inside your nation's land."));
            return 0;
        }
        nation.getClaims().remove(claim);
        manager.save();
        player.sendSystemMessage(Component.literal("Unclaimed " + claim.area() + " blocks. You can now claim "
                + nation.remainingBlocks() + " more.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // ------------------------------------------------------------- claimland

    private static int claimTool(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Nation nation = NationManager.get().nationOf(player.getUUID());
        if (nation == null) {
            ctx.getSource().sendFailure(Component.literal("Create or join a nation first (/nation create <name>)."));
            return 0;
        }
        if (!nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only your nation's leader can claim land."));
            return 0;
        }
        boolean given = ClaimTool.giveIfMissing(player);
        player.sendSystemMessage(Component.literal(given ? "You received the " : "Use your ")
                        .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Land Claim Shovel").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": left-click corner 1, right-click corner 2, then /claimland confirm.")
                        .withStyle(ChatFormatting.GREEN)), false);
        player.sendSystemMessage(Component.literal("Land left to claim: " + nation.remainingBlocks() + " blocks.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int claimConfirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        if (nation == null || !nation.isOwner(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only your nation's leader can claim land."));
            return 0;
        }
        NationManager.Selection selection = manager.selectionOf(player.getUUID());
        if (!selection.isComplete()) {
            ctx.getSource().sendFailure(Component.literal("Select both corners first (left & right click with the claim shovel)."));
            return 0;
        }
        String worldId = player.level().dimension().location().toString();
        if (!worldId.equals(selection.worldId)) {
            ctx.getSource().sendFailure(Component.literal("Your selection is in a different world. Select new corners here."));
            return 0;
        }

        Claim claim = new Claim(worldId,
                selection.cornerA.getX(), selection.cornerA.getZ(),
                selection.cornerB.getX(), selection.cornerB.getZ());

        if (claim.area() > nation.remainingBlocks()) {
            ctx.getSource().sendFailure(Component.literal("That area (" + claim.area() + " blocks) is too big. You have "
                    + nation.remainingBlocks() + " blocks left. Buy more with /nation upgrade."));
            return 0;
        }
        if (manager.overlaps(worldId, claim)) {
            ctx.getSource().sendFailure(Component.literal("This area overlaps another nation's land."));
            return 0;
        }

        nation.getClaims().add(claim);
        manager.clearSelection(player.getUUID());
        manager.save();
        player.sendSystemMessage(Component.literal("Claimed ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(claim.area() + " blocks").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GREEN))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Component.literal(". " + nation.remainingBlocks() + " blocks left.").withStyle(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int claimCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager.get().clearSelection(player.getUUID());
        player.sendSystemMessage(Component.literal("Selection cleared.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int claimInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUUID());
        NationManager.Selection selection = manager.selectionOf(player.getUUID());

        if (selection.cornerA != null) {
            BlockPos a = selection.cornerA;
            player.sendSystemMessage(Component.literal("Corner 1: " + a.getX() + ", " + a.getZ()).withStyle(ChatFormatting.GRAY), false);
        }
        if (selection.cornerB != null) {
            BlockPos b = selection.cornerB;
            player.sendSystemMessage(Component.literal("Corner 2: " + b.getX() + ", " + b.getZ()).withStyle(ChatFormatting.GRAY), false);
        }
        if (selection.isComplete()) {
            long dx = Math.abs(selection.cornerA.getX() - selection.cornerB.getX()) + 1L;
            long dz = Math.abs(selection.cornerA.getZ() - selection.cornerB.getZ()) + 1L;
            player.sendSystemMessage(Component.literal("Selection: " + dx + " x " + dz + " = " + (dx * dz) + " blocks")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            player.sendSystemMessage(Component.literal("No active selection.").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        if (nation != null) {
            player.sendSystemMessage(Component.literal("Land limit: " + nation.claimedBlocks() + " / " + nation.maxBlocks()
                    + " used.").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ------------------------------------------------------------- messaging

    /** Sends a tagged message to all online members of a nation. */
    private static void broadcastToNation(net.minecraft.server.MinecraftServer server, Nation nation, Component message) {
        Component full = NationTeams.chatPrefix(nation).copy().append(message);
        for (UUID member : nation.getMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(full, false);
            }
        }
    }
}
