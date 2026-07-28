package com.nationeconomy.shop;

import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared implementation used by {@code /sell}, {@code /sellall},
 * {@code /sellhelditem} and right-clicks inside the shop GUI.
 */
public final class SellLogic {

    private SellLogic() {
    }

    /**
     * Sells up to {@code maxAmount} items of the given id from the player's
     * whole inventory at the shop's sell price.
     *
     * @return how many items were actually sold (0 when nothing was sold).
     */
    public static int sell(ServerPlayer player, String itemId, int maxAmount) {
        ShopItem shopItem = ShopManager.get().findSellable(itemId);
        if (shopItem == null || !shopItem.isSellable()) {
            return 0;
        }
        int sold = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && sold < maxAmount; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ShopManager.idOf(stack.getItem()).equals(itemId)) {
                continue;
            }
            int take = Math.min(maxAmount - sold, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            sold += take;
        }
        if (sold > 0) {
            double earned = sold * shopItem.getSell();
            EconomyManager.get().add(player.getUUID(), earned);
            player.getInventory().setChanged();
            player.sendSystemMessage(Component.literal("Sold ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(sold + "x ").withStyle(ChatFormatting.AQUA))
                    .append(Component.translatable(ShopManager.itemOf(itemId).getDescriptionId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(MoneyUtil.format(earned)).withStyle(ChatFormatting.GOLD)), false);
        }
        return sold;
    }

    /** Sells every sellable item in the player's inventory. Reports the outcome in chat. */
    public static void sellAll(ServerPlayer player) {
        Map<String, Integer> soldItems = new LinkedHashMap<>();
        double total = 0;

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = ShopManager.idOf(stack.getItem());
            ShopItem shopItem = ShopManager.get().findSellable(id);
            if (shopItem == null || !shopItem.isSellable()) {
                continue;
            }
            int count = stack.getCount();
            inventory.setItem(slot, ItemStack.EMPTY);
            soldItems.merge(id, count, Integer::sum);
            total += count * shopItem.getSell();
        }

        if (soldItems.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nothing in your inventory can be sold to the shop.").withStyle(ChatFormatting.RED), false);
            return;
        }

        EconomyManager.get().add(player.getUUID(), total);
        inventory.setChanged();
        player.sendSystemMessage(Component.literal("— Sold everything —").withStyle(ChatFormatting.GOLD), false);
        soldItems.forEach((id, count) -> player.sendSystemMessage(Component.literal("  " + count + "x ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.translatable(ShopManager.itemOf(id).getDescriptionId()).withStyle(ChatFormatting.AQUA)), false));
        player.sendSystemMessage(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyUtil.format(total)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("   ").append(ColorUtils.withStyle("(balance: " + MoneyUtil.format(
                        EconomyManager.get().balance(player.getUUID())) + ")", ChatFormatting.DARK_GRAY))), false);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    /** Sells the stack the player is currently holding. */
    public static void sellHeldItem(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not holding anything.").withStyle(ChatFormatting.RED), false);
            return;
        }
        String id = ShopManager.idOf(held.getItem());
        ShopItem shopItem = ShopManager.get().findSellable(id);
        int amount = held.getCount();
        if (shopItem == null || !shopItem.isSellable()) {
            player.sendSystemMessage(Component.translatable(held.getItem().getDescriptionId()).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" cannot be sold to the shop.").withStyle(ChatFormatting.RED)), false);
            return;
        }
        sell(player, id, amount);
    }
}
