package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** The special items around the nation core. */
public final class CoreItems {

    public static final String HEALER_NAME = "Core Healer";

    private CoreItems() {
    }

    /** The orb item displayed on the core. */
    public static ItemStack coreOrb() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        stack.set(DataComponents.CUSTOM_NAME, ColorUtils.legacy("&b&lNation Core"));
        return stack;
    }

    /** The craftable core healer (recipe in the mod's datapack). */
    public static ItemStack coreHealer() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.empty().setStyle(Style.EMPTY.withItalic(false))
                        .append(Component.literal(HEALER_NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                line("Right-click your nation's core"),
                line("to restore 500 core hits."))));
        return stack;
    }

    private static Component line(String text) {
        return Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** Checks whether a stack is a Core Healer (nether star renamed accordingly). */
    public static boolean isCoreHealer(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.NETHER_STAR)) {
            return false;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && name.getString().equals(HEALER_NAME);
    }
}
