package dev.keryeshka.worldbosses.mixin;

import dev.keryeshka.worldbosses.server.RoyalStatOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace every royal AntarchySettings getter with World Bosses-owned stats.
 * Unlike copying the original TOML into static fields once, these hooks
 * remain authoritative even after Antarchy's configuration is reloaded.
 */
@Pseudo
@Mixin(targets = "com.craisinlord.antarchy.config.AntarchySettings", remap = false)
public abstract class AntarchyRoyalStatsMixin {
    @Inject(method = "kingHealth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat0(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingHealth", 3400D));
    }

    @Inject(method = "queenHealth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat1(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenHealth", 4300D));
    }

    @Inject(method = "kingAttackDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat2(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingAttackDamage", 50D));
    }

    @Inject(method = "queenAttackDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat3(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenAttackDamage", 45D));
    }

    @Inject(method = "royalBossArmor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat4(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("armor", 20D));
    }

    @Inject(method = "kingArmor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat5(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingArmor", 30D));
    }

    @Inject(method = "royalBossFollowRange", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat6(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("followRange", 128D));
    }

    @Inject(method = "royalBossMovementSpeed", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat7(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("movementSpeed", 0.22D));
    }

    @Inject(method = "royalBossKnockbackResistance", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat8(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("knockbackResistance", 1D));
    }

    @Inject(method = "royalBossStepHeight", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat9(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("stepHeight", 3D));
    }

    @Inject(method = "royalBossMaxSingleHitDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat10(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("maxSingleHitDamage", 250D));
    }

    @Inject(method = "royalBossHitOverflowRate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat11(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("hitOverflowRate", 0.25D));
    }

    @Inject(method = "royalBossBiteReach", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat12(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("biteReach", 12D));
    }

    @Inject(method = "royalBossBiteDamageMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat13(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("biteDamageMultiplier", 1.6D));
    }

    @Inject(method = "royalBossBiteCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat14(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("biteCooldownTicks", 45));
    }

    @Inject(method = "royalBossMultiplayerScalingEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat15(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(RoyalStatOverrides.boolStat("multiplayerScalingEnabled", true));
    }

    @Inject(method = "royalBossScalingMaxPlayers", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat16(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("scalingMaxPlayers", 4));
    }

    @Inject(method = "royalBossHealthPerAdditionalPlayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat17(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("healthPerAdditionalPlayer", 0.35D));
    }

    @Inject(method = "royalBossDamagePerAdditionalPlayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat18(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("damagePerAdditionalPlayer", 0.1D));
    }

    @Inject(method = "royalBoundaryRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat19(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("royalBoundaryRadius", 192D));
    }

    @Inject(method = "royalBoundaryWarningRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat20(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("royalBoundaryWarningRadius", 160D));
    }

    @Inject(method = "royalBoundaryGraceTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat21(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("royalBoundaryGraceTicks", 60));
    }

    @Inject(method = "queenBeamDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat22(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamDamage", 45D));
    }

    @Inject(method = "queenBeamRange", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat23(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamRange", 100D));
    }

    @Inject(method = "queenBeamMinimumRange", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat24(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamMinimumRange", 22D));
    }

    @Inject(method = "queenBeamDurationTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat25(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamDurationTicks", 30));
    }

    @Inject(method = "queenBeamWindupTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat26(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamWindupTicks", 20));
    }

    @Inject(method = "queenBeamTravelTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat27(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamTravelTicks", 12));
    }

    @Inject(method = "queenBeamCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat28(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamCooldownTicks", 90));
    }

    @Inject(method = "queenBeamTracking", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat29(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamTracking", 0.1D));
    }

    @Inject(method = "queenBeamTerrainRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat30(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamTerrainRadius", 5D));
    }

    @Inject(method = "queenBeamTerrainCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat31(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamTerrainCap", 192));
    }

    @Inject(method = "queenBeamPhaseOneCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat32(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamPhaseOneCap", 1));
    }

    @Inject(method = "queenBeamPhaseTwoCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat33(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamPhaseTwoCap", 2));
    }

    @Inject(method = "queenBeamPhaseThreeCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat34(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBeamPhaseThreeCap", 3));
    }

    @Inject(method = "queenBeamPhaseThreeMaxVolleyChance", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat35(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBeamPhaseThreeMaxVolleyChance", 0.2D));
    }

    @Inject(method = "kingBeamDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat36(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingBeamDamage", 50D));
    }

    @Inject(method = "kingBeamRange", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat37(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingBeamRange", 100D));
    }

    @Inject(method = "kingBeamMinimumRange", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat38(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingBeamMinimumRange", 32D));
    }

    @Inject(method = "kingBeamDurationTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat39(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingBeamDurationTicks", 30));
    }

    @Inject(method = "kingBeamWindupTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat40(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingBeamWindupTicks", 20));
    }

    @Inject(method = "kingBeamTravelTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat41(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingBeamTravelTicks", 12));
    }

    @Inject(method = "kingBeamCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat42(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingBeamCooldownTicks", 90));
    }

    @Inject(method = "kingBeamTracking", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat43(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingBeamTracking", 0.1D));
    }

    @Inject(method = "kingBeamTerrainRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat44(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingBeamTerrainRadius", 5D));
    }

    @Inject(method = "kingBeamTerrainCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat45(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingBeamTerrainCap", 192));
    }

    @Inject(method = "royalDecreeCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat46(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("royalDecreeCooldownTicks", 700));
    }

    @Inject(method = "kingRoyalMusterCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat47(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingRoyalMusterCooldownTicks", 500));
    }

    @Inject(method = "kingRoyalMusterWindupTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat48(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingRoyalMusterWindupTicks", 30));
    }

    @Inject(method = "kingRoyalMusterDurationTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat49(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingRoyalMusterDurationTicks", 600));
    }

    @Inject(method = "kingRoyalMusterRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat50(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingRoyalMusterRadius", 32D));
    }

    @Inject(method = "kingRoyalMusterCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat51(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingRoyalMusterCap", 12));
    }

    @Inject(method = "queenBlackHoleRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat52(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBlackHoleRadius", 14D));
    }

    @Inject(method = "queenBlackHoleActiveTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat53(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBlackHoleActiveTicks", 120));
    }

    @Inject(method = "queenBlackHolePullStrength", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat54(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenBlackHolePullStrength", 0.16D));
    }

    @Inject(method = "queenBlackHoleBlockSuctionCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat55(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("queenBlackHoleBlockSuctionCap", 160));
    }

    @Inject(method = "kingFireballDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat56(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingFireballDamage", 48D));
    }

    @Inject(method = "kingFireballRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat57(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingFireballRadius", 4.5D));
    }

    @Inject(method = "kingFireballCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat58(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingFireballCooldownTicks", 150));
    }

    @Inject(method = "kingIceballDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat59(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingIceballDamage", 18D));
    }

    @Inject(method = "kingIceballRadius", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat60(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingIceballRadius", 5D));
    }

    @Inject(method = "kingIceballCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat61(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingIceballCooldownTicks", 190));
    }

    @Inject(method = "kingIceSpikeDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat62(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingIceSpikeDamage", 24D));
    }

    @Inject(method = "kingIceSpikeCooldownTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat63(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingIceSpikeCooldownTicks", 240));
    }

    @Inject(method = "kingElementalTerrainCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat64(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RoyalStatOverrides.intStat("kingElementalTerrainCap", 256));
    }

    @Inject(method = "royalBossSoundVolume", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat65(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("royalBossSoundVolume", 4.5D));
    }

    @Inject(method = "glimmerKingHealthMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat66(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingGlimmerHealthMultiplier", 1.75D));
    }

    @Inject(method = "glimmerKingDamageMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat67(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingGlimmerDamageMultiplier", 1.35D));
    }

    @Inject(method = "glimmerKingCooldownMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat68(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("kingGlimmerCooldownMultiplier", 0.65D));
    }

    @Inject(method = "glimmerQueenHealthMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat69(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenGlimmerHealthMultiplier", 1.75D));
    }

    @Inject(method = "glimmerQueenDamageMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat70(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenGlimmerDamageMultiplier", 1.35D));
    }

    @Inject(method = "glimmerQueenCooldownMultiplier", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void worldbosses$stat71(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(RoyalStatOverrides.doubleStat("queenGlimmerCooldownMultiplier", 0.65D));
    }
}
