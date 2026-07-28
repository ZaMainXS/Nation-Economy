package com.nationeconomy.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nationeconomy.NationEconomyMod;
import com.nationeconomy.shop.gui.ShopMainMenu;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Shop related commands:
 * <ul>
 *     <li>{@code /shop} — opens the shop GUI</li>
 *     <li>{@code /sell [item] [amount]} — sells held item or a chosen item</li>
 *     <li>{@code /sellall} — sells everything sellable</li>
 *     <li>{@code /sellhelditem} — sells the stack in your hand</li>
 *     <li>{@code /shopadmin ...} — category/item management for server ops</li>
 * </ul>
 */
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        // /shop
        dispatcher.register(CommandManager.literal("shop")
                .executes(ShopCommands::openShop));

        // /sell [item] [amount]
        dispatcher.register(CommandManager.literal("sell")
                .executes(ShopCommands::sellHand)
                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                        .executes(ctx -> sellItem(ctx, Integer.MAX_VALUE))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> sellItem(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))));

        // /sellall
        dispatcher.register(CommandManager.literal("sellall")
                .executes(ShopCommands::sellAll));

        // /sellhelditem
        dispatcher.register(CommandManager.literal("sellhelditem")
                .executes(ShopCommands::sellHand));

        // /shopadmin
        dispatcher.register(shopAdmin(registryAccess));
    }

    // ------------------------------------------------------------- players

    private static int openShop(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ShopMainMenu.open(player);
        return 1;
    }

    private static int sellHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        SellLogic.sellHeldItem(ctx.getSource().getPlayerOrThrow());
        return 1;
    }

    private static int sellAll(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        SellLogic.sellAll(ctx.getSource().getPlayerOrThrow());
        return 1;
    }

    private static int sellItem(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Item item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();
        String id = ShopManager.idOf(item);
        int sold = SellLogic.sell(player, id, amount);
        if (sold == 0) {
            ShopItem shopItem = ShopManager.get().findSellable(id);
            if (shopItem == null || !shopItem.isSellable()) {
                ctx.getSource().sendError(Text.literal(item.getName().getString() + " cannot be sold to the shop."));
            } else {
                ctx.getSource().sendError(Text.literal("You don't have any " + item.getName().getString() + " to sell."));
            }
        }
        return sold > 0 ? 1 : 0;
    }

    // -------------------------------------------------------------- admins

    private static LiteralArgumentBuilder<ServerCommandSource> shopAdmin(CommandRegistryAccess registryAccess) {
        return CommandManager.literal("shopadmin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("category")
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, ShopManager.CATEGORY_SLOTS - 1))
                                        .then(CommandManager.argument("icon", ItemStackArgumentType.itemStack(registryAccess))
                                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                        .executes(ShopCommands::categoryCreate)))))
                        .then(CommandManager.literal("delete")
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .executes(ShopCommands::categoryDelete)))
                        .then(CommandManager.literal("list")
                                .executes(ShopCommands::categoryList)))
                .then(CommandManager.literal("item")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                                .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0, 1000000000))
                                                        .then(CommandManager.argument("sellPrice", DoubleArgumentType.doubleArg(-1, 1000000000))
                                                                .executes(ShopCommands::itemAdd))))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                                .executes(ShopCommands::itemRemove)))))
                .then(CommandManager.literal("reload")
                        .executes(ShopCommands::reload));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCategories(
            CommandContext<ServerCommandSource> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
                ShopManager.get().categories().stream().map(ShopCategory::getId).toList(), builder);
    }

    private static int categoryCreate(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        Item icon = ItemStackArgumentType.getItemStackArgument(ctx, "icon").getItem();
        String name = StringArgumentType.getString(ctx, "name");

        ShopManager manager = ShopManager.get();
        ShopCategory existing = manager.categoryAtSlot(slot);
        if (existing != null) {
            source.sendError(Text.literal("Slot " + slot + " is already used by category '" + existing.getId()
                    + "'. Delete it first or pick another slot."));
            return 0;
        }
        ShopCategory category = manager.createCategory(name, ShopManager.idOf(icon), slot);
        manager.save();
        source.sendFeedback(() -> Text.literal("Created category ")
                .append(Text.literal(category.getId()).formatted(Formatting.AQUA))
                .append(Text.literal(" in slot " + slot + ". Add items with /shopadmin item add "
                        + category.getId() + " <item> <buy> <sell>.")), false);
        return 1;
    }

    private static int categoryDelete(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        ShopManager manager = ShopManager.get();
        if (!manager.removeCategory(id)) {
            source.sendError(Text.literal("Unknown category '" + id + "'."));
            return 0;
        }
        manager.save();
        source.sendFeedback(() -> Text.literal("Deleted category '" + id + "'."), true);
        return 1;
    }

    private static int categoryList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("—— Shop categories ——").formatted(Formatting.GOLD), false);
        for (ShopCategory category : ShopManager.get().categories()) {
            source.sendFeedback(() -> Text.literal(" ")
                    .append(Text.literal(category.getId()).formatted(Formatting.AQUA))
                    .append(Text.literal("  slot " + category.getSlot()).formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("  icon " + category.getIcon()).formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("  " + category.getItems().size() + " items").formatted(Formatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int itemAdd(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String categoryId = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        Item item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();
        double buy = ctx.getArgument("buyPrice", Double.class);
        double sell = ctx.getArgument("sellPrice", Double.class);

        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        if (category == null) {
            source.sendError(Text.literal("Unknown category '" + categoryId + "'."));
            return 0;
        }
        String itemId = ShopManager.idOf(item);
        category.getItems().put(itemId, new ShopItem(itemId, buy, sell));
        manager.markDirty();
        manager.save();
        source.sendFeedback(() -> Text.literal("Added ")
                .append(item.getName().copy().formatted(Formatting.AQUA))
                .append(Text.literal(" to '" + categoryId + "' (buy " + buy + ", sell " + sell + ").")), true);
        return 1;
    }

    private static int itemRemove(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String categoryId = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        Item item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();

        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        if (category == null || category.getItems().remove(ShopManager.idOf(item)) == null) {
            source.sendError(Text.literal("That item is not in category '" + categoryId + "'."));
            return 0;
        }
        manager.markDirty();
        manager.save();
        source.sendFeedback(() -> Text.literal("Removed ")
                .append(item.getName().copy().formatted(Formatting.AQUA))
                .append(Text.literal(" from '" + categoryId + "'.")), true);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        if (NationEconomyMod.dataDir() == null) {
            ctx.getSource().sendError(Text.literal("Server data not initialized yet."));
            return 0;
        }
        ShopManager.get().load(NationEconomyMod.dataDir());
        ctx.getSource().sendFeedback(() -> Text.literal("Reloaded the shop configuration from shop.json."), true);
        return 1;
    }
}
