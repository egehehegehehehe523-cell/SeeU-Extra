package dev.keryeshka.seeu.extra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityFlags;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EquipmentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
    private static final long PROXY_REUSE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final long ANIMATION_TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

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

    @FunctionalInterface
    public interface ProxyRenderSubmission {
        void submit(String typeId, Entity entity, Vec3 position, Runnable draw);
    }

    private static final ProxyRenderSubmission DIRECT_SUBMISSION =
            (typeId, entity, position, draw) -> draw.run();

    public void render(
            PoseStack poseStack,
            MultiBufferSource consumers,
            Vec3 cameraPosition,
            Frustum frustum,
            float partialTick,
            ClientOffer offer
    ) {
        render(poseStack, consumers, cameraPosition, frustum, partialTick, offer, DIRECT_SUBMISSION);
    }

    public void render(
            PoseStack poseStack,
            MultiBufferSource consumers,
            Vec3 cameraPosition,
            Frustum frustum,
            float partialTick,
            ClientOffer offer,
            ProxyRenderSubmission submission
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

        boolean shaderPackActive = ShaderPackState.isActive();
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
            DistantEntityVisualHooks.Adapter adapter = DistantEntityVisualHooks.adapter(snapshot.typeId());
            boolean persistentVisual = offer.persistentAnimations().contains(snapshot.typeId())
                    || (adapter != null && adapter.suppressNativeDraw());
            boolean suppressNative = adapter != null && adapter.suppressNativeDraw();
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
                // An external renderer adapter may take over native draws.
                if (vanillaCovered && !suppressNative) {
                    continue;
                }

                Entity renderEntity;
                Vec3 position;
                float renderYaw;
                // A client entity just beyond the normal chunk boundary
                // may exist but no longer receive ticks. Never use it as the
                // distant visual there: its GeckoLib controller can freeze.
                double vanillaChunkBoundary = minecraft.options.getEffectiveRenderDistance() * 16.0D;
                boolean withinTickingClientArea = vanillaEntity != null
                        && Math.max(
                                Math.abs(cameraPosition.x - vanillaEntity.getX()),
                                Math.abs(cameraPosition.z - vanillaEntity.getZ())
                        ) <= vanillaChunkBoundary;
                Entity liveNative = withinTickingClientArea && persistentVisual
                        ? vanillaEntity : null;
                if (withinTickingClientArea && !persistentVisual) {
                    // For ordinary entities, retain vanilla's native controller.
                    renderEntity = vanillaEntity;
                    position = vanillaEntity.position();
                    renderYaw = vanillaEntity.getYRot();
                } else {
                    if (proxy == null || proxy.level != level || !proxy.typeId.equals(snapshot.typeId())) {
                        proxy = createProxy(level, snapshot, persistentVisual);
                        proxies.put(snapshot.uuid(), proxy);
                    }
                    proxy.lastSeenFrame = currentFrame;
                    proxy.lastSeenNanos = now;
                    // GeckoLib's native/proxy animation cache is shared by
                    // royal adapters. Never use the PLAYER's tick count here:
                    // a persistent boss may be years older than the player,
                    // causing GeckoLib's animation clock to run backwards on
                    // native/proxy transitions and freeze its flying loop.
                    // Inside ticking chunks use the real entity coordinates;
                    // distant snapshots can trail a charging boss by multiple
                    // blocks, otherwise misaligning the visible model and hitbox.
                    applyPosition(proxy.entity, state,
                            proxy.animationTick(state.age(), liveNative, now), liveNative);
                    if (proxy.appliedRevision != state.revision()) {
                        applySnapshotState(proxy.entity, snapshot);
                        proxy.appliedRevision = state.revision();
                    }
                    if (liveNative != null) {
                        syncNativeState(liveNative, proxy.entity);
                        if (adapter != null) adapter.syncNativeState(liveNative, proxy.entity);
                        proxy.nativeStatePresent = true;
                    } else if (proxy.nativeStatePresent) {
                        if (adapter != null) adapter.detached(proxy.entity);
                        proxy.nativeStatePresent = false;
                    }
                    renderEntity = proxy.entity;
                    position = liveNative != null ? liveNative.position() : state.position();
                    renderYaw = liveNative != null ? liveNative.getYRot() : state.bodyYaw();
                }

                boolean frustumVisible = shouldRenderBeyondVanillaDistance(
                        renderEntity, frustum, adapter);
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
                boolean syntheticEntity = renderEntity != vanillaEntity;
                // Full-bright block light and a glowing tag can turn
                // synthetic entities emissive in Iris shader packs.
                if (syntheticEntity) {
                    renderEntity.setGlowingTag(!shaderPackActive
                            && EntityFlags.has(snapshot.flags(), EntityFlags.GLOWING));
                }
                // Never ask the client to load a distant chunk for its light. When the
                // chunk is available, sample normally; otherwise use daylight without
                // fabricated maximum block light (which shaders interpret as emission).
                int packedLight = shaderPackActive
                        ? (level.hasChunkAt(BlockPos.containing(position))
                            ? dispatcher.getPackedLightCoords(renderEntity, partialTick)
                            : LightTexture.FULL_SKY)
                        : LightTexture.FULL_BRIGHT;
                poseStack.pushPose();
                try {
                    submission.submit(snapshot.typeId(), renderEntity, position, () -> dispatcher.render(
                            renderEntity,
                            position.x - cameraPosition.x,
                            position.y - cameraPosition.y,
                            position.z - cameraPosition.z,
                            renderYaw,
                            partialTick,
                            poseStack,
                            consumers,
                            packedLight
                    ));
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

        // Keep animation controllers across brief native tracking gaps only.
        proxies.values().removeIf(proxy -> proxy.lastSeenFrame != currentFrame
                && now - proxy.lastSeenNanos > PROXY_REUSE_NANOS);
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
            Entity entity, Frustum frustum, DistantEntityVisualHooks.Adapter adapter) {
        // Bypass vanilla's distance test; optional adapters expand the
        // physical hitbox to cover large animated model geometry.
        double margin = adapter == null ? Math.max(4.0D, entity.getBbWidth())
                : adapter.renderMargin(entity);
        return frustum == null || frustum.isVisible(
                entity.getBoundingBox().inflate(margin)
        );
    }

    /** True when a registered adapter owns the tracked native entity's draw. */
    public boolean ownsNativeRender(Entity nativeEntity, ClientOffer offer) {
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(nativeEntity.getType()).toString();
        DistantEntityVisualHooks.Adapter adapter = DistantEntityVisualHooks.adapter(typeId);
        if (adapter == null || !adapter.suppressNativeDraw()
                || !offer.enabled() || offer.maximumDistanceFor(typeId) <= 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().toString().equals(tracker.dimensionKey())) {
            return false;
        }
        // A proxy and native entity deliberately share a UUID. Never cancel
        // the synthetic renderer's own draw: only the entity registered in
        // ClientLevel by its runtime ID is eligible for vanilla suppression.
        if (minecraft.level.getEntity(nativeEntity.getId()) != nativeEntity) {
            return false;
        }
        ProxyEntry entry = proxies.get(nativeEntity.getUUID());
        if (entry == null || entry.level != minecraft.level || entry.animationCache == null) {
            return false;
        }
        for (TrackedExtraEntity tracked : tracker.entities()) {
            InterpolatedEntityState state = tracked.sample(System.nanoTime());
            if (state.snapshot().uuid().equals(nativeEntity.getUUID())) {
                double distance = minecraft.player.distanceToSqr(state.position());
                int max = Math.min(offer.maximumDistanceBlocks(), offer.maximumDistanceFor(typeId));
                return max > 0 && distance >= square(offer.minimumDistanceBlocks())
                        && distance <= square(max);
            }
        }
        return false;
    }

    /** Offer GeckoLib's native trigger packets the persistent proxy controller. */
    public Object animationCacheForNative(Entity nativeEntity) {
        if (nativeEntity == null || Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.getEntity(nativeEntity.getId()) != nativeEntity
                || DistantEntityVisualHooks.adapter(BuiltInRegistries.ENTITY_TYPE.getKey(nativeEntity.getType()).toString()) == null) {
            return null;
        }
        ProxyEntry entry = proxies.get(nativeEntity.getUUID());
        return entry != null && entry.level == nativeEntity.level() ? entry.animationCache : null;
    }

    private static void syncNativeState(Entity source, Entity destination) {
        var data = source.getEntityData().getNonDefaultValues();
        if (data != null) {
            destination.getEntityData().assignValues(data);
        }
        if (source instanceof LivingEntity real && destination instanceof LivingEntity proxy) {
            proxy.setHealth(real.getHealth());
        }
    }

    private ProxyEntry createProxy(ClientLevel level, EntitySnapshot snapshot, boolean persistentVisual) {
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
        if (persistentVisual) {
            try {
                animationCache = entity.getClass().getMethod("getAnimatableInstanceCache").invoke(entity);
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.debug("No accessible persistent animation cache for {}", snapshot.typeId(), failure);
            }
        }
        return new ProxyEntry(level, snapshot.typeId(), entity, animationCache);
    }

    private static void applyPosition(Entity entity, InterpolatedEntityState state,
                                      int animationTick, Entity liveNative) {
        Vec3 position = liveNative == null ? state.position() : liveNative.position();
        float bodyYaw = liveNative == null ? state.bodyYaw()
                : liveNative instanceof LivingEntity living ? living.yBodyRot : liveNative.getYRot();
        float headYaw = liveNative == null ? state.headYaw()
                : liveNative instanceof LivingEntity living ? living.getYHeadRot() : liveNative.getYRot();
        float pitch = liveNative == null ? state.pitch() : liveNative.getXRot();
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
        private boolean nativeStatePresent;
        private final ClientLevel level;
        private final String typeId;
        private final Entity entity;
        private final Object animationCache;
        private long appliedRevision = Long.MIN_VALUE;
        private long animationClockNanos = Long.MIN_VALUE;
        private long animationRemainderNanos;
        private int animationAge;
        private long lastSeenFrame;
        private long lastSeenNanos;

        private ProxyEntry(ClientLevel level, String typeId, Entity entity, Object animationCache) {
            this.level = level;
            this.typeId = typeId;
            this.entity = entity;
            this.animationCache = animationCache;
        }

        /** A per-proxy, never-rewinding GeckoLib clock in the boss's own age domain. */
        private int animationTick(int snapshotAge, Entity nativeEntity, long now) {
            int authoritativeAge = Math.max(0, snapshotAge);
            if (nativeEntity != null) {
                authoritativeAge = Math.max(authoritativeAge, nativeEntity.tickCount);
            }
            if (animationClockNanos == Long.MIN_VALUE) {
                animationClockNanos = now;
                animationAge = authoritativeAge;
                return animationAge;
            }

            // Continue advancing through packet gaps, even if the last
            // received snapshot age is stale. Do not fast-forward minutes of
            // model animation after a renderer has been out of view.
            long elapsed = Math.max(0L, now - animationClockNanos);
            long bounded = Math.min(elapsed, TimeUnit.SECONDS.toNanos(1));
            long total = bounded + animationRemainderNanos;
            long passedTicks = total / ANIMATION_TICK_NANOS;
            animationRemainderNanos = total % ANIMATION_TICK_NANOS;
            animationClockNanos = now;

            long advanced = (long) animationAge + passedTicks;
            animationAge = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(advanced, (long) authoritativeAge));
            return animationAge;
        }
    }
}
