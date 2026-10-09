package com.bleid.vestments.mixin;

import com.bleid.vestments.paladin.PaladinBonus;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** «Стойкость» доспеха паладина: срезает итоговый урон после брони и зачарований. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void vestments$paladinReduce(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        float v = cir.getReturnValueF();
        float r = PaladinBonus.reduce((LivingEntity) (Object) this, source, v);
        if (r != v) cir.setReturnValue(r);
    }
}
