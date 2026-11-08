package dev.keryeshka.worldbosses.client;

import dev.keryeshka.seeu.extra.client.DistantEntityVisualHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;

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
