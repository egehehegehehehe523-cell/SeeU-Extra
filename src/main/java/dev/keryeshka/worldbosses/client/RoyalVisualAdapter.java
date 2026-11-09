package dev.keryeshka.worldbosses.client;

import dev.keryeshka.seeu.extra.client.DistantEntityVisualHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Locale;

/**
 * Preserves real Antarchy flying/attack animation state on the persistent
 * SeeU proxy. The Queen's own beam elements and effects remain native.
 */
public final class RoyalVisualAdapter implements DistantEntityVisualHooks.Adapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    private static final Object[] SLOTS;
    private static final Method GET_END;
    private static final Method SET_END;
    private static boolean usable = true;
    private boolean animationRecoveryUsable = true;
    private final Map<Entity, Long> lastAnimationRecoveryCheck = new WeakHashMap<>();

    static {
        Object[] slots = {};
        Method get = null, set = null;
        try {
            Class<?> boss = Class.forName("com.craisinlord.antarchy.content.entity.royal.RoyalBossEntity");
            Class<?> slot = Class.forName("com.craisinlord.antarchy.content.entity.royal.RoyalHead$Slot");
            slots = slot.getEnumConstants();
            get = boss.getMethod("getRoyalBeamEndPosition", slot);
            set = boss.getMethod("setRoyalBeamEndPosition", slot, Vec3.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.warn("Antarchy beam API unavailable; beam endpoints will not be mirrored", ex);
        }
        SLOTS = slots; GET_END = get; SET_END = set;
    }

    @Override public boolean suppressNativeDraw() { return true; }
    @Override public double renderMargin(Entity entity) { return 96.0D; }

    @Override public void syncNativeState(Entity source, Entity proxy) {
        if (source instanceof RoyalFlyingVisualAccess live
                && proxy instanceof RoyalFlyingVisualAccess visual) {
            boolean flying = live.seeuExtra$isRoyalFlying();
            if (visual.seeuExtra$isRoyalFlying() != flying) {
                visual.seeuExtra$setRoyalFlying(flying);
            }
        }
        if (!usable || GET_END == null || SET_END == null) return;
        try {
            for (Object slot : SLOTS) {
                SET_END.invoke(proxy, slot, GET_END.invoke(source, slot));
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            usable = false;
            LOGGER.warn("Disabling royal beam endpoint synchronization", ex);
        }
    }

    /**
     * Recover only a STOPPED flying controller; never restart a running
     * animation, the boss's head attacks, or an intentional ground animation.
     * GeckoLib's synthetic controller can otherwise remain on its bind pose
     * after native/offloaded entity tracking changes.
     */
    @Override public void proxyUpdated(Entity proxy, boolean nativePresent) {
        if (!animationRecoveryUsable
                || !(proxy instanceof RoyalFlyingVisualAccess flying)
                || !flying.seeuExtra$isRoyalFlying()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Long last = lastAnimationRecoveryCheck.get(proxy);
        if (last != null && now >= last && now - last < 20L) return;
        lastAnimationRecoveryCheck.put(proxy, now);
        try {
            Object cache = proxy.getClass().getMethod("getAnimatableInstanceCache").invoke(proxy);
            Object manager = cache.getClass().getMethod("getManagerForId", long.class)
                    .invoke(cache, (long) proxy.getId());
            Object controllers = manager.getClass().getMethod("getAnimationControllers").invoke(manager);
            if (!(controllers instanceof Map<?, ?> byName)) return;
            Object locomotion = byName.get("locomotion");
            if (locomotion == null) return;
            Object state = locomotion.getClass().getMethod("getAnimationState").invoke(locomotion);
            if (state != null && state.toString().toUpperCase(Locale.ROOT).contains("STOP")) {
                locomotion.getClass().getMethod("forceAnimationReset").invoke(locomotion);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            animationRecoveryUsable = false;
            LOGGER.warn("GeckoLib locomotion recovery unavailable for royal visual proxies", exception);
        }
    }

    @Override public void detached(Entity proxy) {
        if (proxy instanceof RoyalFlyingVisualAccess flight
                && !flight.seeuExtra$isRoyalFlying()) {
            flight.seeuExtra$setRoyalFlying(true);
        }
        if (!usable || SET_END == null) return;
        try {
            for (Object slot : SLOTS) {
                SET_END.invoke(proxy, slot, (Object) null);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            usable = false;
            LOGGER.warn("Disabling royal beam endpoint cleanup", ex);
        }
    }
}
