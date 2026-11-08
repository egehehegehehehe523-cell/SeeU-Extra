package dev.keryeshka.seeu.extra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityFlags;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EquipmentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExtraEntityRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
    private static final AtomicInteger NEXT_PROXY_ID = new AtomicInteger(1_100_000_000);
    private static final String KING_TYPE = "antarchy:king";
    // Preserve the controller across brief native-tracking gaps.
    private static final long KING_PROXY_REUSE_NANOS = TimeUnit.MINUTES.toNanos(2);

    private final ExtraEntityTracker tracker;
    private final Map<UUID, ProxyEntry> proxies = new HashMap<>();
    private final Set<String> quarantinedTypes = new HashSet<>();
    private final Set<String> loggedBeyondVanillaTypes = new HashSet<>();
    private final Map<UUID, Entity> realEntities = new HashMap<>();
    private long frame;
    private boolean loggedFirstSubmission;

    public ExtraEntityRenderer(ExtraEntityTracker tracker) {
        this.tracker = tracker;
    }

    public void clear() {
        proxies.clear();
        quarantinedTypes.clear();
        loggedBeyondVanillaTypes.clear();
        realEntities.clear();
        frame = 0;
        loggedFirstSubmission = false;
    }

    public void render(
            PoseStack poseStack,
            MultiBufferSource consumers,
            Vec3 cameraPosition,
            Frustum frustum,
            float partialTick,
            ClientOffer offer
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer viewer = minecraft.player;
        if (!offer.enabled() || level == null || viewer == null) {
            proxies.clear();
            return;
        }
        if (!level.dimension().location().toString().equals(tracker.dimensionKey())) {
            proxies.clear();
            return;
        }

        long currentFrame = nextFrame();
        long now = System.nanoTime();
        double minimumDistanceSquared = square(offer.minimumDistanceBlocks());
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        realEntities.clear();
        for (Entity entity : level.entitiesForRendering()) {
            realEntities.put(entity.getUUID(), entity);
        }

        for (TrackedExtraEntity tracked : tracker.entities()) {
            InterpolatedEntityState state = tracked.sample(now);
            EntitySnapshot snapshot = state.snapshot();
            double distanceSquared = viewer.distanceToSqr(state.position());
            int typeMaximum = offer.maximumDistanceFor(snapshot.typeId());
            if (typeMaximum <= 0 || distanceSquared < minimumDistanceSquared
                    || distanceSquared > square(Math.min(typeMaximum, offer.maximumDistanceBlocks()))) {
                continue;
            }
            Entity vanillaEntity = realEntities.get(snapshot.uuid());
            boolean vanillaCovered = vanillaEntity != null && isCoveredByVanilla(
                    dispatcher,
                    vanillaEntity,
                    frustum,
                    cameraPosition
            );
            if (quarantinedTypes.contains(snapshot.typeId())) {
                continue;
            }

            try {
                ProxyEntry proxy = proxies.get(snapshot.uuid());
                if (proxy != null) {
                    // Do not discard the proxy while vanilla is active; a
                    // transient re-tracking gap should reuse its controller.
                    proxy.lastSeenFrame = currentFrame;
                    proxy.lastSeenNanos = now;
                }
                // The King always uses his persistent proxy, even in native combat range.
                // RoyalBossRendererMixin prevents vanilla's duplicate King draw.
                if (vanillaCovered && !KING_TYPE.equals(snapshot.typeId())) {
                    continue;
                }

                Entity renderEntity;
                Vec3 position;
                float renderYaw;                // A client entity just beyond the normal chunk boundary
                // may exist but no longer receive ticks. Never use it as the
                // distant visual there: its GeckoLib controller can freeze.
                double vanillaChunkBoundary = minecraft.options.getEffectiveRenderDistance() * 16.0D;
                boolean withinTickingClientArea = vanillaEntity != null
                        && Math.max(
                                Math.abs(cameraPosition.x - vanillaEntity.getX()),
                                Math.abs(cameraPosition.z - vanillaEntity.getZ())
                        ) <= vanillaChunkBoundary;
                Entity liveKing = withinTickingClientArea && KING_TYPE.equals(snapshot.typeId())
                        ? vanillaEntity : null;
                if (withinTickingClientArea && !KING_TYPE.equals(snapshot.typeId())) {
                    // For ordinary entities, retain vanilla's native controller.
                    renderEntity = vanillaEntity;
                    position = vanillaEntity.position();
                    renderYaw = vanillaEntity.getYRot();
                } else {
                    if (proxy == null || proxy.level != level || !proxy.typeId.equals(snapshot.typeId())) {
                        proxy = createProxy(level, snapshot);
                        proxies.put(snapshot.uuid(), proxy);
                    }
                    proxy.lastSeenFrame = currentFrame;
                    proxy.lastSeenNanos = now;
                    // Use a monotonic client tick for the synthetic GeckoLib
                    // entity; snapshot ages can be stale or arrive in bursts.
                    // Inside ticking chunks use the real King's coordinates;
                    // distant snapshots can trail a charging boss by multiple
                    // blocks, otherwise misaligning the visible model and hitbox.
                    applyPosition(proxy.entity, state, viewer.tickCount, liveKing);
                    if (proxy.appliedRevision != state.revision()) {
                        applySnapshotState(proxy.entity, snapshot);
                        proxy.appliedRevision = state.revision();
                    }
                    if (liveKing != null) {
                        // Antarchy's synced royal data drives attack layers
                        // and phases, while the proxy owns the visual controller.
                        syncNativeKingState(liveKing, proxy.entity);
                        proxy.nativeKingPresent = true;
                    } else if (proxy.nativeKingPresent) {
                        // The real King was offloaded/untracked. A former
                        // attack must never remain on the retained proxy.
                        KingBeamState.clear(proxy.entity);
                        proxy.nativeKingPresent = false;
                    }
                    renderEntity = proxy.entity;
                    position = liveKing != null ? liveKing.position() : state.position();
                    renderYaw = liveKing != null ? liveKing.getYRot() : state.bodyYaw();
                }

                boolean frustumVisible = shouldRenderBeyondVanillaDistance(
                        renderEntity, frustum, KING_TYPE.equals(snapshot.typeId()));
                double horizontalX = Math.abs(cameraPosition.x - state.position().x);
                double horizontalZ = Math.abs(cameraPosition.z - state.position().z);
                if (Math.max(horizontalX, horizontalZ)
                        > minecraft.options.getEffectiveRenderDistance() * 16.0D
                        && loggedBeyondVanillaTypes.add(snapshot.typeId())) {
                    LOGGER.info(
                            "SeeU Extra proxy beyond normal chunks: type={}, distance={}, frustumVisible={}",
                            snapshot.typeId(), Math.round(Math.sqrt(distanceSquared)), frustumVisible);
                }
                if (!frustumVisible) {
                    continue;
                }
                poseStack.pushPose();
                try {
                    dispatcher.render(
                            renderEntity,
                            position.x - cameraPosition.x,
                            position.y - cameraPosition.y,
                            position.z - cameraPosition.z,
                            renderYaw,
                            partialTick,
                            poseStack,
                            consumers,
                            LightTexture.FULL_BRIGHT
                    );
                    if (!loggedFirstSubmission) {
                        LOGGER.info(
                                "Submitted first SeeU Extra entity: type={}, distance={}",
                                snapshot.typeId(),
                                Math.round(Math.sqrt(distanceSquared))
                        );
                        loggedFirstSubmission = true;
                    }
                } finally {
                    poseStack.popPose();
                }
            } catch (RuntimeException | LinkageError failure) {
                quarantine(snapshot.typeId(), failure);
            }
        }

        // Keep one dormant King proxy across native handoffs; only tracked
        // snapshots actually render, so this cannot duplicate a real boss.
        // World changes and disconnects still clear the cache immediately.
        proxies.values().removeIf(proxy -> proxy.lastSeenFrame != currentFrame
                && (!KING_TYPE.equals(proxy.typeId)
                        || now - proxy.lastSeenNanos > KING_PROXY_REUSE_NANOS));
    }

    private static boolean isCoveredByVanilla(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            Frustum frustum,
            Vec3 cameraPosition
    ) {
        // Having a real entity in ClientLevel is not proof that vanilla still
        // renders or ticks it. World/entity distance settings may already have
        // removed it from the normal render pass.
        int renderChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        double horizontalX = cameraPosition.x - entity.getX();
        double horizontalZ = cameraPosition.z - entity.getZ();
        double vanillaBoundary = renderChunks * 16.0D;
        if (Math.max(Math.abs(horizontalX), Math.abs(horizontalZ)) > vanillaBoundary) {
            return false;
        }
        return frustum == null
                ? entity.shouldRender(cameraPosition.x, cameraPosition.y, cameraPosition.z)
                : dispatcher.shouldRender(entity, frustum,
                        cameraPosition.x, cameraPosition.y, cameraPosition.z);
    }

    private static boolean shouldRenderBeyondVanillaDistance(
            Entity entity, Frustum frustum, boolean king) {
        // Bypass vanilla's distance test. Antarchy's King model is much wider
        // than its physical hitbox (giant animated wings/tail); avoid frustum
        // popping when only those parts are inside the camera view.
        double margin = king ? 96.0D : Math.max(4.0D, entity.getBbWidth());
        return frustum == null || frustum.isVisible(
                entity.getBoundingBox().inflate(margin)
        );
    }

    /** True only when the same King has a currently renderable SeeU snapshot. */
    public boolean ownsKingRender(Entity nativeKing, ClientOffer offer) {
        if (!KING_TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(nativeKing.getType()).toString())
                || !offer.enabled() || offer.maximumDistanceFor(KING_TYPE) <= 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().toString().equals(tracker.dimensionKey())) {
            return false;
        }
        // Proxy and native King deliberately have the same UUID. Never cancel
        // the synthetic renderer's own draw: only the entity registered in
        // ClientLevel by its runtime ID is eligible for vanilla suppression.
        if (minecraft.level.getEntity(nativeKing.getId()) != nativeKing) {
            return false;
        }
        ProxyEntry entry = proxies.get(nativeKing.getUUID());
        if (entry == null || entry.level != minecraft.level || entry.animationCache == null) {
            return false;
        }
        for (TrackedExtraEntity tracked : tracker.entities()) {
            InterpolatedEntityState state = tracked.sample(System.nanoTime());
            if (state.snapshot().uuid().equals(nativeKing.getUUID())) {
                double distance = minecraft.player.distanceToSqr(state.position());
                int max = Math.min(offer.maximumDistanceBlocks(), offer.maximumDistanceFor(KING_TYPE));
                return max > 0 && distance >= square(offer.minimumDistanceBlocks())
                        && distance <= square(max);
            }
        }
        return false;
    }

    /** Give GeckoLib's native King trigger packets the same long-lived controller. */
    public Object animationCacheForNativeKing(Entity nativeKing) {
        if (nativeKing == null || Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.getEntity(nativeKing.getId()) != nativeKing
                || !KING_TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(nativeKing.getType()).toString())) {
            return null;
        }
        ProxyEntry entry = proxies.get(nativeKing.getUUID());
        return entry != null && entry.level == nativeKing.level() ? entry.animationCache : null;
    }

    private static void syncNativeKingState(Entity source, Entity destination) {
        var data = source.getEntityData().getNonDefaultValues();
        if (data != null) {
            destination.getEntityData().assignValues(data);
        }
        // getNonDefaultValues() intentionally omits changes back to default.
        // RoyalBossEntity's BEAM_ACTIVE defaults to false, so the normal
        // entity-data copy alone leaves an old beam stuck on the proxy.
        KingBeamState.copy(source, destination);
        if (source instanceof LivingEntity real && destination instanceof LivingEntity proxy) {
            proxy.setHealth(real.getHealth());
        }
    }

    /**
     * Antarchy is an optional runtime dependency, not a compile dependency.
     * Its public beam API lets us mirror all three head beams without
     * modifying vanilla/NeoVoxy rendering or restarting GeckoLib controllers.
     */
    private static final class KingBeamState {
        private static final Object[] SLOTS;
        private static final Method GET_END;
        private static final Method SET_END;
        private static boolean usable = true;

        static {
            Object[] slots = new Object[0];
            Method get = null;
            Method set = null;
            try {
                Class<?> boss = Class.forName(
                        "com.craisinlord.antarchy.content.entity.royal.RoyalBossEntity");
                Class<?> slot = Class.forName(
                        "com.craisinlord.antarchy.content.entity.royal.RoyalHead$Slot");
                slots = slot.getEnumConstants();
                get = boss.getMethod("getRoyalBeamEndPosition", slot);
                set = boss.getMethod("setRoyalBeamEndPosition", slot, Vec3.class);
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.warn("Cannot synchronize optional Antarchy King beam visuals", failure);
            }
            SLOTS = slots;
            GET_END = get;
            SET_END = set;
        }

        private static void copy(Entity source, Entity destination) {
            if (!usable || GET_END == null || SET_END == null) return;
            try {
                for (Object slot : SLOTS) {
                    // A null end is important: it clears a previously active
                    // beam instead of leaving the last synced coordinates.
                    Vec3 end = (Vec3) GET_END.invoke(source, slot);
                    SET_END.invoke(destination, slot, end);
                }
            } catch (ReflectiveOperationException | RuntimeException failure) {
                disable(failure);
            }
        }

        private static void clear(Entity destination) {
            if (!usable || SET_END == null) return;
            try {
                for (Object slot : SLOTS) {
                    SET_END.invoke(destination, slot, (Vec3) null);
                }
            } catch (ReflectiveOperationException | RuntimeException failure) {
                disable(failure);
            }
        }

        private static void disable(Exception failure) {
            usable = false;
            LOGGER.warn("Disabling Antarchy beam-state sync after an unexpected API mismatch", failure);
        }
    }

    private ProxyEntry createProxy(ClientLevel level, EntitySnapshot snapshot) {
        ResourceLocation identifier = ResourceLocation.tryParse(snapshot.typeId());
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            throw new IllegalArgumentException("Client registry has no entity type " + snapshot.typeId());
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(identifier);
        if (entityType == null) {
            throw new IllegalArgumentException("Client registry returned no entity type " + snapshot.typeId());
        }
        Entity entity = entityType.create(level);
        if (entity == null) {
            throw new IllegalStateException("Entity type returned no proxy for " + snapshot.typeId());
        }

        entity.setId(nextProxyId());
        entity.setUUID(snapshot.uuid());
        entity.noPhysics = true;
        entity.setNoGravity(true);
        Object animationCache = null;
        if (KING_TYPE.equals(snapshot.typeId())) {
            try {
                animationCache = entity.getClass().getMethod("getAnimatableInstanceCache").invoke(entity);
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.warn("King GeckoLib cache could not be shared; using fallback visuals", failure);
            }
        }
        return new ProxyEntry(level, snapshot.typeId(), entity, animationCache);
    }

    private static void applyPosition(Entity entity, InterpolatedEntityState state,
                                      int animationTick, Entity liveKing) {
        Vec3 position = liveKing == null ? state.position() : liveKing.position();
        float bodyYaw = liveKing == null ? state.bodyYaw()
                : liveKing instanceof LivingEntity living ? living.yBodyRot : liveKing.getYRot();
        float headYaw = liveKing == null ? state.headYaw()
                : liveKing instanceof LivingEntity living ? living.getYHeadRot() : liveKing.getYRot();
        float pitch = liveKing == null ? state.pitch() : liveKing.getXRot();
        entity.setOldPosAndRot();
        entity.xo = position.x;
        entity.yo = position.y;
        entity.zo = position.z;
        entity.xOld = position.x;
        entity.yOld = position.y;
        entity.zOld = position.z;
        entity.moveTo(position.x, position.y, position.z, bodyYaw, pitch);
        entity.setYRot(bodyYaw);
        entity.yRotO = bodyYaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        if (entity instanceof LivingEntity living) {
            living.setYBodyRot(bodyYaw);
            living.yBodyRotO = bodyYaw;
            living.setYHeadRot(headYaw);
            living.yHeadRotO = headYaw;
        }
        entity.tickCount = animationTick;
    }

    private static void applySnapshotState(Entity entity, EntitySnapshot snapshot) {
        entity.setDeltaMovement(snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
        entity.setPose(Pose.valueOf(snapshot.pose()));
        entity.setSharedFlagOnFire(EntityFlags.has(snapshot.flags(), EntityFlags.ON_FIRE));
        entity.setInvisible(EntityFlags.has(snapshot.flags(), EntityFlags.INVISIBLE));
        entity.setGlowingTag(EntityFlags.has(snapshot.flags(), EntityFlags.GLOWING));
        entity.setShiftKeyDown(EntityFlags.has(snapshot.flags(), EntityFlags.CROUCHING));
        entity.setSprinting(EntityFlags.has(snapshot.flags(), EntityFlags.SPRINTING));
        entity.setSwimming(EntityFlags.has(snapshot.flags(), EntityFlags.SWIMMING));
        if (entity instanceof Mob mob) {
            mob.setBaby(EntityFlags.has(snapshot.flags(), EntityFlags.BABY));
        }
        if (entity instanceof LivingEntity living) {
            living.setItemSlot(EquipmentSlot.MAINHAND, itemStack(snapshot.mainHand()));
            living.setItemSlot(EquipmentSlot.OFFHAND, itemStack(snapshot.offHand()));
            living.setItemSlot(EquipmentSlot.FEET, itemStack(snapshot.feet()));
            living.setItemSlot(EquipmentSlot.LEGS, itemStack(snapshot.legs()));
            living.setItemSlot(EquipmentSlot.CHEST, itemStack(snapshot.chest()));
            living.setItemSlot(EquipmentSlot.HEAD, itemStack(snapshot.head()));
        }
    }

    private static ItemStack itemStack(EquipmentSnapshot equipment) {
        if (equipment.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation identifier = ResourceLocation.tryParse(equipment.itemId());
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(identifier);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, equipment.count());
    }

    private void quarantine(String typeId, Throwable failure) {
        if (!quarantinedTypes.add(typeId)) {
            return;
        }
        proxies.entrySet().removeIf(entry -> entry.getValue().typeId.equals(typeId));
        LOGGER.warn("Quarantined entity type {} after a proxy failure", typeId, failure);
    }

    private long nextFrame() {
        if (frame == Long.MAX_VALUE) {
            frame = 1;
            for (ProxyEntry proxy : proxies.values()) {
                proxy.lastSeenFrame = 0;
            }
        } else {
            frame++;
        }
        return frame;
    }

    private static int nextProxyId() {
        return NEXT_PROXY_ID.getAndUpdate(current ->
                current == Integer.MAX_VALUE ? 1_100_000_000 : current + 1
        );
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private static final class ProxyEntry {
        private boolean nativeKingPresent;
        private final ClientLevel level;
        private final String typeId;
        private final Entity entity;
        private final Object animationCache;
        private long appliedRevision = Long.MIN_VALUE;
        private long lastSeenFrame;
        private long lastSeenNanos;

        private ProxyEntry(ClientLevel level, String typeId, Entity entity, Object animationCache) {
            this.level = level;
            this.typeId = typeId;
            this.entity = entity;
            this.animationCache = animationCache;
        }
    }
}
