package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.WorldBossesOptions;
import dev.keryeshka.worldbosses.server.RoyalFlightMotion;
import dev.keryeshka.worldbosses.server.RoyalFlightSteeringAccess;
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
public abstract class KingIdleWanderMixin implements RoyalFlightSteeringAccess {
    @Unique private RoyalFlightMotion.State seeuExtra$flight;
    @Unique private static final double seeuExtra$cruiseSpeed = 0.85D;
    @Unique private static final double seeuExtra$maxVerticalSpeed = 0.07D;
    @Unique private static final double seeuExtra$minProgressSquared = 0.0025D;

    @Unique private Vec3 seeuExtra$lastPosition;
    @Unique private Vec3 seeuExtra$heading;
    @Unique private int seeuExtra$headingTicks;
    @Unique private int seeuExtra$stationaryTicks;
    @Unique private boolean seeuExtra$steering;
    @Unique private int seeuExtra$recoveryTicks;
    // Keep the last combat heading instead of resuming an old patrol bearing.
    @Unique private boolean seeuExtra$wasFighting;

    /**
     * The persistent-flight King must have exactly one idle-flight controller.
     * Neutralize the native patrol's pending waypoint and previous velocity
     * before it can compete with our continuous directional flight. Combat
     * takes over unchanged as soon as Antarchy acquires a target.
     */
    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void seeuExtra$beginIdleFlight(CallbackInfo ci) {
        Mob king = (Mob) (Object) this;
        if (king.getTarget() != null) {
            seeuExtra$wasFighting = true;
        }
        if (!WorldBossesOptions.kingRoam || !(king.level() instanceof ServerLevel)
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
        if (king.getTarget() != null) {
            seeuExtra$wasFighting = true;
        }
        if (!(king.level() instanceof ServerLevel level) || !king.isAlive()
                || king.isNoAi() || king.getTarget() != null
                || (!WorldBossesOptions.kingRoam && !WorldBossesOptions.kingWander)) {
            seeuExtra$reset();
            return;
        }

        Vec3 position = king.position();
        if (seeuExtra$wasFighting) {
            // Antarchy owns all facing during combat. When it releases the
            // target, discard the old wandering bearing rather than turning
            // back toward a heading saved before the fight.
            if (seeuExtra$flight != null) {
                seeuExtra$flight.aimAt(king.getYRot(), position.y);
            }
            seeuExtra$wasFighting = false;
        }
        if (WorldBossesOptions.kingRoam) {
            if (seeuExtra$flight == null) {
                seeuExtra$flight = RoyalFlightMotion.State.begin(king.getYRot(), position.y);
            }
            RoyalFlightMotion.Step motion = RoyalFlightMotion.advanceWander(
                    seeuExtra$flight, king.getYRot(), position.x, position.y, position.z,
                    level.getMinBuildHeight(), level.getMaxBuildHeight());
            // Antarchy can still assign a waypoint during its tick; it must
            // never compete with the persistent flight's shared heading.
            king.getNavigation().stop();
            king.getMoveControl().setWantedPosition(
                    position.x, position.y, position.z, 0.0D);
            Vec3 step = new Vec3(motion.vx(), motion.vy(), motion.vz());
            king.move(MoverType.SELF, step);
            king.setDeltaMovement(step);
            king.hasImpulse = true;
            king.setYRot(motion.yaw());
            king.setYBodyRot(motion.yaw());
            king.setYHeadRot(motion.yaw());
            return;
        }
        boolean madeProgress = seeuExtra$lastPosition != null
                && position.distanceToSqr(seeuExtra$lastPosition) >= seeuExtra$minProgressSquared;
        if (madeProgress) {
            seeuExtra$stationaryTicks = 0;
            // Native patrol recovered; do not run the fallback against
            // Antarchy's own MoveControl for another 100 ticks.
            if (!WorldBossesOptions.kingRoam && seeuExtra$steering) {
                seeuExtra$reset();
                return;
            }
        } else if (seeuExtra$lastPosition != null) {
            ++seeuExtra$stationaryTicks;
        }
        seeuExtra$lastPosition = position;

        // Global roam drives direction continuously while idle, whereas the
        // ordinary fallback waits ~6 seconds for native patrol to move.
        if (!WorldBossesOptions.kingRoam && !seeuExtra$steering
                && (!WorldBossesOptions.kingWander || seeuExtra$stationaryTicks < 120)) {
            return;
        }
        if (!WorldBossesOptions.kingRoam) {
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
            double angle = seeuExtra$heading == null && WorldBossesOptions.kingRoam
                    ? (king.getYRot() * Mth.DEG_TO_RAD)
                    : king.getRandom().nextDouble() * Math.PI * 2.0D;
            double rise = king.getRandom().nextDouble() * 0.32D - 0.16D;
            if (seeuExtra$stationaryTicks >= 30) {
                rise = 0.25D;
            }
            seeuExtra$heading = WorldBossesOptions.kingRoam
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
        if (WorldBossesOptions.kingRoam) {
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

    @Override
    public RoyalFlightMotion.State seeuExtra$flightState() {
        return seeuExtra$flight == null ? null : seeuExtra$flight.copy();
    }

    @Override
    public void seeuExtra$restoreFlightState(RoyalFlightMotion.State state) {
        seeuExtra$flight = state == null ? null : state.copy();
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
