package dev.keryeshka.seeu.extra.neoforge.mixin;

import dev.keryeshka.seeu.extra.neoforge.KingWanderOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Antarchy-only hook: retain its native boss event's player roster for
 * encounter logic and sound targeting, but prevent a second client bossbar.
 * SeeU Extra owns one continuous dimension-wide bar for the King instead.
 * No change to Queen or other Antarchy bosses.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.content.entity.royal.RoyalBossEntity", remap = false)
public abstract class KingGlobalBossBarMixin {
    @Shadow(remap = false) @Final private ServerBossEvent bossEvent;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void seeuExtra$globalKingBar(EntityType<?> entityType, Level level, CallbackInfo ci) {
        if (!level.isClientSide && KingWanderOptions.globalBossBar
                && "antarchy:king".equals(
                        BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString())) {
            // setVisible(false) leaves addPlayer/removePlayer bookkeeping intact.
            // It only stops the native bar's add/remove/update packets.
            bossEvent.setVisible(false);
        }
    }
}
