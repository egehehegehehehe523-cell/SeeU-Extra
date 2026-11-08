package dev.keryeshka.seeu.extra.protocol;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ClientOffer(
        int protocolVersion,
        boolean enabled,
        int maximumDistanceBlocks,
        int minimumDistanceBlocks,
        int entityViewScaleQ10,
        Map<String, Integer> entityDistances
) {
    // Kept for source compatibility with existing API users and tests.
    public ClientOffer(int protocolVersion, boolean enabled, int maximumDistanceBlocks,
                       int minimumDistanceBlocks, int entityViewScaleQ10) {
        this(protocolVersion, enabled, maximumDistanceBlocks, minimumDistanceBlocks,
                entityViewScaleQ10, Map.of());
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
    }

    // Absent type means opt-out, not a fallback to the global maximum.
    public int maximumDistanceFor(String typeId) {
        return entityDistances.getOrDefault(typeId, 0);
    }
}
