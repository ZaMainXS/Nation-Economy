package com.nationeconomy.shop.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;

import java.util.List;

/** Shared building blocks for the server-side shop menus. */
public final class GuiElements {

    private GuiElements() {
    }

    /** Empty gray filler pane (blank name, hidden tooltip). */
    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        stack.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    /**
     * Wraps text so it renders without the default italic style item
     * tooltips apply to custom names and lore.
     */
    public static Text noItalic(Text text) {
        return Text.empty().setStyle(Style.EMPTY.withItalic(false)).append(text);
    }

    /** Sets the display name of a stack (non-italic). */
    public static void name(ItemStack stack, Text name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, noItalic(name));
    }

    /** Sets the lore lines of a stack (non-italic). */
    public static void lore(ItemStack stack, List<Text> lines) {
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines.stream().map(GuiElements::noItalic).toList()));
    }
}
