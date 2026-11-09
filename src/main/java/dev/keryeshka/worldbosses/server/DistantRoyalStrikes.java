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
        double yawDegrees = yaw + random.nextDouble(-105.0D, 105.0D);
        double bearing = Math.toRadians(yawDegrees);
        double distance = random.nextDouble(minRange, maxRange + 0.0001D);
        double dx = bossX - Math.sin(bearing) * distance;
        double dz = bossZ + Math.cos(bearing) * distance;
        if (Math.abs(dx) > RoyalFlightMotion.WORLD_EDGE - 64
                || Math.abs(dz) > RoyalFlightMotion.WORLD_EDGE - 64) return false;

        int x = (int) Math.floor(dx);
        int z = (int) Math.floor(dz);
        int surface;
        try {
            // getChunkNow is explicitly non-loading. In distant terrain,
            // getBaseHeight samples worldgen noise without allocating a chunk.
            LevelChunk loaded = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            surface = loaded != null
                    ? loaded.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15)
                    : level.getChunkSource().getGenerator().getBaseHeight(
                            x, z, Heightmap.Types.WORLD_SURFACE_WG,
                            level, level.getChunkSource().randomState());
        } catch (RuntimeException error) {
            if (!warnedHeightQuery) {
                warnedHeightQuery = true;
                LOGGER.warn("Cannot estimate distant royal beam terrain without loading chunks", error);
            }
            return false;
        }
        // Avoid sky beams, underwater/void heights, and shots travelling up
        // through mountains above the boss. The exact terrain after structures
        // or other mods may differ from raw worldgen's approximate base height.
        if (surface <= level.getMinBuildHeight() + 5
                || surface > level.getMaxBuildHeight() - 5
                || surface + 12.0D >= bossY) return false;

        double impactY = surface + 0.25D;
        double minPlayerSq = Math.pow(config.distantGroundBeamMinimumPlayerDistance, 2);
        for (ServerPlayer player : level.players()) {
            if (!player.isRemoved() && player.isAlive()
                    && player.distanceToSqr(dx, impactY, dz) < minPlayerSq) return false;
        }
        boolean started = PendingRoyalBeams.scheduleDistant(level, bossId, bossType,
                new Vec3(dx, impactY, dz), config.distantGroundBeamDurationTicks);
        if (started) {
            LOGGER.debug("Scheduled {} remote ground beam at {}, {}, {}",
                    bossType, x, surface, z);
        }
        return started;
    }
}
