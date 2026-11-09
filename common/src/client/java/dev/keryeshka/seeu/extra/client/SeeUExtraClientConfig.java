package dev.keryeshka.seeu.extra.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.keryeshka.seeu.extra.ResourceIdentifier;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityViewScale;
import dev.keryeshka.seeu.extra.protocol.ExtraProtocol;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

public final class SeeUExtraClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "seeu-extra-client.json";

    public int configVersion = 3;
    public boolean enabled = true;
    public int maximumDistanceBlocks = ExtraServerSettings.DEFAULT_MAXIMUM_DISTANCE_BLOCKS;
    public int minimumDistanceBlocks = ExtraServerSettings.DEFAULT_MINIMUM_DISTANCE_BLOCKS;
    // An absent entity ID is disabled. This map contains ONLY opted-in entity types.
    public Map<String, Integer> entityDistances = new TreeMap<>();
    public Set<String> persistentAnimations = new TreeSet<>();
    /** Entity defaults installed once by registered addons; user F8 choices win later. */
    public Set<String> initializedAddonDefaults = new TreeSet<>();
    private transient Path configPath;

    public static SeeUExtraClientConfig load(Path configDirectory) {
        Path path = configDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            SeeUExtraClientConfig config = Files.exists(path)
                    ? GSON.fromJson(Files.readString(path), SeeUExtraClientConfig.class)
                    : new SeeUExtraClientConfig();
            if (config == null) {
                throw new IllegalStateException("Config is empty: " + path);
            }
            config.configPath = path;
            config.sanitize();
            config.save();
            return config;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load config: " + path, exception);
        }
    }

    public void save() {
        if (configPath == null) {
            throw new IllegalStateException("Config was not loaded from a file");
        }
        sanitize();
        try {
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config: " + configPath, exception);
        }
    }

    /** Idempotent first-install preset. Existing F8 selections are not changed. */
    public void enableByDefaultOnce(String typeId, int rangeBlocks) {
        if (!ResourceIdentifier.isValid(typeId) || rangeBlocks <= 0) return;
        if (initializedAddonDefaults == null) initializedAddonDefaults = new TreeSet<>();
        if (!initializedAddonDefaults.add(typeId)) return;
        // A pre-existing explicit distance is never overwritten.
        if (!isEntityEnabled(typeId) && maximumDistanceBlocks > 0) {
            setEntityEnabled(typeId, true);
            if (isEntityEnabled(typeId)) {
                setEntityDistance(typeId, Math.min(rangeBlocks, maximumDistanceBlocks));
                setPersistentAnimation(typeId, true);
            }
        }
        save();
    }

    public boolean isEntityEnabled(String typeId) {
        return entityDistances != null && entityDistances.containsKey(typeId);
    }

    public boolean hasPersistentAnimation(String typeId) {
        return persistentAnimations != null && persistentAnimations.contains(typeId);
    }

    public void setPersistentAnimation(String typeId, boolean enabled) {
        if (!isEntityEnabled(typeId)) return;
        if (persistentAnimations == null) persistentAnimations = new TreeSet<>();
        if (enabled) persistentAnimations.add(typeId);
        else persistentAnimations.remove(typeId);
    }

    public int entityDistance(String typeId) {
        return entityDistances == null ? 0 : entityDistances.getOrDefault(typeId, 0);
    }

    public int defaultEntityDistance() {
        return maximumDistanceBlocks;
    }

    public void setEntityEnabled(String typeId, boolean enabled) {
        if (!ResourceIdentifier.isValid(typeId)) {
            return;
        }
        if (enabled) {
            if (entityDistances.size() < ExtraProtocol.MAX_CONFIGURED_ENTITY_TYPES) {
                entityDistances.putIfAbsent(typeId, Math.max(1, maximumDistanceBlocks));
            }
        } else {
            entityDistances.remove(typeId);
            persistentAnimations.remove(typeId);
        }
    }

    public void setEntityDistance(String typeId, int distanceBlocks) {
        if (isEntityEnabled(typeId) && distanceBlocks >= 1 && distanceBlocks <= maximumDistanceBlocks) {
            entityDistances.put(typeId, distanceBlocks);
        }
    }

    public ClientOffer offer() {
        sanitize();
        Minecraft minecraft = Minecraft.getInstance();
        int entityViewScaleQ10 = EntityViewScale.fromOptions(
                minecraft.options.getEffectiveRenderDistance(),
                minecraft.options.entityDistanceScaling().get()
        );
        return new ClientOffer(
                ExtraProtocol.VERSION,
                enabled,
                maximumDistanceBlocks,
                minimumDistanceBlocks,
                entityViewScaleQ10,
                entityDistances,
                persistentAnimations
        );
    }

    private void sanitize() {
        configVersion = 3;
        maximumDistanceBlocks = clamp(maximumDistanceBlocks, 0, ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS);
        minimumDistanceBlocks = clamp(minimumDistanceBlocks, 0, maximumDistanceBlocks);
        Map<String, Integer> normalized = new TreeMap<>();
        if (entityDistances != null) {
            for (Map.Entry<String, Integer> entry : entityDistances.entrySet()) {
                if (normalized.size() >= ExtraProtocol.MAX_CONFIGURED_ENTITY_TYPES) {
                    break;
                }
                Integer distance = entry.getValue();
                if (ResourceIdentifier.isValid(entry.getKey()) && distance != null
                        && distance > 0 && maximumDistanceBlocks > 0) {
                    normalized.put(entry.getKey(), clamp(distance, 1, maximumDistanceBlocks));
                }
            }
        }
        entityDistances = normalized;
        Set<String> validatedAnimations = new TreeSet<>();
        if (persistentAnimations != null) {
            for (String type : persistentAnimations) {
                if (normalized.containsKey(type)) validatedAnimations.add(type);
            }
        }
        persistentAnimations = validatedAnimations;
        // Persist first-install markers even when an entity was disabled in F8.
        Set<String> validDefaults = new TreeSet<>();
        if (initializedAddonDefaults != null) {
            for (String type : initializedAddonDefaults) {
                if (ResourceIdentifier.isValid(type)) validDefaults.add(type);
            }
        }
        initializedAddonDefaults = validDefaults;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
