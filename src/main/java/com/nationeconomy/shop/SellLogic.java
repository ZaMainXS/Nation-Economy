package com.nationeconomy.shop;

import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public static int sell(ServerPlayerEntity player, String itemId, int maxAmount) {
        ShopItem shopItem = ShopManager.get().findSellable(itemId);
        if (shopItem == null || !shopItem.isSellable()) {
            return 0;
        }
        int sold = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size() && sold < maxAmount; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !ShopManager.idOf(stack.getItem()).equals(itemId)) {
                continue;
            }
            int take = Math.min(maxAmount - sold, stack.getCount());
            stack.decrement(take);
            if (stack.isEmpty()) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }
            sold += take;
        }
        if (sold > 0) {
            double earned = sold * shopItem.getSell();
            EconomyManager.get().add(player.getUuid(), earned);
            player.getInventory().markDirty();
            player.sendMessage(Text.literal("Sold ").formatted(Formatting.GRAY)
                    .append(Text.literal(sold + "x ").formatted(Formatting.AQUA))
                    .append(Text.translatable(ShopManager.itemOf(itemId).getTranslationKey()).formatted(Formatting.AQUA))
                    .append(Text.literal(" for ").formatted(Formatting.GRAY))
                    .append(Text.literal(MoneyUtil.format(earned)).formatted(Formatting.GOLD)), false);
        }
        return sold;
    }

    /** Sells every sellable item in the player's inventory. Reports the outcome in chat. */
    public static void sellAll(ServerPlayerEntity player) {
        Map<String, Integer> soldItems = new LinkedHashMap<>();
        double total = 0;

        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = ShopManager.idOf(stack.getItem());
            ShopItem shopItem = ShopManager.get().findSellable(id);
            if (shopItem == null || !shopItem.isSellable()) {
                continue;
            }
            int count = stack.getCount();
            inventory.setStack(slot, ItemStack.EMPTY);
            soldItems.merge(id, count, Integer::sum);
            total += count * shopItem.getSell();
        }

        if (soldItems.isEmpty()) {
            player.sendMessage(Text.literal("Nothing in your inventory can be sold to the shop.").formatted(Formatting.RED), false);
            return;
        }

        EconomyManager.get().add(player.getUuid(), total);
        inventory.markDirty();
        player.sendMessage(Text.literal("— Sold everything —").formatted(Formatting.GOLD), false);
        soldItems.forEach((id, count) -> player.sendMessage(Text.literal("  " + count + "x ")
                .formatted(Formatting.AQUA)
                .append(Text.translatable(ShopManager.itemOf(id).getTranslationKey()).formatted(Formatting.AQUA)), false));
        player.sendMessage(Text.literal("Total: ").formatted(Formatting.GRAY)
                .append(Text.literal(MoneyUtil.format(total)).formatted(Formatting.GOLD))
                .append(Text.literal("   ").append(ColorUtils.formatted("(balance: " + MoneyUtil.format(
                        EconomyManager.get().balance(player.getUuid())) + ")", Formatting.DARK_GRAY))), false);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    /** Sells the stack the player is currently holding. */
    public static void sellHeldItem(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("You are not holding anything.").formatted(Formatting.RED), false);
            return;
        }
        String id = ShopManager.idOf(held.getItem());
        ShopItem shopItem = ShopManager.get().findSellable(id);
        int amount = held.getCount();
        if (shopItem == null || !shopItem.isSellable()) {
            player.sendMessage(Text.translatable(held.getItem().getTranslationKey()).formatted(Formatting.AQUA)
                    .append(Text.literal(" cannot be sold to the shop.").formatted(Formatting.RED)), false);
            return;
        }
        sell(player, id, amount);
    }
}
