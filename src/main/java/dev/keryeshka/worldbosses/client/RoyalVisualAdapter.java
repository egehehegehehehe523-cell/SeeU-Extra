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
    private boolean distantShootWarningLogged;
    private final Map<Entity, Long> lastAnimationRecoveryCheck = new WeakHashMap<>();
    private final Map<Entity, Boolean> distantBeamApplied = new WeakHashMap<>();


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
        syncDistantGroundBeam(proxy, nativePresent);
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

    /**
     * SeeU already draws Antarchy's textured beam whenever a centre-head
     * endpoint is present on the synthetic boss. Reuse that exact renderer
     * for remote nuclear strikes; no new shader pass or Voxy mixin.
     */
    private void syncDistantGroundBeam(Entity proxy, boolean nativePresent) {
        if (!usable || SET_END == null || GET_END == null) return;
        Object centre = null;
        for (Object slot : SLOTS) {
            if (slot instanceof Enum<?> named && "CENTER".equals(named.name())) {
                centre = slot;
                break;
            }
        }
        if (centre == null) return;
        Vec3 impact = RoyalStrikeClient.impact(proxy.getUUID());
        if (impact != null && !nativePresent
                && RoyalStrikeClient.needsShootAnimation(proxy.getUUID())) {
            triggerDistantShootWhenReady(proxy);
        }
        try {
            if (impact != null) {
                // Native beams retain priority while the actual Antarchy
                // entity is engaged; otherwise the persistent strike renders.
                Vec3 nativeEndpoint = nativePresent
                        ? (Vec3) GET_END.invoke(proxy, centre) : null;
                if (nativeEndpoint == null) {
                    SET_END.invoke(proxy, centre, impact);
                    distantBeamApplied.put(proxy, Boolean.TRUE);
                } else {
                    // A real combat beam always wins; don't override its
                    // endpoint with the older offloaded ground strike.
                    distantBeamApplied.remove(proxy);
                }
            } else if (distantBeamApplied.remove(proxy) != null && !nativePresent) {
                SET_END.invoke(proxy, centre, (Object) null);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            usable = false;
            LOGGER.warn("Disabling distant royal beam endpoint rendering", failure);
        }
    }

    /**
     * Antarchy's setRoyalBeamEndPosition only draws geometry. The actual
     * shoot_1 animation is a separate triggerable GeckoLib controller on the
     * centre head. Trigger that controller LOCALLY on SeeU's synthetic boss:
     * calling GeoEntity.triggerAnim would send a tracking packet from the
     * server and cannot reach this offloaded client-only entity.
     *
     * GeckoLib initializes controllers on first render. Don't consume the
     * one-shot event until head_center really exists.
     */
    private void triggerDistantShootWhenReady(Entity proxy) {
        try {
            Object cache = proxy.getClass().getMethod("getAnimatableInstanceCache").invoke(proxy);
            Object manager = cache.getClass().getMethod("getManagerForId", long.class)
                    .invoke(cache, (long) proxy.getId());
            Object controllers = manager.getClass().getMethod("getAnimationControllers").invoke(manager);
            if (!(controllers instanceof Map<?, ?> byName) || !byName.containsKey("head_center")) {
                return;
            }
            manager.getClass().getMethod("tryTriggerAnimation", String.class, String.class)
                    .invoke(manager, "head_center", "shoot");
            RoyalStrikeClient.acknowledgeShootAnimation(proxy.getUUID());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!distantShootWarningLogged) {
                distantShootWarningLogged = true;
                LOGGER.warn("Could not trigger GeckoLib centre-head animation for distant royal beam", failure);
            }
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
