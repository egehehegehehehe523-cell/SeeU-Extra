package dev.keryeshka.worldbosses.client;

import dev.keryeshka.seeu.extra.client.DistantEntityVisualHooks;
import dev.keryeshka.worldbosses.WorldBosses;
import net.neoforged.api.distmarker.Dist;
import dev.keryeshka.seeu.extra.neoforge.client.SeeUExtraNeoForgeClient;
import net.neoforged.fml.common.Mod;

@Mod(value = WorldBosses.MOD_ID, dist = Dist.CLIENT)
public final class WorldBossesClient {
    public WorldBossesClient() {
        var royal = new RoyalVisualAdapter();
        DistantEntityVisualHooks.register("antarchy:king", royal);
        DistantEntityVisualHooks.register("antarchy:queen", royal);
        // One-time suggested default, without overriding an F8 user opt-out.
        SeeUExtraNeoForgeClient.enableDistantEntityByDefaultOnce("antarchy:queen", 2048);
    }
}
