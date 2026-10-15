package com.bleid.vestments.mixin;

import com.bleid.vestments.horse.CartEntity;
import com.bleid.vestments.horse.HorseSprint;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Скорость коня под всадником: CTRL — рывок (только без повозки); в упряжке — медленнее. Приручение — связь с рогом. */
@Mixin(AbstractHorseEntity.class)
public abstract class HorseSpeedMixin {
    @Inject(method = "getSaddledSpeed", at = @At("RETURN"), cancellable = true, require = 0)
    private void vestments$sprint(PlayerEntity rider, CallbackInfoReturnable<Float> cir) {
        AbstractHorseEntity self = (AbstractHorseEntity) (Object) this;
        boolean cart = CartEntity.hitchedTo(self) != null;
        float v = cir.getReturnValueF();
        if (cart) v *= HorseSprint.CART;
        else if (rider != null && HorseSprint.sprinting(rider)) v *= HorseSprint.BOOST;
        cir.setReturnValue(v);
    }

    @Inject(method = "bondWithPlayer", at = @At("RETURN"), require = 0)
    private void vestments$tamed(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) com.bleid.vestments.horse.HorseBond.onTamed((AbstractHorseEntity) (Object) this, player);
    }
}
