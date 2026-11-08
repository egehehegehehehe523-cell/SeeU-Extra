package dev.keryeshka.worldbosses.client;

/**
 * Optional Antarchy-only bridge. The NeoForge RoyalBossEntity mixin exposes
 * the King's actual flight flag without linking the common renderer to
 * Antarchy classes or adding any Minecraft/NeoVoxy renderer mixins.
 */
public interface RoyalFlyingVisualAccess {
    boolean seeuExtra$isRoyalFlying();
    void seeuExtra$setRoyalFlying(boolean flying);
}
