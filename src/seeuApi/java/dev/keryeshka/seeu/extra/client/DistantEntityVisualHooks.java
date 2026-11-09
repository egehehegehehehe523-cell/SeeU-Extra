package dev.keryeshka.seeu.extra.client;

import net.minecraft.world.entity.Entity;

/**
 * Signature-only stub of the SeeU Extra Continuation 0.3.0 addon API.
 * Compiled separately and NEVER packaged with World Bosses.
 */
public final class DistantEntityVisualHooks {
    private DistantEntityVisualHooks() {}

    public interface Adapter {
        default boolean suppressNativeDraw() { return false; }
        default double renderMargin(Entity entity) { return 8.0D; }
        default void syncNativeState(Entity source, Entity proxy) {}
        default void detached(Entity proxy) {}
    }

    public static void register(String typeId, Adapter adapter) {
        throw new AssertionError("Compile-only SeeU Extra stub");
    }
}
