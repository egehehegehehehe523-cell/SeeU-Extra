package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalHbmCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keep Antarchy's own ability selection, windup, audio, animation and
 * particle cast. Replace just the original projectile entity insertion
 * with an HBM black hole at its already chosen destination.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.QueenEntity", remap = false)
public abstract class QueenHbmBlackHoleMixin {
    @Redirect(method = "castBlackHole",
            at = @At(value = "INVOKE",
                target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                remap = false),
            remap = false, require = 0)
    private boolean worldbosses$replaceBlackHole(ServerLevel level, Entity original) {
        if (!RoyalHbmCompat.hbmAbilitiesReady()) return level.addFreshEntity(original);
        Vec3 destination = RoyalHbmCompat.nativeQueenHoleDestination(original);
        Entity queen = (Entity) (Object) this;
        if (destination != null
                && RoyalHbmCompat.spawnQueenHole(level, destination, queen.getUUID())) {
            return true;
        }
        return level.addFreshEntity(original);
    }
}
