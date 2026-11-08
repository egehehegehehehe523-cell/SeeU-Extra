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
    @Unique private static final double seeuExtra$cruiseSpeed = 0.85D;
    @Unique private static final double seeuExtra$maxVerticalSpeed = 0.07D;
    @Unique private static final double seeuExtra$minProgressSquared = 0.0025D;

    @Unique private Vec3 seeuExtra$lastPosition;
    @Unique private Vec3 seeuExtra$heading;
    @Unique private int seeuExtra$headingTicks;
    @Unique private int seeuExtra$stationaryTicks;
    @Unique private boolean seeuExtra$steering;
    @Unique private int seeuExtra$recoveryTicks;

    /**
     * The persistent-flight King must have exactly one idle-flight controller.
     * Neutralize the native patrol's pending waypoint and previous velocity
     * before it can compete with our continuous directional flight. Combat
     * takes over unchanged as soon as Antarchy acquires a target.
     */
    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void seeuExtra$beginIdleFlight(CallbackInfo ci) {
        Mob king = (Mob) (Object) this;
        if (!KingWanderOptions.globalRoam || !(king.level() instanceof ServerLevel)
                || !king.isAlive() || king.isNoAi() || king.getTarget() != null) {
            return;
        }
        king.getNavigation().stop();
        king.getMoveControl().setWantedPosition(
                king.getX(), king.getY(), king.getZ(), 0.0D);
        king.setDeltaMovement(Vec3.ZERO);
    }

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
        boolean madeProgress = seeuExtra$lastPosition != null
                && position.distanceToSqr(seeuExtra$lastPosition) >= seeuExtra$minProgressSquared;
        if (madeProgress) {
            seeuExtra$stationaryTicks = 0;
            // Native patrol recovered; do not run the fallback against
            // Antarchy's own MoveControl for another 100 ticks.
            if (!KingWanderOptions.globalRoam && seeuExtra$steering) {
                seeuExtra$reset();
                return;
            }
        } else if (seeuExtra$lastPosition != null) {
            ++seeuExtra$stationaryTicks;
        }
        seeuExtra$lastPosition = position;

        // Global roam drives direction continuously while idle, whereas the
        // ordinary fallback waits ~6 seconds for native patrol to move.
        if (!KingWanderOptions.globalRoam && !seeuExtra$steering
                && (!KingWanderOptions.enabled || seeuExtra$stationaryTicks < 120)) {
            return;
        }
        if (!KingWanderOptions.globalRoam) {
            if (!seeuExtra$steering) {
                // A bounded escape nudge, not perpetual world roaming.
                seeuExtra$recoveryTicks = 100;
            } else if (--seeuExtra$recoveryTicks <= 0) {
                seeuExtra$reset();
                return;
            }
        }
        seeuExtra$steering = true;

        // Retry a stalled direction at a bounded interval, not on every tick.
        if (seeuExtra$stationaryTicks >= 30 && seeuExtra$stationaryTicks % 40 == 0) {
            seeuExtra$headingTicks = 0;
        }
        if (seeuExtra$heading == null || --seeuExtra$headingTicks <= 0) {
            // First handoff inherits the heading the distant flight was using.
            // Choosing a completely random angle here makes the King visibly
            // spin/jitter at the moment the real entity becomes active.
            double angle = seeuExtra$heading == null && KingWanderOptions.globalRoam
                    ? (king.getYRot() * Mth.DEG_TO_RAD)
                    : king.getRandom().nextDouble() * Math.PI * 2.0D;
            double rise = king.getRandom().nextDouble() * 0.32D - 0.16D;
            if (seeuExtra$stationaryTicks >= 30) {
                rise = 0.25D;
            }
            seeuExtra$heading = KingWanderOptions.globalRoam
                    ? new Vec3(-Math.sin(angle), rise, Math.cos(angle)).normalize()
                    : new Vec3(Math.cos(angle), rise, Math.sin(angle)).normalize();
            seeuExtra$headingTicks = 180 + king.getRandom().nextInt(260);
        }

        double lower = level.getMinBuildHeight() + 24.0D;
        double upper = level.getMaxBuildHeight() - king.getBbHeight() - 20.0D;
        if (position.y < lower || position.y > upper) {
            double vertical = position.y < lower ? 0.20D : -0.20D;
            seeuExtra$heading = new Vec3(seeuExtra$heading.x, vertical, seeuExtra$heading.z).normalize();
        }

        // Face the intended heading first, then move along the actual facing.
        // Previously displacement was applied immediately while the body only
        // rotated 4 degrees/tick, making the King appear to fly backwards.
        float wantedYaw = (float) (Mth.atan2(-seeuExtra$heading.x,
                seeuExtra$heading.z) * Mth.RAD_TO_DEG);
        float yaw = Mth.approachDegrees(king.getYRot(), wantedYaw, 10.0F);
        double radians = yaw * Mth.DEG_TO_RAD;
        Vec3 step = new Vec3(
                -Math.sin(radians) * seeuExtra$cruiseSpeed,
                Mth.clamp(seeuExtra$heading.y * seeuExtra$cruiseSpeed,
                        -seeuExtra$maxVerticalSpeed, seeuExtra$maxVerticalSpeed),
                Math.cos(radians) * seeuExtra$cruiseSpeed
        );

        // Native patrol may have issued another waypoint at the end of its
        // tick. Clear it so no stale waypoint accelerates the King next tick.
        // No Minecraft/NeoVoxy mixin and no unloaded-chunk queries.
        if (KingWanderOptions.globalRoam) {
            king.getNavigation().stop();
            king.getMoveControl().setWantedPosition(
                    position.x, position.y, position.z, 0.0D);
        }
        king.move(MoverType.SELF, step);
        king.setDeltaMovement(step);
        king.hasImpulse = true;
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
        seeuExtra$recoveryTicks = 0;
    }
}
