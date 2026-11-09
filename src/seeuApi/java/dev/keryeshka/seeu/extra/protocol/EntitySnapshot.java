package dev.keryeshka.seeu.extra.protocol;

import java.util.UUID;

/** SeeU Extra 0.3.0 record ABI; never shipped with World Bosses. */
public record EntitySnapshot(
        UUID uuid, String typeId,
        double x, double y, double z,
        float bodyYaw, float headYaw, float pitch,
        double velocityX, double velocityY, double velocityZ,
        int age, String pose, int flags,
        EquipmentSnapshot mainHand, EquipmentSnapshot offHand,
        EquipmentSnapshot feet, EquipmentSnapshot legs,
        EquipmentSnapshot chest, EquipmentSnapshot head
) {}
