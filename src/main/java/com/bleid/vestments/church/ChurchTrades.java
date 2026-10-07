package com.bleid.vestments.church;

import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffer;

/**
 * Скидки у церковного священника — только игрокам-священникам. Растут с саном;
 * III степень (Епископат) получает всё за изумруды бесплатно.
 */
public final class ChurchTrades {
    /** Особая цена-метка «бесплатно»: TradeOfferMixin в этом случае не требует оплаты. */
    public static final int FREE = -1_000_000;

    /** Скидка по сану (I и II степень), доля цены. */
    private static final float[] DISCOUNT = { 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.55f };
    /** Епископат: скидка на сделки, где житель покупает у игрока (изумруды из ничего не даём). */
    private static final float EPISCOPATE_SELL_DISCOUNT = 0.60f;

    private ChurchTrades() { }

    public static void apply(VillagerEntity villager, ServerPlayerEntity player) {
        if (!ChurchPriests.isChurchPriest(villager) || !ServicePoints.canServe(player)) return;
        int rank = ServicePoints.rank(player);
        boolean episcopate = Ranks.degreeOf(rank) == 2;
        for (TradeOffer offer : villager.getOffers()) {
            boolean paysEmeralds = offer.getOriginalFirstBuyItem().isOf(Items.EMERALD) && offer.getSecondBuyItem().isEmpty();
            if (episcopate && paysEmeralds) {
                offer.increaseSpecialPrice(FREE - offer.getSpecialPrice());
                continue;
            }
            float pct = episcopate ? EPISCOPATE_SELL_DISCOUNT : DISCOUNT[Math.min(rank, DISCOUNT.length - 1)];
            int base = offer.getOriginalFirstBuyItem().getCount();
            int cut = Math.max(1, (int) Math.floor(base * pct));
            offer.increaseSpecialPrice(-cut);
        }
        Text msg = episcopate
                ? Text.translatable("message.vestments.church_free")
                : Text.translatable("message.vestments.church_discount",
                        Math.round(DISCOUNT[Math.min(rank, DISCOUNT.length - 1)] * 100));
        player.sendMessage(msg.copy().formatted(Formatting.GOLD), true);
    }
}
