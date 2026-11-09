package dev.keryeshka.seeu.extra.client;

import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registration-only optional adapters for third-party animated entity types. */
public final class DistantEntityVisualHooks {
    public interface Adapter {
        /** Add-on's renderer mixin suppresses the native model while our proxy is active. */
        default boolean suppressNativeDraw() { return false; }
        default double renderMargin(Entity entity) { return 8.0D; }
        default void syncNativeState(Entity nativeEntity, Entity proxy) {}
        default void detached(Entity proxy) {}
        /** Client render-tick hook for optional addon-specific proxy animation recovery. */
        default void proxyUpdated(Entity proxy, boolean nativePresent) {}
    }
    private static final Map<String, Adapter> ADAPTERS = new ConcurrentHashMap<>();
    private DistantEntityVisualHooks() {}
    public static void register(String typeId, Adapter adapter) {
        if (typeId == null || adapter == null) throw new IllegalArgumentException("Null adapter");
        ADAPTERS.put(typeId, adapter);
    }
    public static Adapter adapter(String typeId) { return ADAPTERS.get(typeId); }
}
