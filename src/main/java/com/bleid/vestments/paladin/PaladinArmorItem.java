package com.bleid.vestments.paladin;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.classes.ClassItem;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Часть доспеха паладина: способность, условие полного комплекта, нужное звание и класс. */
public class PaladinArmorItem extends ArmorItem implements ClassItem {
    public PaladinSet set;

    public PaladinArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public String requiredClass() {
        return "paladin";
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.paladin"))
                .formatted(RankView.clientPaladin() ? Formatting.DARK_AQUA : Formatting.RED));
        if (set != null) {
            for (Text t : set.abilities(getType())) lines.add(t.copy().formatted(Formatting.GOLD));
            lines.add(Text.translatable("tooltip.vestments.full_set").formatted(Formatting.GRAY, Formatting.ITALIC));
            boolean ok = RankView.clientPaladinRank() >= set.rank;
            lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(PaladinRanks.nameKey(set.rank)))
                    .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        }
        return Optional.of(new SmallTooltipData(lines));
    }
}
