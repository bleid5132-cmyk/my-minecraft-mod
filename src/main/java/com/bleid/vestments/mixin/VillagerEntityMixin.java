package com.bleid.vestments.mixin;

import com.bleid.vestments.church.ChurchTrades;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** После обычных скидок за репутацию добавляем скидку священнику у церковного священника. */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void vestments$priestDiscount(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp) {
            ChurchTrades.apply((VillagerEntity) (Object) this, sp);
        }
    }
}
