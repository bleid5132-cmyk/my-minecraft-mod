package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ЛКМ с мечом паладина запускает приём вместо обычного удара. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientCombatMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void vestments$paladinAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CombatClient.onAttack((MinecraftClient) (Object) this)) cir.setReturnValue(false);
    }
}
