package dev.keryeshka.worldbosses.server;

import dev.keryeshka.worldbosses.WorldBossesOptions;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.server.DistantEntityServerHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import java.util.Set;
import java.util.HashSet;

/** Two independent single-boss flight states, one for each Antarchy royal. */
public final class RoyalBossServerExtension implements DistantEntityServerHooks.Extension {
    private static final String KING = "antarchy:king";
    private static final String QUEEN = "antarchy:queen";
    private final PersistentRoyalFlight king;
    private final PersistentRoyalFlight queen;
    private final Set<String> types;

    public RoyalBossServerExtension(WorldBossesOptions settings) {
        // Retain the existing King's SavedData key for upgrade compatibility.
        king = new PersistentRoyalFlight(KING, "seeu_extra_persistent_king",
                "king", BossEvent.BossBarColor.YELLOW, settings.enableKing, settings.distantKingDecrees, settings);
        queen = new PersistentRoyalFlight(QUEEN, "world_bosses_persistent_queen",
                "queen", BossEvent.BossBarColor.PURPLE, settings.enableQueen, false, settings);
        var configured = new HashSet<String>();
        if (settings.enableKing) configured.add(KING);
        if (settings.enableQueen) configured.add(QUEEN);
        types = Set.copyOf(configured);
    }

    @Override public Set<String> entityTypes() { return types; }
    @Override public void tick(MinecraftServer server) {
        if (types.contains(KING)) king.tick(server);
        if (types.contains(QUEEN)) queen.tick(server);
        PendingRoyalBeams.tickAll(server);
    }
    @Override public void updateBossBars(MinecraftServer server, ExtraServerSettings settings) {
        king.updateBossBars(server, settings);
        queen.updateBossBars(server, settings);
    }
    @Override public EntitySnapshot offloadedSnapshot(ServerLevel level, String typeId) {
        return switch (typeId) {
            case KING -> king.snapshot(level);
            case QUEEN -> queen.snapshot(level);
            default -> null;
        };
    }
}
