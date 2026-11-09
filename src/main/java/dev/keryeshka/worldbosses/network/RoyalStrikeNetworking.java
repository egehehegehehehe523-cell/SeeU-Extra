package dev.keryeshka.worldbosses.network;

import dev.keryeshka.worldbosses.WorldBosses;
import dev.keryeshka.worldbosses.client.RoyalStrikeClient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Clientbound packets carry only the chosen impact and remaining timer;
 * no chunk data, ticket, or per-particle traffic is transmitted.
 */
@EventBusSubscriber(modid = WorldBosses.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class RoyalStrikeNetworking {
    private RoyalStrikeNetworking() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RoyalStrikePayload.TYPE, RoyalStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> RoyalStrikeClient.accept(payload)));
    }

    public static void broadcast(ServerLevel level, RoyalStrikePayload payload, double maxRange) {
        double radiusSq = maxRange * maxRange;
        for (ServerPlayer viewer : level.players()) {
            if (!viewer.isRemoved() && !viewer.isSpectator()
                    && viewer.distanceToSqr(payload.impact()) <= radiusSq) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }
}
