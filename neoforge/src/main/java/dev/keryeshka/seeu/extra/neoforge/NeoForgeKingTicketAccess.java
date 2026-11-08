package dev.keryeshka.seeu.extra.neoforge;

import dev.keryeshka.seeu.extra.server.KingTicketAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * NeoForge explicitly distinguishes ordinary chunk tickets from tickets
 * allowed to force entity ticking away from the player simulation range.
 */
final class NeoForgeKingTicketAccess implements KingTicketAccess {
    @Override
    public void add(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius) {
        level.getChunkSource().addRegionTicket(type, pos, radius, pos, true);
    }

    @Override
    public void remove(ServerLevel level, TicketType<ChunkPos> type, ChunkPos pos, int radius) {
        level.getChunkSource().removeRegionTicket(type, pos, radius, pos, true);
    }
}
