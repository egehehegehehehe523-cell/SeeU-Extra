package dev.keryeshka.seeu.extra.neoforge.client;

import dev.keryeshka.seeu.extra.SeeUExtra;
import dev.keryeshka.seeu.extra.client.ExtraEntityRenderer;
import dev.keryeshka.seeu.extra.client.ExtraEntityTracker;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientConfig;
import dev.keryeshka.seeu.extra.client.SeeUExtraConfigScreen;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientEndpoint;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

@Mod(value = SeeUExtra.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SeeUExtra.MOD_ID, value = Dist.CLIENT)
public final class SeeUExtraNeoForgeClient {
    private static SeeUExtraClientEndpoint endpoint;
    private static ExtraEntityRenderer renderer;
    private static SeeUExtraClientConfig configuration;

    public SeeUExtraNeoForgeClient() {
        SeeUExtraClientConfig config = SeeUExtraClientConfig.load(FMLPaths.CONFIGDIR.get());
        configuration = config;
        ExtraEntityTracker tracker = new ExtraEntityTracker();
        renderer = new ExtraEntityRenderer(tracker);
        Supplier<ClientOffer> offerSupplier = config::offer;
        endpoint = new SeeUExtraClientEndpoint(tracker, renderer, offerSupplier);
        SeeUClientAddons.getInstance().register(
                SeeUExtra.DESCRIPTOR,
                () -> ExtraPacketCodec.encodeClientOffer(offerSupplier.get()),
                endpoint
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (configuration != null) {
            while (SeeUExtraNeoForgeKeys.CONFIGURE.consumeClick()) {
                client.setScreen(new SeeUExtraConfigScreen(client.screen, configuration));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (endpoint != null) {
            endpoint.resetWorld();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (renderer == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var consumers = Minecraft.getInstance().renderBuffers().bufferSource();
        ClientOffer offer = endpoint.synchronizeOffer();
        renderer.render(
                event.getPoseStack(),
                consumers,
                event.getCamera().getPosition(),
                event.getFrustum(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false),
                offer,
                (typeId, entity, position, draw) -> VoxyEntityDepthBridge.submit(
                        typeId, entity, position, event.getCamera().getPosition(),
                        event.getModelViewMatrix(), event.getProjectionMatrix(),
                        consumers, draw)
        );
        consumers.endBatch();
    }

    /** Client-only bridge for optional independent renderer/cache adapters. */
    public static boolean ownsNativeRender(Entity entity) {
        return renderer != null && configuration != null
                && renderer.ownsNativeRender(entity, configuration.offer());
    }

    public static Object animationCacheForNative(Entity entity) {
        return renderer == null ? null : renderer.animationCacheForNative(entity);
    }

    private static void clear() {
        if (endpoint != null) {
            endpoint.clear();
        }
    }
}
