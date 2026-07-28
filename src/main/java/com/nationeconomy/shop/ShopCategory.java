package com.nationeconomy.shop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A shop category shown in the main shop menu.
 *
 * <p>The display {@link #getName() name} may contain legacy formatting codes
 * ({@code &a}, {@code &l}, {@code &#RRGGBB}, ...). The icon is rendered in
 * the configured {@link #getSlot() slot} of the main 30-slot shop menu area.
 */
public class ShopCategory {

    /** Identifier used in commands (lowercase, no formatting codes). */
    private String id = "category";
    /** Display name, may contain & formatting codes. */
    private String name = "Category";
    /** Item id used as the icon in the main menu. */
    private String icon = "minecraft:chest";
    /** Slot within the main shop menu (0-29). */
    private int slot = 0;
    /** item id -> shop item */
    private Map<String, ShopItem> items = new LinkedHashMap<>();

    public ShopCategory() {
    }

    public ShopCategory(String id, String name, String icon, int slot) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.slot = slot;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public Map<String, ShopItem> getItems() {
        return items;
    }

    public void setItems(Map<String, ShopItem> items) {
        this.items = items;
    }
}
