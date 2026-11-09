package dev.keryeshka.worldbosses.server;

import net.minecraft.world.entity.Entity;

/**
 * Server-owned Treason currency for Antarchy's King.
 *
 * Every actual Decree violation adds one point, whether the King is native
 * or stored as an offloaded persistent flight. Only a successfully started
 * special grab should spend points; missing animation assets never consume
 * them. NeoForge's persistent entity data also survives ordinary saves.
 */
public final class RoyalTreason {
    public static final String POINTS_KEY = "world_bosses_treason_points";

    private RoyalTreason() {}

    public static int points(Entity king) {
        return Math.max(0, king.getPersistentData().getInt(POINTS_KEY));
    }

    public static int award(Entity king) {
        int current = points(king);
        int updated = current == Integer.MAX_VALUE ? current : current + 1;
        king.getPersistentData().putInt(POINTS_KEY, updated);
        return updated;
    }

    public static void set(Entity king, int points) {
        king.getPersistentData().putInt(POINTS_KEY, Math.max(0, points));
    }

    /** Atomic server-tick debit. Call only once a valid grab actually begins. */
    public static boolean trySpend(Entity king, int cost) {
        if (cost <= 0) return false;
        int available = points(king);
        if (available < cost) return false;
        set(king, available - cost);
        return true;
    }
}
