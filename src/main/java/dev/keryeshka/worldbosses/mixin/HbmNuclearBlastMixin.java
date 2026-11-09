package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalHbmCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.List;

/**
 * NTM's nuclear damage loop also inflicts knockback and fire after hurt().
 * Remove only the two royals from its iteration so ALL three effects are
 * suppressed. Manticore minions are deliberately not excluded.
 */
@Pseudo
@Mixin(targets = "com.hbm.explosion.ExplosionNukeGeneric", remap = false)
public abstract class HbmNuclearBlastMixin {
    @Redirect(method = "dealDamage",
            at = @At(value = "INVOKE",
                target = "Ljava/util/List;iterator()Ljava/util/Iterator;",
                remap = false),
            remap = false, require = 0)
    private static Iterator<Entity> worldbosses$excludeRoyalsFromNukes(List<Entity> entities) {
        if (!RoyalHbmCompat.royalImmunityEnabled()) return entities.iterator();
        return entities.stream().filter(entity -> !RoyalHbmCompat.isRoyal(entity)).iterator();
    }
}
