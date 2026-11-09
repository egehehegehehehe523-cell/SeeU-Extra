package dev.keryeshka.worldbosses;

import dev.keryeshka.seeu.extra.server.DistantEntityServerHooks;
import dev.keryeshka.worldbosses.server.RoyalBossServerExtension;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Independent NeoForge add-on requiring SeeU Extra Continuation and Antarchy. */
@Mod(WorldBosses.MOD_ID)
public final class WorldBosses {
    public static final String MOD_ID = "world_bosses";
    public WorldBosses() {
        WorldBossesOptions config = WorldBossesOptions.load(FMLPaths.CONFIGDIR.get());
        RoyalBossServerExtension extension = new RoyalBossServerExtension(config);
        DistantEntityServerHooks.register(extension);
        // Queen is controlled by this addon, not by SeeU Extra's per-type
        // server selector. Run once per logical server tick; SeeU only
        // transports snapshots supplied by the extension.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                extension.tickQueen(event.getServer()));
    }
}
