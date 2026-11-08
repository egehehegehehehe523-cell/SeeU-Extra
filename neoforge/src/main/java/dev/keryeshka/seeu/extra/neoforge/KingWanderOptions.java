package dev.keryeshka.seeu.extra.neoforge;

/** Test-only, server-configurable Antarchy King idle-flight fallback. */
public final class KingWanderOptions {
    public static volatile boolean enabled = true;
    // Experimental: continuous directional idle roaming while the real King is ticking.
    // Does not make unloaded entities tick or eliminate the need for chunk tickets.
    public static volatile boolean globalRoam = false;
    // Suppress native King boss-bar packets while the persistent global bar owns the UI.
    public static volatile boolean globalBossBar = false;

    private KingWanderOptions() {
    }
}
