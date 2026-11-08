package dev.keryeshka.seeu.extra.neoforge.mixin;

import dev.keryeshka.seeu.extra.neoforge.KingWanderOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only targets Antarchy's King, never Minecraft, Voxy, or other mobs.
 * The King already has an idle patrol; this is a conservative fallback
 * for when its normal flight AI fails to make any horizontal progress.
 */
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.KingEntity", remap = false)
public abstract class KingIdleWanderMixin {
    @Unique private int seeuExtra$stationaryTicks;
    @Unique private int seeuExtra$retryTicks;
    @Unique private Vec3 seeuExtra$waypoint;

    @Inject(method = "tick", at = @At("TAIL"), remap = false, require = 0)
    private void seeuExtra$idleFlightFallback(CallbackInfo ci) {
        Mob king = (Mob) (Object) this;
        if (!KingWanderOptions.enabled || !(king.level() instanceof ServerLevel level)
                || king.isNoAi() || !king.isAlive() || king.getTarget() != null) {
            seeuExtra$stationaryTicks = 0;
            seeuExtra$waypoint = null;
            return;
        }

        // Native Antarchy movement gets priority. Never touch combat AI.
        if (king.getDeltaMovement().horizontalDistanceSqr() > 0.0016D) {
            seeuExtra$stationaryTicks = 0;
            seeuExtra$waypoint = null;
            return;
        }

        if (++seeuExtra$stationaryTicks < 120) {
            return;
        }
        if (seeuExtra$retryTicks > 0) {
            seeuExtra$retryTicks--;
        }

        if (seeuExtra$waypoint != null
                && (king.position().distanceToSqr(seeuExtra$waypoint) < 16.0D
                    || seeuExtra$stationaryTicks > 220)) {
            seeuExtra$waypoint = null;
        }
        if (seeuExtra$waypoint == null && seeuExtra$retryTicks == 0) {
            seeuExtra$retryTicks = 40;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = king.getRandom().nextDouble() * Math.PI * 2.0D;
                double distance = 12.0D + king.getRandom().nextDouble() * 16.0D;
                double x = king.getX() + Math.cos(angle) * distance;
                double z = king.getZ() + Math.sin(angle) * distance;
                double y = Mth.clamp(king.getY() + king.getRandom().nextDouble() * 10.0D - 4.0D,
                        level.getMinBuildHeight() + 12.0D,
                        level.getMaxBuildHeight() - king.getBbHeight() - 12.0D);
                BlockPos destination = BlockPos.containing(x, y, z);
                if (!level.hasChunkAt(destination)) {
                    continue;
                }
                AABB future = king.getBoundingBox().move(x - king.getX(), y - king.getY(), z - king.getZ());
                if (!level.noCollision(king, future)) {
                    continue;
                }
                seeuExtra$waypoint = new Vec3(x, y, z);
                break;
            }
        }
        if (seeuExtra$waypoint != null) {
            // The King installs its native patrol movement after super.tick().
            // This tail injection supplies a waypoint for the following tick
            // without replacing any navigation or flight-control classes.
            king.getMoveControl().setWantedPosition(
                    seeuExtra$waypoint.x, seeuExtra$waypoint.y, seeuExtra$waypoint.z, 0.85D);
        }
    }
}
