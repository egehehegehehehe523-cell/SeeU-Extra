package dev.keryeshka.worldbosses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-configured King/Queen options. Independent of SeeU Extra's config. */
public final class WorldBossesOptions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
            WorldBossesOptions options = Files.exists(file)
                    ? GSON.fromJson(Files.readString(file), WorldBossesOptions.class)
                    : new WorldBossesOptions();
            if (options == null) throw new IllegalStateException("World Bosses config is empty");
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
