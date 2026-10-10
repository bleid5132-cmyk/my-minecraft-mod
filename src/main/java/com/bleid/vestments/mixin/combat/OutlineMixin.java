package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.HammerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Цель «Молота правосудия» под прицелом получает контур. */
@Mixin(MinecraftClient.class)
public abstract class OutlineMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void vestments$hammerTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (HammerClient.highlighted(entity)) cir.setReturnValue(true);
    }
}
