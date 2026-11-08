package dev.keryeshka.seeu.extra.protocol;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

public record ClientOffer(
        int protocolVersion,
        boolean enabled,
        int maximumDistanceBlocks,
        int minimumDistanceBlocks,
        int entityViewScaleQ10,
        Map<String, Integer> entityDistances,
        Set<String> persistentAnimations
) {
    public ClientOffer(int protocolVersion, boolean enabled, int maximumDistanceBlocks,
                       int minimumDistanceBlocks, int entityViewScaleQ10,
                       Map<String, Integer> entityDistances) {
        this(protocolVersion, enabled, maximumDistanceBlocks, minimumDistanceBlocks,
                entityViewScaleQ10, entityDistances, Set.of());
    }
    // Kept for source compatibility with existing API users and tests.
    public ClientOffer(int protocolVersion, boolean enabled, int maximumDistanceBlocks,
                       int minimumDistanceBlocks, int entityViewScaleQ10) {
        this(protocolVersion, enabled, maximumDistanceBlocks, minimumDistanceBlocks,
                entityViewScaleQ10, Map.of(), Set.of());
    }

    public ClientOffer {
        if (protocolVersion != ExtraProtocol.VERSION) {
            throw new IllegalArgumentException("Unsupported protocol version: " + protocolVersion);
        }
        if (maximumDistanceBlocks < 0 || maximumDistanceBlocks > ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS) {
            throw new IllegalArgumentException("Maximum distance is outside the supported range");
        }
        if (minimumDistanceBlocks < 0 || minimumDistanceBlocks > maximumDistanceBlocks) {
            throw new IllegalArgumentException("Minimum distance exceeds the maximum distance");
        }
        if (entityViewScaleQ10 < ExtraProtocol.MIN_ENTITY_VIEW_SCALE_Q10
                || entityViewScaleQ10 > ExtraProtocol.MAX_ENTITY_VIEW_SCALE_Q10) {
            throw new IllegalArgumentException("Entity view scale is outside the supported range");
        }
        if (entityDistances == null || entityDistances.size() > ExtraProtocol.MAX_CONFIGURED_ENTITY_TYPES) {
            throw new IllegalArgumentException("Too many configured entity types");
        }
        TreeMap<String, Integer> normalized = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : entityDistances.entrySet()) {
            String key = entry.getKey();
            Integer range = entry.getValue();
            if (!ResourceIdentifier.isValid(key) || range == null || range < 1
                    || range > maximumDistanceBlocks) {
                throw new IllegalArgumentException("Invalid per-type entity range: " + key);
            }
            normalized.put(key, range);
        }
        entityDistances = Collections.unmodifiableMap(normalized);
        if (persistentAnimations == null || persistentAnimations.size() > ExtraProtocol.MAX_CONFIGURED_ENTITY_TYPES) {
            throw new IllegalArgumentException("Too many persistent animation types");
        }
        TreeSet<String> animationTypes = new TreeSet<>();
        for (String type : persistentAnimations) {
            if (!normalized.containsKey(type)) throw new IllegalArgumentException(
                    "Animation type is not enabled: " + type);
            animationTypes.add(type);
        }
        persistentAnimations = Collections.unmodifiableSet(animationTypes);
    }

    // Absent type means opt-out, not a fallback to the global maximum.
    public int maximumDistanceFor(String typeId) {
        return entityDistances.getOrDefault(typeId, 0);
    }
}
