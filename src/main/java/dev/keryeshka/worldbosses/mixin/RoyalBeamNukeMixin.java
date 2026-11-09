package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalHbmCompat;
import dev.keryeshka.worldbosses.server.RoyalPendingBeamAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The centre-head beam is still Antarchy's native fixed-aim beam. Remember
 * only its actual hitpoint, and trigger HBM exactly once on normal decay.
 * Early cancelled beams never reach their configured firing duration.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.RoyalBossEntity", remap = false)
public abstract class RoyalBeamNukeMixin implements RoyalPendingBeamAccess {
    @Unique private Vec3 worldbosses$centerHit;
    @Unique private int worldbosses$firstBeamTick = -1;
    @Unique private int worldbosses$lastBeamTick = -1;

    @Inject(method = "setRoyalBeamEndPosition(Lcom/craisinlord/antarchy/content/entity/royal/RoyalHead$Slot;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void worldbosses$onBeamEndpoint(@Coerce Object slot, Vec3 newPoint, CallbackInfo ci) {
        Entity royal = (Entity) (Object) this;
        if (!(royal.level() instanceof ServerLevel level)
                || !RoyalHbmCompat.hbmAbilitiesReady()
                || !RoyalHbmCompat.isRoyal(royal)
                || !(slot instanceof Enum<?> name) || !"CENTER".equals(name.name())) {
            return;
        }
        int tick = royal.tickCount;
        if (newPoint != null) {
            if (worldbosses$firstBeamTick < 0 || tick - worldbosses$lastBeamTick > 2) {
                worldbosses$firstBeamTick = tick;
            }
            worldbosses$lastBeamTick = tick;
            worldbosses$centerHit = newPoint;
            return;
        }
        if (worldbosses$centerHit != null && worldbosses$firstBeamTick >= 0
                && tick - worldbosses$firstBeamTick >= worldbosses$expectedBeamTicks(royal) - 2
                && tick - worldbosses$lastBeamTick <= 2) {
            RoyalHbmCompat.detonateRoyalBeam(level, royal, worldbosses$centerHit);
        }
        worldbosses$centerHit = null;
        worldbosses$firstBeamTick = -1;
        worldbosses$lastBeamTick = -1;
    }

    @Override
    public ContinuedBeam worldbosses$capturePendingBeam() {
        Entity royal = (Entity) (Object) this;
        if (!(royal.level() instanceof ServerLevel)
                || !RoyalHbmCompat.hbmAbilitiesReady()
                || worldbosses$centerHit == null
                || worldbosses$firstBeamTick < 0
                || royal.tickCount - worldbosses$lastBeamTick > 2) return null;
        int elapsed = Math.max(0, royal.tickCount - worldbosses$firstBeamTick);
        int remaining = Math.max(1, worldbosses$expectedBeamTicks(royal) - elapsed);
        return new ContinuedBeam(worldbosses$centerHit, remaining);
    }

    @Unique
    private static int worldbosses$expectedBeamTicks(Entity royal) {
        try {
            String name = RoyalHbmCompat.isRoyal(royal)
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(royal.getType())
                        .getPath().equals("queen")
                    ? "queenBeamDurationTicks" : "kingBeamDurationTicks";
            Object ticks = Class.forName("com.craisinlord.antarchy.config.AntarchySettings")
                    .getMethod(name).invoke(null);
            if (ticks instanceof Number number) return Math.max(1, number.intValue());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Antarchy's 1.21.1 default for both royals.
        }
        return 30;
    }
}
