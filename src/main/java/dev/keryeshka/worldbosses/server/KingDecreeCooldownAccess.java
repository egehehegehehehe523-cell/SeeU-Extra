package dev.keryeshka.worldbosses.server;

/**
 * Optional Antarchy King bridge. The native entity and the lightweight
 * distant-decree manager share one cooldown across offload/restore.
 */
public interface KingDecreeCooldownAccess {
    /** Finish any native decree and return the cooldown the distant boss inherits. */
    int seeuExtra$finishDecreeForOffload();

    /** Retain whichever cooldown is longer after the King returns to combat. */
    void seeuExtra$inheritDecreeCooldown(int ticks);
}
