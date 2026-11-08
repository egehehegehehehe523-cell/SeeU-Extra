package dev.keryeshka.seeu.extra.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Opt-in, bounded, temporary entity-ticking chunk tickets for a single
 * Antarchy King per dimension. Tickets are removed when nobody is watching.
 * This does not change Minecraft/Antarchy AI or keep client-side proxies alive.
 */
final class DistantKingTickManager {
    // Radius three corresponds to a level-30 (entity ticking) center in 1.21.1.
    // Do not use persistent /forceload entries or TicketType.FORCED.
    private static final int ENTITY_TICKING_RADIUS = 3;
    private static final TicketType<ChunkPos> KING_TICKET = TicketType.create(
            "seeu_extra_distant_king",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    private final Map<ServerLevel, ChunkPos> held = new IdentityHashMap<>();
    private final KingTicketAccess ticketAccess;

    DistantKingTickManager(KingTicketAccess ticketAccess) {
        this.ticketAccess = ticketAccess;
    }

    void update(Map<ServerLevel, ChunkPos> wanted) {
        held.entrySet().removeIf(entry -> {
            ChunkPos next = wanted.get(entry.getKey());
            if (entry.getValue().equals(next)) {
                return false;
            }
            release(entry.getKey(), entry.getValue());
            return true;
        });

        for (Map.Entry<ServerLevel, ChunkPos> entry : wanted.entrySet()) {
            if (!held.containsKey(entry.getKey())) {
                ServerLevel level = entry.getKey();
                ChunkPos pos = entry.getValue();
                ticketAccess.add(level, KING_TICKET, pos, ENTITY_TICKING_RADIUS);
                held.put(level, pos);
            }
        }
    }

    void clear() {
        update(Map.of());
    }

    private void release(ServerLevel level, ChunkPos pos) {
        ticketAccess.remove(level, KING_TICKET, pos, ENTITY_TICKING_RADIUS);
    }
}
