package com.bleid.vestments.paladin;

import com.bleid.vestments.classes.ClassItem;
import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Святой реликварий — во вторую руку вместо щита. Не блокирует удары, зато усиливает
 * «Наложение рук» и ауры паладина и понемногу лечит союзников рядом.
 */
public class ReliquaryItem extends Item implements ClassItem {
    public static final int RANK = 3;

    public ReliquaryItem(Settings settings) {
        super(settings);
    }

    @Override
    public String requiredClass() {
        return "paladin";
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.paladin"))
                .formatted(RankView.clientPaladin() ? Formatting.DARK_AQUA : Formatting.RED));
        lines.add(Text.translatable("tooltip.vestments.reliquary.1").formatted(Formatting.GOLD));
        lines.add(Text.translatable("tooltip.vestments.reliquary.2").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.vestments.reliquary.3").formatted(Formatting.GRAY));
        boolean ok = RankView.clientPaladinRank() >= RANK;
        lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(PaladinRanks.nameKey(RANK)))
                .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        return Optional.of(new SmallTooltipData(lines));
    }

    /** Реликварий во второй руке у паладина нужного звания. */
    public static boolean held(net.minecraft.entity.player.PlayerEntity p) {
        return p.getOffHandStack().getItem() instanceof ReliquaryItem
                && com.bleid.vestments.service.RankView.hasPaladin(p, RANK);
    }
}
