package dev.keryeshka.worldbosses.network;

import dev.keryeshka.worldbosses.WorldBosses;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** One tiny packet per visible distant strike and periodic synchronization.
 * remainingTicks=0 removes the associated beam immediately.
 */
public record RoyalStrikePayload(UUID bossId, Vec3 impact, int remainingTicks)
        implements CustomPacketPayload {
    public static final Type<RoyalStrikePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WorldBosses.MOD_ID, "distant_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoyalStrikePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.bossId());
                        buf.writeDouble(packet.impact().x);
                        buf.writeDouble(packet.impact().y);
                        buf.writeDouble(packet.impact().z);
                        buf.writeVarInt(packet.remainingTicks());
                    },
                    buf -> new RoyalStrikePayload(buf.readUUID(),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
