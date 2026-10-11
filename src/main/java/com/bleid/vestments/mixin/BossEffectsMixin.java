package com.bleid.vestments.mixin;

import com.bleid.vestments.boss.BossEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** «Истощение» ослабляет любое лечение, «Оглушение» не даёт прыгать. */
@Mixin(LivingEntity.class)
public abstract class BossEffectsMixin {
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float vestments$exhaustion(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.hasStatusEffect(BossEffects.EXHAUSTION) ? amount * 0.25f : amount;
    }

    @Inject(method = "getJumpVelocity", at = @At("HEAD"), cancellable = true)
    private void vestments$stunJump(CallbackInfoReturnable<Float> cir) {
        if (((LivingEntity) (Object) this).hasStatusEffect(BossEffects.STUN)) cir.setReturnValue(0f);
    }
}
