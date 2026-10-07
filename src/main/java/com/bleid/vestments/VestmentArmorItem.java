package com.bleid.vestments;

import com.bleid.vestments.gear.GearSet;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.Ranks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Часть облачения сана: в описании — своя способность, условие полного комплекта и нужный сан. */
public class VestmentArmorItem extends ArmorItem {
    /** Комплект, к которому относится предмет (проставляет RankGear). */
    public GearSet set;

    public VestmentArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        if (set != null) {
            Text ability = set.ability(getType());
            lines.add(ability != null ? ability.copy().formatted(Formatting.GOLD)
                    : Text.translatable("tooltip.vestments.ability.protection").formatted(Formatting.GRAY));
            lines.add(Text.translatable("tooltip.vestments.full_set").formatted(Formatting.GRAY, Formatting.ITALIC));
            boolean ok = RankView.clientRank() >= set.rank;
            lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(Ranks.nameKey(set.rank)))
                    .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        }
        return Optional.of(new SmallTooltipData(lines));
    }
}
