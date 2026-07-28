package com.nationeconomy.shop;

/**
 * One tradeable entry inside a shop category.
 *
 * <p>{@code buy} is the price per item when buying from the shop;
 * {@code sell} is the price per item when selling to the shop.
 * A negative value disables that action.
 */
public class ShopItem {

    private String item = "minecraft:stone";
    private double buy = 0;
    private double sell = 0;

    public ShopItem() {
    }

    public ShopItem(String item, double buy, double sell) {
        this.item = item;
        this.buy = buy;
        this.sell = sell;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public double getBuy() {
        return buy;
    }

    public void setBuy(double buy) {
        this.buy = buy;
    }

    public double getSell() {
        return sell;
    }

    public void setSell(double sell) {
        this.sell = sell;
    }

    public boolean isBuyable() {
        return buy >= 0;
    }

    public boolean isSellable() {
        return sell >= 0;
    }
}
