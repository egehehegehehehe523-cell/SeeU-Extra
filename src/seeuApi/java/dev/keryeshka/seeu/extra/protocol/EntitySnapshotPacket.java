package dev.keryeshka.seeu.extra.protocol;

import java.util.List;

/** SeeU Extra 0.3.0 packet record ABI; never shipped with World Bosses. */
public record EntitySnapshotPacket(
        String dimensionKey, long sequence, int updateIntervalTicks,
        boolean full, List<EntitySnapshot> snapshots
) {}
