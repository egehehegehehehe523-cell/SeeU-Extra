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
    public boolean queenGlobalRoam = false;
    public boolean globalBossBars = true;
    // Optional HBM integration; the addon keeps native abilities if HBM is absent.
    public boolean hbmAbilities = true;
    // HBM's strengths are actual crater radii, not TNT-damage multipliers.
    // These deliberately tiny royal-boss radii avoid hundreds of LOD updates.
    public static final int DEFAULT_KING_NUCLEAR_STRENGTH = 16;
    public static final int DEFAULT_QUEEN_BALEFIRE_STRENGTH = 12;
    public static final int MAX_KING_NUCLEAR_STRENGTH = 24;
    public static final int MAX_QUEEN_BALEFIRE_STRENGTH = 18;
    public int kingNuclearStrength = DEFAULT_KING_NUCLEAR_STRENGTH;
    public int queenBalefireStrength = DEFAULT_QUEEN_BALEFIRE_STRENGTH;
    public float queenBlackHoleMaxSize = 6.4F; // HBM pull radius = size * 15
    public int queenBlackHoleLifetimeTicks = 100;
    public boolean royalHbmImmunity = true;

    public static volatile boolean kingWander = true;
    public static volatile boolean queenWander = true;
    public static volatile boolean kingRoam = true;
    public static volatile boolean queenRoam = false;
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
            // Existing installs already wrote 150-strength defaults to disk.
            // Merely changing Java field initializers would leave every old
            // world generating the enormous craters reported by players.
            if (options.kingNuclearStrength == 150) {
                options.kingNuclearStrength = DEFAULT_KING_NUCLEAR_STRENGTH;
            }
            if (options.queenBalefireStrength == 150) {
                options.queenBalefireStrength = DEFAULT_QUEEN_BALEFIRE_STRENGTH;
            }
            options.kingNuclearStrength = Math.max(1, Math.min(
                    MAX_KING_NUCLEAR_STRENGTH, options.kingNuclearStrength));
            options.queenBalefireStrength = Math.max(1, Math.min(
                    MAX_QUEEN_BALEFIRE_STRENGTH, options.queenBalefireStrength));
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
