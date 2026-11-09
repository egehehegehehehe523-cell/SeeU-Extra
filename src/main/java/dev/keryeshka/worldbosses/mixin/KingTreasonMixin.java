package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalTreason;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * This redirect runs only after Antarchy has checked activeDecree, player
 * health and deduplicated the Decree's violation. Preserve Set.add's return
 * value exactly, and credit a point only for the first failure per participant
 * in a proclamation. No modification of Antarchy's punishment is necessary.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.KingEntity", remap = false)
public abstract class KingTreasonMixin {
    @Redirect(
            method = "violateDecree(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Set;add(Ljava/lang/Object;)Z",
                    remap = false),
            remap = false,
            require = 0
    )
    private boolean worldbosses$onRealDecreeViolation(Set<?> recorded, Object playerId) {
        @SuppressWarnings("unchecked")
        Set<Object> violators = (Set<Object>) recorded;
        boolean firstFailure = violators.add(playerId);
        if (firstFailure) RoyalTreason.award((Entity) (Object) this);
        return firstFailure;
    }
}
