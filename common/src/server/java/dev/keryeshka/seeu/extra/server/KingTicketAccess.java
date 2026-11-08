package dev.keryeshka.seeu.extra.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Loader bridge: NeoForge has a forceTicks overload that vanilla/Fabric
 * does not. Avoid Minecraft mixins or reflection at the call site.
 */
public interface KingTicketAccess {
    void add(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius);

    void remove(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius);

    static KingTicketAccess vanilla() {
        return new KingTicketAccess() {
            @Override
            public void add(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius) {
                level.getChunkSource().addRegionTicket(type, pos, radius, pos);
            }

            @Override
            public void remove(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius) {
                level.getChunkSource().removeRegionTicket(type, pos, radius, pos);
            }
        };
    }
}
