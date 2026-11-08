package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.config.EntitySelector;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityFlags;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;
import dev.keryeshka.seeu.extra.protocol.EquipmentSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class ExtraEntityBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
    // Last-known visuals bridge a chunk unload. These do NOT tick unloaded mobs.
    // Bound both age and count to avoid retaining a world-sized entity history.
    private static final long RETAIN_UNLOADED_TICKS = 20L * 60L * 10L;
    private static final int MAX_RETAINED_PER_LEVEL = 1024;
    private static final Comparator<RankedCandidate> NEAREST_FIRST = Comparator
            .comparing(RankedCandidate::need)
            .thenComparingDouble(RankedCandidate::distanceSquared)
            .thenComparing(candidate -> candidate.candidate().entity().getUUID());

    private ExtraServerSettings settings;
    private final Set<String> quarantinedTypes = new HashSet<>();
    private final Map<ServerLevel, Map<UUID, RetainedSnapshot>> retainedByLevel = new IdentityHashMap<>();
    private int elapsedTicks;
    private long sequence;

    public ExtraEntityBroadcaster(ExtraServerSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(ExtraServerSettings settings) {
        this.settings = settings;
        this.elapsedTicks = 0;
        this.quarantinedTypes.clear();
        this.retainedByLevel.clear();
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public void tick(MinecraftServer server, Collection<? extends ExtraViewerSession> sessions) {
        ExtraServerSettings currentSettings = settings;
        if (!currentSettings.enabled()) {
            elapsedTicks = 0;
            retainedByLevel.clear();
            return;
        }
        if (sessions.isEmpty()) {
            retainedByLevel.clear();
            return;
        }

        elapsedTicks++;
        if (elapsedTicks < currentSettings.updateIntervalTicks()) {
            return;
        }
        elapsedTicks = 0;

        Map<ServerLevel, List<ExtraViewerSession>> sessionsByLevel = groupSessions(sessions);
        if (sessionsByLevel.isEmpty()) {
            retainedByLevel.clear();
            return;
        }
        retainedByLevel.keySet().removeIf(level -> !sessionsByLevel.containsKey(level));

        EntitySelector selector = currentSettings.selector();
        long packetSequence = nextSequence();
        for (Map.Entry<ServerLevel, List<ExtraViewerSession>> entry : sessionsByLevel.entrySet()) {
            ServerLevel level = entry.getKey();
            Set<String> optedInTypes = new HashSet<>();
            for (ExtraViewerSession session : entry.getValue()) {
                optedInTypes.addAll(session.offer().entityDistances().keySet());
            }
            if (optedInTypes.isEmpty()) {
                continue;
            }
            Map<UUID, EntitySnapshot> snapshotCache = new HashMap<>();
            Set<UUID> failedSnapshots = new HashSet<>();
            Set<UUID> currentlyLoaded = new HashSet<>();
            Map<UUID, RetainedSnapshot> retained = retainedByLevel.computeIfAbsent(
                    level, ignored -> new HashMap<>());
            pruneRetained(retained, level.getGameTime());
            SpatialCellIndex<EntityCandidate> candidates = scanLevel(
                    server, level, selector, optedInTypes, snapshotCache,
                    failedSnapshots, currentlyLoaded, retained);
            trimRetained(retained);
            String dimensionKey = level.dimension().location().toString();
            for (ExtraViewerSession session : entry.getValue()) {
                sendSnapshot(
                        server,
                        session,
                        dimensionKey,
                        packetSequence,
                        candidates,
                        snapshotCache,
                        failedSnapshots,
                        currentSettings,
                        retained,
                        currentlyLoaded
                );
            }
        }
    }

    private static Map<ServerLevel, List<ExtraViewerSession>> groupSessions(
            Collection<? extends ExtraViewerSession> sessions
    ) {
        Map<ServerLevel, List<ExtraViewerSession>> grouped = new IdentityHashMap<>();
        for (ExtraViewerSession session : sessions) {
            if (session == null || session.offer() == null || !session.offer().enabled()
                    || session.offer().entityDistances().isEmpty()) {
                continue;
            }
            ServerPlayer player = session.player();
            if (player == null || player.isRemoved()) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            grouped.computeIfAbsent(level, ignored -> new ArrayList<>()).add(session);
        }
        return grouped;
    }

    private SpatialCellIndex<EntityCandidate> scanLevel(
            MinecraftServer server,
            ServerLevel level,
            EntitySelector selector,
            Set<String> optedInTypes,
            Map<UUID, EntitySnapshot> snapshotCache,
            Set<UUID> failedSnapshots,
            Set<UUID> currentlyLoaded,
            Map<UUID, RetainedSnapshot> retained
    ) {
        SpatialCellIndex<EntityCandidate> candidates = new SpatialCellIndex<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }

            ResourceLocation typeIdentifier = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeIdentifier == null) {
                continue;
            }
            String typeId = typeIdentifier.toString();
            if (quarantinedTypes.contains(typeId)
                    || !selector.selects(typeId)
                    || !optedInTypes.contains(typeId)
                    || containsPlayer(entity)) {
                continue;
            }
            EntityCandidate candidate = new EntityCandidate(
                    entity,
                    typeId,
                    vanillaTrackingRangeBlocks(server, entity)
            );
            UUID uuid = entity.getUUID();
            currentlyLoaded.add(uuid);
            candidates.add(candidate, entity.getX(), entity.getY(), entity.getZ());
            // Remember *before* vanilla stops tracking. Otherwise an entity
            // whose chunk unloads immediately after the handoff is never cached.
            try {
                EntitySnapshot snapshot = createSnapshot(candidate);
                snapshotCache.put(uuid, snapshot);
                retained.put(uuid, new RetainedSnapshot(
                        snapshot, level.getGameTime(), new WeakReference<>(entity)));
            } catch (RuntimeException | LinkageError failure) {
                failedSnapshots.add(uuid);
                quarantine(typeId, failure);
                retained.remove(uuid);
            }
        }
        return candidates;
    }

    private static boolean containsPlayer(Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        for (Entity passenger : entity.getPassengers()) {
            if (containsPlayer(passenger)) {
                return true;
            }
        }
        return false;
    }

    private void sendSnapshot(
            MinecraftServer server,
            ExtraViewerSession session,
            String dimensionKey,
            long sequence,
            SpatialCellIndex<EntityCandidate> candidates,
            Map<UUID, EntitySnapshot> snapshotCache,
            Set<UUID> failedSnapshots,
            ExtraServerSettings settings,
            Map<UUID, RetainedSnapshot> retained,
            Set<UUID> currentlyLoaded
    ) {
        ServerPlayer viewer = session.player();
        ClientOffer offer = session.offer();
        int maximumDistance = Math.min(settings.maximumDistanceBlocks(), offer.maximumDistanceBlocks());
        int minimumDistance = Math.max(settings.minimumDistanceBlocks(), offer.minimumDistanceBlocks());

        List<EntitySnapshot> snapshots;
        if (minimumDistance > maximumDistance) {
            snapshots = List.of();
        } else {
            List<EntityCandidate> nearest = selectNearest(
                    server,
                    viewer,
                    candidates,
                    minimumDistance,
                    maximumDistance,
                    settings.entityCap(),
                    offer
            );
            snapshots = new ArrayList<>(nearest.size());
            for (EntityCandidate candidate : nearest) {
                UUID uuid = candidate.entity().getUUID();
                if (failedSnapshots.contains(uuid) || quarantinedTypes.contains(candidate.typeId())) {
                    continue;
                }
                EntitySnapshot snapshot = snapshotCache.get(uuid);
                if (snapshot == null) {
                    try {
                        snapshot = createSnapshot(candidate);
                        snapshotCache.put(uuid, snapshot);
                    } catch (RuntimeException | LinkageError failure) {
                        failedSnapshots.add(uuid);
                        quarantine(candidate.typeId(), failure);
                        continue;
                    }
                }
                snapshots.add(snapshot);
            }
            // A mob can be removed from the loaded entity manager the moment
            // its server chunk unloads. Preserve a bounded last-known visual
            // while its source entity no longer ticks; never force-load chunks.
            appendRetainedSnapshots(
                    snapshots, viewer, offer, settings, retained, currentlyLoaded,
                    minimumDistance, maximumDistance);
        }

        try {
            session.send(new EntitySnapshotPacket(
                    dimensionKey,
                    sequence,
                    settings.updateIntervalTicks(),
                    true,
                    snapshots
            ));
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Failed to send an entity snapshot to {}", viewer.getGameProfile().getName(), failure);
        }
    }

    private void pruneRetained(Map<UUID, RetainedSnapshot> retained, long gameTick) {
        retained.entrySet().removeIf(entry -> {
            RetainedSnapshot cache = entry.getValue();
            Entity source = cache.source().get();
            if (gameTick - cache.lastSeenTick() > RETAIN_UNLOADED_TICKS) {
                return true;
            }
            if (source == null) {
                return false;
            }
            if (source.isRemoved()) {
                // Keep only genuine unloads; do not display a dead or discarded
                // boss as a ghost after it has been removed from the world.
                return source.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK;
            }
            return !source.isAlive();
        });
    }

    private static void trimRetained(Map<UUID, RetainedSnapshot> retained) {
        if (retained.size() <= MAX_RETAINED_PER_LEVEL) {
            return;
        }
        List<Map.Entry<UUID, RetainedSnapshot>> oldestFirst =
                new ArrayList<>(retained.entrySet());
        oldestFirst.sort(Comparator.comparingLong(entry -> entry.getValue().lastSeenTick()));
        for (int i = 0; i < oldestFirst.size() - MAX_RETAINED_PER_LEVEL; i++) {
            retained.remove(oldestFirst.get(i).getKey());
        }
    }

    private void appendRetainedSnapshots(
            List<EntitySnapshot> snapshots,
            ServerPlayer viewer,
            ClientOffer offer,
            ExtraServerSettings settings,
            Map<UUID, RetainedSnapshot> retained,
            Set<UUID> currentlyLoaded,
            int minimumDistance,
            int maximumDistance
    ) {
        if (snapshots.size() >= settings.entityCap()) {
            return;
        }
        double minSq = square(minimumDistance);
        List<RetainedSnapshot> available = new ArrayList<>();
        for (Map.Entry<UUID, RetainedSnapshot> entry : retained.entrySet()) {
            if (currentlyLoaded.contains(entry.getKey())) {
                continue;
            }
            RetainedSnapshot cached = entry.getValue();
            EntitySnapshot snapshot = cached.snapshot();
            if (quarantinedTypes.contains(snapshot.typeId())
                    || !settings.selector().selects(snapshot.typeId())) {
                continue;
            }
            int typeMaximum = Math.min(maximumDistance, offer.maximumDistanceFor(snapshot.typeId()));
            if (typeMaximum <= 0) {
                continue;
            }
            Entity previousSource = cached.source().get();
            if (previousSource != null && !previousSource.broadcastToPlayer(viewer)) {
                continue;
            }
            double distanceSq = distanceSquared(viewer, snapshot);
            if (distanceSq >= minSq && distanceSq <= square(typeMaximum)) {
                available.add(cached);
            }
        }
        available.sort(Comparator.comparingDouble(
                cached -> distanceSquared(viewer, cached.snapshot())));
        for (RetainedSnapshot cached : available) {
            if (snapshots.size() >= settings.entityCap()) {
                break;
            }
            snapshots.add(cached.snapshot());
        }
    }

    private static double distanceSquared(ServerPlayer viewer, EntitySnapshot snapshot) {
        double x = viewer.getX() - snapshot.x();
        double y = viewer.getY() - snapshot.y();
        double z = viewer.getZ() - snapshot.z();
        return x * x + y * y + z * z;
    }

    private static List<EntityCandidate> selectNearest(
            MinecraftServer server,
            ServerPlayer viewer,
            SpatialCellIndex<EntityCandidate> candidates,
            int minimumDistanceBlocks,
            int maximumDistanceBlocks,
            int cap,
            ClientOffer viewerOffer
    ) {
        if (cap <= 0) {
            return List.of();
        }

        PriorityQueue<RankedCandidate> nearest = new PriorityQueue<>(cap, NEAREST_FIRST.reversed());
        double minimumDistanceSquared = square(minimumDistanceBlocks);
        double maximumDistanceSquared = square(maximumDistanceBlocks);
        int requestedViewDistance = viewer.requestedViewDistance();
        int serverViewDistance = server.getPlayerList().getViewDistance();
        int vanillaViewDistanceBlocks = Math.max(2, Math.min(requestedViewDistance, serverViewDistance)) * 16;
        candidates.visitNearby(
                viewer.getX(),
                viewer.getZ(),
                maximumDistanceBlocks,
                () -> searchDistanceSquared(nearest, cap, maximumDistanceSquared),
                cellCandidates -> {
                    for (EntityCandidate candidate : cellCandidates) {
                        Entity entity = candidate.entity();
                        int typeMaximum = Math.min(maximumDistanceBlocks,
                                viewerOffer.maximumDistanceFor(candidate.typeId()));
                        if (typeMaximum <= 0 || !entity.broadcastToPlayer(viewer)) {
                            continue;
                        }

                        double distanceSquared = viewer.distanceToSqr(entity);
                        if (distanceSquared < minimumDistanceSquared || distanceSquared > square(typeMaximum)) {
                            continue;
                        }

                        VanillaEntityHandoff.SnapshotNeed need = VanillaEntityHandoff.classify(
                                isTrackedByVanilla(viewer, candidate, vanillaViewDistanceBlocks),
                                distanceSquared,
                                entity.getBoundingBox().getSize(),
                                viewerOffer.entityViewScaleQ10()
                        );
                        if (need == VanillaEntityHandoff.SnapshotNeed.NONE) {
                            continue;
                        }

                        RankedCandidate ranked = new RankedCandidate(candidate, distanceSquared, need);

                        if (nearest.size() < cap) {
                            nearest.add(ranked);
                        } else if (isBetter(ranked, nearest.peek())) {
                            nearest.remove();
                            nearest.add(ranked);
                        }
                    }
                }
        );

        List<RankedCandidate> ranked = new ArrayList<>(nearest);
        ranked.sort(NEAREST_FIRST);
        List<EntityCandidate> result = new ArrayList<>(ranked.size());
        for (RankedCandidate candidate : ranked) {
            result.add(candidate.candidate());
        }
        return result;
    }

    private static boolean isTrackedByVanilla(
            ServerPlayer viewer,
            EntityCandidate candidate,
            int vanillaViewDistanceBlocks
    ) {
        int trackingDistance = Math.min(
                candidate.vanillaTrackingRangeBlocks(),
                vanillaViewDistanceBlocks
        );
        if (trackingDistance <= 0
                || !viewer.getChunkTrackingView().contains(candidate.entity().chunkPosition())) {
            return false;
        }

        double deltaX = viewer.getX() - candidate.entity().getX();
        double deltaZ = viewer.getZ() - candidate.entity().getZ();
        return deltaX * deltaX + deltaZ * deltaZ <= square(trackingDistance);
    }

    private static double searchDistanceSquared(
            PriorityQueue<RankedCandidate> nearest,
            int cap,
            double maximumDistanceSquared
    ) {
        if (nearest.size() < cap
                || nearest.peek().need() == VanillaEntityHandoff.SnapshotNeed.PREFETCH) {
            return maximumDistanceSquared;
        }
        return nearest.peek().distanceSquared();
    }

    private static boolean isBetter(RankedCandidate candidate, RankedCandidate currentWorst) {
        return NEAREST_FIRST.compare(candidate, currentWorst) < 0;
    }

    private static int vanillaTrackingRangeBlocks(MinecraftServer server, Entity entity) {
        int rangeBlocks = clientTrackingRangeBlocks(entity);
        return server.getScaledTrackingDistance(rangeBlocks);
    }

    private static int clientTrackingRangeBlocks(Entity entity) {
        int rangeBlocks = safeBlocks(entity.getType().clientTrackingRange());
        for (Entity passenger : entity.getPassengers()) {
            rangeBlocks = Math.max(rangeBlocks, clientTrackingRangeBlocks(passenger));
        }
        return rangeBlocks;
    }

    private static int safeBlocks(int chunkDistance) {
        if (chunkDistance <= 0) {
            return 0;
        }
        if (chunkDistance > Integer.MAX_VALUE / 16) {
            return Integer.MAX_VALUE;
        }
        return chunkDistance * 16;
    }

    private static EntitySnapshot createSnapshot(EntityCandidate candidate) {
        Entity entity = candidate.entity();
        Vec3 velocity = entity.getDeltaMovement();
        double verticalVelocity = SnapshotVelocity.vertical(velocity.y, entity.onGround());
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        int flags = flags(entity, living);
        return new EntitySnapshot(
                entity.getUUID(),
                candidate.typeId(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                living == null ? entity.getYRot() : living.yBodyRot,
                entity.getYHeadRot(),
                entity.getXRot(),
                velocity.x,
                verticalVelocity,
                velocity.z,
                entity.tickCount,
                entity.getPose().name(),
                flags,
                equipment(living, EquipmentSlot.MAINHAND),
                equipment(living, EquipmentSlot.OFFHAND),
                equipment(living, EquipmentSlot.FEET),
                equipment(living, EquipmentSlot.LEGS),
                equipment(living, EquipmentSlot.CHEST),
                equipment(living, EquipmentSlot.HEAD)
        );
    }

    private static int flags(Entity entity, LivingEntity living) {
        int flags = 0;
        if (entity.isOnFire()) {
            flags |= EntityFlags.ON_FIRE;
        }
        if (entity.isInvisible()) {
            flags |= EntityFlags.INVISIBLE;
        }
        if (entity.isCurrentlyGlowing()) {
            flags |= EntityFlags.GLOWING;
        }
        if (entity.isShiftKeyDown()) {
            flags |= EntityFlags.CROUCHING;
        }
        if (entity.isSprinting()) {
            flags |= EntityFlags.SPRINTING;
        }
        if (entity.isSwimming()) {
            flags |= EntityFlags.SWIMMING;
        }
        if (living != null && living.isFallFlying()) {
            flags |= EntityFlags.FALL_FLYING;
        }
        if (living != null && living.isBaby()) {
            flags |= EntityFlags.BABY;
        }
        return flags;
    }

    private static EquipmentSnapshot equipment(LivingEntity living, EquipmentSlot slot) {
        if (living == null) {
            return EquipmentSnapshot.EMPTY;
        }
        ItemStack stack = living.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return EquipmentSnapshot.EMPTY;
        }
        ResourceLocation itemIdentifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemIdentifier == null) {
            return EquipmentSnapshot.EMPTY;
        }
        return new EquipmentSnapshot(itemIdentifier.toString(), stack.getCount());
    }

    private long nextSequence() {
        if (sequence == Long.MAX_VALUE) {
            sequence = 0;
        } else {
            sequence++;
        }
        return sequence;
    }

    private void quarantine(String typeId, Throwable failure) {
        if (quarantinedTypes.add(typeId)) {
            LOGGER.warn("Quarantined server entity type {} after snapshot extraction failed", typeId, failure);
        }
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private record EntityCandidate(Entity entity, String typeId, int vanillaTrackingRangeBlocks) {
    }

    private record RetainedSnapshot(
            EntitySnapshot snapshot, long lastSeenTick, WeakReference<Entity> source
    ) {
    }

    private record RankedCandidate(
            EntityCandidate candidate,
            double distanceSquared,
            VanillaEntityHandoff.SnapshotNeed need
    ) {
    }
}
