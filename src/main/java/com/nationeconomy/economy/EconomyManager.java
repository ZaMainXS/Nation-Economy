package com.nationeconomy.economy;

import com.nationeconomy.util.JsonFiles;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds every player's balance. Persisted to {@code <world>/nationeconomy/balances.json}.
 */
public final class EconomyManager {

    /** Balance every brand-new player starts with. */
    public static final double STARTING_BALANCE = 100.0;

    private static final EconomyManager INSTANCE = new EconomyManager();

    private final Map<UUID, Double> balances = new LinkedHashMap<>();
    private Path file;
    private boolean dirty;

    private EconomyManager() {
    }

    public static EconomyManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------- storage

    public void load(Path dir) {
        file = dir.resolve("balances.json");
        balances.clear();
        Data data = JsonFiles.read(file, Data.class);
        if (data != null && data.balances != null) {
            balances.putAll(data.balances);
        }
        dirty = false;
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.balances = new LinkedHashMap<>(balances);
        JsonFiles.write(file, data);
        dirty = false;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    // ------------------------------------------------------------- balance

    /** Creates the starting account when a player joins for the first time. */
    public void ensureAccount(UUID uuid) {
        if (!balances.containsKey(uuid)) {
            balances.put(uuid, STARTING_BALANCE);
            dirty = true;
        }
    }

    public double balance(UUID uuid) {
        ensureAccount(uuid);
        return balances.getOrDefault(uuid, STARTING_BALANCE);
    }

    public double balance(ServerPlayerEntity player) {
        return balance(player.getUuid());
    }

    public void set(UUID uuid, double amount) {
        balances.put(uuid, Math.max(0, amount));
        dirty = true;
    }

    public void add(UUID uuid, double amount) {
        set(uuid, balance(uuid) + amount);
    }

    /** @return {@code true} when the player had enough money and it was withdrawn. */
    public boolean withdraw(UUID uuid, double amount) {
        double current = balance(uuid);
        if (current < amount) {
            return false;
        }
        set(uuid, current - amount);
        return true;
    }

    public boolean has(UUID uuid, double amount) {
        return balance(uuid) >= amount;
    }

    /** Top balances, richest first. */
    public List<Map.Entry<UUID, Double>> top(int count) {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(balances.entrySet());
        entries.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        return entries.subList(0, Math.min(count, entries.size()));
    }

    private static final class Data {
        Map<UUID, Double> balances = new LinkedHashMap<>();
    }
}
