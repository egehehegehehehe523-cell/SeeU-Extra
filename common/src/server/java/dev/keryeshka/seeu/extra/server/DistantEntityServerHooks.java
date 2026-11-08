package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server extension point for independent mods supplying offloaded entities.
 * Registration is performed once during mod initialization. All callbacks
 * execute on Minecraft's server thread; no SeeU chunk tickets are required.
 */
public final class DistantEntityServerHooks {
    public interface Extension {
        Set<String> entityTypes();
        void tick(MinecraftServer server);
        void updateBossBars(MinecraftServer server, ExtraServerSettings settings);
        EntitySnapshot offloadedSnapshot(ServerLevel level, String typeId);
    }

    private static final List<Extension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private DistantEntityServerHooks() {}

    public static void register(Extension extension) {
        if (extension == null) throw new IllegalArgumentException("Null extension");
        if (!EXTENSIONS.contains(extension)) EXTENSIONS.add(extension);
    }

    public static List<Extension> extensions() {
        return List.copyOf(EXTENSIONS);
    }

    public static boolean priorityType(String typeId) {
        for (Extension extension : EXTENSIONS) {
            if (extension.entityTypes().contains(typeId)) return true;
        }
        return false;
    }
}
