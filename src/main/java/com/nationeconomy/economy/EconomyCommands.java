package com.nationeconomy.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.KnownPlayers;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player economy commands: {@code /balance}, {@code /pay}, {@code /baltop}
 * and the admin command {@code /eco}.
 */
public final class EconomyCommands {

    private EconomyCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        // /balance (alias /bal)
        for (String name : new String[]{"balance", "bal"}) {
            dispatcher.register(CommandManager.literal(name)
                    .executes(EconomyCommands::balance));
        }

        // /baltop
        dispatcher.register(CommandManager.literal("baltop")
                .executes(EconomyCommands::balTop));

        // /pay <player> <amount>
        dispatcher.register(CommandManager.literal("pay")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(KnownPlayers.names(), builder))
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(EconomyCommands::pay))));

        // /eco <give|take|set> <player> <amount>  (ops only)
        dispatcher.register(CommandManager.literal("eco")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.GIVE)))))
                .then(CommandManager.literal("take")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.TAKE)))))
                .then(CommandManager.literal("set")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.SET))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> playerArg() {
        return CommandManager.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(KnownPlayers.names(), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, Double> amountArg() {
        return CommandManager.argument("amount", DoubleArgumentType.doubleArg(0));
    }

    // -------------------------------------------------------------- /balance

    private static int balance(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        double balance = EconomyManager.get().balance(player.getUuid());
        player.sendMessage(Text.literal("Your balance: ").formatted(Formatting.GRAY)
                .append(Text.literal(MoneyUtil.format(balance)).formatted(Formatting.GOLD)), false);
        return 1;
    }

    // --------------------------------------------------------------- /baltop

    private static int balTop(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        List<Map.Entry<UUID, Double>> top = EconomyManager.get().top(10);
        source.sendFeedback(() -> Text.literal("—— Richest players ——").formatted(Formatting.GOLD), false);
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : top) {
            String name = Optional.ofNullable(KnownPlayers.nameOf(entry.getKey())).orElse("Unknown");
            int place = rank++;
            double money = entry.getValue();
            source.sendFeedback(() -> Text.literal(" #" + place + " ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(name).formatted(Formatting.AQUA))
                    .append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(MoneyUtil.format(money)).formatted(Formatting.GOLD)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /pay

    private static int pay(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = ctx.getArgument("amount", Double.class);

        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        if (target.get().getId().equals(sender.getUuid())) {
            ctx.getSource().sendError(Text.literal("You cannot pay yourself."));
            return 0;
        }
        EconomyManager economy = EconomyManager.get();
        if (!economy.withdraw(sender.getUuid(), amount)) {
            ctx.getSource().sendError(Text.literal("You don't have " + MoneyUtil.format(amount) + "."));
            return 0;
        }
        economy.add(target.get().getId(), amount);

        sender.sendMessage(Text.literal("Paid ").formatted(Formatting.GRAY)
                .append(Text.literal(MoneyUtil.format(amount)).formatted(Formatting.GOLD))
                .append(Text.literal(" to ").formatted(Formatting.GRAY))
                .append(Text.literal(target.get().getName()).formatted(Formatting.AQUA)), false);

        ServerPlayerEntity onlineTarget = ctx.getSource().getServer().getPlayerManager().getPlayer(target.get().getId());
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Text.literal("You received ").formatted(Formatting.GRAY)
                    .append(Text.literal(MoneyUtil.format(amount)).formatted(Formatting.GOLD))
                    .append(Text.literal(" from ").formatted(Formatting.GRAY))
                    .append(Text.literal(sender.getName().getString()).formatted(Formatting.AQUA)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /eco

    private enum AdminOp {GIVE, TAKE, SET}

    private static int admin(CommandContext<ServerCommandSource> ctx, AdminOp op) {
        ServerCommandSource source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = ctx.getArgument("amount", Double.class);

        Optional<GameProfile> target = KnownPlayers.resolve(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendError(Text.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        EconomyManager economy = EconomyManager.get();
        economy.ensureAccount(target.get().getId());
        switch (op) {
            case GIVE -> economy.add(target.get().getId(), amount);
            case TAKE -> economy.add(target.get().getId(), -Math.min(amount, economy.balance(target.get().getId())));
            case SET -> economy.set(target.get().getId(), amount);
        }
        double newBalance = economy.balance(target.get().getId());
        String verb = switch (op) {
            case GIVE -> "Gave " + MoneyUtil.format(amount) + " to ";
            case TAKE -> "Took " + MoneyUtil.format(amount) + " from ";
            case SET -> "Set the balance of ";
        };
        source.sendFeedback(() -> Text.literal(verb)
                        .append(ColorUtils.formatted(target.get().getName(), Formatting.AQUA))
                        .append(Text.literal(". New balance: " + MoneyUtil.format(newBalance))),
                true);
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(target.get().getId());
        if (onlineTarget != null && op != AdminOp.SET) {
            onlineTarget.sendMessage(Text.literal("An admin adjusted your balance: ")
                    .append(Text.literal(MoneyUtil.format(newBalance)).formatted(Formatting.GOLD)), false);
        }
        return 1;
    }
}
