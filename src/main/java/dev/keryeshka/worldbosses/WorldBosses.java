package dev.keryeshka.worldbosses;

import dev.keryeshka.seeu.extra.server.DistantEntityServerHooks;
import dev.keryeshka.worldbosses.server.RoyalBossServerExtension;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import dev.keryeshka.worldbosses.server.RoyalHbmCompat;

/** Independent NeoForge add-on requiring SeeU Extra Continuation and Antarchy. */
@Mod(WorldBosses.MOD_ID)
public final class WorldBosses {
    public static final String MOD_ID = "world_bosses";
    public WorldBosses() {
        WorldBossesOptions config = WorldBossesOptions.load(FMLPaths.CONFIGDIR.get());
        DistantEntityServerHooks.register(new RoyalBossServerExtension(config));
        RoyalHbmCompat.configure(config);
        NeoForge.EVENT_BUS.addListener(RoyalHbmCompat::onIncomingDamage);
    }
}
