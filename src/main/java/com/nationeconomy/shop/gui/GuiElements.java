package com.nationeconomy.shop.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;

import java.util.List;

/** Shared building blocks for the server-side shop menus. */
public final class GuiElements {

    private GuiElements() {
    }

    /** Empty gray filler pane (blank name, hidden tooltip). */
    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        stack.set(DataComponents.TOOLTIP_DISPLAY,
                new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        return stack;
    }

    /**
     * Wraps text so it renders without the default italic style item
     * tooltips apply to custom names and lore.
     */
    public static Component noItalic(Component text) {
        return Component.empty().setStyle(Style.EMPTY.withItalic(false)).append(text);
    }

    /** Sets the display name of a stack (non-italic). */
    public static void name(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, noItalic(name));
    }

    /** Sets the lore lines of a stack (non-italic). */
    public static void lore(ItemStack stack, List<Component> lines) {
        stack.set(DataComponents.LORE, new ItemLore(lines.stream().map(GuiElements::noItalic).toList()));
    }
}
