package dev.keryeshka.worldbosses.server;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Read a native centre-head beam's final aim and unexpired firing duration
 * before replacing the entity with the persistent flight simulation.
 */
public interface RoyalPendingBeamAccess {
    @Nullable ContinuedBeam worldbosses$capturePendingBeam();

    record ContinuedBeam(Vec3 impact, int remainingTicks) {}
}
