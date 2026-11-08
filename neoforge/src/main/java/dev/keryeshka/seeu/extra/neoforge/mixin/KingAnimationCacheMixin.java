package dev.keryeshka.seeu.extra.neoforge.mixin;

import dev.keryeshka.seeu.extra.neoforge.client.SeeUExtraNeoForgeClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Antarchy's real King receives GeckoLib trigger packets in combat, while
 * SeeU's synthetic King owns the visible model. Share the persistent proxy's
 * instance cache, including its locomotion/head/wing/body controllers.
 * InstancedAnimatableInstanceCache returns one manager regardless of entity ID.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.RoyalBossEntity", remap = false)
public abstract class KingAnimationCacheMixin {
    @Inject(method = "getAnimatableInstanceCache", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void seeuExtra$preserveKingController(CallbackInfoReturnable<Object> ci) {
        Entity boss = (Entity) (Object) this;
        Object shared = SeeUExtraNeoForgeClient.animationCacheForNativeKing(boss);
        if (shared != null) {
            ci.setReturnValue(shared);
        }
    }
}
