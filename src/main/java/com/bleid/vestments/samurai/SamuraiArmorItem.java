package com.bleid.vestments.samurai;

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

/** Часть доспеха самурая: бонус полного комплекта (см. SamuraiServer). */
public class SamuraiArmorItem extends ArmorItem implements ClassItem {
    public final String set;

    public SamuraiArmorItem(ArmorMaterial material, Type type, Settings settings, String set) {
        super(material, type, settings);
        this.set = set;
    }

    @Override
    public String requiredClass() {
        return "samurai";
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.samurai"))
                .formatted("samurai".equals(RankView.clientClass) ? Formatting.DARK_AQUA : Formatting.RED));
        lines.add(Text.translatable("tooltip.vestments.samurai_set." + set).formatted(Formatting.GOLD));
        lines.add(Text.translatable("tooltip.vestments.full_set").formatted(Formatting.GRAY, Formatting.ITALIC));
        return Optional.of(new SmallTooltipData(lines));
    }
}
