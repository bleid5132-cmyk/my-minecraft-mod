package com.bleid.vestments.mixin;

import com.bleid.vestments.patriarch.SoulAllies;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Призванные души не исчезают сами (в т.ч. на «Мирной» сложности) — их временем жизни управляет SoulAllies. */
@Mixin(MobEntity.class)
public abstract class MobEntitySoulMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void vestments$keepSoul(CallbackInfo ci) {
        if (((MobEntity) (Object) this).getCommandTags().contains(SoulAllies.TAG)) ci.cancel();
    }
}
