package dev.keryeshka.seeu.extra.neoforge.mixin;

import dev.keryeshka.seeu.extra.neoforge.KingWanderOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Idle flight for Antarchy's King that does not query distant chunk terrain.
 * The real King still needs ticking chunks; this is NOT off-chunk simulation.
 *
 * The native King patrol already issues MoveControl waypoints; adding more
 * waypoints can make the huge boss turn without making forward progress.
 * Use gently steered, collision-aware displacement when idle instead.
 * Combat and target acquisition always retain control.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.KingEntity", remap = false)
public abstract class KingIdleWanderMixin {
    @Unique private static final double seeuExtra$cruiseSpeed = 0.20D;
    @Unique private static final double seeuExtra$maxVerticalSpeed = 0.07D;
    @Unique private static final double seeuExtra$minProgressSquared = 0.0025D;

    @Unique private Vec3 seeuExtra$lastPosition;
    @Unique private Vec3 seeuExtra$heading;
    @Unique private int seeuExtra$headingTicks;
    @Unique private int seeuExtra$stationaryTicks;
    @Unique private boolean seeuExtra$steering;

    @Inject(method = "tick", at = @At("TAIL"), remap = false, require = 0)
    private void seeuExtra$directionalIdleFlight(CallbackInfo ci) {
        Mob king = (Mob) (Object) this;
        if (!(king.level() instanceof ServerLevel level) || !king.isAlive()
                || king.isNoAi() || king.getTarget() != null
                || (!KingWanderOptions.globalRoam && !KingWanderOptions.enabled)) {
            seeuExtra$reset();
            return;
        }

        Vec3 position = king.position();
        if (seeuExtra$lastPosition != null
                && position.distanceToSqr(seeuExtra$lastPosition) < seeuExtra$minProgressSquared) {
            ++seeuExtra$stationaryTicks;
        } else {
            seeuExtra$stationaryTicks = 0;
        }
        seeuExtra$lastPosition = position;

        // Global roam drives direction continuously while idle, whereas the
        // ordinary fallback waits ~6 seconds for native patrol to move.
        if (!KingWanderOptions.globalRoam && !seeuExtra$steering
                && (!KingWanderOptions.enabled || seeuExtra$stationaryTicks < 120)) {
            return;
        }
        seeuExtra$steering = true;

        // Retry a stalled direction at a bounded interval, not on every tick.
        if (seeuExtra$stationaryTicks >= 30 && seeuExtra$stationaryTicks % 40 == 0) {
            seeuExtra$headingTicks = 0;
        }
        if (seeuExtra$heading == null || --seeuExtra$headingTicks <= 0) {
            double angle = king.getRandom().nextDouble() * Math.PI * 2.0D;
            double rise = king.getRandom().nextDouble() * 0.32D - 0.16D;
            if (seeuExtra$stationaryTicks >= 30) {
                rise = 0.25D;
            }
            seeuExtra$heading = new Vec3(Math.cos(angle), rise, Math.sin(angle)).normalize();
            seeuExtra$headingTicks = 180 + king.getRandom().nextInt(260);
        }

        double lower = level.getMinBuildHeight() + 24.0D;
        double upper = level.getMaxBuildHeight() - king.getBbHeight() - 20.0D;
        if (position.y < lower || position.y > upper) {
            double vertical = position.y < lower ? 0.20D : -0.20D;
            seeuExtra$heading = new Vec3(seeuExtra$heading.x, vertical, seeuExtra$heading.z).normalize();
        }

        Vec3 step = new Vec3(
                seeuExtra$heading.x * seeuExtra$cruiseSpeed,
                Mth.clamp(seeuExtra$heading.y * seeuExtra$cruiseSpeed,
                        -seeuExtra$maxVerticalSpeed, seeuExtra$maxVerticalSpeed),
                seeuExtra$heading.z * seeuExtra$cruiseSpeed
        );

        // Unlike setPos, normal move() keeps Minecraft's entity manager
        // authoritative. No heightmap lookup, chunk query, or path search.
        king.move(MoverType.SELF, step);
        king.setDeltaMovement(step);
        king.hasImpulse = true;
        float wantedYaw = (float) (Mth.atan2(-step.x, step.z) * Mth.RAD_TO_DEG);
        float yaw = Mth.approachDegrees(king.getYRot(), wantedYaw, 4.0F);
        king.setYRot(yaw);
        king.setYBodyRot(yaw);
        king.setYHeadRot(yaw);
    }

    @Unique
    private void seeuExtra$reset() {
        seeuExtra$lastPosition = null;
        seeuExtra$heading = null;
        seeuExtra$headingTicks = 0;
        seeuExtra$stationaryTicks = 0;
        seeuExtra$steering = false;
    }
}
