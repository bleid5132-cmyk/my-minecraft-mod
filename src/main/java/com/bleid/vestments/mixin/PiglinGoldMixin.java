package com.bleid.vestments.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PiglinBrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Золотой парадный доспех самурая: пиглины считают его золотым (не нападают). */
@Mixin(PiglinBrain.class)
public abstract class PiglinGoldMixin {
    @Inject(method = "wearsGoldArmor", at = @At("RETURN"), cancellable = true)
    private static void vestments$samuraiGold(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && com.bleid.vestments.samurai.SamuraiGear.wearsAny(entity, "kinkou")) cir.setReturnValue(true);
    }
}
