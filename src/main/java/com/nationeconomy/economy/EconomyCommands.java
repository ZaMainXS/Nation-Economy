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
import net.minecraft.commands.CommandBuildContext;
import com.nationeconomy.util.SuggestUtil;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        // /balance (alias /bal)
        for (String name : new String[]{"balance", "bal"}) {
            dispatcher.register(Commands.literal(name)
                    .executes(EconomyCommands::balance));
        }

        // /baltop
        dispatcher.register(Commands.literal("baltop")
                .executes(EconomyCommands::balTop));

        // /pay <player> <amount>
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SuggestUtil.suggestMatching(KnownPlayers.names(), builder))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(EconomyCommands::pay))));

        // /eco <give|take|set> <player> <amount>  (ops only)
        dispatcher.register(Commands.literal("eco")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("give")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.GIVE)))))
                .then(Commands.literal("take")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.TAKE)))))
                .then(Commands.literal("set")
                        .then(playerArg()
                                .then(amountArg()
                                        .executes(ctx -> admin(ctx, AdminOp.SET))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> SuggestUtil.suggestMatching(KnownPlayers.names(), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> amountArg() {
        return Commands.argument("amount", DoubleArgumentType.doubleArg(0));
    }

    // -------------------------------------------------------------- /balance

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double balance = EconomyManager.get().balance(player.getUUID());
        player.sendSystemMessage(Component.literal("Your balance: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyUtil.format(balance)).withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    // --------------------------------------------------------------- /baltop

    private static int balTop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Map.Entry<UUID, Double>> top = EconomyManager.get().top(10);
        source.sendSuccess(() -> Component.literal("—— Richest players ——").withStyle(ChatFormatting.GOLD), false);
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : top) {
            String name = Optional.ofNullable(KnownPlayers.nameOf(entry.getKey())).orElse("Unknown");
            int place = rank++;
            double money = entry.getValue();
            source.sendSuccess(() -> Component.literal(" #" + place + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(MoneyUtil.format(money)).withStyle(ChatFormatting.GOLD)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /pay

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = ctx.getArgument("amount", Double.class);

        Optional<GameProfile> target = KnownPlayers.resolve(ctx.getSource().getServer(), targetName);
        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        if (target.get().id().equals(sender.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }
        EconomyManager economy = EconomyManager.get();
        if (!economy.withdraw(sender.getUUID(), amount)) {
            ctx.getSource().sendFailure(Component.literal("You don't have " + MoneyUtil.format(amount) + "."));
            return 0;
        }
        economy.add(target.get().id(), amount);

        sender.sendSystemMessage(Component.literal("Paid ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyUtil.format(amount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target.get().name()).withStyle(ChatFormatting.AQUA)), false);

        ServerPlayer onlineTarget = ctx.getSource().getServer().getPlayerList().getPlayer(target.get().id());
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.literal("You received ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyUtil.format(amount)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" from ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(sender.getName().getString()).withStyle(ChatFormatting.AQUA)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /eco

    private enum AdminOp {GIVE, TAKE, SET}

    private static int admin(CommandContext<CommandSourceStack> ctx, AdminOp op) {
        CommandSourceStack source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        double amount = ctx.getArgument("amount", Double.class);

        Optional<GameProfile> target = KnownPlayers.resolve(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Unknown player '" + targetName + "'."));
            return 0;
        }
        EconomyManager economy = EconomyManager.get();
        economy.ensureAccount(target.get().id());
        switch (op) {
            case GIVE -> economy.add(target.get().id(), amount);
            case TAKE -> economy.add(target.get().id(), -Math.min(amount, economy.balance(target.get().id())));
            case SET -> economy.set(target.get().id(), amount);
        }
        double newBalance = economy.balance(target.get().id());
        String verb = switch (op) {
            case GIVE -> "Gave " + MoneyUtil.format(amount) + " to ";
            case TAKE -> "Took " + MoneyUtil.format(amount) + " from ";
            case SET -> "Set the balance of ";
        };
        source.sendSuccess(() -> Component.literal(verb)
                        .append(ColorUtils.formatted(target.get().name(), ChatFormatting.AQUA))
                        .append(Component.literal(". New balance: " + MoneyUtil.format(newBalance))),
                true);
        ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(target.get().id());
        if (onlineTarget != null && op != AdminOp.SET) {
            onlineTarget.sendSystemMessage(Component.literal("An admin adjusted your balance: ")
                    .append(Component.literal(MoneyUtil.format(newBalance)).withStyle(ChatFormatting.GOLD)), false);
        }
        return 1;
    }
}
