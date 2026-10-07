package com.bleid.vestments.mixin;

import com.bleid.vestments.church.ChurchTrades;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Сделка с ценой-меткой FREE ничего не стоит (и на сервере, и в окне торговли у игрока). */
@Mixin(TradeOffer.class)
public abstract class TradeOfferMixin {
    @Shadow public abstract int getSpecialPrice();

    @Inject(method = "getAdjustedFirstBuyItem", at = @At("HEAD"), cancellable = true)
    private void vestments$free(CallbackInfoReturnable<ItemStack> cir) {
        if (getSpecialPrice() <= ChurchTrades.FREE / 2) cir.setReturnValue(ItemStack.EMPTY);
    }
}
