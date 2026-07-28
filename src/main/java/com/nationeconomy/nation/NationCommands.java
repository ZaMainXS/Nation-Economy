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
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        var nation = CommandManager.literal("nation").executes(NationCommands::help);

        nation.then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(NationCommands::create)));
        nation.then(CommandManager.literal("join")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(NationCommands::suggestNations)
                        .executes(NationCommands::join)));
        nation.then(CommandManager.literal("leave").executes(NationCommands::leave));
        nation.then(CommandManager.literal("disband")
                .executes(NationCommands::disbandRequest)
                .then(CommandManager.literal("confirm").executes(NationCommands::disbandConfirm)));
        nation.then(CommandManager.literal("banish")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .executes(ctx -> banish(ctx, true))));
        nation.then(CommandManager.literal("unbanish")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .executes(ctx -> banish(ctx, false))));
        nation.then(CommandManager.literal("allow").then(CommandManager.literal("access")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                .suggests(NationCommands::suggestPermissions)
                                .executes(ctx -> access(ctx, true))))));
        nation.then(CommandManager.literal("deny").then(CommandManager.literal("access")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(NationCommands::suggestPlayers)
                        .then(CommandManager.argument("permission", StringArgumentType.word())
                                .suggests(NationCommands::suggestPermissions)
                                .executes(ctx -> access(ctx, false))))));
        nation.then(CommandManager.literal("trusted").executes(NationCommands::trusted));
        nation.then(CommandManager.literal("color")
                .then(CommandManager.argument("color", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(ColorUtils.namedColors().keySet(), builder))
                        .executes(NationCommands::color)));
        nation.then(CommandManager.literal("colors").executes(NationCommands::colors));
        nation.then(CommandManager.literal("sethome")
                .executes(ctx -> setHome(ctx, -1))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(ctx -> setHome(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1))));
        nation.then(CommandManager.literal("home")
                .executes(ctx -> home(ctx, 0))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(ctx -> home(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1))));
        nation.then(CommandManager.literal("delhome")
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, Nation.MAX_HOMES))
                        .executes(NationCommands::delHome)));
        nation.then(CommandManager.literal("homes").executes(NationCommands::homes));
        nation.then(CommandManager.literal("info")
                .executes(NationCommands::infoSelf)
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(NationCommands::suggestNations)
                        .executes(NationCommands::infoOther)));
        nation.then(CommandManager.literal("list").executes(NationCommands::list));
        nation.then(CommandManager.literal("members").executes(NationCommands::members));
        nation.then(CommandManager.literal("map")
                .executes(ctx -> map(ctx, NationMapRenderer.DEFAULT_RADIUS))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, NationMapRenderer.MAX_RADIUS))
                        .executes(ctx -> map(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
        nation.then(CommandManager.literal("upgrade").executes(NationCommands::upgrade));
        nation.then(CommandManager.literal("unclaim").executes(NationCommands::unclaim));

        dispatcher.register(nation);

        // /claimland
        var claimland = CommandManager.literal("claimland").executes(NationCommands::claimTool);
        claimland.then(CommandManager.literal("confirm").executes(NationCommands::claimConfirm));
        claimland.then(CommandManager.literal("cancel").executes(NationCommands::claimCancel));
        claimland.then(CommandManager.literal("info").executes(NationCommands::claimInfo));
        dispatcher.register(claimland);
    }

    // ----------------------------------------------------------- suggestions

    private static CompletableFuture<Suggestions> suggestNations(CommandContext<ServerCommandSource> ctx,
                                                                 SuggestionsBuilder builder) {
        java.util.List<String> names = new java.util.ArrayList<>();
        NationManager.get().nations().forEach(n -> names.add(n.getName()));
        return CommandSource.suggestMatching(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<ServerCommandSource> ctx,
                                                                 SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(KnownPlayers.names(), builder);
    }

    private static CompletableFuture<Suggestions> suggestPermissions(CommandContext<ServerCommandSource> ctx,
                                                                     SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
                List.of("break", "place", "chest", "use", "all"), builder);
    }

    // ------------------------------------------------------------------ help

    private static int help(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("—— Nation commands ——").formatted(Formatting.GOLD), false);
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
            source.sendFeedback(() -> Text.literal(" " + line[0]).formatted(Formatting.YELLOW)
                    .append(Text.literal(" — " + line[1]).formatted(Formatting.DARK_GRAY)), false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- create

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "name");
        NationManager manager = NationManager.get();

        // One nation per player until theirs is destroyed/lost.
        if (manager.nationOf(player.getUuid()) != null) {
            ctx.getSource().sendError(Text.literal("You are already in a nation. You can only be in one — "
                    + "leave it or wait until it is destroyed."));
            return 0;
        }
        if (!name.matches(NAME_PATTERN)) {
            ctx.getSource().sendError(Text.literal("Names must be 3-16 characters: letters, numbers, underscores."));
            return 0;
        }
        if (!NameFilter.isClean(name)) {
            ctx.getSource().sendError(Text.literal("That name is not allowed. Pick a respectful name."));
            return 0;
        }
        if (manager.find(name).isPresent()) {
            ctx.getSource().sendError(Text.literal("A nation with that name already exists."));
            return 0;
        }

        Nation nation = new Nation(name, player.getUuid());
        manager.createNation(nation);
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);

        // The nation core spawns where you stand — guard it with your life.
        ServerWorld world = (ServerWorld) player.getWorld();
        CoreManager.createCore(world, nation, player.getBlockPos());
        manager.save();

        player.sendMessage(Text.literal("You founded the nation ").formatted(Formatting.GREEN)
                .append(ColorUtils.colored(name, nation.getRgb()))
                .append(Text.literal("!").formatted(Formatting.GREEN)), false);
        player.sendMessage(Text.literal("Your Nation Core spawned here (10,000 hits). If raiders destroy it, "
                        + "your nation falls — build defenses around it!")
                .formatted(Formatting.YELLOW), false);
        ctx.getSource().getServer().getPlayerManager().broadcast(Text.empty()
                .append(Text.literal(player.getName().getString()).formatted(Formatting.AQUA))
                .append(Text.literal(" founded the nation ").formatted(Formatting.GRAY))
                .append(ColorUtils.colored(name, nation.getRgb())), false);
        return 1;
    }

    // ------------------------------------------------------------------ join

    private static int join(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "name");
        NationManager manager = NationManager.get();

        if (manager.nationOf(player.getUuid()) != null) {
            ctx.getSource().sendError(Text.literal("You are already in a nation. /nation leave first."));
            return 0;
        }
        Optional<Nation> target = manager.find(name);
        if (target.isEmpty()) {
            ctx.getSource().sendError(Text.literal("There is no nation called '" + name + "'."));
            return 0;
        }
        Nation nation = target.get();
        if (nation.isBanished(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("You have been banished from " + nation.getName() + "."));
            return 0;
        }
        nation.addMember(player.getUuid());
        manager.setPlayerNation(player.getUuid(), nation.getKey());
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Text.literal(player.getName().getString())
                .formatted(Formatting.AQUA)
                .append(Text.literal(" joined the nation!").formatted(Formatting.GRAY)));
        player.sendMessage(Text.literal("Welcome to ").formatted(Formatting.GREEN)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
        return 1;
    }

    // ----------------------------------------------------------------- leave

    private static int leave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        if (nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Leaders can't leave. Use /nation disband instead."));
            return 0;
        }
        nation.removeMember(player.getUuid());
        manager.setPlayerNation(player.getUuid(), null);
        NationTeams.applyToPlayer(ctx.getSource().getServer(), player);
        manager.save();

        player.sendMessage(Text.literal("You left ").formatted(Formatting.GRAY)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
        broadcastToNation(ctx.getSource().getServer(), nation, Text.literal(player.getName().getString())
                .formatted(Formatting.AQUA)
                .append(Text.literal(" left the nation.").formatted(Formatting.GRAY)));
        return 1;
    }

    // --------------------------------------------------------------- disband

    private static int disbandRequest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can disband the nation."));
            return 0;
        }
        PENDING_DISBAND.put(player.getUuid(), System.currentTimeMillis());
        player.sendMessage(Text.literal("⚠ Are you sure you want to disband ").formatted(Formatting.RED)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal("? All land and the core will be lost forever.").formatted(Formatting.RED)), false);
        player.sendMessage(Text.literal("Type /nation disband confirm within 15 seconds to proceed.")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int disbandConfirm(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("You have no nation to disband."));
            return 0;
        }
        Long requested = PENDING_DISBAND.remove(player.getUuid());
        if (requested == null || System.currentTimeMillis() - requested > DISBAND_CONFIRM_MILLIS) {
            player.sendMessage(Text.literal("No pending disband. Run /nation disband first.")
                    .formatted(Formatting.RED), false);
            return 0;
        }
        disbandNow(ctx.getSource().getServer(), manager, nation,
                Text.literal(player.getName().getString()).formatted(Formatting.AQUA));
        return 1;
    }

    /** Shared teardown used by the disband command. */
    private static void disbandNow(net.minecraft.server.MinecraftServer server, NationManager manager,
                                   Nation nation, Text who) {
        String name = nation.getName();
        int rgb = nation.getRgb();
        CoreManager.removeCore(server, nation);
        nation.clearCore();
        manager.disband(nation);
        NationTeams.removeTeam(server, nation);
        for (UUID member : nation.getMembers()) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(member);
            if (online != null) {
                NationTeams.applyToPlayer(server, online);
                online.sendMessage(Text.literal("Your nation " + name + " was disbanded.").formatted(Formatting.RED), false);
            }
        }
        manager.save();
        server.getPlayerManager().broadcast(Text.empty()
                .append(who)
                .append(Text.literal(" disbanded the nation ").formatted(Formatting.GRAY))
                .append(ColorUtils.colored(name, rgb))
                .append(Text.literal(". Its land is wilderness again.").formatted(Formatting.GRAY)), false);
    }

    // ---------------------------------------------------------------- banish

    private static int banish(CommandContext<ServerCommandSource> ctx, boolean ban) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "player");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can do that."));
            return 0;
        }
        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        UUID targetId = target.get().getId();
        if (targetId.equals(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("You can't (un)banish yourself."));
            return 0;
        }

        if (ban) {
            nation.removeMember(targetId);
            nation.getTrusted().remove(targetId);
            nation.getBanished().add(targetId);
            if (nation.getKey().equals(manager.keyOf(targetId))) {
                manager.setPlayerNation(targetId, null);
            }
            player.sendMessage(Text.literal("Banished ").formatted(Formatting.GRAY)
                    .append(Text.literal(target.get().getName()).formatted(Formatting.RED))
                    .append(Text.literal(" from the nation.").formatted(Formatting.GRAY)), false);
            ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(targetId);
            if (online != null) {
                NationTeams.applyToPlayer(ctx.getSource().getServer(), online);
                online.sendMessage(Text.literal("You were banished from ").formatted(Formatting.RED)
                        .append(ColorUtils.colored(nation.getName(), nation.getRgb())), false);
            }
        } else {
            if (!nation.getBanished().remove(targetId)) {
                ctx.getSource().sendError(Text.literal(target.get().getName() + " is not banished."));
                return 0;
            }
            player.sendMessage(Text.literal("Lifted the ban of ").formatted(Formatting.GRAY)
                    .append(Text.literal(target.get().getName()).formatted(Formatting.GREEN)), false);
        }
        manager.save();
        return 1;
    }

    // ---------------------------------------------------- allow / deny access

    private static int access(CommandContext<ServerCommandSource> ctx, boolean grant) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "player");
        String permissionName = StringArgumentType.getString(ctx, "permission");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can manage land permissions."));
            return 0;
        }
        Optional<NationPermission> permission = NationPermission.parse(permissionName);
        if (permission.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Unknown permission. Use: break, place, chest, use or all."));
            return 0;
        }
        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Unknown player '" + targetName + "'."));
            return 0;
        }

        if (grant) {
            nation.trust(target.get().getId(), permission.get());
        } else {
            nation.untrust(target.get().getId(), permission.get());
        }
        manager.save();

        String action = grant ? "Granted " : "Revoked ";
        String preposition = grant ? " to " : " from ";
        player.sendMessage(Text.literal(action + permission.get().displayName() + " access" + preposition)
                .formatted(Formatting.GRAY)
                .append(Text.literal(target.get().getName()).formatted(Formatting.AQUA)), false);
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(target.get().getId());
        if (online != null && grant) {
            online.sendMessage(NationTeams.chatPrefix(nation)
                    .copy()
                    .append(Text.literal("You now have " + permission.get().displayName()
                            + " access in this land.").formatted(Formatting.GRAY)), false);
        }
        return 1;
    }

    // --------------------------------------------------------------- trusted

    private static int trusted(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        player.sendMessage(Text.literal("—— Trusted players of " + nation.getName() + " ——").formatted(Formatting.GOLD), false);
        if (nation.getTrusted().isEmpty()) {
            player.sendMessage(Text.literal("Nobody has been granted access yet.").formatted(Formatting.DARK_GRAY), false);
        }
        nation.getTrusted().forEach((uuid, permissions) -> {
            String name = Optional.ofNullable(KnownPlayers.nameOf(uuid)).orElse(uuid.toString());
            StringBuilder perms = new StringBuilder();
            permissions.forEach(p -> perms.append(p.displayName()).append(' '));
            String permList = perms.toString().trim();
            player.sendMessage(Text.literal(" " + name).formatted(Formatting.AQUA)
                    .append(Text.literal(" — " + permList).formatted(Formatting.GRAY)), false);
        });
        return 1;
    }

    // ----------------------------------------------------------------- color

    private static int color(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String colorInput = StringArgumentType.getString(ctx, "color");
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can change the nation color."));
            return 0;
        }
        Integer rgb = ColorUtils.parseColor(colorInput);
        if (rgb == null) {
            ctx.getSource().sendError(Text.literal("Unknown color. Use a name (/nation colors) or hex like #FF8800."));
            return 0;
        }
        nation.setRgb(rgb);
        NationTeams.ensureTeam(ctx.getSource().getServer(), nation);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Text.literal("The nation color is now ")
                .formatted(Formatting.GRAY)
                .append(ColorUtils.colored("this color!", rgb)));
        return 1;
    }

    private static int colors(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal("—— Nation colors ——").formatted(Formatting.GOLD), false);
        var line = Text.literal("");
        int[] count = {0};
        ColorUtils.namedColors().forEach((name, rgb) -> {
            line.append(ColorUtils.colored(name + "  ", rgb));
            if (++count[0] % 5 == 0) {
                ctx.getSource().sendFeedback(() -> line.copy(), false);
            }
        });
        ctx.getSource().sendFeedback(() -> line.copy(), false);
        ctx.getSource().sendFeedback(() -> Text.literal("…or any hex color, e.g. /nation color #ff8800")
                .formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    // ----------------------------------------------------------------- homes

    private static Nation.Home currentHome(ServerPlayerEntity player) {
        return new Nation.Home(player.getWorld().getRegistryKey().getValue().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    private static int setHome(CommandContext<ServerCommandSource> ctx, int slot) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can set nation homes."));
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
                ctx.getSource().sendError(Text.literal("Slot " + (slot + 1) + " is not available. "
                        + "Delete one with /nation delhome first."));
                return 0;
            }
            manager.save();
            player.sendMessage(Text.literal("Nation home " + (slot + 1) + " set here.").formatted(Formatting.GREEN), false);
            return 1;
        }
        if (homes.size() >= Nation.MAX_HOMES) {
            ctx.getSource().sendError(Text.literal("All 3 nation homes are set. /nation delhome <1-3> to free one."));
            return 0;
        }
        homes.add(currentHome(player));
        manager.save();
        player.sendMessage(Text.literal("Nation home " + homes.size() + " set here. Teleport with /nation home "
                + homes.size() + ".").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int home(CommandContext<ServerCommandSource> ctx, int slot) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        if (CombatManager.denyIfTagged(player, "teleport home")) {
            return 0;
        }
        List<Nation.Home> homes = nation.getHomes();
        if (homes.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Your nation has no home yet. A leader can set one with /nation sethome."));
            return 0;
        }
        if (slot >= homes.size()) {
            ctx.getSource().sendError(Text.literal("Home " + (slot + 1) + " isn't set. Your nation has "
                    + homes.size() + " home(s) (/nation homes)."));
            return 0;
        }
        Nation.Home target = homes.get(slot);
        Identifier id = Identifier.tryParse(target.getWorld());
        ServerWorld world = id == null ? null
                : ctx.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        if (world == null) {
            ctx.getSource().sendError(Text.literal("The home's world no longer exists."));
            return 0;
        }
        player.teleport(world, target.getX(), target.getY(), target.getZ(), Set.of(),
                target.getYaw(), target.getPitch(), false);
        player.sendMessage(Text.literal("Welcome home ").formatted(Formatting.GREEN)
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(" member! (home " + (slot + 1) + ")").formatted(Formatting.GRAY)), false);
        player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        return 1;
    }

    private static int delHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can delete nation homes."));
            return 0;
        }
        if (slot < 0 || slot >= nation.getHomes().size()) {
            ctx.getSource().sendError(Text.literal("There is no home in slot " + (slot + 1) + "."));
            return 0;
        }
        nation.getHomes().remove(slot);
        manager.save();
        player.sendMessage(Text.literal("Deleted nation home " + (slot + 1) + ".").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int homes(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        player.sendMessage(Text.literal("—— " + nation.getName() + " homes (" + nation.getHomes().size() + "/"
                + Nation.MAX_HOMES + ") ——").formatted(Formatting.GOLD), false);
        int i = 1;
        for (Nation.Home home : nation.getHomes()) {
            int index = i++;
            player.sendMessage(Text.literal(" #" + index + " ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(home.getWorld()).formatted(Formatting.AQUA))
                    .append(Text.literal("  " + (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ())
                            .formatted(Formatting.GRAY)), false);
        }
        if (nation.getHomes().isEmpty()) {
            player.sendMessage(Text.literal("None set. Leader: /nation sethome").formatted(Formatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ info

    private static int infoSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        showInfo(ctx.getSource(), nation);
        return 1;
    }

    private static int infoOther(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Nation> nation = NationManager.get().find(name);
        if (nation.isEmpty()) {
            ctx.getSource().sendError(Text.literal("There is no nation called '" + name + "'."));
            return 0;
        }
        showInfo(ctx.getSource(), nation.get());
        return 1;
    }

    private static void showInfo(ServerCommandSource source, Nation nation) {
        String ownerName = Optional.ofNullable(KnownPlayers.nameOf(nation.getOwner())).orElse("?");
        source.sendFeedback(() -> Text.empty()
                .append(ColorUtils.colored("■ ", nation.getRgb()))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()).formatted(Formatting.BOLD))
                .append(Text.literal("  leader: " + ownerName).formatted(Formatting.DARK_GRAY)), false);
        source.sendFeedback(() -> Text.literal(" Members: " + nation.getMembers().size()).formatted(Formatting.GRAY)
                .append(Text.literal("    Land: " + nation.claimedBlocks() + " / " + nation.maxBlocks()
                        + " blocks (" + nation.getClaims().size() + " claims)").formatted(Formatting.GRAY))
                .append(Text.literal("    Homes: " + nation.getHomes().size() + "/" + Nation.MAX_HOMES)
                        .formatted(Formatting.GRAY)), false);
        source.sendFeedback(() -> Text.literal(" Color: ").formatted(Formatting.GRAY)
                .append(ColorUtils.colored(String.format("#%06X", nation.getRgb()), nation.getRgb()))
                .append(Text.literal("    Core: ").formatted(Formatting.GRAY))
                .append(Text.literal(nation.hasCore()
                        ? nation.getCoreHits() + "/" + Nation.MAX_CORE_HITS + " hits left"
                        : "no core").formatted(nation.hasCore() ? Formatting.YELLOW : Formatting.DARK_GRAY)), false);
        if (!nation.getClaims().isEmpty()) {
            source.sendFeedback(() -> Text.literal(" Claims:").formatted(Formatting.DARK_GRAY), false);
            for (Claim claim : nation.getClaims()) {
                source.sendFeedback(() -> Text.literal("  " + claim.getWorld() + " " + claim)
                        .formatted(Formatting.DARK_GRAY), false);
            }
        }
    }

    // ------------------------------------------------------------------ list

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal("—— Nations (" + NationManager.get().nationCount() + ") ——")
                .formatted(Formatting.GOLD), false);
        NationManager.get().nations().forEach(nation -> ctx.getSource().sendFeedback(() -> Text.empty()
                .append(ColorUtils.colored("■ ", nation.getRgb()))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(" — " + nation.getMembers().size() + " members, "
                        + nation.claimedBlocks() + " blocks claimed").formatted(Formatting.GRAY)), false));
        if (NationManager.get().nationCount() == 0) {
            ctx.getSource().sendFeedback(() -> Text.literal("None yet. Create one with /nation create <name>!")
                    .formatted(Formatting.DARK_GRAY), false);
        }
        return 1;
    }

    // --------------------------------------------------------------- members

    private static int members(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        var server = ctx.getSource().getServer();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You are not in a nation."));
            return 0;
        }
        player.sendMessage(Text.literal("—— Members of " + nation.getName() + " ——").formatted(Formatting.GOLD), false);
        for (UUID member : nation.getMembers()) {
            String name = Optional.ofNullable(KnownPlayers.nameOf(member)).orElse(member.toString());
            boolean online = server.getPlayerManager().getPlayer(member) != null;
            String role = nation.isOwner(member) ? " (leader)" : "";
            player.sendMessage(Text.literal(" " + name).formatted(online ? Formatting.GREEN : Formatting.GRAY)
                    .append(Text.literal(role).formatted(Formatting.YELLOW))
                    .append(Text.literal(online ? "  • online" : "  • offline").formatted(Formatting.DARK_GRAY)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------- map

    private static int map(CommandContext<ServerCommandSource> ctx, int radius) throws CommandSyntaxException {
        NationMapRenderer.render(ctx.getSource().getPlayerOrThrow(), radius);
        return 1;
    }

    // --------------------------------------------------------------- upgrade

    private static int upgrade(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("You need to be in a nation to upgrade its land limit."));
            return 0;
        }
        if (!consume(player, new ItemStack(Items.NETHERITE_INGOT))) {
            ctx.getSource().sendError(Text.literal("You need 1 netherite ingot in your inventory to buy +"
                    + Nation.BLOCKS_PER_NETHERITE + " claim blocks."));
            return 0;
        }
        nation.addBonusBlocks(Nation.BLOCKS_PER_NETHERITE);
        manager.save();

        broadcastToNation(ctx.getSource().getServer(), nation, Text.literal(player.getName().getString())
                .formatted(Formatting.AQUA)
                .append(Text.literal(" upgraded the land limit to " + nation.maxBlocks() + " blocks!")
                        .formatted(Formatting.GRAY)));
        return 1;
    }

    private static boolean consume(ServerPlayerEntity player, ItemStack wanted) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(wanted.getItem()) && stack.getCount() >= wanted.getCount()) {
                stack.decrement(wanted.getCount());
                if (stack.isEmpty()) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------- unclaim

    private static int unclaim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only the nation leader can unclaim land."));
            return 0;
        }
        String worldId = player.getWorld().getRegistryKey().getValue().toString();
        BlockPos pos = player.getBlockPos();
        Claim claim = nation.claimAt(worldId, pos.getX(), pos.getZ());
        if (claim == null) {
            ctx.getSource().sendError(Text.literal("You are not standing inside your nation's land."));
            return 0;
        }
        nation.getClaims().remove(claim);
        manager.save();
        player.sendMessage(Text.literal("Unclaimed " + claim.area() + " blocks. You can now claim "
                + nation.remainingBlocks() + " more.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    // ------------------------------------------------------------- claimland

    private static int claimTool(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Nation nation = NationManager.get().nationOf(player.getUuid());
        if (nation == null) {
            ctx.getSource().sendError(Text.literal("Create or join a nation first (/nation create <name>)."));
            return 0;
        }
        if (!nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only your nation's leader can claim land."));
            return 0;
        }
        boolean given = ClaimTool.giveIfMissing(player);
        player.sendMessage(Text.literal(given ? "You received the " : "Use your ")
                        .formatted(Formatting.GREEN)
                .append(Text.literal("Land Claim Shovel").formatted(Formatting.GOLD))
                .append(Text.literal(": left-click corner 1, right-click corner 2, then /claimland confirm.")
                        .formatted(Formatting.GREEN)), false);
        player.sendMessage(Text.literal("Land left to claim: " + nation.remainingBlocks() + " blocks.")
                .formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    private static int claimConfirm(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        if (nation == null || !nation.isOwner(player.getUuid())) {
            ctx.getSource().sendError(Text.literal("Only your nation's leader can claim land."));
            return 0;
        }
        NationManager.Selection selection = manager.selectionOf(player.getUuid());
        if (!selection.isComplete()) {
            ctx.getSource().sendError(Text.literal("Select both corners first (left & right click with the claim shovel)."));
            return 0;
        }
        String worldId = player.getWorld().getRegistryKey().getValue().toString();
        if (!worldId.equals(selection.worldId)) {
            ctx.getSource().sendError(Text.literal("Your selection is in a different world. Select new corners here."));
            return 0;
        }

        Claim claim = new Claim(worldId,
                selection.cornerA.getX(), selection.cornerA.getZ(),
                selection.cornerB.getX(), selection.cornerB.getZ());

        if (claim.area() > nation.remainingBlocks()) {
            ctx.getSource().sendError(Text.literal("That area (" + claim.area() + " blocks) is too big. You have "
                    + nation.remainingBlocks() + " blocks left. Buy more with /nation upgrade."));
            return 0;
        }
        if (manager.overlaps(worldId, claim)) {
            ctx.getSource().sendError(Text.literal("This area overlaps another nation's land."));
            return 0;
        }

        nation.getClaims().add(claim);
        manager.clearSelection(player.getUuid());
        manager.save();
        player.sendMessage(Text.literal("Claimed ").formatted(Formatting.GREEN)
                .append(Text.literal(claim.area() + " blocks").formatted(Formatting.AQUA))
                .append(Text.literal(" for ").formatted(Formatting.GREEN))
                .append(ColorUtils.colored(nation.getName(), nation.getRgb()))
                .append(Text.literal(". " + nation.remainingBlocks() + " blocks left.").formatted(Formatting.DARK_GRAY)), false);
        return 1;
    }

    private static int claimCancel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager.get().clearSelection(player.getUuid());
        player.sendMessage(Text.literal("Selection cleared.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int claimInfo(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        NationManager manager = NationManager.get();
        Nation nation = manager.nationOf(player.getUuid());
        NationManager.Selection selection = manager.selectionOf(player.getUuid());

        if (selection.cornerA != null) {
            BlockPos a = selection.cornerA;
            player.sendMessage(Text.literal("Corner 1: " + a.getX() + ", " + a.getZ()).formatted(Formatting.GRAY), false);
        }
        if (selection.cornerB != null) {
            BlockPos b = selection.cornerB;
            player.sendMessage(Text.literal("Corner 2: " + b.getX() + ", " + b.getZ()).formatted(Formatting.GRAY), false);
        }
        if (selection.isComplete()) {
            long dx = Math.abs(selection.cornerA.getX() - selection.cornerB.getX()) + 1L;
            long dz = Math.abs(selection.cornerA.getZ() - selection.cornerB.getZ()) + 1L;
            player.sendMessage(Text.literal("Selection: " + dx + " x " + dz + " = " + (dx * dz) + " blocks")
                    .formatted(Formatting.YELLOW), false);
        } else {
            player.sendMessage(Text.literal("No active selection.").formatted(Formatting.DARK_GRAY), false);
        }
        if (nation != null) {
            player.sendMessage(Text.literal("Land limit: " + nation.claimedBlocks() + " / " + nation.maxBlocks()
                    + " used.").formatted(Formatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ------------------------------------------------------------- messaging

    /** Sends a tagged message to all online members of a nation. */
    private static void broadcastToNation(net.minecraft.server.MinecraftServer server, Nation nation, Text message) {
        Text full = NationTeams.chatPrefix(nation).copy().append(message);
        for (UUID member : nation.getMembers()) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(member);
            if (online != null) {
                online.sendMessage(full, false);
            }
        }
    }
}
