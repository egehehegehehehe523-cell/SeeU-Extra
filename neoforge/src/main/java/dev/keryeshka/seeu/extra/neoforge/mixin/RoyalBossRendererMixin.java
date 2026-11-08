package dev.keryeshka.seeu.extra.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.seeu.extra.neoforge.client.SeeUExtraNeoForgeClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress only the real King's duplicate model; no Minecraft/Voxy renderer mixins. */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.client.renderer.RoyalBossRenderer", remap = false)
public abstract class RoyalBossRendererMixin {
    @Inject(method = "render(Lcom/craisinlord/antarchy/content/entity/royal/RoyalBossEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void seeuExtra$singleKingVisual(@Coerce Entity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource consumers, int packedLight, CallbackInfo ci) {
        if (SeeUExtraNeoForgeClient.ownsNativeKingRender(entity)) {
            ci.cancel();
        }
    }
}
