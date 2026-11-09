package dev.keryeshka.worldbosses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-configured King/Queen options. Independent of SeeU Extra's config. */
public final class WorldBossesOptions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // The original Queen release opted out of roaming by default. Migrate
    // those configs once; afterwards a user-selected false remains respected.
    public int configVersion = 2;
    public boolean enableKing = true;
    public boolean enableQueen = true;
    public boolean distantKingDecrees = true;
    public boolean kingIdleWanderFallback = true;
    public boolean queenIdleWanderFallback = true;
    public boolean kingGlobalRoam = true;
    public boolean queenGlobalRoam = true;
    public boolean globalBossBars = true;

    public static volatile boolean kingWander = true;
    public static volatile boolean queenWander = true;
    public static volatile boolean kingRoam = true;
    public static volatile boolean queenRoam = true;
    public static volatile boolean globalBars = true;
    public static volatile boolean kingEnabled = true;
    public static volatile boolean queenEnabled = true;

    public static WorldBossesOptions load(Path configDir) {
        Path file = configDir.resolve("world-bosses-server.json");
        try {
            Files.createDirectories(configDir);
            boolean existing = Files.exists(file);
            String saved = existing ? Files.readString(file) : null;
            WorldBossesOptions options = existing
                    ? GSON.fromJson(saved, WorldBossesOptions.class)
                    : new WorldBossesOptions();
            if (options == null) throw new IllegalStateException("World Bosses config is empty");
            if (existing) {
                JsonObject stored = JsonParser.parseString(saved).getAsJsonObject();
                if (!stored.has("configVersion")) {
                    // Version 1 generated queenGlobalRoam=false automatically.
                    // Upgrade the old default; retain explicit changes made
                    // after this version is written.
                    options.queenGlobalRoam = true;
                }
            }
            options.configVersion = 2;
            kingWander = options.kingIdleWanderFallback;
            queenWander = options.queenIdleWanderFallback;
            kingRoam = options.kingGlobalRoam;
            queenRoam = options.queenGlobalRoam;
            globalBars = options.globalBossBars;
            kingEnabled = options.enableKing;
            queenEnabled = options.enableQueen;
            Files.writeString(file, GSON.toJson(options));
            return options;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Failed loading World Bosses configuration", failure);
        }
    }
}
