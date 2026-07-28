package com.nationeconomy.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.nationeconomy.shop.gui.GuiElements;
import com.nationeconomy.util.ColorUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * The in-game manual ({@code /nationalexplain}) — a paged GUI that explains
 * the whole mod: economy, shop, nations, claiming, homes, combat, raiding
 * and the admin tools.
 */
public class GuideGui extends ChestMenu {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;
    private static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private static final Component TITLE = ColorUtils.legacy("&6&l✦ Guide ✦");

    private final SimpleContainer inventory;
    private int page;

    // -------------------------------------------------------------- command

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nationalexplain")
                .executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException(), 0);
                    return 1;
                }));
    }

    public static void open(ServerPlayer player, int page) {
        SimpleContainer inventory = new SimpleContainer(SIZE);
        int targetPage = page;
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new GuideGui(syncId, playerInventory, inventory, targetPage),
                TITLE));
    }

    private GuideGui(int syncId, Inventory playerInventory, SimpleContainer inventory, int page) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.page = Math.max(0, Math.min(page, PAGES.size() - 1));
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }

        Page content = PAGES.get(page);
        int index = 0;
        for (Entry entry : content.entries) {
            if (index >= ENTRY_SLOTS.length) {
                break;
            }
            ItemStack stack = new ItemStack(entry.icon);
            GuiElements.name(stack, Component.literal(entry.name).withStyle(ChatFormatting.GOLD));
            GuiElements.lore(stack, java.util.Arrays.stream(entry.lore)
                    .map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY))
                    .map(text -> (Component) text)
                    .toList());
            inventory.setItem(ENTRY_SLOTS[index++], stack);
        }

        ItemStack header = new ItemStack(content.icon);
        GuiElements.name(header, Component.literal(content.title).withStyle(ChatFormatting.GOLD));
        GuiElements.lore(header, List.of(Component.literal("Page " + (page + 1) + " / " + PAGES.size())
                .withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(4, header);

        ItemStack previous = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(previous, Component.literal("« Previous Page").withStyle(ChatFormatting.AQUA));
        inventory.setItem(SLOT_PREVIOUS, previous);

        ItemStack indicator = new ItemStack(Items.BOOK);
        GuiElements.name(indicator, Component.literal("Page " + (page + 1) + " / " + PAGES.size()).withStyle(ChatFormatting.GRAY));
        inventory.setItem(49, indicator);

        ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(next, Component.literal("Next Page »").withStyle(ChatFormatting.AQUA));
        inventory.setItem(SLOT_NEXT, next);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Component.literal("Close").withStyle(ChatFormatting.RED));
        inventory.setItem(SLOT_CLOSE, close);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }
        switch (slotIndex) {
            case SLOT_PREVIOUS -> {
                if (page > 0) {
                    page--;
                    refresh();
                    serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
            }
            case SLOT_NEXT -> {
                if (page < PAGES.size() - 1) {
                    page++;
                    refresh();
                    serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
            }
            case SLOT_CLOSE -> serverPlayer.closeContainer();
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // -------------------------------------------------------------- content

    private record Entry(net.minecraft.item.Item icon, String name, String[] lore) {
    }

    private record Page(net.minecraft.item.Item icon, String title, List<Entry> entries) {
    }

    private static Entry e(net.minecraft.item.Item icon, String name, String... lore) {
        return new Entry(icon, name, lore);
    }

    private static final List<Page> PAGES = List.of(
            new Page(Items.NETHER_STAR, "Welcome to Nation & Economy", List.of(
                    e(Items.GOLD_INGOT, "Economy", "A full shop with configurable", "categories, buy/sell prices,", "/pay, /balance and more."),
                    e(Items.SHIELD, "Nations", "Found a nation, claim land, pick", "your color, defend your core."),
                    e(Items.MAP, "/nation map", "View the territory map in chat,", "hover the squares for details."),
                    e(Items.BOOK, "This guide", "Click the arrows to browse", "every system step by step."))),
            new Page(Items.GOLD_INGOT, "Money & Selling", List.of(
                    e(Items.SUNFLOWER, "/balance", "Check your money."),
                    e(Items.PAPER, "/pay <player> <amount>", "Send money to someone."),
                    e(Items.HOPPER, "/sellall", "Sell everything sellable", "in your inventory."),
                    e(Items.ARMOR_STAND, "/sellhelditem", "Sell the stack in your hand."),
                    e(Items.ITEM_FRAME, "/sell <item> [amount]", "Sell a specific item."),
                    e(Items.DRAGON_EGG, "/baltop", "See the richest players."))),
            new Page(Items.CHEST, "Using the Shop", List.of(
                    e(Items.CHEST, "/shop", "Opens the category menu.", "The 30 slots hold categories", "configured by the admins."),
                    e(Items.GREEN_DYE, "Buying", "Hover shows the price.", "Left-click: buy 1.", "Shift-left-click: buy a stack."),
                    e(Items.ORANGE_DYE, "Selling", "Right-click: sell 1.", "Shift-right-click: sell all", "you own of that item."),
                    e(Items.CLOCK, "Combat lock", "While in combat you", "cannot buy anything."))),
            new Page(Items.WHITE_BANNER, "Nations Basics", List.of(
                    e(Items.OAK_SIGN, "/nation create <name>", "Found your nation.", "One nation per player.", "A core spawns where you stand."),
                    e(Items.OPEN_EYEBLOSSOM, "/nation join <name>", "Join an existing nation.", "Banished players can't rejoin."),
                    e(Items.WATER_BUCKET, "/nation color <color>", "Pick any color:", "red, navy, pink... or #RRGGBB.", "It colors chat, tab and the map."),
                    e(Items.PLAYER_HEAD, "/nation members", "See who's in your nation."),
                    e(Items.BOOK, "/nation info / list", "Details about any nation."))),
            new Page(Items.GOLDEN_SHOVEL, "Claiming Land", List.of(
                    e(Items.GOLDEN_SHOVEL, "/claimland", "Get the claim shovel.", "Left-click = corner 1,", "right-click = corner 2."),
                    e(Items.FILLED_MAP, "/claimland confirm", "Claims the selected area.", "It costs 1 block per column."),
                    e(Items.SPYGLASS, "Limits", "Every nation starts with", "1,000,000 blocks (1000x1000)."),
                    e(Items.NETHERITE_INGOT, "/nation upgrade", "Spend 1 netherite ingot", "to gain +100 claim blocks."),
                    e(Items.BARRIER, "/nation unclaim", "Remove the claim you", "are standing in (leader)."))),
            new Page(Items.TRIAL_KEY, "Permissions & Access", List.of(
                    e(Items.CHEST, "Outsiders", "Can't break, place, open chests", "or use anything in your land."),
                    e(Items.LIME_DYE, "/nation allow access", "<player> <break|place|chest|use|all>", "Grants that permission."),
                    e(Items.RED_DYE, "/nation deny access", "Revokes a permission again."),
                    e(Items.BOOK, "/nation trusted", "Lists who has access."),
                    e(Items.SKELETON_SKULL, "/nation banish <player>", "Kick & ban someone", "from your nation (leader)."))),
            new Page(Items.RED_BED, "Nation Homes", List.of(
                    e(Items.RED_BED, "/nation sethome [slot]", "Set a nation home (max 3)."),
                    e(Items.ENDER_PEARL, "/nation home [slot]", "Teleport to a nation home."),
                    e(Items.CLAY_BALL, "/nation delhome <slot>", "Remove one."),
                    e(Items.CLOCK, "Combat lock", "You can't teleport", "home while in combat."))),
            new Page(Items.DIAMOND_SWORD, "The Combat System", List.of(
                    e(Items.DIAMOND_SWORD, "30 seconds", "Hitting (or getting hit by) a", "player tags you for 30s."),
                    e(Items.BARRIER, "While tagged", "• no buying from the shop", "• no /nation home"),
                    e(Items.SKELETON_SKULL, "Logging out", "Quitting while tagged kills", "you and drops your items."),
                    e(Items.CLOCK, "Crash? Your timer stays", "If your game crashes, the", "30s timer keeps running for", "your return."))),
            new Page(Items.IRON_PICKAXE, "Raiding Nations", List.of(
                    e(Items.STONE_PICKAXE, "1,000 hits per block", "You can break into foreign land,", "but every block needs 1000 hits."),
                    e(Items.NETHER_STAR, "The Nation Core", "An orb floating above a block", "spawns with every nation."),
                    e(Items.DIAMOND_AXE, "10,000 core hits", "Destroy the core and the whole", "nation falls — announced publicly."),
                    e(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Core Healer", "Craft: ancient debris, diamond", "blocks, netherite upgrades,", "gold blocks. Right-click the", "core: +500 hits."))),
            new Page(Items.COMMAND_BLOCK, "Admin Tools (Ops)", List.of(
                    e(Items.COMMAND_BLOCK, "/shopadmin", "Opens the admin GUI:", "categories, items, prices,", "slots, icons, names."),
                    e(Items.OAK_SIGN, "/economycategory create", "<slot> <name> — quick category."),
                    e(Items.EMERALD, "/economyhanditem add", "<buy> <sell> — adds the item", "you are holding."),
                    e(Items.CLOCK, "/sreload confirm", "Reloads shop.json and", "refreshes open shop GUIs."),
                    e(Items.GOLD_BLOCK, "/eco give/take/set", "Manage player balances."))));

}
