package com.bleid.vestments;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Предмет облачения: в описании показывает свою способность и условие полного комплекта. */
public class VestmentArmorItem extends ArmorItem {
    private final String abilityKey;

    public VestmentArmorItem(ArmorMaterial material, Type type, Settings settings, String abilityKey) {
        super(material, type, settings);
        this.abilityKey = abilityKey;
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        return Optional.of(new SmallTooltipData(List.of(
                Text.translatable(abilityKey).formatted(Formatting.GOLD),
                Text.translatable("tooltip.vestments.full_set").formatted(Formatting.GRAY, Formatting.ITALIC))));
    }
}
