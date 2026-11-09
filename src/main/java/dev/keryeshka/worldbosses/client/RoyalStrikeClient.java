package dev.keryeshka.worldbosses.client;

import dev.keryeshka.worldbosses.network.RoyalStrikePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side render-only strike state; actual HBM blast is server owned.
 * A repeated packet keeps late joiners and LOD viewers synchronized.
 */
public final class RoyalStrikeClient {
    private static final Map<UUID, Strike> ACTIVE = new HashMap<>();
    private static Object levelIdentity;
    private record Strike(Vec3 impact, long expiresAt) {}
    private RoyalStrikeClient() {}

    public static void accept(RoyalStrikePayload packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level != levelIdentity) {
            ACTIVE.clear();
            levelIdentity = level;
        }
        if (packet.remainingTicks() <= 0) {
            ACTIVE.remove(packet.bossId());
        } else {
            ACTIVE.put(packet.bossId(), new Strike(packet.impact(),
                    level.getGameTime() + Math.min(240, packet.remainingTicks())));
        }
    }

    public static Vec3 impact(UUID royalId) {
        var level = Minecraft.getInstance().level;
        if (level == null || level != levelIdentity) {
            ACTIVE.clear();
            levelIdentity = level;
            return null;
        }
        Strike strike = ACTIVE.get(royalId);
        if (strike == null) return null;
        if (level.getGameTime() >= strike.expiresAt()) {
            ACTIVE.remove(royalId);
            return null;
        }
        return strike.impact();
    }
}
