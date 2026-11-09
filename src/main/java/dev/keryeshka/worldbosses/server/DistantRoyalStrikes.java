package dev.keryeshka.worldbosses.server;

import dev.keryeshka.worldbosses.WorldBossesOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Rare ambient, downward head-beam shots by offloaded royals.
 * A candidate uses ONE loaded-chunk heightmap or ONE generator height query.
 * It never calls level.getChunk, creates a chunk ticket, or loads terrain in
 * order to acquire a strike target. HBM still owns any subsequent crater work.
 */
public final class DistantRoyalStrikes {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    private static boolean warnedHeightQuery;
    private DistantRoyalStrikes() {}

    public static long nextCooldown(long now, WorldBossesOptions settings, String bossType) {
        int base = "antarchy:queen".equals(bossType)
                ? settings.queenDistantBeamCooldownTicks : settings.kingDistantBeamCooldownTicks;
        base = Math.max(600, Math.min(72000, base));
        return now + base + ThreadLocalRandom.current().nextInt(Math.max(1, base / 2));
    }

    public static boolean schedule(ServerLevel level, UUID bossId, String bossType,
                                   double bossX, double bossY, double bossZ, float yaw,
                                   WorldBossesOptions config) {
        if (!config.distantGroundBeamStrikes || !RoyalHbmCompat.hbmAbilitiesReady()
                || PendingRoyalBeams.hasPending(level, bossId)) return false;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double minRange = Math.max(16.0D, config.distantGroundBeamMinimumRange);
        double maxRange = Math.max(minRange, config.distantGroundBeamMaximumRange);
        double minPlayerSq = (double) config.distantGroundBeamMinimumPlayerDistance
                * config.distantGroundBeamMinimumPlayerDistance;

        // Each trigger gets at most FOUR cheap candidate tests. The previous
        // single roll frequently failed on the Queen when terrain rose near
        // her altitude, so waiting through another three-minute cooldown
        // could look like a broken ability.
        // Only the initial call checks the loaded-chunk map. Unloaded areas
        // use the generator's base height without creating a LevelChunk.
        for (int attempt = 0; attempt < 4; attempt++) {
            // A head beam should go roughly FORWARD, not sideways or behind.
            double bearing = Math.toRadians(yaw + random.nextDouble(-28.0D, 28.0D));
            double distance = minRange + (maxRange - minRange) * random.nextDouble();
            double dx = bossX - Math.sin(bearing) * distance;
            double dz = bossZ + Math.cos(bearing) * distance;
            if (Math.abs(dx) >= RoyalFlightMotion.WORLD_EDGE - 64
                    || Math.abs(dz) >= RoyalFlightMotion.WORLD_EDGE - 64) continue;

            int x = (int) Math.floor(dx);
            int z = (int) Math.floor(dz);
            int surface;
            try {
                LevelChunk loaded = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                surface = loaded != null
                        ? loaded.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15)
                        : level.getChunkSource().getGenerator().getBaseHeight(
                                x, z, Heightmap.Types.WORLD_SURFACE_WG,
                                level, level.getChunkSource().randomState());
            } catch (RuntimeException error) {
                if (!warnedHeightQuery) {
                    warnedHeightQuery = true;
                    LOGGER.warn("Failed estimating remote royal beam terrain without loading chunks", error);
                }
                continue;
            }

            // The target must be BELOW the boss's middle head, but don't
            // reject otherwise valid low Queen flights with the former
            // overly strict 12-block altitude margin.
            if (surface <= level.getMinBuildHeight() + 5
                    || surface > level.getMaxBuildHeight() - 5
                    || surface + 4.0D >= bossY) continue;

            double impactY = surface + 0.25D;
            boolean protectedPlayer = false;
            for (ServerPlayer player : level.players()) {
                if (!player.isRemoved() && player.isAlive()
                        && player.distanceToSqr(dx, impactY, dz) < minPlayerSq) {
                    protectedPlayer = true;
                    break;
                }
            }
            if (protectedPlayer) continue;

            Vec3 impact = new Vec3(dx, impactY, dz);
            if (PendingRoyalBeams.scheduleDistant(level, bossId, bossType,
                    impact, config.distantGroundBeamDurationTicks)) {
                LOGGER.debug("{} distant ground strike at {} {} {} (candidate {})",
                        bossType, x, surface, z, attempt + 1);
                return true;
            }
            return false; // Pending event already exists; don't try more targets.
        }
        LOGGER.debug("{} found no safe forward ground target (4 bounded tries)", bossType);
        return false;
    }
}
