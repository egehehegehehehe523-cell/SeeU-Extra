package dev.keryeshka.worldbosses.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Only the already-committed nuclear/Balefire centre-beam detonation is
 * detached from native entity ticking. This event is not tied to whether
 * the royal boss is currently loaded and persists in the dimension SavedData.
 *
 * An attack scheduled before offloading therefore finishes at its original
 * hit position instead of disappearing when the native mob is discarded.
 */
public final class PendingRoyalBeams extends SavedData {
    private static final String DATA_NAME = "world_bosses_pending_royal_beams";
    private final List<Pending> active = new ArrayList<>();
    private long lastUpdateTick = Long.MIN_VALUE;

    private record Pending(UUID royalId, String typeId, Vec3 impact, long dueTick) {}

    private static PendingRoyalBeams data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PendingRoyalBeams::new, PendingRoyalBeams::load, null), DATA_NAME);
    }

    public static void schedule(ServerLevel level, Entity royal,
                                RoyalPendingBeamAccess.ContinuedBeam beam, String typeId) {
        if (!RoyalHbmCompat.hbmAbilitiesReady() || beam == null) return;
        PendingRoyalBeams data = data(level);
        UUID id = royal.getUUID();
        // If the same source is re-tracked, preserve its existing deadline.
        if (data.active.stream().anyMatch(p -> p.royalId().equals(id))) return;
        long due = level.getGameTime() + Math.max(1, beam.remainingTicks());
        data.active.add(new Pending(id, typeId, beam.impact(), due));
        data.setDirty();
    }

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            data(level).tick(level);
        }
    }

    private void tick(ServerLevel level) {
        if (active.isEmpty()) return;
        long now = level.getGameTime();
        if (lastUpdateTick == now) return;
        lastUpdateTick = now;
        for (Iterator<Pending> iterator = active.iterator(); iterator.hasNext();) {
            Pending event = iterator.next();
            if (now < event.dueTick()) continue;
            iterator.remove();
            setDirty();
            RoyalHbmCompat.detonateRoyalBeam(level, event.royalId(), event.typeId(), event.impact());
        }
    }

    private static PendingRoyalBeams load(CompoundTag tag, HolderLookup.Provider registry) {
        PendingRoyalBeams data = new PendingRoyalBeams();
        ListTag entries = tag.getList("Beams", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Owner") || !entry.contains("Kind") || !entry.contains("Due")) continue;
            Vec3 position = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) continue;
            data.active.add(new Pending(entry.getUUID("Owner"), entry.getString("Kind"),
                    position, entry.getLong("Due")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        ListTag list = new ListTag();
        for (Pending p : active) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", p.royalId());
            entry.putString("Kind", p.typeId());
            entry.putDouble("X", p.impact().x);
            entry.putDouble("Y", p.impact().y);
            entry.putDouble("Z", p.impact().z);
            entry.putLong("Due", p.dueTick());
            list.add(entry);
        }
        tag.put("Beams", list);
        return tag;
    }
}
