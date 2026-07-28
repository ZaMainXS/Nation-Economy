package com.nationeconomy.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.nationeconomy.NationEconomyMod;
import com.nationeconomy.shop.admin.ShopAdminMainGui;
import com.nationeconomy.shop.gui.ShopMainMenu;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Shop related commands:
 * <ul>
 *     <li>{@code /shop} — opens the shop GUI</li>
 *     <li>{@code /sell [item] [amount]} — sells held item or a chosen item</li>
 *     <li>{@code /sellall} — sells everything sellable</li>
 *     <li>{@code /sellhelditem} — sells the stack in your hand</li>
 *     <li>{@code /shopadmin ...} — full admin GUI + category/item commands (ops)</li>
 *     <li>{@code /economycategory create <slot> <name>} — quick category creation (ops)</li>
 *     <li>{@code /economyhanditem add <buy> <sell> [category]} — add held item (ops)</li>
 *     <li>{@code /sreload confirm} — reload shop.json + refresh open shop GUIs (ops)</li>
 * </ul>
 */
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        // /shop
        dispatcher.register(Commands.literal("shop")
                .executes(ShopCommands::openShop));

        // /sell [item] [amount]
        dispatcher.register(Commands.literal("sell")
                .executes(ShopCommands::sellHand)
                .then(Commands.argument("item", ItemArgument.item(registryAccess))
                        .executes(ctx -> sellItem(ctx, Integer.MAX_VALUE))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> sellItem(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))));

        // /sellall
        dispatcher.register(Commands.literal("sellall")
                .executes(ShopCommands::sellAll));

        // /sellhelditem
        dispatcher.register(Commands.literal("sellhelditem")
                .executes(ShopCommands::sellHand));

        // /shopadmin (GUI) + /shopadmin <subcommands>
        dispatcher.register(shopAdmin(registryAccess));

        // /economycategory create <slot> <name> | delete <category>
        // (registered lowercase and Capitalized — command names are case sensitive)
        for (String alias : new String[]{"economycategory", "Economycategory"}) {
            dispatcher.register(Commands.literal(alias)
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("create")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(0, ShopManager.CATEGORY_SLOTS - 1))
                                    .then(Commands.argument("name", StringArgumentType.greedyString())
                                            .executes(ShopCommands::economyCategoryCreate))))
                    .then(Commands.literal("delete")
                            .then(Commands.argument("category", StringArgumentType.word())
                                    .suggests(ShopCommands::suggestCategories)
                                    .executes(ShopCommands::categoryDelete)));
        }

        // /economyhanditem add <buy> <sell> [category]
        for (String alias : new String[]{"economyhanditem", "Economyhanditem"}) {
            dispatcher.register(Commands.literal(alias)
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("add")
                            .then(Commands.argument("buyPrice", DoubleArgumentType.doubleArg(0, 1000000000))
                                    .then(Commands.argument("sellPrice", DoubleArgumentType.doubleArg(-1, 1000000000))
                                            .executes(ctx -> handItemAdd(ctx, null))
                                            .then(Commands.argument("category", StringArgumentType.word())
                                                    .suggests(ShopCommands::suggestCategories)
                                                    .executes(ctx -> handItemAdd(ctx,
                                                            StringArgumentType.getString(ctx, "category")))))))));
        }

        // /sreload [confirm]
        dispatcher.register(Commands.literal("sreload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("Are you sure? Run /sreload confirm to reload the shop data.")
                                    .withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                })
                .then(Commands.literal("confirm")
                        .executes(ShopCommands::shopReload)));
    }

    // ------------------------------------------------------------- players

    private static int openShop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ShopMainMenu.open(player);
        return 1;
    }

    private static int sellHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SellLogic.sellHeldItem(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int sellAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SellLogic.sellAll(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int sellItem(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Item item = ItemArgument.getItem(ctx, "item").createItemStack(1, false).getItem();
        String id = ShopManager.idOf(item);
        int sold = SellLogic.sell(player, id, amount);
        if (sold == 0) {
            ShopItem shopItem = ShopManager.get().findSellable(id);
            if (shopItem == null || !shopItem.isSellable()) {
                ctx.getSource().sendFailure(Component.literal(item.getDescription().getString() + " cannot be sold to the shop."));
            } else {
                ctx.getSource().sendFailure(Component.literal("You don't have any " + item.getDescription().getString() + " to sell."));
            }
        }
        return sold > 0 ? 1 : 0;
    }

    // -------------------------------------------------------------- reload

    private static int shopReload(CommandContext<CommandSourceStack> ctx) {
        if (NationEconomyMod.dataDir() == null) {
            ctx.getSource().sendFailure(Component.literal("Server data not initialized yet."));
            return 0;
        }
        ShopManager.get().load(NationEconomyMod.dataDir());
        // Show the new/updated content to everyone currently browsing.
        ShopAdminMainGui.refreshShops();
        ctx.getSource().sendSuccess(() ->
                Component.literal("Shop reloaded — all open shop GUIs have been refreshed."), true);
        return 1;
    }

    // ------------------------------------------------------ economy admin

    private static int economyCategoryCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        String name = StringArgumentType.getString(ctx, "name");

        ShopManager manager = ShopManager.get();
        ShopCategory existing = manager.categoryAtSlot(slot);
        if (existing != null) {
            source.sendFailure(Component.literal("Slot " + slot + " is already used by category '" + existing.getId()
                    + "'. Delete it first or pick another slot."));
            return 0;
        }
        ShopCategory category = manager.createCategory(name, "minecraft:chest", slot);
        manager.save();
        source.sendSuccess(() -> Component.literal("Created category ")
                .append(Component.literal(category.getId()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" in slot " + slot + ". Add items by holding them and running "
                        + "/economyhanditem add <buy> <sell> " + category.getId() + ".")
                        .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int handItemAdd(CommandContext<CommandSourceStack> ctx, String categoryId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double buy = ctx.getArgument("buyPrice", Double.class);
        double sell = ctx.getArgument("sellPrice", Double.class);

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold the item you want to add in your main hand."));
            return 0;
        }
        String itemId = ShopManager.idOf(held.getItem());
        ShopManager manager = ShopManager.get();

        ShopCategory category;
        if (categoryId != null) {
            category = manager.category(categoryId);
            if (category == null) {
                ctx.getSource().sendFailure(Component.literal("Unknown category '" + categoryId + "'."));
                return 0;
            }
        } else {
            // No category given: use (or create on the spot) the catch-all one.
            category = manager.category("misc");
            if (category == null) {
                int slot = manager.firstFreeSlot();
                if (slot < 0) {
                    ctx.getSource().sendFailure(Component.literal("All 30 category slots are taken! Use /economycategory."));
                    return 0;
                }
                category = manager.createCategory("&7&lMisc", itemId, slot);
                ctx.getSource().sendSuccess(() -> Component.literal("Created the 'misc' category in slot " + slot + ".")
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        String id = category.getId();
        category.getItems().put(itemId, new ShopItem(itemId, buy, sell));
        manager.markDirty();
        manager.save();
        ShopAdminMainGui.refreshShops();
        ShopItem finalItem = category.getItems().get(itemId);
        ctx.getSource().sendSuccess(() -> Component.literal("Added ")
                .append(held.getItem().getDescription().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" to '" + id + "' (buy " + finalItem.getBuy()
                        + ", sell " + finalItem.getSell() + ").")), true);
        return 1;
    }

    // -------------------------------------------------------------- shopAdmin

    private static LiteralArgumentBuilder<CommandSourceStack> shopAdmin(CommandBuildContext registryAccess) {
        return Commands.literal("shopadmin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ShopCommands::openAdminGui)
                .then(Commands.literal("category")
                        .then(Commands.literal("create")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, ShopManager.CATEGORY_SLOTS - 1))
                                        .then(Commands.argument("icon", ItemArgument.item(registryAccess))
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ShopCommands::categoryCreate)))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .executes(ShopCommands::categoryDelete)))
                        .then(Commands.literal("list")
                                .executes(ShopCommands::categoryList)))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .then(Commands.argument("buyPrice", DoubleArgumentType.doubleArg(0, 1000000000))
                                                        .then(Commands.argument("sellPrice", DoubleArgumentType.doubleArg(-1, 1000000000))
                                                                .executes(ShopCommands::itemAdd))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests(ShopCommands::suggestCategories)
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .executes(ShopCommands::itemRemove)))))
                .then(Commands.literal("reload")
                        .executes(ShopCommands::shopReload));
    }

    private static int openAdminGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("The admin GUI can only be opened by an in-game op."));
            return 0;
        }
        ShopAdminMainGui.open(player);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestMatching(
                ShopManager.get().categories().stream().map(ShopCategory::getId).toList(), builder);
    }

    private static int categoryCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        Item icon = ItemArgument.getItem(ctx, "icon").createItemStack(1, false).getItem();
        String name = StringArgumentType.getString(ctx, "name");

        ShopManager manager = ShopManager.get();
        ShopCategory existing = manager.categoryAtSlot(slot);
        if (existing != null) {
            source.sendFailure(Component.literal("Slot " + slot + " is already used by category '" + existing.getId()
                    + "'. Delete it first or pick another slot."));
            return 0;
        }
        ShopCategory category = manager.createCategory(name, ShopManager.idOf(icon), slot);
        manager.save();
        source.sendSuccess(() -> Component.literal("Created category ")
                .append(Component.literal(category.getId()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" in slot " + slot + ". Add items with /shopadmin item add "
                        + category.getId() + " <item> <buy> <sell>.").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int categoryDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        ShopManager manager = ShopManager.get();
        if (manager.category(id) == null) {
            source.sendFailure(Component.literal("Unknown category '" + id + "'."));
            return 0;
        }
        manager.removeCategory(id);
        manager.save();
        source.sendSuccess(() -> Component.literal("Deleted category '" + id + "'."), true);
        return 1;
    }

    private static int categoryList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("—— Shop categories ——").withStyle(ChatFormatting.GOLD), false);
        for (ShopCategory category : ShopManager.get().categories()) {
            source.sendSuccess(() -> Component.literal(" ")
                    .append(Component.literal(category.getId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  slot " + category.getSlot()).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("  icon " + category.getIcon()).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("  " + category.getItems().size() + " items").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int itemAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String categoryId = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        Item item = ItemArgument.getItem(ctx, "item").createItemStack(1, false).getItem();
        double buy = ctx.getArgument("buyPrice", Double.class);
        double sell = ctx.getArgument("sellPrice", Double.class);

        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        if (category == null) {
            source.sendFailure(Component.literal("Unknown category '" + categoryId + "'."));
            return 0;
        }
        String itemId = ShopManager.idOf(item);
        category.getItems().put(itemId, new ShopItem(itemId, buy, sell));
        manager.markDirty();
        manager.save();
        ShopAdminMainGui.refreshShops();
        source.sendSuccess(() -> Component.literal("Added ")
                .append(item.getDescription().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" to '" + categoryId + "' (buy " + buy + ", sell " + sell + ").")), true);
        return 1;
    }

    private static int itemRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String categoryId = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
        Item item = ItemArgument.getItem(ctx, "item").createItemStack(1, false).getItem();

        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        if (category == null || category.getItems().remove(ShopManager.idOf(item)) == null) {
            source.sendFailure(Component.literal("That item is not in category '" + categoryId + "'."));
            return 0;
        }
        manager.markDirty();
        manager.save();
        ShopAdminMainGui.refreshShops();
        source.sendSuccess(() -> Component.literal("Removed ")
                .append(item.getDescription().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" from '" + categoryId + "'.")), true);
        return 1;
    }
}
