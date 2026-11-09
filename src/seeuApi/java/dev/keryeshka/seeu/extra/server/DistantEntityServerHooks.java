package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;

/** SeeU Extra 0.3.0 interface signature; not included in the released addon. */
public final class DistantEntityServerHooks {
    private DistantEntityServerHooks() {}

    public interface Extension {
        Set<String> entityTypes();
        void tick(MinecraftServer server);
        void updateBossBars(MinecraftServer server, ExtraServerSettings settings);
        EntitySnapshot offloadedSnapshot(ServerLevel level, String typeId);
    }

    public static void register(Extension extension) {
        throw new AssertionError("Compile-only SeeU Extra stub");
    }
}
