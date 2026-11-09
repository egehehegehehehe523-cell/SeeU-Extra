package dev.keryeshka.worldbosses.server;

import dev.keryeshka.worldbosses.network.RoyalStrikeNetworking;
import dev.keryeshka.worldbosses.network.RoyalStrikePayload;
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
 * One durable strike deadline per royal, independent of native mob ticking.
 * Handles both interrupted native beams and deliberately fired remote beams.
 * Sends a tiny clientbound endpoint snapshot, never custom chunk snapshots.
 */
public final class PendingRoyalBeams extends SavedData {
    private static final String DATA_NAME = "world_bosses_pending_royal_beams";
    private final List<Pending> active = new ArrayList<>();
    private long lastUpdateTick = Long.MIN_VALUE;

    private record Pending(UUID royalId, String typeId, Vec3 impact,
                           long dueTick, boolean visible) {}

    private static PendingRoyalBeams data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PendingRoyalBeams::new, PendingRoyalBeams::load, null), DATA_NAME);
    }

    public static boolean hasPending(ServerLevel level, UUID royalId) {
        return data(level).active.stream().anyMatch(p -> p.royalId().equals(royalId));
    }

    public static void schedule(ServerLevel level, Entity royal,
                                RoyalPendingBeamAccess.ContinuedBeam beam, String typeId) {
        if (!RoyalHbmCompat.hbmAbilitiesReady() || beam == null) return;
        scheduleDistant(level, royal.getUUID(), typeId, beam.impact(), beam.remainingTicks());
    }

    public static boolean scheduleDistant(ServerLevel level, UUID royalId, String typeId,
                                          Vec3 impact, int remainingTicks) {
        if (!RoyalHbmCompat.hbmAbilitiesReady() || royalId == null || impact == null
                || remainingTicks < 1 || hasPending(level, royalId)) return false;
        if (!"antarchy:king".equals(typeId) && !"antarchy:queen".equals(typeId)) return false;
        PendingRoyalBeams data = data(level);
        long due = level.getGameTime() + remainingTicks;
        data.active.add(new Pending(royalId, typeId, impact, due, true));
        data.setDirty();
        broadcast(level, royalId, impact, remainingTicks);
        return true;
    }

    private static void broadcast(ServerLevel level, UUID royalId, Vec3 impact, int remaining) {
        RoyalStrikeNetworking.broadcast(level,
                new RoyalStrikePayload(royalId, impact, Math.max(0, remaining)),
                RoyalHbmCompat.distantStrikeViewerRange());
    }

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) data(level).tick(level);
    }

    private void tick(ServerLevel level) {
        if (active.isEmpty()) return;
        long now = level.getGameTime();
        if (lastUpdateTick == now) return;
        lastUpdateTick = now;
        for (Iterator<Pending> iterator = active.iterator(); iterator.hasNext();) {
            Pending event = iterator.next();
            if (now >= event.dueTick()) {
                iterator.remove();
                setDirty();
                if (event.visible()) broadcast(level, event.royalId(), event.impact(), 0);
                RoyalHbmCompat.detonateRoyalBeam(level, event.royalId(), event.typeId(), event.impact());
            } else if (event.visible() && now % 20L == 0L) {
                // A joining player, or a player who has just entered SeeU
                // range, can still see an already firing beam.
                broadcast(level, event.royalId(), event.impact(),
                        (int) Math.min(200, event.dueTick() - now));
            }
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
                    position, entry.getLong("Due"), entry.getBoolean("Visible")));
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
            entry.putBoolean("Visible", p.visible());
            list.add(entry);
        }
        tag.put("Beams", list);
        return tag;
    }
}
