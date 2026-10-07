package com.bleid.vestments.mixin;

import com.bleid.vestments.church.ChurchTrades;
import net.minecraft.item.ItemStack;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Бесплатная сделка: товар появляется в слоте результата при пустых слотах оплаты. */
@Mixin(MerchantInventory.class)
public abstract class MerchantInventoryMixin {
    @Shadow @Final private Merchant merchant;
    @Shadow @Nullable private TradeOffer tradeOffer;
    @Shadow private int offerIndex;
    @Shadow private int merchantRewardedExperience;

    @Shadow public abstract ItemStack getStack(int slot);
    @Shadow public abstract void setStack(int slot, ItemStack stack);

    @Inject(method = "updateOffers", at = @At("HEAD"), cancellable = true)
    private void vestments$freeTrade(CallbackInfo ci) {
        if (!getStack(0).isEmpty() || !getStack(1).isEmpty()) return;
        TradeOfferList offers = merchant.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) return;
        TradeOffer offer = offers.get(offerIndex);
        if (offer.getSpecialPrice() > ChurchTrades.FREE / 2 || offer.isDisabled()) return;
        tradeOffer = offer;
        setStack(2, offer.copySellItem());
        merchantRewardedExperience = offer.getMerchantExperience();
        ci.cancel();
    }
}
