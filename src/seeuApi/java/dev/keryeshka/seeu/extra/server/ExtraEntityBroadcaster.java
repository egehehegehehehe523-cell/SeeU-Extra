package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import net.minecraft.world.entity.Entity;

/** SeeU Extra 0.3.0 broadcaster signature; never shipped with World Bosses. */
public final class ExtraEntityBroadcaster {
    private ExtraEntityBroadcaster() {}
    public static EntitySnapshot captureSnapshot(Entity entity) {
        throw new AssertionError("Compile-only SeeU Extra stub");
    }
}
