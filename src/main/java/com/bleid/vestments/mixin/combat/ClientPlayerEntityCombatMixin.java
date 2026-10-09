package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Во время удара ноги «заняты»: ходьба сильно замедляется. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityCombatMixin {
    @Shadow public Input input;

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void vestments$lockMove(CallbackInfo ci) {
        float f = CombatClient.moveFactor();
        if (f < 1f) {
            input.movementForward *= f;
            input.movementSideways *= f;
        }
    }
}
