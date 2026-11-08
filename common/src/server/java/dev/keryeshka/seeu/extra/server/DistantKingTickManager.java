package dev.keryeshka.seeu.extra.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Opt-in, bounded, temporary entity-ticking chunk tickets for a single
 * Antarchy King per dimension. Tickets are removed when nobody is watching.
 * This does not change Minecraft/Antarchy AI or keep client-side proxies alive.
 */
final class DistantKingTickManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
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
        for (Map.Entry<ServerLevel, ChunkPos> entry : wanted.entrySet()) {
            ServerLevel level = entry.getKey();
            ChunkPos next = entry.getValue();
            ChunkPos previous = held.get(level);
            if (next.equals(previous)) {
                continue;
            }
            // Acquire the next ticking chunk *before* releasing the previous
            // one, preventing a temporary gap whenever the King crosses
            // a chunk boundary.
            ticketAccess.add(level, KING_TICKET, next, ENTITY_TICKING_RADIUS);
            held.put(level, next);
            if (previous != null) {
                release(level, previous);
            } else {
                LOGGER.info("Activated live King LOD ticking in dimension {}", level.dimension().location());
            }
        }

        held.entrySet().removeIf(entry -> {
            if (wanted.containsKey(entry.getKey())) {
                return false;
            }
            release(entry.getKey(), entry.getValue());
            LOGGER.info("Stopped live King LOD ticking in dimension {}", entry.getKey().dimension().location());
            return true;
        });
    }

    void clear() {
        update(Map.of());
    }

    private void release(ServerLevel level, ChunkPos pos) {
        ticketAccess.remove(level, KING_TICKET, pos, ENTITY_TICKING_RADIUS);
    }
}
