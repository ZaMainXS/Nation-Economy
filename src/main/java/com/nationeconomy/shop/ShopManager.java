package com.nationeconomy.shop;

import com.nationeconomy.NationEconomyMod;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.JsonFiles;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holds the shop layout: categories, slots, items and prices.
 * Persisted to {@code <world>/nationeconomy/shop.json} so anything created
 * with {@code /shopadmin} survives restarts.
 */
public final class ShopManager {

    /** Number of category slots inside the main shop menu (0-29). */
    public static final int CATEGORY_SLOTS = 30;

    private static final ShopManager INSTANCE = new ShopManager();

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    private ShopManager() {
    }

    public static ShopManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------- storage

    public void load(Path dir) {
        file = dir.resolve("shop.json");
        categories.clear();
        boolean fileExisted = java.nio.file.Files.isRegularFile(file);
        Data data = JsonFiles.read(file, Data.class);
        if (data != null && data.categories != null && !data.categories.isEmpty()) {
            data.categories.forEach((id, category) -> {
                category.setId(id);
                categories.put(id, category);
            });
        } else if (fileExisted) {
            // The file exists but could not be parsed (or is empty). NEVER
            // overwrite a broken hand-edited shop.json with defaults — an
            // admin typo would otherwise wipe the whole shop. Keep the shop
            // empty, log loudly, and let /sreload try again after the fix.
            NationEconomyMod.LOGGER.error(
                    "{} exists but could not be loaded — shop stays empty until the JSON is fixed (nothing was overwritten).",
                    file);
        } else {
            createDefaults();
            save();
        }
        dirty = false;
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.categories = new LinkedHashMap<>(categories);
        JsonFiles.write(file, data);
        dirty = false;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void markDirty() {
        dirty = true;
    }

    // ---------------------------------------------------------- categories

    public Collection<ShopCategory> categories() {
        return categories.values();
    }

    @Nullable
    public ShopCategory category(String id) {
        return categories.get(id.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public ShopCategory categoryAtSlot(int slot) {
        for (ShopCategory category : categories.values()) {
            if (category.getSlot() == slot) {
                return category;
            }
        }
        return null;
    }

    /** The first unused category slot (0-29), or {@code -1} when all are taken. */
    public int firstFreeSlot() {
        boolean[] used = new boolean[CATEGORY_SLOTS];
        for (ShopCategory category : categories.values()) {
            if (category.getSlot() >= 0 && category.getSlot() < CATEGORY_SLOTS) {
                used[category.getSlot()] = true;
            }
        }
        for (int slot = 0; slot < CATEGORY_SLOTS; slot++) {
            if (!used[slot]) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Creates a new category. The id is derived from the display name
     * (formatting codes stripped), made unique if needed.
     */
    public ShopCategory createCategory(String name, String icon, int slot) {
        String base = ColorUtils.stripLegacy(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "category";
        }
        String id = base;
        int n = 2;
        while (categories.containsKey(id)) {
            id = base + "_" + n++;
        }
        ShopCategory category = new ShopCategory(id, name, icon, slot);
        categories.put(id, category);
        markDirty();
        return category;
    }

    public boolean removeCategory(String id) {
        boolean removed = categories.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    // --------------------------------------------------------------- items

    /** Finds a shop entry selling this item (any category), or {@code null}. */
    @Nullable
    public ShopItem findSellable(String itemId) {
        for (ShopCategory category : categories.values()) {
            ShopItem item = category.getItems().get(itemId);
            if (item != null && item.isSellable()) {
                return item;
            }
        }
        return null;
    }

    /** Resolves an item id (with or without namespace) to an {@link Item}. */
    public static Item itemOf(String itemId) {
        String id = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            NationEconomyMod.LOGGER.warn("Unknown shop item '{}', using stone instead.", itemId);
            return Items.STONE;
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    /** Checks whether an item id points at a real item. */
    public static boolean isValidItem(String itemId) {
        String id = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier);
    }

    /** Canonical id of an item ("minecraft:stone"). */
    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    // ------------------------------------------------------------- defaults

    /** Placeholder categories & prices, created on first run. */
    private void createDefaults() {
        ShopCategory blocks = new ShopCategory("blocks", "&a&lBlocks", "minecraft:grass_block", 10);
        blocks.getItems().put("minecraft:stone", new ShopItem("minecraft:stone", 10, 5));
        blocks.getItems().put("minecraft:cobblestone", new ShopItem("minecraft:cobblestone", 8, 4));
        blocks.getItems().put("minecraft:dirt", new ShopItem("minecraft:dirt", 5, 2));
        blocks.getItems().put("minecraft:grass_block", new ShopItem("minecraft:grass_block", 15, 7));
        blocks.getItems().put("minecraft:oak_log", new ShopItem("minecraft:oak_log", 20, 10));
        blocks.getItems().put("minecraft:glass", new ShopItem("minecraft:glass", 12, 6));
        blocks.getItems().put("minecraft:sand", new ShopItem("minecraft:sand", 5, 2));

        ShopCategory redstone = new ShopCategory("redstonemats", "&c&lRedstone Mats", "minecraft:redstone", 12);
        redstone.getItems().put("minecraft:redstone", new ShopItem("minecraft:redstone", 8, 4));
        redstone.getItems().put("minecraft:redstone_block", new ShopItem("minecraft:redstone_block", 72, 36));
        redstone.getItems().put("minecraft:redstone_torch", new ShopItem("minecraft:redstone_torch", 15, 7));
        redstone.getItems().put("minecraft:repeater", new ShopItem("minecraft:repeater", 60, 30));
        redstone.getItems().put("minecraft:comparator", new ShopItem("minecraft:comparator", 80, 40));
        redstone.getItems().put("minecraft:piston", new ShopItem("minecraft:piston", 100, 50));
        redstone.getItems().put("minecraft:observer", new ShopItem("minecraft:observer", 120, 60));
        redstone.getItems().put("minecraft:hopper", new ShopItem("minecraft:hopper", 90, 45));

        ShopCategory pots = new ShopCategory("pots", "&e&lPots", "minecraft:flower_pot", 14);
        pots.getItems().put("minecraft:flower_pot", new ShopItem("minecraft:flower_pot", 10, 5));
        pots.getItems().put("minecraft:decorated_pot", new ShopItem("minecraft:decorated_pot", 30, 15));
        pots.getItems().put("minecraft:brick", new ShopItem("minecraft:brick", 5, 2));
        pots.getItems().put("minecraft:cauldron", new ShopItem("minecraft:cauldron", 40, 20));
        pots.getItems().put("minecraft:brewing_stand", new ShopItem("minecraft:brewing_stand", 120, 60));

        ShopCategory food = new ShopCategory("food", "&d&lFood", "minecraft:cooked_beef", 16);
        food.getItems().put("minecraft:cooked_beef", new ShopItem("minecraft:cooked_beef", 12, 6));
        food.getItems().put("minecraft:cooked_porkchop", new ShopItem("minecraft:cooked_porkchop", 12, 6));
        food.getItems().put("minecraft:bread", new ShopItem("minecraft:bread", 8, 4));
        food.getItems().put("minecraft:apple", new ShopItem("minecraft:apple", 5, 2));
        food.getItems().put("minecraft:golden_carrot", new ShopItem("minecraft:golden_carrot", 20, 10));
        food.getItems().put("minecraft:carrot", new ShopItem("minecraft:carrot", 3, 1));
        food.getItems().put("minecraft:baked_potato", new ShopItem("minecraft:baked_potato", 4, 2));
        food.getItems().put("minecraft:cake", new ShopItem("minecraft:cake", 40, 20));

        categories.put(blocks.getId(), blocks);
        categories.put(redstone.getId(), redstone);
        categories.put(pots.getId(), pots);
        categories.put(food.getId(), food);
        markDirty();
    }

    private static final class Data {
        Map<String, ShopCategory> categories = new LinkedHashMap<>();
    }
}
