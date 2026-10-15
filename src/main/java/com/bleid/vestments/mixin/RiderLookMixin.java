package com.bleid.vestments.mixin;

import com.bleid.vestments.horse.CartEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Всадник коня в упряжке поворачивает голову только в пределах 180° (как в лодке). */
@Mixin(Entity.class)
public abstract class RiderLookMixin {
    @Inject(method = "onPassengerLookAround", at = @At("HEAD"))
    private void vestments$cartLook(Entity passenger, CallbackInfo ci) {
        if ((Object) this instanceof AbstractHorseEntity h) CartEntity.clampRider(h, passenger);
    }
}
