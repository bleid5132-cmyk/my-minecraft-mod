package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.HammerClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Контур цели молота — золотой. */
@Mixin(Entity.class)
public abstract class OutlineColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void vestments$gold(CallbackInfoReturnable<Integer> cir) {
        if (HammerClient.highlighted((Entity) (Object) this)) cir.setReturnValue(0xFFD24A);
    }
}
