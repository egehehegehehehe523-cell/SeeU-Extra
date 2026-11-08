package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.KingDecreeCooldownAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Only touches Antarchy's King. All cooldown handling remains on the server
 * tick thread; no Minecraft, C2ME or Voxy mixins are needed.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.KingEntity", remap = false)
public abstract class KingDecreeCooldownMixin implements KingDecreeCooldownAccess {
    @Shadow private int decreeCooldownTicks;
    @Shadow private int activeDecreeTicks;
    @Shadow public abstract void clearActiveDecree();

    @Override
    public int seeuExtra$finishDecreeForOffload() {
        // Clearing an active native decree also clears its player titles and
        // applies Antarchy's phase-specific recovery/cooldown.
        if (this.activeDecreeTicks > 0) {
            this.clearActiveDecree();
        }
        return Math.max(0, this.decreeCooldownTicks);
    }

    @Override
    public void seeuExtra$inheritDecreeCooldown(int ticks) {
        this.decreeCooldownTicks = Math.max(
                this.decreeCooldownTicks, Math.max(0, ticks));
    }
}
