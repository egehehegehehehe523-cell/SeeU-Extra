package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-authoritative, single-King-per-dimension dormant flight.
 *
 * A real King is serialized (including Antarchy's custom NBT and health) and
 * discarded while outside player simulation. Its one logical replacement
 * moves through unloaded coordinates without asking for a chunk or a block.
 * It is restored with the original UUID only after its destination chunk is
 * naturally entity-ticking; no restoration or roaming chunk ticket is used.
 */
public final class PersistentKingFlight {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
    private static final String DATA_NAME = "seeu_extra_persistent_king";
    private static final String KING_TYPE = "antarchy:king";
    private static final double DETECTION_RANGE_SQUARED = 2048.0D * 2048.0D;
    private static final double WANDER_SPEED = 0.85D; // blocks/tick, 17 blocks/sec
    private static final double CHASE_SPEED = 1.20D;  // blocks/tick, 24 blocks/sec
    private static final double MAX_VERTICAL_SPEED = 0.14D;
    private static final double WORLD_EDGE = 29_999_744.0D;

    private final boolean enabled;
    private final Map<ServerLevel, UUID> lastLiveKing = new IdentityHashMap<>();
    private final Map<ServerLevel, UUID> currentTarget = new IdentityHashMap<>();
    private int discoveryTicks;

    public PersistentKingFlight(boolean enabled) {
        this.enabled = enabled;
    }

    public void tick(MinecraftServer server) {
        if (!enabled) {
            return;
        }
        ++discoveryTicks;
        for (ServerLevel level : server.getAllLevels()) {
            KingData data = state(level);
            if (data.offloaded) {
                // If stale chunk storage reintroduces the already detached UUID,
                // the SavedData copy remains authoritative until reactivation.
                Entity stale = level.getEntity(data.base.uuid());
                if (stale != null && !stale.isRemoved()) {
                    stale.discard();
                    LOGGER.warn("Discarded duplicate chunk King {} while persistent flight is active",
                            data.base.uuid());
                }
                simulate(server, level, data);
            } else {
                findAndOffload(server, level, data);
            }
        }
    }

    public EntitySnapshot snapshot(ServerLevel level) {
        if (!enabled) {
            return null;
        }
        KingData data = state(level);
        if (!data.offloaded || data.base == null) {
            return null;
        }
        EntitySnapshot base = data.base;
        return new EntitySnapshot(
                base.uuid(), base.typeId(), data.x, data.y, data.z,
                data.yaw, data.yaw, base.pitch(),
                data.vx, data.vy, data.vz,
                data.age, base.pose(), base.flags(),
                base.mainHand(), base.offHand(), base.feet(),
                base.legs(), base.chest(), base.head()
        );
    }

    private static KingData state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingData::new, KingData::load), DATA_NAME);
    }

    private void findAndOffload(MinecraftServer server, ServerLevel level, KingData data) {
        Entity candidate = null;
        UUID known = lastLiveKing.get(level);
        if (known != null) {
            candidate = level.getEntity(known);
        }
        // Do the all-entity discovery only once a second when there is no
        // known King; subsequent ticks use the UUID-indexed entity lookup.
        if (candidate == null && discoveryTicks % 20 == 0) {
            for (Entity entity : level.getAllEntities()) {
                if (isKing(entity)) {
                    candidate = entity;
                    lastLiveKing.put(level, entity.getUUID());
                    break;
                }
            }
        }
        if (!(candidate instanceof Mob king) || king.isRemoved() || !king.isAlive()
                || king.isNoAi()
                || !king.getPassengers().isEmpty()) {
            return;
        }

        double activeRange = Math.max(32.0D,
                (server.getPlayerList().getSimulationDistance() + 1) * 16.0D);
        if (nearestPlayer(level, king.getX(), king.getZ(), activeRange * activeRange) != null) {
            return;
        }

        CompoundTag entityTag = new CompoundTag();
        EntitySnapshot base;
        try {
            if (!king.save(entityTag)) {
                return; // Never discard a King whose full boss state could not be saved.
            }
            base = ExtraEntityBroadcaster.captureKingSnapshot(king);
            data.capture(entityTag, base);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error("Could not serialize King flight state; keeping the real King alive", failure);
            return;
        }
        lastLiveKing.remove(level);
        currentTarget.remove(level);
        // Discard, not unload: never leave a second boss stored in his old chunk.
        king.discard();
        LOGGER.info("King {} entered persistent flight in {}", base.uuid(),
                level.dimension().location());
    }

    private void simulate(MinecraftServer server, ServerLevel level, KingData data) {
        if (level.getGameTime() % 20L == 0L) {
            ServerPlayer nearest = nearestPlayer(level, data.x, data.z, DETECTION_RANGE_SQUARED);
            if (nearest == null) {
                currentTarget.remove(level);
            } else {
                currentTarget.put(level, nearest.getUUID());
            }
        }

        ServerPlayer player = null;
        UUID chosen = currentTarget.get(level);
        if (chosen != null) {
            ServerPlayer found = server.getPlayerList().getPlayer(chosen);
            if (found != null && found.serverLevel() == level && targetable(found)
                    && horizontalDistanceSquared(data.x, data.z, found.getX(), found.getZ())
                    <= DETECTION_RANGE_SQUARED) {
                player = found;
            }
        }

        // Handoff only when a nearby player has naturally made this chunk
        // entity-ticking. No tickets, pathfinding, block reads or generation.
        if (player != null && withinActivationDistance(server, data, player)
                && tryRestore(level, data)) {
            currentTarget.remove(level);
            return;
        }

        double desiredYaw;
        if (player != null) {
            double dx = player.getX() - data.x;
            double dz = player.getZ() - data.z;
            desiredYaw = Math.toDegrees(Math.atan2(-dx, dz));
        } else {
            if (--data.wanderTicks <= 0) {
                data.wanderYaw = Mth.wrapDegrees(data.yaw
                        + ThreadLocalRandom.current().nextFloat() * 160.0F - 80.0F);
                data.wanderTicks = 240 + ThreadLocalRandom.current().nextInt(320);
                data.wanderRise = ThreadLocalRandom.current().nextDouble(-0.10D, 0.10D);
            }
            desiredYaw = data.wanderYaw;
        }

        // The King moves in the direction his model actually faces, instead of
        // moving sideways while the body takes several seconds to catch up.
        data.yaw = Mth.approachDegrees(data.yaw, (float) desiredYaw, 9.0F);
        double radians = data.yaw * Mth.DEG_TO_RAD;
        double speed = player == null ? WANDER_SPEED : CHASE_SPEED;
        data.vx = -Math.sin(radians) * speed;
        data.vz = Math.cos(radians) * speed;

        double wantedY = player == null ? data.wanderAltitude + data.wanderRise * 70.0D
                : player.getY() + 22.0D;
        double floor = level.getMinBuildHeight() + 28.0D;
        double ceiling = level.getMaxBuildHeight() - 50.0D;
        wantedY = Mth.clamp(wantedY, floor, ceiling);
        data.vy = Mth.clamp((wantedY - data.y) * 0.025D,
                -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);

        data.x = Mth.clamp(data.x + data.vx, -WORLD_EDGE, WORLD_EDGE);
        data.y = Mth.clamp(data.y + data.vy, floor, ceiling);
        data.z = Mth.clamp(data.z + data.vz, -WORLD_EDGE, WORLD_EDGE);
        if (Math.abs(data.x) >= WORLD_EDGE || Math.abs(data.z) >= WORLD_EDGE) {
            data.wanderTicks = 0;
            data.wanderYaw = Mth.wrapDegrees(data.yaw + 180.0F);
        }
        if (data.age < Integer.MAX_VALUE) {
            ++data.age;
        }
        // One SavedData dirty mark per second, not NBT allocations per tick.
        if (level.getGameTime() % 20L == 0L) {
            data.setDirty();
        }
    }

    private static boolean withinActivationDistance(
            MinecraftServer server, KingData state, ServerPlayer player
    ) {
        double radius = Math.max(32.0D,
                (server.getPlayerList().getSimulationDistance() - 2) * 16.0D);
        return horizontalDistanceSquared(state.x, state.z, player.getX(), player.getZ())
                <= radius * radius;
    }

    private static boolean tryRestore(ServerLevel level, KingData state) {
        BlockPos destination = BlockPos.containing(state.x, state.y, state.z);
        if (!level.getChunkSource().hasChunk(destination.getX() >> 4, destination.getZ() >> 4)
                || !level.isPositionEntityTicking(destination)) {
            return false;
        }

        Entity king;
        try {
            king = EntityType.loadEntityRecursive(state.entityTag.copy(), level, entity -> entity);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not deserialize the stored King; leaving his state safely parked", exception);
            return false;
        }
        if (!(king instanceof Mob) || !isKing(king)
                || !king.getUUID().equals(state.base.uuid())) {
            LOGGER.error("Refusing to restore invalid King state for {}", state.base.uuid());
            return false;
        }

        king.moveTo(state.x, state.y, state.z, state.yaw, state.base.pitch());
        king.setDeltaMovement(state.vx, state.vy, state.vz);
        king.tickCount = state.age;
        king.hasImpulse = true;
        // Deserialization restores original health, Antarchy data and UUID.
        if (!level.addFreshEntity(king)) {
            // A UUID collision must never create a second authoritative King.
            return false;
        }

        UUID id = state.base.uuid();
        state.clear();
        LOGGER.info("King {} returned to native Antarchy AI in {}", id,
                level.dimension().location());
        return true;
    }

    private static boolean isKing(Entity entity) {
        return !entity.isRemoved() && entity.isAlive()
                && KING_TYPE.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()).toString());
    }

    private static boolean targetable(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.isCreative();
    }

    private static ServerPlayer nearestPlayer(
            ServerLevel level, double x, double z, double maxSquaredDistance
    ) {
        ServerPlayer closest = null;
        double closestSquared = maxSquaredDistance;
        for (ServerPlayer player : level.players()) {
            if (!targetable(player)) {
                continue;
            }
            double distance = horizontalDistanceSquared(x, z, player.getX(), player.getZ());
            if (distance <= closestSquared) {
                closestSquared = distance;
                closest = player;
            }
        }
        return closest;
    }

    private static double horizontalDistanceSquared(double x, double z, double px, double pz) {
        double dx = x - px;
        double dz = z - pz;
        return dx * dx + dz * dz;
    }

    private static final class KingData extends SavedData {
        private boolean offloaded;
        private CompoundTag entityTag = new CompoundTag();
        private EntitySnapshot base;
        private double x, y, z;
        private double vx, vy, vz;
        private float yaw, wanderYaw;
        private double wanderRise, wanderAltitude;
        private int age, wanderTicks;

        private void capture(CompoundTag serialized, EntitySnapshot initial) {
            entityTag = serialized.copy();
            base = initial;
            x = initial.x(); y = initial.y(); z = initial.z();
            vx = initial.velocityX(); vy = initial.velocityY(); vz = initial.velocityZ();
            yaw = initial.bodyYaw();
            wanderYaw = yaw;
            wanderAltitude = y;
            age = initial.age();
            wanderTicks = 220;
            offloaded = true;
            setDirty();
        }

        private void clear() {
            offloaded = false;
            base = null;
            entityTag = new CompoundTag();
            setDirty();
        }

        private static KingData load(CompoundTag tag, HolderLookup.Provider registries) {
            KingData result = new KingData();
            if (!tag.getBoolean("Offloaded")) {
                return result;
            }
            try {
                EntitySnapshotPacket visual = ExtraPacketCodec.decodeSnapshotPacket(
                        tag.getByteArray("Visual"));
                if (visual.snapshots().size() != 1
                        || !KING_TYPE.equals(visual.snapshots().get(0).typeId())) {
                    return result;
                }
                result.base = visual.snapshots().get(0);
                result.entityTag = tag.getCompound("Entity");
                result.x = tag.getDouble("X");
                result.y = tag.getDouble("Y");
                result.z = tag.getDouble("Z");
                result.vx = tag.getDouble("VX");
                result.vy = tag.getDouble("VY");
                result.vz = tag.getDouble("VZ");
                result.yaw = tag.getFloat("Yaw");
                result.wanderYaw = tag.getFloat("WanderYaw");
                result.wanderRise = tag.getDouble("WanderRise");
                result.wanderAltitude = tag.getDouble("WanderAltitude");
                result.wanderTicks = tag.getInt("WanderTicks");
                result.age = tag.getInt("Age");
                result.offloaded = !result.entityTag.isEmpty()
                        && Double.isFinite(result.x) && Double.isFinite(result.y)
                        && Double.isFinite(result.z);
            } catch (RuntimeException failure) {
                LOGGER.error("Invalid saved King flight; leaving data untouched", failure);
            }
            return result;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Offloaded", offloaded);
            if (offloaded) {
                tag.put("Entity", entityTag.copy());
                tag.putByteArray("Visual", ExtraPacketCodec.encodeSnapshotPacket(
                        new EntitySnapshotPacket("minecraft:overworld", 0L, 4,
                                true, List.of(base))));
                tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
                tag.putDouble("VX", vx); tag.putDouble("VY", vy); tag.putDouble("VZ", vz);
                tag.putFloat("Yaw", yaw); tag.putFloat("WanderYaw", wanderYaw);
                tag.putDouble("WanderRise", wanderRise);
                tag.putDouble("WanderAltitude", wanderAltitude);
                tag.putInt("WanderTicks", wanderTicks);
                tag.putInt("Age", age);
            }
            return tag;
        }
    }
}
