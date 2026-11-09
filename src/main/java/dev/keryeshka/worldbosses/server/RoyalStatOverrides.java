package dev.keryeshka.worldbosses.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Authoritative World Bosses replacement for Antarchy's [royalBosses] settings.
 * The initial config is seeded from antarchy_mobs.toml (if present) and then
 * owned exclusively by World Bosses. No original TOML files are edited.
 */
public final class RoyalStatOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_PROFILE = "{\"kingHealth\":3400,\"queenHealth\":4300,\"kingAttackDamage\":50,\"queenAttackDamage\":45,\"armor\":20,\"kingArmor\":30,\"followRange\":128,\"movementSpeed\":0.22,\"knockbackResistance\":1,\"stepHeight\":3,\"maxSingleHitDamage\":250,\"hitOverflowRate\":0.25,\"biteReach\":12,\"biteDamageMultiplier\":1.6,\"biteCooldownTicks\":45,\"multiplayerScalingEnabled\":true,\"scalingMaxPlayers\":4,\"healthPerAdditionalPlayer\":0.35,\"damagePerAdditionalPlayer\":0.1,\"royalBoundaryRadius\":192,\"royalBoundaryWarningRadius\":160,\"royalBoundaryGraceTicks\":60,\"queenBeamDamage\":45,\"queenBeamRange\":100,\"queenBeamMinimumRange\":22,\"queenBeamDurationTicks\":30,\"queenBeamWindupTicks\":20,\"queenBeamTravelTicks\":12,\"queenBeamCooldownTicks\":90,\"queenBeamTracking\":0.1,\"queenBeamTerrainRadius\":5,\"queenBeamTerrainCap\":192,\"queenBeamPhaseOneCap\":1,\"queenBeamPhaseTwoCap\":2,\"queenBeamPhaseThreeCap\":3,\"queenBeamPhaseThreeMaxVolleyChance\":0.2,\"kingBeamDamage\":50,\"kingBeamRange\":100,\"kingBeamMinimumRange\":32,\"kingBeamDurationTicks\":30,\"kingBeamWindupTicks\":20,\"kingBeamTravelTicks\":12,\"kingBeamCooldownTicks\":90,\"kingBeamTracking\":0.1,\"kingBeamTerrainRadius\":5,\"kingBeamTerrainCap\":192,\"royalDecreeCooldownTicks\":700,\"kingRoyalMusterCooldownTicks\":500,\"kingRoyalMusterWindupTicks\":30,\"kingRoyalMusterDurationTicks\":600,\"kingRoyalMusterRadius\":32,\"kingRoyalMusterCap\":12,\"queenBlackHoleRadius\":14,\"queenBlackHoleActiveTicks\":120,\"queenBlackHolePullStrength\":0.16,\"queenBlackHoleBlockSuctionCap\":160,\"kingFireballDamage\":48,\"kingFireballRadius\":4.5,\"kingFireballCooldownTicks\":150,\"kingIceballDamage\":18,\"kingIceballRadius\":5,\"kingIceballCooldownTicks\":190,\"kingIceSpikeDamage\":24,\"kingIceSpikeCooldownTicks\":240,\"kingElementalTerrainCap\":256,\"royalBossSoundVolume\":4.5,\"kingGlimmerHealthMultiplier\":1.75,\"kingGlimmerDamageMultiplier\":1.35,\"kingGlimmerCooldownMultiplier\":0.65,\"queenGlimmerHealthMultiplier\":1.75,\"queenGlimmerDamageMultiplier\":1.35,\"queenGlimmerCooldownMultiplier\":0.65}";
    private static volatile JsonObject configured = JsonParser.parseString(DEFAULT_PROFILE).getAsJsonObject();
    private RoyalStatOverrides() {}

    public static void load(Path configDir) {
        Path own = configDir.resolve("world-bosses-royal-stats.json");
        JsonObject output = JsonParser.parseString(DEFAULT_PROFILE).getAsJsonObject();
        try {
            Files.createDirectories(configDir);
            if (Files.isRegularFile(own)) {
                JsonObject saved = JsonParser.parseString(Files.readString(own)).getAsJsonObject();
                for (String key : output.keySet()) {
                    if (saved.has(key) && !saved.get(key).isJsonNull())
                        output.add(key, saved.get(key));
                }
            } else {
                Path original = configDir.resolve("antarchy_mobs.toml");
                if (Files.isRegularFile(original)) {
                    boolean royals = false;
                    for (String raw : Files.readAllLines(original)) {
                        String line = raw.strip();
                        if (line.startsWith("[") && line.endsWith("]")) {
                            royals = "[royalBosses]".equals(line);
                        } else if (royals && !line.startsWith("#") && line.contains("=")) {
                            String[] entry = line.split("=", 2);
                            String key = entry[0].trim();
                            if (output.has(key)) {
                                try {
                                    JsonElement value = JsonParser.parseString(entry[1].split("#", 2)[0].trim());
                                    if (value.isJsonPrimitive()) output.add(key, value);
                                } catch (RuntimeException ignored) {
                                    LOGGER.warn("Invalid imported Antarchy royal stat: {}", key);
                                }
                            }
                        }
                    }
                }
            }
            configured = output;
            Files.writeString(own, GSON.toJson(output));
            LOGGER.info("World Bosses: loaded {} authoritative royal stats from {}",
                    output.size(), own);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Could not load World Bosses royal stats", error);
        }
    }

    public static int intStat(String key, int fallback) {
        try {
            JsonElement value = configured.get(key);
            return value == null ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) { return fallback; }
    }
    public static double doubleStat(String key, double fallback) {
        try {
            JsonElement value = configured.get(key);
            double result = value == null ? fallback : value.getAsDouble();
            return Double.isFinite(result) ? result : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    public static boolean boolStat(String key, boolean fallback) {
        try {
            JsonElement value = configured.get(key);
            return value == null ? fallback : value.getAsBoolean();
        } catch (RuntimeException ignored) { return fallback; }
    }
}
