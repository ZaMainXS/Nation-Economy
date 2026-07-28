package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** The special items around the nation core. */
public final class CoreItems {

    public static final String HEALER_NAME = "Core Healer";

    private CoreItems() {
    }

    /** The orb item displayed on the core. */
    public static ItemStack coreOrb() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        stack.set(DataComponentTypes.CUSTOM_NAME, ColorUtils.legacy("&b&lNation Core"));
        return stack;
    }

    /** The craftable core healer (recipe in the mod's datapack). */
    public static ItemStack coreHealer() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.empty().setStyle(Style.EMPTY.withItalic(false))
                        .append(Text.literal(HEALER_NAME).formatted(Formatting.GOLD, Formatting.BOLD)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                line("Right-click your nation's core"),
                line("to restore 500 core hits."))));
        return stack;
    }

    private static Text line(String text) {
        return Text.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Text.literal(text).formatted(Formatting.GRAY));
    }

    /** Checks whether a stack is a Core Healer (nether star renamed accordingly). */
    public static boolean isCoreHealer(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.NETHER_STAR)) {
            return false;
        }
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && name.getString().equals(HEALER_NAME);
    }
}
