package com.nationeconomy.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nationeconomy.NationEconomyMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny JSON persistence helper shared by the data managers. */
public final class JsonFiles {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() {
    }

    /** Reads and deserializes a JSON file, or returns {@code null} when missing/unparseable. */
    public static <T> T read(Path file, Class<T> type) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception e) {
            NationEconomyMod.LOGGER.error("Failed to read {}: {}", file, e.toString());
            return null;
        }
    }

    /** Serializes an object to a JSON file, creating parent directories as needed. */
    public static void write(Path file, Object data) {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            NationEconomyMod.LOGGER.error("Failed to write {}: {}", file, e.toString());
        }
    }
}
