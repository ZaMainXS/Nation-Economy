package com.nationeconomy.nation;

/**
 * A rectangular piece of nation territory: a world plus an inclusive
 * rectangle of block columns (x/z). One "claim block" is one x/z column.
 */
public class Claim {

    private String world = "minecraft:overworld";
    private int x1;
    private int z1;
    private int x2;
    private int z2;

    public Claim() {
    }

    public Claim(String world, int ax, int az, int bx, int bz) {
        this.world = world;
        this.x1 = Math.min(ax, bx);
        this.z1 = Math.min(az, bz);
        this.x2 = Math.max(ax, bx);
        this.z2 = Math.max(az, bz);
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX1() {
        return x1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getZ2() {
        return z2;
    }

    /** Area of this claim in x/z columns = claim blocks used. */
    public long area() {
        return ((long) x2 - x1 + 1) * ((long) z2 - z1 + 1);
    }

    public boolean contains(String worldId, int x, int z) {
        return world.equals(worldId) && x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public boolean intersects(String worldId, Claim other) {
        return world.equals(worldId)
                && x1 <= other.x2 && x2 >= other.x1
                && z1 <= other.z2 && z2 >= other.z1;
    }

    @Override
    public String toString() {
        return "[" + x1 + ", " + z1 + "] -> [" + x2 + ", " + z2 + "] (" + area() + " blocks)";
    }
}
