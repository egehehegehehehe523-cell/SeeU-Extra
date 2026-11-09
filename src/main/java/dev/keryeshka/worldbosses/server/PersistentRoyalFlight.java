package dev.keryeshka.worldbosses.server;

import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.seeu.extra.server.ExtraEntityBroadcaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative, single royal-boss per dimension dormant flight.
 *
 * A real royal boss is serialized (including Antarchy's custom NBT and health) and
 * discarded while outside player simulation. Its one logical replacement
 * moves through unloaded coordinates without asking for a chunk or a block.
 * It is restored with the original UUID only after its destination chunk is
 * naturally entity-ticking; no restoration or roaming chunk ticket is used.
 */
public final class PersistentRoyalFlight {
    private static final Logger LOGGER = LoggerFactory.getLogger("World Bosses");
    private static final double DETECTION_RANGE_SQUARED = 2048.0D * 2048.0D;
    private static final double MAX_VERTICAL_SPEED = RoyalFlightMotion.MAX_VERTICAL_SPEED;
    private static final double WORLD_EDGE = RoyalFlightMotion.WORLD_EDGE;
    private final boolean enabled;
    private final String typeId;
    private final String dataName;
    private final ResourceLocation flySound, roarSound;
    private final BossEvent.BossBarColor barColor;
    private final DistantKingDecrees distantDecrees;
    private final Map<ServerLevel, UUID> lastLiveBoss = new IdentityHashMap<>();
    private final Map<ServerLevel, UUID> currentTarget = new IdentityHashMap<>();
    private final Map<ServerLevel, ServerBossEvent> distantBossBars = new IdentityHashMap<>();
    private int discoveryTicks;

    public PersistentRoyalFlight(String typeId, String dataName, String soundPrefix,
                                 BossEvent.BossBarColor barColor, boolean enabled,
                                 boolean allowDistantDecrees) {
        this.typeId = typeId;
        this.dataName = dataName;
        this.barColor = barColor;
        this.enabled = enabled;
        this.flySound = ResourceLocation.fromNamespaceAndPath("antarchy", soundPrefix + "_fly_loop");
        this.roarSound = ResourceLocation.fromNamespaceAndPath("antarchy", soundPrefix + "_roar");
        this.distantDecrees = enabled && allowDistantDecrees && "antarchy:king".equals(typeId)
                ? new DistantKingDecrees() : null;
    }

    public void tick(MinecraftServer server) {
        if (!enabled) {
            return;
        }
        ++discoveryTicks;
        for (ServerLevel level : server.getAllLevels()) {
            RoyalData data = state(level);
            if (data.offloaded) {
                // If stale chunk storage reintroduces the already detached UUID,
                // the SavedData copy remains authoritative until reactivation.
                Entity stale = level.getEntity(data.base.uuid());
                if (stale != null && !stale.isRemoved()) {
                    stale.discard();
                    LOGGER.warn("Discarded duplicate chunk Royal boss {} while persistent flight is active",
                            data.base.uuid());
                }
                simulate(server, level, data);
            } else {
                if (distantDecrees != null) {
                    distantDecrees.clear(level);
                }
                findAndOffload(server, level, data);
            }
        }
    }

    public EntitySnapshot snapshot(ServerLevel level) {
        if (!enabled) {
            return null;
        }
        RoyalData data = state(level);
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

    /**
     * One dimension-wide boss event with a stable identity across native and
     * offloaded flight. It is independent of chunk tracking, render distance,
     * client LOD opt-in, and whether the King currently has a target.
     *
     * The native Antarchy event keeps its internal player list for combat and
     * sounds but is hidden while this event is enabled (Antarchy-only mixin).
     */
    public void updateBossBars(MinecraftServer server, ExtraServerSettings settings) {
        Set<ServerLevel> present = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        if (enabled && dev.keryeshka.worldbosses.WorldBossesOptions.globalBars
                && settings.enabled() && settings.selector().selects(typeId)) {
            for (ServerLevel level : server.getAllLevels()) {
                RoyalData data = state(level);
                float health;
                if (data.offloaded && data.base != null) {
                    health = data.healthFraction;
                } else {
                    UUID known = lastLiveBoss.get(level);
                    Entity entity = known == null ? null : level.getEntity(known);
                    if (!(entity instanceof Mob king) || !isBoss(king)) {
                        continue;
                    }
                    health = king.getHealth() / Math.max(1.0F, king.getMaxHealth());
                }
                present.add(level);
                ServerBossEvent bar = distantBossBars.computeIfAbsent(level,
                        ignored -> new ServerBossEvent(
                                Component.translatable("entity." + typeId.replace(':', '.')),
                                barColor,
                                BossEvent.BossBarOverlay.PROGRESS));
                bar.setProgress(Mth.clamp(health, 0.0F, 1.0F));

                // All players in the King's dimension can see the global boss
                // status. Never use the entity tracking or SeeU viewer list.
                Set<ServerPlayer> wanted = new HashSet<>();
                for (ServerPlayer player : level.players()) {
                    if (!player.isRemoved() && !player.isSpectator()) {
                        wanted.add(player);
                        if (!bar.getPlayers().contains(player)) {
                            bar.addPlayer(player);
                        }
                    }
                }
                for (ServerPlayer tracked : Set.copyOf(bar.getPlayers())) {
                    if (!wanted.contains(tracked)) {
                        bar.removePlayer(tracked);
                    }
                }
            }
        }
        // Only destruction, changing dimensions or disabling the feature
        // removes this event; native/offloaded transitions never do.
        distantBossBars.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            for (ServerPlayer tracked : Set.copyOf(entry.getValue().getPlayers())) {
                entry.getValue().removePlayer(tracked);
            }
            return true;
        });
    }

    private RoyalData state(ServerLevel level) {
        RoyalData data = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RoyalData::new, RoyalData::load, null), dataName);
        if (data.offloaded && (data.base == null || !typeId.equals(data.base.typeId()))) {
            // Never materialize a saved visual from the wrong royal type.
            LOGGER.error("Invalid persistent royal state in {} for {}; leaving it inactive",
                    dataName, typeId);
            data.clear();
        }
        return data;
    }

    private void findAndOffload(MinecraftServer server, ServerLevel level, RoyalData data) {
        Entity candidate = null;
        UUID known = lastLiveBoss.get(level);
        if (known != null) {
            candidate = level.getEntity(known);
        }
        // Do the all-entity discovery only once a second when there is no
        // known King; subsequent ticks use the UUID-indexed entity lookup.
        if (candidate == null && discoveryTicks % 20 == 0) {
            for (Entity entity : level.getAllEntities()) {
                if (isBoss(entity)) {
                    candidate = entity;
                    lastLiveBoss.put(level, entity.getUUID());
                    break;
                }
            }
        }
        if (!(candidate instanceof Mob king) || king.isRemoved() || !king.isAlive()
                || king.isNoAi()
                || !king.getPassengers().isEmpty()) {
            return;
        }

        // Offload *before* the last entity-ticking chunk, not after it.
        // With the old simulationDistance + 1 threshold Minecraft could stop
        // ticking/unload the real King before SavedData captured his flight.
        double activeRange = Math.max(16.0D,
                (server.getPlayerList().getSimulationDistance() - 1) * 16.0D);
        // Rendering / interaction requires the native King near *any*
        // non-spectating player, including Creative-mode testers. Only the
        // chase-target search below should exclude Creative players.
        if (nearestPresentPlayer(level, king.getX(), king.getZ(),
                activeRange * activeRange) != null) {
            return;
        }

        CompoundTag entityTag = new CompoundTag();
        EntitySnapshot base;
        try {
            if (!king.save(entityTag)) {
                return; // Never discard a King whose full boss state could not be saved.
            }
            base = ExtraEntityBroadcaster.captureSnapshot(king);
            RoyalFlightMotion.State heading = king instanceof RoyalFlightSteeringAccess access
                    ? access.seeuExtra$flightState() : null;
            data.capture(entityTag, base,
                    king.getHealth() / Math.max(1.0F, king.getMaxHealth()), heading);
            // Treason stays with this one King when his live entity is
            // serialized out of simulation; do not reset on boundary crossing.
            data.treasonPoints = RoyalTreason.points(king);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error("Could not serialize King flight state; keeping the real King alive", failure);
            return;
        }
        if (distantDecrees != null) {
            int nativeCooldown = king instanceof KingDecreeCooldownAccess access
                    ? access.seeuExtra$finishDecreeForOffload() : 0;
            // If the live King has already issued a decree, its remaining
            // recovery must delay the distant version too.
            distantDecrees.inheritNativeCooldown(level, nativeCooldown);
        }
        lastLiveBoss.remove(level);
        // Preserve the same pursuit target during native -> logical handoff
        // rather than reselecting another player at the next 20-tick poll.
        // This UUID is only used for cheap player-only pursuit math, not for
        // native navigation/pathfinding into unloaded chunks.
        if (king.getTarget() instanceof ServerPlayer victim && targetable(victim)) {
            currentTarget.put(level, victim.getUUID());
        } else {
            currentTarget.remove(level);
        }
        // Discard, not unload: never leave a second boss stored in his old chunk.
        king.discard();
        LOGGER.info("Royal boss {} entered persistent flight in {}", base.uuid(),
                level.dimension().location());
    }

    private void simulate(MinecraftServer server, ServerLevel level, RoyalData data) {
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
        // Restore the real King for combat and vanilla damage interaction even
        // when the nearby player is Creative. He still chases survival only.
        ServerPlayer nearbyForActivation = nearestPresentPlayer(
                level, data.x, data.z, DETECTION_RANGE_SQUARED);
        if (nearbyForActivation != null
                && withinActivationDistance(server, data, nearbyForActivation)) {
            // tryRestore clears data.base on success; keep the UUID for
            // the immediate native entity lookup after the handoff.
            UUID kingId = data.base.uuid();
            if (tryRestore(level, data, player)) {
                lastLiveBoss.put(level, kingId);
                currentTarget.remove(level);
                if (distantDecrees != null) {
                    int remaining = distantDecrees.handoffToNative(level);
                    Entity liveKing = level.getEntity(kingId);
                    if (liveKing instanceof KingDecreeCooldownAccess access) {
                        access.seeuExtra$inheritDecreeCooldown(remaining);
                    }
                }
                return;
            }
        }

        // Only these four player-state decrees run without the real entity.
        // Combat decrees, attacks and summons always wait for native AI.
        if (distantDecrees != null) {
            distantDecrees.tick(level, data.x, data.y, data.z, data::awardTreason);
        }
        playDistantAmbient(level, data);

        double floor = level.getMinBuildHeight() + 28.0D;
        double ceiling = level.getMaxBuildHeight() - 50.0D;
        if (player == null) {
            // Identical heading, turning cadence and vertical steering to the
            // real King's idle controller; never reset at the chunk boundary.
            RoyalFlightMotion.Step step = RoyalFlightMotion.advanceWander(
                    data.flight, data.yaw, data.x, data.y, data.z,
                    level.getMinBuildHeight(), level.getMaxBuildHeight());
            data.yaw = step.yaw();
            data.vx = step.vx();
            data.vy = step.vy();
            data.vz = step.vz();
        } else {
            double dx = player.getX() - data.x;
            double dz = player.getZ() - data.z;
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            data.yaw = Mth.approachDegrees(data.yaw, desiredYaw, 9.0F);
            data.flight.aimAt(data.yaw, data.y);
            double radians = data.yaw * Mth.DEG_TO_RAD;
            data.vx = -Math.sin(radians) * RoyalFlightMotion.CHASE_SPEED;
            data.vz = Math.cos(radians) * RoyalFlightMotion.CHASE_SPEED;
            data.vy = Mth.clamp((Mth.clamp(player.getY() + 22.0D,
                    floor, ceiling) - data.y) * 0.025D,
                    -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        }
        data.x = Mth.clamp(data.x + data.vx, -WORLD_EDGE, WORLD_EDGE);
        data.y = Mth.clamp(data.y + data.vy, floor, ceiling);
        data.z = Mth.clamp(data.z + data.vz, -WORLD_EDGE, WORLD_EDGE);
        if (data.age < Integer.MAX_VALUE) {
            ++data.age;
        }
        // One SavedData dirty mark per second, not NBT allocations per tick.
        if (level.getGameTime() % 20L == 0L) {
            data.setDirty();
        }
    }

    private static boolean withinActivationDistance(
            MinecraftServer server, RoyalData state, ServerPlayer player
    ) {
        // Stay inside the offload threshold by one chunk (including for low
        // simulation distances) to avoid restore/discard ping-pong.
        double radius = Math.max(8.0D,
                (server.getPlayerList().getSimulationDistance() - 2) * 16.0D);
        return horizontalDistanceSquared(state.x, state.z, player.getX(), player.getZ())
                <= radius * radius;
    }

    private boolean tryRestore(ServerLevel level, RoyalData state, ServerPlayer pursuitTarget) {
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
        if (!(king instanceof Mob) || !isBoss(king)
                || !king.getUUID().equals(state.base.uuid())) {
            LOGGER.error("Refusing to restore invalid King state for {}", state.base.uuid());
            return false;
        }

        king.moveTo(state.x, state.y, state.z, state.yaw, state.base.pitch());
        // NBT predates offloaded flight: its body/head and previous-frame
        // rotations are stale. Align the full pose before client interpolation
        // or Antarchy's native combat AI starts turning the real boss.
        king.setYBodyRot(state.yaw);
        king.setYHeadRot(state.yaw);
        king.yRotO = state.yaw;
        // Previous body/head angles live on LivingEntity, not Entity.
        Mob restored = (Mob) king;
        restored.yBodyRotO = state.yaw;
        restored.yHeadRotO = state.yaw;
        if (king instanceof RoyalFlightSteeringAccess access) {
            access.seeuExtra$restoreFlightState(state.flight);
        }
        king.setDeltaMovement(state.vx, state.vy, state.vz);
        king.tickCount = state.age;
        RoyalTreason.set(king, state.treasonPoints);
        king.hasImpulse = true;
        // Deserialization restores original health, Antarchy data and UUID.
        if (!level.addFreshEntity(king)) {
            // A UUID collision must never create a second authoritative King.
            return false;
        }
        // Preserve the logical pursuit target rather than requiring native AI
        // to rediscover it as the boss enters entity-ticking range. Only hand
        // off a nearby survival player: Antarchy's native combat/navigation
        // should never try to pathfind into unloaded distant chunks.
        if (pursuitTarget != null && targetable(pursuitTarget)
                && pursuitTarget.serverLevel() == level
                && king.distanceToSqr(pursuitTarget) <= 96.0D * 96.0D) {
            ((Mob) king).setTarget(pursuitTarget);
        }

        UUID id = state.base.uuid();
        state.clear();
        LOGGER.info("Royal boss {} returned to native Antarchy AI in {}", id,
                level.dimension().location());
        return true;
    }

    private boolean isBoss(Entity entity) {
        return !entity.isRemoved() && entity.isAlive()
                && typeId.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()).toString());
    }

    private static boolean targetable(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.isCreative();
    }

    private static ServerPlayer nearestPresentPlayer(
            ServerLevel level, double x, double z, double maxSquaredDistance
    ) {
        ServerPlayer closest = null;
        double closestSquared = maxSquaredDistance;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
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

    private void playDistantAmbient(ServerLevel level, RoyalData data) {
        // The normal King plays his own sounds after native restoration.
        // Limited, spaced-out ambience when offloaded avoids overlapping loops.
        if (data.age % 120 == 0) {
            playKingSound(level, data, flySound, 6.0F, 0.92F);
        }
        if (data.age % 440 == 0) {
            playKingSound(level, data, roarSound, 12.0F, 0.95F);
        }
    }

    private static void playKingSound(
            ServerLevel level, RoyalData data, ResourceLocation soundId,
            float volume, float pitch
    ) {
        if (!BuiltInRegistries.SOUND_EVENT.containsKey(soundId)) {
            return;
        }
        // Distance cap before sound dispatch. Minecraft handles spatial
        // attenuation and sends to nearby clients; no world/chunk scans.
        double reachSquared = (16.0D * volume) * (16.0D * volume);
        boolean listener = false;
        for (ServerPlayer player : level.players()) {
            if (!player.isRemoved() && player.distanceToSqr(data.x, data.y, data.z)
                    <= reachSquared) {
                listener = true;
                break;
            }
        }
        if (!listener) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(soundId);
        level.playSound(null, data.x, data.y, data.z, event,
                SoundSource.HOSTILE, volume, pitch);
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

    private static final class RoyalData extends SavedData {
        private boolean offloaded;
        private CompoundTag entityTag = new CompoundTag();
        private EntitySnapshot base;
        private double x, y, z;
        private double vx, vy, vz;
        private float yaw;
        private RoyalFlightMotion.State flight = RoyalFlightMotion.State.begin(0.0F, 0.0D);
        private int age;
        private int treasonPoints;
        private float healthFraction = 1.0F;

        private void awardTreason() {
            if (treasonPoints < Integer.MAX_VALUE) {
                treasonPoints++;
                setDirty();
            }
        }

        private void capture(CompoundTag serialized, EntitySnapshot initial, float healthFraction,
                RoyalFlightMotion.State flightState) {
            entityTag = serialized.copy();
            this.healthFraction = Mth.clamp(healthFraction, 0.0F, 1.0F);
            base = initial;
            x = initial.x(); y = initial.y(); z = initial.z();
            vx = initial.velocityX(); vy = initial.velocityY(); vz = initial.velocityZ();
            yaw = initial.bodyYaw();
            flight = flightState == null ? RoyalFlightMotion.State.begin(yaw, y)
                    : flightState.copy();
            age = initial.age();
            offloaded = true;
            setDirty();
        }

        private void clear() {
            offloaded = false;
            base = null;
            entityTag = new CompoundTag();
            setDirty();
        }

        private static RoyalData load(CompoundTag tag, HolderLookup.Provider registries) {
            RoyalData result = new RoyalData();
            if (!tag.getBoolean("Offloaded")) {
                return result;
            }
            try {
                EntitySnapshotPacket visual = ExtraPacketCodec.decodeSnapshotPacket(
                        tag.getByteArray("Visual"));
                if (visual.snapshots().size() != 1) {
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
                result.flight = new RoyalFlightMotion.State(
                        tag.getFloat("WanderYaw"), tag.getInt("WanderTicks"),
                        tag.getDouble("WanderRise"), tag.getDouble("WanderAltitude"));
                result.age = tag.getInt("Age");
                result.treasonPoints = Math.max(0, tag.getInt("Treason"));
                result.healthFraction = tag.contains("HealthFraction")
                        ? Mth.clamp(tag.getFloat("HealthFraction"), 0.0F, 1.0F) : 1.0F;
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
                tag.putFloat("Yaw", yaw); tag.putFloat("WanderYaw", flight.heading());
                tag.putDouble("WanderRise", flight.rise());
                tag.putDouble("WanderAltitude", flight.altitude());
                tag.putInt("WanderTicks", flight.remaining());
                tag.putInt("Age", age);
                tag.putInt("Treason", treasonPoints);
                tag.putFloat("HealthFraction", healthFraction);
            }
            return tag;
        }
    }
}
