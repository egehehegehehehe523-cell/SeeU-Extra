package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalHbmCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Optional HBM entity mixin: exclude royals and the Queen's auxiliaries at
 * the pull query itself. This also prevents central black-hole damage and
 * avoids velocity-cancel hacks or expensive post-tick scans.
 */
@Pseudo
@Mixin(targets = "com.hbm.entity.effect.EntityBlackHole", remap = false)
public abstract class HbmBlackHoleMixin {
    @Shadow public abstract void setSize(float size);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void worldbosses$growAndExpire(CallbackInfo ci) {
        Entity hole = (Entity) (Object) this;
        if (hole.level().isClientSide) return;
        CompoundTag tag = hole.getPersistentData();
        if (!tag.getBoolean(RoyalHbmCompat.HOLE_MARKER)) return;
        int age = tag.getInt(RoyalHbmCompat.HOLE_AGE);
        int lifetime = Math.max(20, tag.getInt(RoyalHbmCompat.HOLE_LIFETIME));
        if (age >= lifetime) {
            hole.discard();
            ci.cancel();
            return;
        }
        float maximum = Math.max(0.5F, tag.getFloat(RoyalHbmCompat.HOLE_MAX_SIZE));
        int grow = Math.max(1, (int) (lifetime * 0.60F));
        int shrinkStart = Math.max(grow, (int) (lifetime * 0.75F));
        float size = age < grow
                ? 0.45F + (maximum - 0.45F) * age / grow
                : age < shrinkStart
                ? maximum
                : maximum * Math.max(0F, (lifetime - age) / (float) Math.max(1, lifetime - shrinkStart));
        setSize(Math.max(0.25F, size));
        tag.putInt(RoyalHbmCompat.HOLE_AGE, age + 1);
    }

    @ModifyArg(method = "tick",
            at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                remap = false),
            index = 2, remap = false, require = 0)
    private Predicate<Entity> worldbosses$excludeRoyalPull(Predicate<Entity> original) {
        Entity hole = (Entity) (Object) this;
        boolean queensHole = hole.getPersistentData().getBoolean(RoyalHbmCompat.HOLE_MARKER);
        return original.and(entity -> !RoyalHbmCompat.excludeFromBlackHole(entity, queensHole));
    }
}
